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