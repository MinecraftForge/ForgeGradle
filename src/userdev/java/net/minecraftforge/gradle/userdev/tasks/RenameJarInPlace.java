package net.minecraftforge.gradle.userdev.tasks;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.common.task.JarExec;

public class RenameJarInPlace extends JarExec {
    private File input;
    private File temp;
    private File mappings;

    public RenameJarInPlace() {
        tool = "net.md-5:SpecialSource:1.8.3:shaded"; // This is not use for binpatching, so we dont really need to let users config
        args = new String[] { "--in-jar", "{input}", "--out-jar", "{output}", "--srg-in", "{mappings}"};
        this.getOutputs().upToDateWhen(task -> false);
    }

    @Override
    protected List<String> filterArgs() {

        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", temp.getAbsolutePath());
        replace.put("{mappings}", getMappings().getAbsolutePath());

        return Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

    @Override
    @TaskAction
    public void apply() throws IOException {
        temp = getProject().file("build/" + getName() + "/output.jar");
        if (!temp.getParentFile().exists())
            temp.getParentFile().mkdirs();

        super.apply();

        FileUtils.copyFile(temp, getInput());
    }

    @InputFile
    public File getMappings() {
        return mappings;
    }
    public void setMappings(File value) {
        this.mappings = value;
    }

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }
}
