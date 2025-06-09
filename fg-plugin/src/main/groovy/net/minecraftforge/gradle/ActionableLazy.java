/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/// Represents a lazily computed value with the ability to optionally work with it using [#ifPresent(Action)] and safely
/// mutate it using [#map(Action)].
final class ActionableLazy<T> implements Supplier<T> {
    private final Closure<T> closure;
    private @Nullable T value;

    private boolean present = false;

    static <T> ActionableLazy<T> of(Closure<T> closure) {
        return new ActionableLazy<>(closure);
    }

    private ActionableLazy(Closure<T> closure) {
        this.closure = closure.compose(Closures.runnable(() -> this.present = true));
    }

    void map(Action<? super T> action) {
        this.present = true;
        this.closure.andThen(Closures.<T>unaryOperator(value -> {
            action.execute(value);
            return value;
        }));
    }

    void ifPresent(Action<? super T> action) {
        if (!this.isPresent()) return;
        action.execute(this.get());
    }

    boolean isPresent() {
        return this.present;
    }

    @Override
    public T get() {
        return this.value == null ? this.value = Closures.invoke(this.closure) : this.value;
    }
}
