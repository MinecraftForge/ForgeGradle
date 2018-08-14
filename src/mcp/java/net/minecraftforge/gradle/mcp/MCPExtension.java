package net.minecraftforge.gradle.mcp;

import org.gradle.api.Action;
import org.gradle.api.Project;

import net.minecraftforge.gradle.mcp.task.DownloadMCPMappingsTask;

import javax.inject.Inject;
import java.io.File;
import java.util.Map;

public class MCPExtension {

    public final Source source;

    public Object config;
    public String pipeline;

    private Object mappings;

    @Inject
    public MCPExtension(Project project) {
        this.source = project.getObjects().newInstance(Source.class, project);
    }

    public void source(Action<? super Source> action) {
        action.execute(source);
    }

    public Object getMappings() {
        return mappings;
    }

    public void setMappings(Map<String, String> map) { //Whats the difference between the action and map?
        mappings = DownloadMCPMappingsTask.getDefault(map.get("channel"), map.get("version")); //mappings channel: 'snapshot', version: '20180101' Will append current MC version if none is specified in Version
    }

    public void setMappings(Object obj) {
        if (obj instanceof String || //Custom full artifact
            obj instanceof File  ) { //Custom zip file
            mappings = obj;
        } else {
            throw new IllegalArgumentException("Mappings must be file, string, or map");
        }
    }

    public static class Source {

        public final Unpacked unpacked;
        public final Packed packed;

        @Inject
        public Source(Project project) {
            this.unpacked = project.getObjects().newInstance(Unpacked.class, project);
            this.packed = project.getObjects().newInstance(Packed.class, project);
        }

        public void unpacked(Action<? super Unpacked> action) {
            action.execute(unpacked);
        }

        public void packed(Action<? super Packed> action) {
            action.execute(packed);
        }

        public static class Unpacked {

            public File output;

            @Inject
            public Unpacked(Project project) {
                output = project.file(project.getProjectDir().getAbsolutePath() + "/generated/unpacked.zip");
            }

        }

        public static class Packed {

            public File output;

            @Inject
            public Packed(Project project) {
                output = project.file(project.getProjectDir().getAbsolutePath() + "/generated/packed.zip");
            }

        }

    }

}
