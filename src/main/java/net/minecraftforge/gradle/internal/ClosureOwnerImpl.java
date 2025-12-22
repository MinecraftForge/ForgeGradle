/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.InvokerHelper;

abstract class ClosureOwnerImpl<D> extends GroovyObjectSupport implements ClosureOwnerInternal<D> {
    final Object originalOwner;
    final D ownerDelegate;

    ClosureOwnerImpl(Object originalOwner, D ownerDelegate) {
        this.originalOwner = originalOwner;
        this.ownerDelegate = ownerDelegate;
    }

    @Override
    public D getOwnerDelegate() {
        return this.ownerDelegate;
    }

    @Override
    public Object invokeMethod(String name, Object args) {
        try {
            return super.invokeMethod(name, args);
        } catch (Exception e) {
            try {
                return InvokerHelper.invokeMethod(this.originalOwner, name, args);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    @Override
    public Object getProperty(String propertyName) {
        try {
            return super.getProperty(propertyName);
        } catch (Exception e) {
            try {
                return InvokerHelper.getProperty(originalOwner, propertyName);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    @Override
    public void setProperty(String propertyName, Object newValue) {
        try {
            super.setProperty(propertyName, newValue);
        } catch (Exception e) {
            try {
                InvokerHelper.setProperty(originalOwner, propertyName, newValue);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    static class MinecraftDependencyImpl extends ClosureOwnerImpl<net.minecraftforge.gradle.MinecraftDependency> implements ClosureOwnerInternal.MinecraftDependency {
        MinecraftDependencyImpl(Object originalOwner, net.minecraftforge.gradle.MinecraftDependency ownerDelegate) {
            super(originalOwner, ownerDelegate);
        }
    }
}
