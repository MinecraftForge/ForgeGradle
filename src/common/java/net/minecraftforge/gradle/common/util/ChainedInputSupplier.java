package net.minecraftforge.gradle.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraftforge.srg2source.util.io.InputSupplier;

public class ChainedInputSupplier implements InputSupplier {

    private final List<InputSupplier> inputs = new ArrayList<>();

    public ChainedInputSupplier(Collection<InputSupplier> inputs) {
        this.inputs.addAll(inputs);
    }

    public ChainedInputSupplier(InputSupplier... inputs) {
        for (InputSupplier i : inputs) {
            this.inputs.add(i);
        }
    }

    public void add(InputSupplier input) {
        this.inputs.add(input);
    }

    public InputSupplier shrink() {
        return this.inputs.size() == 1 ? this.inputs.get(0) : this;
    }

    @Override
    public void close() throws IOException {
        for (InputSupplier sup : this.inputs) {
            sup.close();
        }
    }

    @Override
    public String getRoot(String resource) {
        return this.inputs.stream().map(sup -> sup.getRoot(resource)).filter(v -> v != null).findFirst().orElse(null);
    }

    @Override
    public InputStream getInput(String resource) {
        return this.inputs.stream().map(sup -> sup.getInput(resource)).filter(v -> v != null).findFirst().orElse(null);
    }

    @Override
    public List<String> gatherAll(String path) {
        List<String> ret = new ArrayList<>();
        this.inputs.forEach(s -> ret.addAll(s.gatherAll(path)));
        return ret;
    }

}
