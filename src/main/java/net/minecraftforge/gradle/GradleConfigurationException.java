package net.minecraftforge.gradle;

public class GradleConfigurationException extends RuntimeException
{
    // because compiler complaints
    private static final long serialVersionUID = 1L;

    public GradleConfigurationException(String message, Throwable cause)
    {
        super(message, cause);
    }

    public GradleConfigurationException(String message)
    {
        super(message);
    }

    public GradleConfigurationException(Throwable cause)
    {
        super(cause);
    }
}
