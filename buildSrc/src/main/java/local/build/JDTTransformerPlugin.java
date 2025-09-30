/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package local.build;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import javax.inject.Inject;

@SuppressWarnings("unused")
abstract class JDTTransformerPlugin implements Plugin<Project> {
    @Inject
    public JDTTransformerPlugin() { }

    @Override
    public void apply(Project project) {
        project.getExtensions().create("jdt", JDTTransformerExtension.class);

        project.getPluginManager().withPlugin("java", javaPlugin ->
            JDTClassTransformer.register(project)
        );
    }
}
