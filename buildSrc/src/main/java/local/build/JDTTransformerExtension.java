/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package local.build;

import org.gradle.api.attributes.HasConfigurableAttributes;

public interface JDTTransformerExtension {
    default void transform(HasConfigurableAttributes<?> dependency) {
        JDTClassTransformer.transform(dependency);
    }
}
