package net.minecraftforge.gradle;

import org.gradle.api.Project;

public class JenkinsExtensionObject
{
    @SuppressWarnings("unused")
    private Project project;

    private String server = "http://ci.jenkins.minecraftforge.net/";
    private String job;
    private String authName = "console_script";
    private String authPassword = "dc6d48ca20a474beeac280a9a16a926e";

    public JenkinsExtensionObject(Project project)
    {
        this.project = project;
    }

    public String getServer()
    {
        return server;
    }

    public void setServer(String server)
    {
        this.server = server;
    }

    public String getJob()
    {
        return job;
    }

    public void setJob(String job)
    {
        this.job = job;
    }

    public String getAuthName()
    {
        return authName;
    }

    public void setAuthName(String authName)
    {
        this.authName = authName;
    }

    public String getAuthPassword()
    {
        return authPassword;
    }

    public void setAuthPassword(String authPassword)
    {
        this.authPassword = authPassword;
    }
}
