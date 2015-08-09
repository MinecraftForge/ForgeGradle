package net.minecraftforge.gradle.util.json.fgversion;

import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * This class exist to be deserialised by FGWrapperDeserialiser
 */
public class FGVersionWrapper
{
    public List<String>           versions       = Lists.newArrayList();
    public Map<String, FGVersion> versionObjects = Maps.newHashMap();

    @Override
    public String toString()
    {
        return "FGVersionWrapper [versions=" + versions + ", versionObjects=" + versionObjects + "]";
    }
}
