package net.minecraftforge.gradle.util;

import java.io.File;

import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.specs.Spec;

public class ExtractionVisitor implements FileVisitor
{
    private final File                  outputDir;
    private final boolean               emptyDirs;
    private final Spec<FileTreeElement> spec;

    public ExtractionVisitor(File outDir, boolean emptyDirs, Spec<FileTreeElement> spec)
    {
        this.outputDir = outDir;
        this.emptyDirs = emptyDirs;
        this.spec = spec;
    }

    @Override
    public void visitDir(FileVisitDetails details)
    {
        if (emptyDirs && spec.isSatisfiedBy(details))
        {
            File dir = new File(outputDir, details.getPath());
            dir.mkdirs();
        }
    }

    @Override
    public void visitFile(FileVisitDetails details)
    {
        if (!spec.isSatisfiedBy(details))
        {
            return;
        }

        File out = new File(outputDir, details.getPath());
        out.getParentFile().mkdirs();
        details.copyTo(out);
    }
}
