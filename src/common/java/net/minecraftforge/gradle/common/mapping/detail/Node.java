/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package net.minecraftforge.gradle.common.mapping.detail;

import java.util.Objects;

class Node implements IMappingDetail.INode {
    private final String original;
    private final String mapped;
    private final String side;
    private final String javadoc;

    protected Node(String original, String mapped, String side, String javadoc) {
        this.original = original;
        this.mapped = mapped;
        this.javadoc = javadoc;
        this.side = side;
    }

    @Override
    public String getOriginal() {
        return original;
    }

    @Override
    public String getMapped() {
        return mapped;
    }

    @Override
    public String getSide() {
        return side;
    }

    @Override
    public String getJavadoc() {
        return javadoc;
    }

    @Override
    public Node withMapping(String mapped) {
        if (Objects.equals(mapped, this.mapped)) return this;

        return new Node(original, mapped, side, javadoc);
    }

    @Override
    public Node withSide(String side) {
        if (Objects.equals(side, this.side)) return this;

        return new Node(original, mapped, side, javadoc);
    }

    @Override
    public Node withJavadoc(String javadoc) {
        if (Objects.equals(javadoc, this.javadoc)) return this;

        return new Node(original, mapped, side, javadoc);
    }
}