package net.minecraftforge.gradle.json.version;


public class Downloads {
    public DownloadFileInfo client;
    public DownloadFileInfo server;
    public DownloadFileInfo windows_server;

    public static class DownloadFileInfo {
        public String sha1;
        public long size;
        public String url;
    }
}
