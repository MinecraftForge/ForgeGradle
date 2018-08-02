package net.minecraftforge.gradle.forgedev.mcp;

import org.gradle.api.Action;
import org.gradle.api.Project;

import javax.inject.Inject;
import java.io.File;

public class MCPExtension {

    public final Source source;

    public Object config;
    public String pipeline;

    @Inject
    public MCPExtension(Project project) {
        this.source = project.getObjects().newInstance(Source.class, project);
    }

    public void source(Action<? super Source> action) {
        action.execute(source);
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
