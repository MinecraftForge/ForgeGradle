package net.minecraftforge.gradle.delayed;

import org.gradle.api.Project;

@SuppressWarnings("serial")
public class DelayedObject extends DelayedBase<Object>
{
    Object obj;

    public DelayedObject(Object obj, Project owner)
    {
        super(owner, "");
        this.obj = obj;
    }

    @Override
    public Object resolveDelayed()
    {        
        return obj;
    }

}
