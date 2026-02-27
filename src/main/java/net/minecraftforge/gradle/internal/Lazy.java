/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

class Lazy<T> implements Supplier<T> {
    private final Supplier<T> supplier;
    private boolean computed = false;
    private @Nullable T value = null;

    public Lazy(final Supplier<T> supplier) {
        this.supplier = supplier;
    }

    @Override
    public final @Nullable T get() {
        if (this.computed)
            return this.value;

        synchronized (this) {
            if (this.computed)
                return this.value;
            this.computed = true;
            return this.value = supplier.get();
        }
    }
}
