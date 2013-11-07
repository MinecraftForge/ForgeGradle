package net.minecraftforge.gradle.user;

public final class UserConstants
{
    private UserConstants()
    {
        // no touch
    }
    
    public static final String PACK_DIR = "{PACK_DIR}";
    
    public static final String CONF_DIR = PACK_DIR + "/conf";
    public static final String MERGE_CFG = CONF_DIR + "/mcp_merge.cfg";
    public static final String ASTYLE_CFG = CONF_DIR + "/astyle.cfg";
    public static final String JOINED_SRG = CONF_DIR+"/joined.srg";
    public static final String JOINED_EXC = CONF_DIR+"/joined.exc";
    public static final String METHOD_CSV = CONF_DIR+"/methods.csv";
    public static final String FIELD_CSV = CONF_DIR+"/fields.csv";
    public static final String PARAM_CSV = CONF_DIR+"/params.csv";
    public static final String PACKAGE_CSV = CONF_DIR+"/packages.csv";
    
    public static final String JSON = PACK_DIR+"/dev.json";
}
