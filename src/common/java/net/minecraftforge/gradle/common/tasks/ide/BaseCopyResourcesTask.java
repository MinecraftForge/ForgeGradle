package net.minecraftforge.gradle.common.tasks.ide;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

abstract class BaseCopyResourcesTask extends DefaultTask {

    protected final Map<File, File> toCopy = new HashMap<>();

    @TaskAction
    public void run() {
        toCopy.forEach((in, out) -> getProject().copy(spec -> spec.from(in).into(out)));
    }
}
