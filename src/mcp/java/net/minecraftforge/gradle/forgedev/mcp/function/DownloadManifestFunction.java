package net.minecraftforge.gradle.forgedev.mcp.function;

public class DownloadManifestFunction extends AbstractFileDownloadFunction {

    private static final String DEFAULT_OUTPUT = "manifest.json";
    private static final String MANIFEST_URL = "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    public DownloadManifestFunction() {
        super(DEFAULT_OUTPUT, MANIFEST_URL);
    }

}
