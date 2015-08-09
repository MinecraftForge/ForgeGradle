package net.minecraftforge.gradle.util.json.fgversion;

import java.util.Arrays;

import com.google.gson.JsonObject;

public class FGVersion
{
    public String        version, docUrl;
    public String[]      changes, bugs;
    public FGBuildStatus status;
    public int           index;
    public JsonObject    ext;
    
    @Override
    public String toString()
    {
        return "FGVersion [version=" + version + ", docUrl=" + docUrl + ", changes=" + Arrays.toString(changes) + ", bugs=" + Arrays.toString(bugs) + ", status=" + status + ", index=" + index + ", ext=" + ext + "]";
    }
}
