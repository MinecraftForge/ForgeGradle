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

import java.util.Map;

import net.minecraftforge.gradle.common.mapping.IMappingDetail;

public class MappingDetail implements IMappingDetail {
    protected final Map<String, INode> classes;
    protected final Map<String, INode> fields;
    protected final Map<String, INode> methods;
    protected final Map<String, INode> params;

    protected MappingDetail(Map<String, INode> classes, Map<String, INode> fields, Map<String, INode> methods, Map<String, INode> params) {
        this.classes = classes;
        this.fields = fields;
        this.methods = methods;
        this.params = params;
    }

    @Override
    public Map<String, INode> getClasses() {
        return classes;
    }

    @Override
    public Map<String, INode> getFields() {
        return fields;
    }

    @Override
    public Map<String, INode> getMethods() {
        return methods;
    }

    @Override
    public Map<String, INode> getParameters() {
        return params;
    }

    public static IMappingDetail of(Map<String, INode> classes, Map<String, INode> fields, Map<String, INode> methods, Map<String, INode> params) {
        return new MappingDetail(classes, fields, methods, params);
    }
}