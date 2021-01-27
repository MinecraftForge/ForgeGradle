package net.minecraftforge.gradle.common.util;

import com.google.common.collect.ImmutableMap;

import net.minecraftforge.artifactural.gradle.ReflectionUtils;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalDependency;
import org.gradle.api.artifacts.component.ComponentArtifactIdentifier;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.query.ArtifactResolutionQuery;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ArtifactResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ComponentResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.UnresolvedComponentResult;
import org.gradle.api.component.Artifact;
import org.gradle.api.component.Component;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.dependencies.DefaultDependencyArtifact;
import org.gradle.api.internal.artifacts.dependencies.DefaultExternalModuleDependency;
import org.gradle.api.internal.artifacts.query.ArtifactResolutionQueryFactory;
import org.gradle.api.internal.artifacts.result.DefaultArtifactResolutionResult;
import org.gradle.api.internal.artifacts.result.DefaultComponentArtifactsResult;
import org.gradle.api.internal.artifacts.result.DefaultResolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedArtifactResult;
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedComponentResult;
import org.gradle.api.internal.attributes.ImmutableAttributes;
import org.gradle.internal.Describables;
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.jvm.JvmLibrary;
import org.gradle.language.base.artifact.SourcesArtifact;
import org.gradle.language.java.artifact.JavadocArtifact;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

class DeobfTransformerHacks {
    static void apply(Project project, Set<String> whitelist) {
        ReflectionUtils.<ArtifactResolutionQueryFactory>alter(project.getDependencies(), "resolutionQueryFactory", orig -> {
            return () -> {
                log(project, "Creating artifact resolution query for: " + project.getDisplayName());
                return new ConfigurationBasedResolutionQuery(project, whitelist, orig.createArtifactResolutionQuery());
            };
        });
    }

    private static void log(Project project, String message) {
        if (System.getProperty("fg.debugDeobfHacks", "false").equals("true"))
            project.getLogger().lifecycle(message);
    }

    public static class ConfigurationBasedResolutionQuery implements ArtifactResolutionQuery {
        private static final Map<Class<? extends Artifact>, String> ARTIFACT_TYPE_TO_CLASSIFIERS = ImmutableMap.<Class<? extends Artifact>, String>builder()
                                                                                                     .put(JavadocArtifact.class, "javadoc")
                                                                                                     .put(SourcesArtifact.class, "sources")
                                                                                                     .build();

        private static final Map<String, AtomicInteger> CONFIGURATION_COUNTERS = new ConcurrentHashMap<>();

        private final Project                        project;
        private final Set<String>                    whitelist;
        private final ArtifactResolutionQuery        delegate;
        private final Set<ModuleComponentIdentifier> requestedIdentifiers = new HashSet<>();
        private final Set<Class<? extends Artifact>> requestedArtifacts   = new HashSet<>();
        private final Set<Class<? extends Artifact>> allRequestedArtifacts = new HashSet<>();

        public ConfigurationBasedResolutionQuery(final Project project, final Set<String> whitelist, final ArtifactResolutionQuery delegate) {
            this.project = project;
            this.whitelist = whitelist;
            this.delegate = delegate;
        }

        private void log(String message) {
            DeobfTransformerHacks.log(this.project, message);
        }

        @Nonnull
        @Override
        public ArtifactResolutionQuery forComponents(final Iterable<? extends ComponentIdentifier> iterable) {
            iterable.forEach(id -> {
                if (id instanceof ModuleComponentIdentifier) {
                    ModuleComponentIdentifier mod = (ModuleComponentIdentifier)id;
                    if (whitelist.contains(mod.getGroup() + ':' + mod.getModule() + ':' + mod.getVersion()))
                        this.requestedIdentifiers.add(mod);
                }
            });

            this.delegate.forComponents(iterable);

            return this;
        }

