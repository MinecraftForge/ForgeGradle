package net.minecraftforge.gradle.user;

import net.minecraftforge.gradle.common.BasePlugin;

public abstract class UserBasePlugin extends BasePlugin<UserExtension> // TODO: change this to the actual extension class eventually, the one specific to the FML User plugin
{

    @Override
    public void applyPlugin()
    {
        // TODO tasks....
    }
    
    protected Class<UserExtension> getExtensionClass(){ return UserExtension.class; }

    @Override
    protected String getDevJson()
    {
        // TODO what should we put here?
        return null;
    }

}
