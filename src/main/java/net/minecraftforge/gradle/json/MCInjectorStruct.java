package net.minecraftforge.gradle.json;

import java.util.ArrayList;

public class MCInjectorStruct
{
    public EnclosingMethod enclosingMethod = null;
    public ArrayList<InnerClass> innerClasses = null;

    public static class EnclosingMethod
    {
        public final String desc;
        public final String name;
        public final String owner;

        EnclosingMethod(String owner, String name, String desc)
        {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }
    }

    public static class InnerClass
    {
        public String access;
        public final String inner_class;
        public final String inner_name;
        public final String outer_class;
        public final String start;

        InnerClass(String inner_class, String outer_class, String inner_name, String access, String start)
        {
            this.inner_class = inner_class;
            this.outer_class = outer_class;
            this.inner_name = inner_name;
            this.access = access;
            this.start = start;
        }

        public int getAccess() { return Integer.parseInt(access == null ? "0" : access, 16); }
        public int getStart()  { return Integer.parseInt(start  == null ? "0" : start,  10); }
    }
}
