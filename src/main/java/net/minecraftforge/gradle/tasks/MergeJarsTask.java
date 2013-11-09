package net.minecraftforge.gradle.tasks;

import groovy.lang.Closure;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.minecraftforge.gradle.tasks.abstractutil.CachedTask;

import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class MergeJarsTask extends CachedTask
{
    @InputFile
    private Closure<File>                     mergeCfg;

    @InputFile
    private Closure<File>                     client;

    @InputFile
    private Closure<File>                     server;

    @OutputFile
    @Cached
    private Closure<File>                     outJar;

    private static HashMap<String, ClassInfo> shared       = new HashMap<String, ClassInfo>();
    private static HashSet<String>            copyToServer = new HashSet<String>();
    private static HashSet<String>            copyToClient = new HashSet<String>();
    private static HashSet<String>            dontAnnotate = new HashSet<String>();
    private static HashSet<String>            dontProcess  = new HashSet<String>();
    private static final boolean              DEBUG        = false;

    @TaskAction
    public void doTask() throws IOException
    {
        readConfig(getMergeCfg());
        processJar(getClient(), getServer(), getOutJar());
    }

    private boolean readConfig(File mapFile)
    {
        try
        {
            FileInputStream fstream = new FileInputStream(mapFile);
            DataInputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));

            String line;
            while ((line = br.readLine()) != null)
            {
                line = line.split("#")[0];
                char cmd = line.charAt(0);
                line = line.substring(1).trim();

                switch (cmd)
                    {
                        case '!':
                            dontAnnotate.add(line);
                            break;
                        case '<':
                            copyToClient.add(line);
                            break;
                        case '>':
                            copyToServer.add(line);
                            break;
                        case '^':
                            dontProcess.add(line);
                            break;
                    }
            }

            in.close();
            return true;
        }
        catch (Exception e)
        {
            getLogger().error("Error: " + e.getMessage());
            return false;
        }
    }

    public void processJar(File clientInFile, File serverInFile, File outFile) throws IOException
    {
        ZipFile cInJar = null;
        ZipFile sInJar = null;
        ZipOutputStream outJar = null;

        try
        {
            try
            {
                cInJar = new ZipFile(clientInFile);
                sInJar = new ZipFile(serverInFile);
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }

            // different messages.

            try
            {
                outJar = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(outFile)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open output file: " + e.getMessage());
            }

            // read in the jars, and initalize some variables
            HashSet<String> resources = new HashSet<String>();
            HashMap<String, ZipEntry> cClasses = getClassEntries(cInJar, outJar, resources);
            HashMap<String, ZipEntry> sClasses = getClassEntries(sInJar, outJar, resources);
            HashSet<String> cAdded = new HashSet<String>();

            // start processing
            for (Entry<String, ZipEntry> entry : cClasses.entrySet())
            {
                String name = entry.getKey();
                ZipEntry cEntry = entry.getValue();
                ZipEntry sEntry = sClasses.get(name);

                if (sEntry == null)
                {
                    if (!copyToServer.contains(name))
                    {
                        copyClass(cInJar, cEntry, outJar, true);
                        cAdded.add(name);
                    }
                    else
                    {
                        if (DEBUG)
                        {
                            System.out.println("Copy class c->s : " + name);
                        }
                        copyClass(cInJar, cEntry, outJar, true);
                        cAdded.add(name);
                    }
                    continue;
                }

                sClasses.remove(name);
                ClassInfo info = new ClassInfo(name);
                shared.put(name, info);

                byte[] cData = readEntry(cInJar, entry.getValue());
                byte[] sData = readEntry(sInJar, sEntry);
                byte[] data = processClass(cData, sData, info);

                ZipEntry newEntry = new ZipEntry(cEntry.getName());
                outJar.putNextEntry(newEntry);
                outJar.write(data);
                cAdded.add(name);
            }

            for (Entry<String, ZipEntry> entry : sClasses.entrySet())
            {
                if (DEBUG)
                {
                    System.out.println("Copy class s->c : " + entry.getKey());
                }
                copyClass(sInJar, entry.getValue(), outJar, false);
            }

            for (String name : new String[] { SideOnly.class.getName(), Side.class.getName() })
            {
                String eName = name.replace(".", "/");
                String classPath = eName + ".class";
                ZipEntry newEntry = new ZipEntry(classPath);
                System.out.printf("Adding %s\n", classPath);
                if (!cAdded.contains(eName))
                {
                    outJar.putNextEntry(newEntry);
                    outJar.write(getClassBytes(name));
                }
            }

        }
        finally
        {
            if (cInJar != null)
            {
                try
                {
                    cInJar.close();
                }
                catch (IOException e)
                {}
            }

            if (sInJar != null)
            {
                try
                {
                    sInJar.close();
                }
                catch (IOException e)
                {}
            }
            if (outJar != null)
            {
                try
                {
                    outJar.close();
                }
                catch (IOException e)
                {}
            }

        }
    }

    private void copyClass(ZipFile inJar, ZipEntry entry, ZipOutputStream outJar, boolean isClientOnly) throws IOException
    {
        ClassReader reader = new ClassReader(readEntry(inJar, entry));
        ClassNode classNode = new ClassNode();

        reader.accept(classNode, 0);

        if (!dontAnnotate.contains(classNode.name))
        {
            if (classNode.visibleAnnotations == null)
            {
                classNode.visibleAnnotations = new ArrayList<AnnotationNode>();
            }
            classNode.visibleAnnotations.add(getSideAnn(isClientOnly));
        }

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] data = writer.toByteArray();

        ZipEntry newEntry = new ZipEntry(entry.getName());
        if (outJar != null)
        {
            outJar.putNextEntry(newEntry);
            outJar.write(data);
        }
    }

    private byte[] readEntry(ZipFile inFile, ZipEntry entry) throws IOException
    {
        return ByteStreams.toByteArray(inFile.getInputStream(entry));
    }

    private AnnotationNode getSideAnn(boolean isClientOnly)
    {
        AnnotationNode ann = new AnnotationNode(Type.getDescriptor(SideOnly.class));
        ann.values = new ArrayList<Object>();
        ann.values.add("value");
        ann.values.add(new String[] { Type.getDescriptor(Side.class), isClientOnly ? "CLIENT" : "SERVER" });
        return ann;
    }

    /**
     * @param inFile From which to read classes and resources
     * @param outFile The place to write resources and ignored classes
     * @param resources The registry to add resources to, and to check against.
     * @return HashMap of all the desired Classes and their ZipEntrys
     * @throws IOException
     */
    private HashMap<String, ZipEntry> getClassEntries(ZipFile inFile, ZipOutputStream outFile, HashSet<String> resources) throws IOException
    {
        HashMap<String, ZipEntry> ret = new HashMap<String, ZipEntry>();
        for (ZipEntry entry : Collections.list(inFile.entries()))
        {
            String entryName = entry.getName();
            // Always skip the manifest
            if ("META-INF/MANIFEST.MF".equals(entryName))
            {
                continue;
            }
            if (entry.isDirectory())
            {
                if (!resources.contains(entryName))
                {
                    outFile.putNextEntry(entry);
                }
                continue;
            }

            for (String filter : dontProcess)
            {
                if (entryName.startsWith(filter))
                {
                    continue;
                }
            }

            if (!entryName.endsWith(".class") || entryName.startsWith("."))
            {
                if (!resources.contains(entryName))
                {
                    ZipEntry newEntry = new ZipEntry(entryName);
                    outFile.putNextEntry(newEntry);
                    outFile.write(readEntry(inFile, entry));
                    resources.add(entryName);
                }
            }
            else
            {
                ret.put(entryName.replace(".class", ""), entry);
            }
        }
        return ret;
    }

    // @TODO: rewrite.
    public static byte[] getClassBytes(String name) throws IOException
    {
        InputStream classStream = null;
        try
        {
            classStream = MergeJarsTask.class.getResourceAsStream("/" + name.replace('.', '/').concat(".class"));
            return ByteStreams.toByteArray(classStream);
        }
        finally
        {
            if (classStream != null)
            {
                classStream.close();
            }
        }
    }

    public byte[] processClass(byte[] cIn, byte[] sIn, ClassInfo info)
    {
        ClassNode cClassNode = getClassNode(cIn);
        ClassNode sClassNode = getClassNode(sIn);

        processFields(cClassNode, sClassNode, info);
        processMethods(cClassNode, sClassNode, info);

        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cClassNode.accept(writer);
        return writer.toByteArray();
    }

    private ClassNode getClassNode(byte[] data)
    {
        ClassReader reader = new ClassReader(data);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        return classNode;
    }

    private void processFields(ClassNode cClass, ClassNode sClass, ClassInfo info)
    {
        List<FieldNode> cFields = cClass.fields;
        List<FieldNode> sFields = sClass.fields;

        int sI = 0;
        for (int x = 0; x < cFields.size(); x++)
        {
            FieldNode cF = cFields.get(x);
            if (sI < sFields.size())
            {
                if (!cF.name.equals(sFields.get(sI).name))
                {
                    boolean serverHas = false;
                    for (int y = sI + 1; y < sFields.size(); y++)
                    {
                        if (cF.name.equals(sFields.get(y).name))
                        {
                            serverHas = true;
                            break;
                        }
                    }
                    if (serverHas)
                    {
                        boolean clientHas = false;
                        FieldNode sF = sFields.get(sI);
                        for (int y = x + 1; y < cFields.size(); y++)
                        {
                            if (sF.name.equals(cFields.get(y).name))
                            {
                                clientHas = true;
                                break;
                            }
                        }
                        if (!clientHas)
                        {
                            if (sF.visibleAnnotations == null)
                            {
                                sF.visibleAnnotations = new ArrayList<AnnotationNode>();
                            }
                            sF.visibleAnnotations.add(getSideAnn(false));
                            cFields.add(x++, sF);
                            info.sField.add(sF);
                        }
                    }
                    else
                    {
                        if (cF.visibleAnnotations == null)
                        {
                            cF.visibleAnnotations = new ArrayList<AnnotationNode>();
                        }
                        cF.visibleAnnotations.add(getSideAnn(true));
                        sFields.add(sI, cF);
                        info.cField.add(cF);
                    }
                }
            }
            else
            {
                if (cF.visibleAnnotations == null)
                {
                    cF.visibleAnnotations = new ArrayList<AnnotationNode>();
                }
                cF.visibleAnnotations.add(getSideAnn(true));
                sFields.add(sI, cF);
                info.cField.add(cF);
            }
            sI++;
        }
        if (sFields.size() != cFields.size())
        {
            for (int x = cFields.size(); x < sFields.size(); x++)
            {
                FieldNode sF = sFields.get(x);
                if (sF.visibleAnnotations == null)
                {
                    sF.visibleAnnotations = new ArrayList<AnnotationNode>();
                }
                sF.visibleAnnotations.add(getSideAnn(true));
                cFields.add(x++, sF);
                info.sField.add(sF);
            }
        }
    }

    private void processMethods(ClassNode cClass, ClassNode sClass, ClassInfo info)
    {
        List<MethodNode> cMethods = cClass.methods;
        List<MethodNode> sMethods = sClass.methods;
        LinkedHashSet<MethodWrapper> allMethods = Sets.newLinkedHashSet();

        int cPos = 0;
        int sPos = 0;
        int cLen = cMethods.size();
        int sLen = sMethods.size();
        String clientName = "";
        String lastName = clientName;
        String serverName = "";
        while (cPos < cLen || sPos < sLen)
        {
            do
            {
                if (sPos >= sLen)
                {
                    break;
                }
                MethodNode sM = sMethods.get(sPos);
                serverName = sM.name;
                if (!serverName.equals(lastName) && cPos != cLen)
                {
                    if (DEBUG)
                    {
                        System.out.printf("Server -skip : %s %s %d (%s %d) %d [%s]\n", sClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                    }
                    break;
                }
                MethodWrapper mw = new MethodWrapper(sM);
                mw.server = true;
                allMethods.add(mw);
                if (DEBUG)
                {
                    System.out.printf("Server *add* : %s %s %d (%s %d) %d [%s]\n", sClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                }
                sPos++;
            } while (sPos < sLen);
            do
            {
                if (cPos >= cLen)
                {
                    break;
                }
                MethodNode cM = cMethods.get(cPos);
                lastName = clientName;
                clientName = cM.name;
                if (!clientName.equals(lastName) && sPos != sLen)
                {
                    if (DEBUG)
                    {
                        System.out.printf("Client -skip : %s %s %d (%s %d) %d [%s]\n", cClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                    }
                    break;
                }
                MethodWrapper mw = new MethodWrapper(cM);
                mw.client = true;
                allMethods.add(mw);
                if (DEBUG)
                {
                    System.out.printf("Client *add* : %s %s %d (%s %d) %d [%s]\n", cClass.name, clientName, cLen - cPos, serverName, sLen - sPos, allMethods.size(), lastName);
                }
                cPos++;
            } while (cPos < cLen);
        }

        cMethods.clear();
        sMethods.clear();

        for (MethodWrapper mw : allMethods)
        {
            if (DEBUG)
            {
                System.out.println(mw);
            }
            cMethods.add(mw.node);
            sMethods.add(mw.node);
            if (mw.server && mw.client)
            {
                // no op
            }
            else
            {
                if (mw.node.visibleAnnotations == null)
                {
                    mw.node.visibleAnnotations = Lists.newArrayListWithExpectedSize(1);
                }

                mw.node.visibleAnnotations.add(getSideAnn(mw.client));

                if (mw.client)
                {
                    info.sMethods.add(mw.node);
                }
                else
                {
                    info.cMethods.add(mw.node);
                }
            }
        }
    }

    private class MethodWrapper
    {
        private MethodNode node;
        public boolean     client;
        public boolean     server;

        public MethodWrapper(MethodNode node)
        {
            this.node = node;
        }

        @Override
        public boolean equals(Object obj)
        {
            if (obj == null || !(obj instanceof MethodWrapper))
            {
                return false;
            }
            MethodWrapper mw = (MethodWrapper) obj;
            boolean eq = Objects.equal(node.name, mw.node.name) && Objects.equal(node.desc, mw.node.desc);
            if (eq)
            {
                mw.client = client | mw.client;
                mw.server = server | mw.server;
                client = client | mw.client;
                server = server | mw.server;
                if (DEBUG)
                {
                    System.out.printf(" eq: %s %s\n", this, mw);
                }
            }
            return eq;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(node.name, node.desc);
        }

        @Override
        public String toString()
        {
            return Objects.toStringHelper(this).add("name", node.name).add("desc", node.desc).add("server", server).add("client", client).toString();
        }
    }

    @SuppressWarnings("unused")
    private class ClassInfo
    {
        public String                name;
        public ArrayList<FieldNode>  cField   = new ArrayList<FieldNode>();
        public ArrayList<FieldNode>  sField   = new ArrayList<FieldNode>();
        public ArrayList<MethodNode> cMethods = new ArrayList<MethodNode>();
        public ArrayList<MethodNode> sMethods = new ArrayList<MethodNode>();

        public ClassInfo(String name)
        {
            this.name = name;
        }

        public boolean isSame()
        {
            return cField.size() == 0 && sField.size() == 0 && cMethods.size() == 0 && sMethods.size() == 0;
        }
    }

    public File getClient()
    {
        return client.call();
    }

    public void setClient(Closure<File> client)
    {
        this.client = client;
    }

    public File getMergeCfg()
    {
        return mergeCfg.call();
    }

    public void setMergeCfg(Closure<File> mergeCfg)
    {
        this.mergeCfg = mergeCfg;
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(Closure<File> outJar)
    {
        this.outJar = outJar;
    }

    public File getServer()
    {
        return this.server.call();
    }

    public void setServer(Closure<File> server)
    {
        this.server = server;
    }
}
