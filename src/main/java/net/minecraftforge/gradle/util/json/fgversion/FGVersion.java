package net.minecraftforge.gradle.util.json.fgversion;

import com.google.gson.JsonObject;

public class FGVersion
{
    public String        version, docUrl;
    public String[]      changes, bugs;
    public FGBuildStatus status;
    public int           index;
    public JsonObject    ext;
}
