package net.minecraftforge.gradle.patcher.task;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;

public class TaskSrg2Src extends DefaultTask {

    private final String from, to;

    @Inject
    public TaskSrg2Src(String from, String to) {
        this.from = from;
        this.to = to;
    }

    @TaskAction
    public void applySrg2Src() {

    }

}
