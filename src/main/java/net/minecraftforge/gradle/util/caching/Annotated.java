package net.minecraftforge.gradle.util.caching;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

class Annotated
{
    private final Class<?> clazz;
    private final String   symbolName;
    private final boolean  isMethod;

    public Annotated(Class<?> clazz, String symbolName, boolean isMethod)
    {
        this.clazz = clazz;
        this.symbolName = symbolName;
        this.isMethod = isMethod;
    }

    public Annotated(Class<?> clazz, String fieldName)
    {
        this.clazz = clazz;
        this.symbolName = fieldName;
        isMethod = false;
    }

    public AnnotatedElement getElement() throws NoSuchMethodException, NoSuchFieldException
    {
        if (isMethod)
            return clazz.getDeclaredMethod(symbolName);
        else
            return clazz.getDeclaredField(symbolName);
    }

    public Object getValue(Object instance) throws NoSuchMethodException, NoSuchFieldException, IllegalArgumentException, IllegalAccessException, InvocationTargetException
    {
        Method method;

        if (isMethod)
            method = clazz.getDeclaredMethod(symbolName);
        else
        {
            // finds the getter, and uses that if possible.
            Field f = clazz.getDeclaredField(symbolName);
            String methodName = f.getType().equals(boolean.class) ? "is" : "get";

            char[] name = symbolName.toCharArray();
            name[0] = Character.toUpperCase(name[0]);
            methodName += new String(name);

            try
            {
                method = clazz.getMethod(methodName, new Class[0]);
            }
            catch (NoSuchMethodException e)
            {
                // method not found. Grab the field via reflection
                f.setAccessible(true);
                return f.get(instance);
            }
        }

        return method.invoke(instance, new Object[0]);
    }
}
