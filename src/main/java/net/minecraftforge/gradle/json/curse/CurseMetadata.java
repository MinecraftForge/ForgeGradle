package net.minecraftforge.gradle.json.curse;

public class CurseMetadata
{

    /** The artifact's changelog */
    public String changelog;
    /** The releaseType for this artifact. {@code alpha, beta, or release} */
    public String releaseType;
    /** The friendly display name for this artifact. May be {@code null} */
    public String displayName;
    /** An array of gameVersions this artifact is compatible with */
    public int[]  gameVersions;

    public CurseRelations relations = null;
}
