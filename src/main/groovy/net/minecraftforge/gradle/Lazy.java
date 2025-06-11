package net.minecraftforge.gradle;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

sealed interface Lazy<T> extends Supplier<T> {
    static <T> Lazy<T> of(Callable<T> callable) {
        return of(Closures.callable(callable));
    }

    static <T> Lazy<T> of(Closure<T> closure) {
        return new Simple<>(closure);
    }

    final class Simple<T> implements Lazy<T> {
        private final Closure<T> closure;
        private @Nullable T value;

        private Simple(Closure<T> closure) {
            this.closure = closure;
        }

        @Override
        public T get() {
            return this.value == null ? this.value = Closures.invoke(this.closure) : this.value;
        }
    }

    sealed interface Actionable<T> extends Lazy<T> {
        static <T> Actionable<T> of(Callable<T> callable) {
            return of(Closures.callable(callable));
        }

        static <T> Actionable<T> of(Closure<T> closure) {
            return new Simple<>(closure);
        }

        boolean isPresent();

        default void ifPresent(Action<? super T> action) {
            if (this.isPresent()) 
                action.execute(this.get());
        }

        void map(Action<? super T> action);

        /// Represents a lazily computed value with the ability to optionally work with it using [#ifPresent(Action)] and
        /// safely mutate it using [#map(Action)].
        final class Simple<T> implements Actionable<T> {
            private final Closure<T> closure;
            private @Nullable T value;

            private boolean present = false;

            private Simple(Closure<T> closure) {
                this.closure = closure.compose(Closures.runnable(() -> this.present = true));
            }

            public void map(Action<? super T> action) {
                this.present = true;
                this.closure.andThen(Closures.<T>unaryOperator(value -> {
                    action.execute(value);
                    return value;
                }));
            }

            public boolean isPresent() {
                return this.present;
            }

            @Override
            public T get() {
                return this.value == null ? this.value = Closures.invoke(this.closure) : this.value;
            }
        }
    }
}
