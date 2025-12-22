/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.util.os.OS;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.gradle.api.Action;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeDisambiguationRule;
import org.gradle.api.attributes.MultipleCandidatesDetails;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.gradle.api.provider.ValueSourceSpec;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Set;

final class ForgeAttributes {
    static final class OperatingSystem {
        static final Attribute<String> ATTRIBUTE = Attribute.of("net.minecraftforge.native.operatingSystem", String.class);

        static abstract class DisambiguationRule implements AttributeDisambiguationRule<String> {
            private final OS current;

            @Inject
            public DisambiguationRule(OS current) {
                this.current = current;
            }

            @Override
            public void execute(MultipleCandidatesDetails<String> details) {
                var options = details.getCandidateValues().stream().map(OS::byKey).toList();

                if (options.contains(current)) {
                    details.closestMatch(current.key());
                    return;
                }

                for (var os : options) {
                    var fallback = execute(current, os);
                    if (fallback != null) {
                        details.closestMatch(fallback);
                        return;
                    }
                }
            }

            @NullUnmarked
            private String execute(OS current, OS os) {
                return current == os
                    ? os.key()
                    : os != null ? execute(current, os.fallback()) : null;
            }
        }

        abstract static class CurrentValue implements ValueSource<OS, CurrentValue.Parameters> {
            interface Parameters extends ValueSourceParameters {
                Action<? super ValueSourceSpec<Parameters>> DEFAULT = spec -> spec.getParameters().getAllowedOperatingSystems().set(Set.of(OS.WINDOWS, OS.MACOS, OS.LINUX));

                SetProperty<OS> getAllowedOperatingSystems();
            }

            @Inject
            public CurrentValue() { }

            @Override
            public @Nullable OS obtain() {
                var allowed = getParameters().getAllowedOperatingSystems().getOrElse(EnumSet.noneOf(OS.class)).toArray(new OS[0]);
                return allowed.length > 0
                    ? OS.current(allowed)
                    : OS.current();
            }
        }
    }

    static final class MappingsChannel {
        static final Attribute<String> ATTRIBUTE = Attribute.of("net.minecraftforge.mappings.channel", String.class);

        static abstract class DisambiguationRule implements AttributeDisambiguationRule<String> {
            @Inject
            public DisambiguationRule() { }

            @Override
            public void execute(MultipleCandidatesDetails<String> details) {
                var options = details.getCandidateValues();
                if (options.contains("official")) {
                    details.closestMatch("official");
                } else if (options.contains("parchment")) {
                    details.closestMatch("parchment");
                }
            }
        }
    }

    static final class MappingsVersion {
        static final Attribute<String> ATTRIBUTE = Attribute.of("net.minecraftforge.mappings.version", String.class);

        static final Comparator<String> COMPARATOR = Util.versionComparator();
    }
}
