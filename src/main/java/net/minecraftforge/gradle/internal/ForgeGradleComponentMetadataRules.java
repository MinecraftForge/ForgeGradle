package net.minecraftforge.gradle.internal;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

final class ForgeGradleComponentMetadataRules {
    @CacheableRule
    abstract static class AlwaysUseMatureStatus implements ComponentMetadataRule {
        @Override
        public void execute(ComponentMetadataContext context) {
            var details = context.getDetails();

            var statusScheme = details.getStatusScheme();
            var status = statusScheme.contains("release") ? "release" : statusScheme.get(statusScheme.size() - 1);
            details.setStatus(status);
        }
    }
}
