package net.minecraftforge.gradle.json.curse;

public class CurseProjectDep
{

    /** The unique slug of the project */
    public String slug;

    /** The type of dependency. {@code embeddedLibrary, optionalLibrary, requiredLibrary, tool, or incompatible} */
    public String type;

    public CurseProjectDep(final String slug, final String type)
    {
        this.slug = slug;
        this.type = type;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o)
        {
            return true;
        }
        if (o == null || getClass() != o.getClass())
        {
            return false;
        }
        CurseProjectDep that = (CurseProjectDep) o;
        return !(slug != null ? !slug.equals(that.slug) : that.slug != null);
    }

    @Override
    public int hashCode()
    {
        return slug != null ? slug.hashCode() : 0;
    }

    @Override
    public String toString()
    {
        return "CurseProjectDep{" +
                "slug='" + slug + '\'' +
                ", type='" + type + '\'' +
                '}';
    }
}