        @Nonnull
        @Override
        public ArtifactResolutionQuery forComponents(@Nonnull final ComponentIdentifier... componentIdentifiers) {
            return this.forComponents(Arrays.asList(componentIdentifiers));
        }

        @Nonnull
        @Override
        public ArtifactResolutionQuery forModule(@Nonnull final String group, @Nonnull final String name, @Nonnull final String version) {
            return this.forComponents(new DefaultModuleComponentIdentifier(DefaultModuleIdentifier.newId(group, name), version));
        }

        @Nonnull
        @Override
        public ArtifactResolutionQuery withArtifacts(@Nonnull final Class<? extends Component> componentType, @SuppressWarnings("unchecked") @Nonnull final Class<? extends Artifact>... artifactTypes) {
            return this.withArtifacts(componentType, Arrays.asList(artifactTypes));
        }

        @Nonnull
        @Override
        public ArtifactResolutionQuery withArtifacts(@Nonnull final Class<? extends Component> componentType, @Nonnull final Collection<Class<? extends Artifact>> artifactTypes) {
            if (componentType == JvmLibrary.class)
                artifactTypes.stream().filter(ARTIFACT_TYPE_TO_CLASSIFIERS::containsKey).forEach(this.requestedArtifacts::add);

            this.delegate.withArtifacts(componentType, artifactTypes);
            this.allRequestedArtifacts.addAll(artifactTypes);

            return this;
        }

