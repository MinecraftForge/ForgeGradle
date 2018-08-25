package net.minecraftforge.gradle.common.config;

import java.util.ArrayList;
import java.util.List;

public class UserdevJsonV1 extends Config {
    public String mcp;
    public List<String> parents;
    public List<String> ats;
    public List<String> srgs;
    public List<String> srg_lines;
    public String binpatches; //To be applied to joined.jar, remapped, and added to the classpath
    public String patches;
    public String sources;
    public String universal; //Remapped and added to the classpath, Contains new classes and resources

    public void addParent(String value) {
        if (this.parents == null) {
            this.parents = new ArrayList<>();
        }
        this.parents.add(0, value);
    }
    public void addAT(String value) {
        if (this.ats == null) {
            this.ats = new ArrayList<>();
        }
        this.ats.add(value);
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
