package net.minecraftforge.gradle.user;


public class FmlUserPlugin extends UserBasePlugin<FmlUserExtension>
{
    @Override
    public void applyPlugin()
    {
        super.applyPlugin();
        
        // TODO tasks....
    }
    
    protected Class<FmlUserExtension> getExtensionClass(){ return FmlUserExtension.class; }
}
