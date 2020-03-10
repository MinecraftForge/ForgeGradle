package net.minecraftforge.gradle.common.task;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;

public class DynamicJarExec extends JarExec {
	private File input;
	private File output;
	private Map<String, File> data;

    @Override
    protected List<String> filterArgs() {
        Map<String, String> replace = new HashMap<>();
        replace.put("{input}", getInput().getAbsolutePath());
        replace.put("{output}", getOutput().getAbsolutePath());
        if (this.data != null)
        	this.data.forEach((key,value) -> replace.put('{' + key + '}', value.getAbsolutePath()));

        return Arrays.stream(getArgs()).map(arg -> replace.getOrDefault(arg, arg)).collect(Collectors.toList());
    }

	@InputFiles
	@Optional
	public Collection<File> getData() {
		return this.data == null ? Collections.emptyList() : this.data.values();
	}

	public void data(String key, File file) {
		this.setData(key, file);
	}
	public void setData(String key, File file) {
		if (this.data == null)
			this.data = new HashMap<>();
		this.data.put(key, file);
	}

    @InputFile
    public File getInput() {
        return input;
    }
    public void setInput(File value) {
        this.input = value;
    }

    @OutputFile
    public File getOutput() {
        if (output == null)
            setOutput(getProject().file("build/" + getName() + "/output.jar"));
        return output;
    }
    public void setOutput(File value) {
        this.output = value;
    }
}
