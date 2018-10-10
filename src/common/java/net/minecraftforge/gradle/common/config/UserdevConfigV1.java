package net.minecraftforge.gradle.common.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraftforge.gradle.common.util.Utils;

public class UserdevConfigV1 extends Config {
    public static UserdevConfigV1 get(InputStream stream) {
        return Utils.fromJson(stream, UserdevConfigV1.class);
    }
    public static UserdevConfigV1 get(byte[] data) {
        return get(new ByteArrayInputStream(data));
    }

    public String mcp;    // Do not specify this unless there is no parent.
    public String parent; // To fully resolve, we must walk the parents until we hit null, and that one must specify a MCP value.
    public List<String> ats;
    public List<String> srgs;
    public List<String> srg_lines;
    public String binpatches; //To be applied to joined.jar, remapped, and added to the classpath
    public boolean srg; //True if binpatches and universal are in srg names
    public String patches;
    public String sources;
    public String universal; //Remapped and added to the classpath, Contains new classes and resources
    public List<String> libraries; //Additional libraries.

    public void addAT(String value) {
        if (this.ats == null) {
            this.ats = new ArrayList<>();
        }
        this.ats.add(value);
    }
    public List<String> getATs() {
        return this.ats == null ? Collections.emptyList() : this.ats;
    }
    public void addSRG(String value) {
        if (this.srgs == null) {
            this.srgs = new ArrayList<>();
        }
        this.srgs.add(value);
    }
    public void addSRGLine(String value) {
        if (this.srg_lines == null) {
            this.srg_lines = new ArrayList<>();
        }
        this.srg_lines.add(value);
    }
}