        @Nonnull
        @Override
        public ArtifactResolutionResult execute() {
            log("Starting artifact resolve for: " + requestedIdentifiers.size() + " identifiers.");
            final Map<ComponentIdentifier, ComponentResult> ourComponentResults = new HashMap<>();
            final Map<String, ComponentIdentifier> identifierMap = new HashMap<>();

            for (final ModuleComponentIdentifier request : this.requestedIdentifiers) {
                log("Resolving: " + request);
                identifierMap.put(request.toString(), request);

                try {
                    final DefaultComponentArtifactsResult ourResult = new DefaultComponentArtifactsResult(request);

                    for (final Class<? extends Artifact> artifactType : this.requestedArtifacts) {
                        final String classifier = ARTIFACT_TYPE_TO_CLASSIFIERS.get(artifactType);

                        final DefaultModuleComponentArtifactIdentifier identifier = new DefaultModuleComponentArtifactIdentifier(request, request.getModule(), request.getGroup(), "test", classifier);

                        try {
                            final ExternalDependency dependency = new DefaultExternalModuleDependency(request.getGroup(), request.getModule(), request.getVersion());
                            //dependency.attributes(atr -> atr.attribute(DeobfTransformer.FG_MAPPED, true));
                            dependency.addArtifact(new DefaultDependencyArtifact(dependency.getName(), "jar", "jar", classifier, null));

                            final Configuration configuration = creatDependencyFor(classifier, dependency);
                            configuration.getDependencies().add(dependency);
                            log("  Resolving configuration: " + configuration + " for: " + dependency);
                            final Set<File> resolvedFiles = configuration.getResolvedConfiguration().getFiles();

                            if (resolvedFiles.isEmpty())
                                throw new IllegalStateException(String.format("Could not resolve artifact: %s with classifier: %s", request.getDisplayName(), classifier));

                            log("  Resolving artifact successful for: " + dependency);
                            ourResult.addArtifact(new DefaultResolvedArtifactResult(identifier, ImmutableAttributes.EMPTY, Describables.of(request.getDisplayName()), artifactType, resolvedFiles.iterator().next()));
                        } catch (Exception e) {
                            log("  Failed to find artifact: " + e.getMessage());
                            ourResult.addArtifact(new DefaultUnresolvedArtifactResult(identifier, artifactType, e));
                        }
                    }

                    ourComponentResults.put(request, ourResult);
                } catch (Exception exception) {
                    log("  Component resolution failure: " + exception.getMessage());
                    ourComponentResults.put(request, new DefaultUnresolvedComponentResult(request, exception));
                }
            }
            log("  Completed the configuration based lookup. Resulted in: " + ourComponentResults.size() + " results.");

            log("Executing inner queries.");
            final ArtifactResolutionResult innerResult = this.delegate.execute();
            final Map<ComponentIdentifier, ComponentResult> innerComponentResults = innerResult.getComponents().stream().collect(Collectors.toMap(ComponentResult::getId, Function.identity()));
            log("  Inner component resolve resulted in: " + innerComponentResults.size() + " results.");

            final Map<String, ComponentIdentifier> ourComponentsKey = ourComponentResults.keySet().stream().collect(Collectors.toMap(ComponentIdentifier::getDisplayName, Function.identity()));
            final Map<String, ComponentIdentifier> innerComponentsKey = innerComponentResults.keySet().stream().collect(Collectors.toMap(ComponentIdentifier::getDisplayName, Function.identity()));

            // Copy over all the parents results that wern't white listed
            final Set<ComponentResult> combinedResults = new HashSet<>();
            innerComponentResults.entrySet().stream().filter(e -> !ourComponentResults.containsKey(e.getKey())).map(Map.Entry::getValue).forEach(combinedResults::add);

            //We only use their keys. Ours are really irrelevant since they are of the wrong inner type.
            final Set<String> identifiers = new HashSet<>(ourComponentsKey.keySet());
            log("Determined that " + identifiers.size() + " need potential merging.");

            for (final String identifier : identifiers) {
                final ComponentResult ourResult = ourComponentsKey.containsKey(identifier) ? ourComponentResults.get(ourComponentsKey.get(identifier)) : null;
                final ComponentResult theirResult = innerComponentsKey.containsKey(identifier) ? innerComponentResults.get(innerComponentsKey.get(identifier)) : null;

                if (ourResult == null) {
                    combinedResults.add(theirResult);
                    continue;
                }

                log("Executing result merge for identifier: " + identifier);
                log("  Merge inputs: " + ourResult + " - " + theirResult);

                //It was not requested from them
                if (theirResult == null) {
                    log("    Taking configuration based result for: " + ourResult + " was not requested from inner.");
                    combinedResults.add(ourResult);
                } else if (theirResult instanceof UnresolvedComponentResult) { //They failed, we always take ours then.
                    log("    Taking configuration based result for: " + ourResult + " inner failed to find find any results.");
                    combinedResults.add(ourResult);
                } else if (ourResult instanceof UnresolvedComponentResult) { //If we fail, but they succeed, grab them.
                    log("    Taking inner based result for: " + ourResult + " configuration failed to find find any results.");
                    combinedResults.add(theirResult);
                } else if (ourResult instanceof ComponentArtifactsResult && theirResult instanceof ComponentArtifactsResult) {
                    log("    Merging result for: " + ourResult + " both inner and configuration found artifacts.");
                    final ComponentArtifactsResult ourSuccessResult = (ComponentArtifactsResult) ourResult;
                    final ComponentArtifactsResult theirSuccessResult = (ComponentArtifactsResult) theirResult;

                    final Set<ArtifactResult> combinedArtifactResults = new HashSet<>();
                    for (final Class<? extends Artifact> requestedArtifact : allRequestedArtifacts)
                    {
                        final Set<ArtifactResult> ourArtifacts = ourSuccessResult.getArtifacts(requestedArtifact);
                        final Set<ArtifactResult> theirArtifacts = theirSuccessResult.getArtifacts(requestedArtifact);

                        if (ourArtifacts.isEmpty()) {
                            log("      Configuration based resolution did not find any artifacts for type: " + requestedArtifact + " for: " + identifier + ". Taking their results: " + theirArtifacts.size());
                        } else if (theirArtifacts.isEmpty()) {
                            log("      Inner based resolution did not find any artifacts for type: " + requestedArtifact + " for: " + identifier + ". Taking our results: " + ourArtifacts.size());
                            combinedArtifactResults.addAll(ourArtifacts);
                        } else {
                            log("      Both inner and configuration found artifacts of type: " + requestedArtifact + " for: " + identifier);
                            final Map<ComponentArtifactIdentifier, ArtifactResult> ourArtifactsById = ourArtifacts.stream().collect(Collectors.toMap(ArtifactResult::getId, Function.identity()));
                            final Map<ComponentArtifactIdentifier, ArtifactResult> theirArtifactsById = theirArtifacts.stream().collect(Collectors.toMap(ArtifactResult::getId, Function.identity()));

                            final Map<String, ComponentArtifactIdentifier> ourArtIds = ourArtifactsById.keySet().stream().collect(Collectors.toMap(ComponentArtifactIdentifier::getDisplayName, Function.identity()));
                            final Map<String, ComponentArtifactIdentifier> innerArtIds = ourArtifactsById.keySet().stream().collect(Collectors.toMap(ComponentArtifactIdentifier::getDisplayName, Function.identity()));

                            final Set<String> artifactIdentifiers = new HashSet<>(ourArtIds.keySet());
                            artifactIdentifiers.addAll(innerArtIds.keySet());

                            for (final String artifactIdentifier : artifactIdentifiers) {
                                log("      Looking up for component artifact identifier: " + artifactIdentifier);
                                final ArtifactResult ourArtifactResult = ourArtifactsById.get(ourArtIds.get(artifactIdentifier));
                                final ArtifactResult theirArtifactResult = theirArtifactsById.get(innerArtIds.get(artifactIdentifier));

                                if (ourArtifactResult instanceof ResolvedArtifactResult) {
                                    log("        Taking our resolved artifact for: " + artifactIdentifier + " -> " + ((ResolvedArtifactResult) ourArtifactResult).getFile().getAbsolutePath());
                                    combinedArtifactResults.add(ourArtifactResult);
                                } else if (theirArtifactResult instanceof ResolvedArtifactResult) {
                                    log("        Taking their resolved artifact for: " + artifactIdentifier + " -> " + ((ResolvedArtifactResult) theirArtifactResult).getFile().getAbsolutePath());
                                    combinedArtifactResults.add(theirArtifactResult);
                                } else if (ourArtifactResult == null && theirArtifactResult != null) {
                                    log("        Taking their artifact for: " + artifactIdentifier);
                                    combinedArtifactResults.add(theirArtifactResult);
                                } else if (ourArtifactResult != null && theirArtifactResult == null) {
                                    log("        Taking our artifact for: " + artifactIdentifier);
                                    combinedArtifactResults.add(ourArtifactResult);
                                } else if (ourArtifactResult != null) {
                                    log("        Taking our last resort artifact for: " + artifactIdentifier);
                                    combinedArtifactResults.add(ourArtifactResult);
                                }
                            }
                        }
                    }

                    final DefaultComponentArtifactsResult ourComponentArtifactResult = new DefaultComponentArtifactsResult(
                      innerComponentsKey.getOrDefault(identifier, identifierMap.getOrDefault(identifier, ourComponentsKey.getOrDefault(identifier, null)))
                    );
                    combinedArtifactResults.forEach(ourComponentArtifactResult::addArtifact);

                    combinedResults.add(ourComponentArtifactResult);
                }
            }

            return new DefaultArtifactResolutionResult(combinedResults);
        }

        @Nonnull
        private Configuration creatDependencyFor(final String classifier, final Dependency dependency) {
            final String key = String.format("%s.%s-%s-%s", dependency.getGroup(), dependency.getName(), dependency.getVersion(), classifier);
            int index = CONFIGURATION_COUNTERS.computeIfAbsent(key, s -> new AtomicInteger()).incrementAndGet();

            Configuration ret = this.project.getConfigurations().create(String.format("_dobf_hack_lookup_%s-%s", key, index));
            ret.getAttributes().attribute(DeobfTransformer.FG_DEOBF, classifier);
            return ret;
        }
    }

}
