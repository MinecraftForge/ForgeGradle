/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

import javax.inject.Inject;
import java.util.List;

final class ForgeGradleComponentMetadataRules {
    @CacheableRule
    abstract static class AlwaysUseMatureStatus implements ComponentMetadataRule {
        private final List<String> modules;

        @Inject
        public AlwaysUseMatureStatus(List<String> modules) {
            this.modules = modules;
        }

        @Override
        public void execute(ComponentMetadataContext context) {
            var details = context.getDetails();
            if (!modules.isEmpty() && !modules.contains(details.getId().getModule().toString())) return;

            var statusScheme = details.getStatusScheme();
            var status = statusScheme.contains("release") ? "release" : statusScheme.get(statusScheme.size() - 1);
            details.setStatus(status);
        }
    }
}
