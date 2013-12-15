package net.minecraftforge.gradle.tasks.dev;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.md_5.specialsource.Jar;
import net.md_5.specialsource.JarMapping;
import net.md_5.specialsource.JarRemapper;
import net.md_5.specialsource.provider.ClassLoaderProvider;
import net.md_5.specialsource.provider.JarProvider;
import net.md_5.specialsource.provider.JointProvider;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.dev.FmlDevPlugin;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.tasks.TaskAction;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.objectweb.asm.Opcodes.*;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;

import de.oceanlabs.mcp.mcinjector.StringUtil;

public class ObfuscateTask extends DefaultTask
{
    private DelayedFile outJar;
    private DelayedFile preFFJar;
    private DelayedFile srg;
    private DelayedFile exc;
    private boolean     reverse;
    private DelayedFile buildFile;
    private DelayedFile methodsCsv;
    private DelayedFile fieldsCsv;

    @TaskAction
    public void doTask() throws IOException
    {
        getLogger().debug("Building child project model...");
        Project childProj = FmlDevPlugin.getProject(getBuildFile(), getProject());
        AbstractTask compileTask = (AbstractTask) childProj.getTasks().getByName("compileJava");
        AbstractTask jarTask = (AbstractTask) childProj.getTasks().getByName("jar");

        // executing jar task
        getLogger().debug("Executing child Jar task...");
        executeTask(jarTask);
        
        File inJar = (File)jarTask.property("archivePath");

        File srg = getSrg();

        if (getExc() != null)
        {
            JarInfo new_info = readJar(inJar);
            JarInfo old_info = readJar(getPreFFJar());
            Map<String, String> clsMap = createClassMap(new_info.map, new_info.interfaces);
            renameAccess(old_info.access);
            Map<String, String> access = mergeAccess(new_info.access, old_info.access);
            srg = createSrg(srg, clsMap, access);
        }

        getLogger().debug("Obfuscating jar...");
        obfuscate(inJar, (FileCollection)compileTask.property("classpath"), srg);
    }
    
    private Map<String, String> mergeAccess(Map<String, AccessInfo> old_data, Map<String, AccessInfo> new_data)
    {
        // Lets remove things that are mapped exactly right:
        //System.out.println("Matches:");
        Iterator<Entry<String, AccessInfo>> itr = old_data.entrySet().iterator();
        while(itr.hasNext())
        {
            Entry<String, AccessInfo> e = itr.next();
            String key = e.getKey();
            AccessInfo n = new_data.get(key);
            if (n != null && e.getValue().targetEquals(n))
            {
                //System.out.println("  " + n.toString());
                itr.remove();
                new_data.remove(key);
            }
        }

        Map<String, String> matched = Maps.newHashMap();
        
        //System.out.println("Matched: ");
        itr = old_data.entrySet().iterator();
        while (itr.hasNext())
        {
            AccessInfo _old = itr.next().getValue();
            Iterator<Entry<String, AccessInfo>> itr2  = new_data.entrySet().iterator();
            while (itr2.hasNext())
            {
                Entry<String, AccessInfo> e2 = itr2.next();
                AccessInfo _new = e2.getValue();
                if (_old.targetEquals(_new) &&
                    _old.owner.equals(_new.owner) &&
                    _old.desc.equals(_new.desc))
                {
                    //System.out.println("  " + _old.name + " -> " + _new.name + " " + _old.toString());
                    matched.put(_old.owner + "/" + _old.name, _new.owner + "/" + _new.name);
                    itr.remove();
                    itr2.remove();
                    break;
                }
            }
        }

        return matched;
    }

    private void renameAccess(Map<String, AccessInfo> data) throws IOException
    {
        final Map<String, String> renames = Maps.newHashMap();
        File[] csvs = new File[]
        {
            fieldsCsv == null ? null : getFieldsCsv(),
            methodsCsv == null ? null : getMethodsCsv()
        };

        for (File f : csvs)
        {
            if (f == null) continue;
           
            Files.readLines(f, Charset.defaultCharset(), new LineProcessor<Object>()
            {
                @Override
                public boolean processLine(String line) throws IOException
                {
                    String[] s = line.split(",");
                    renames.put(s[0], s[1]);
                    return true;
                }
                @Override public Object getResult() { return null; }
            });
        }

        for (Entry<String, AccessInfo> e : data.entrySet())
        {
            AccessInfo i = e.getValue();
            String tmp = renames.get(i.target_name);
            i.target_name = tmp == null ? i.target_name : tmp;
        }
    }
    
    private JarInfo readJar(File inJar) throws IOException
    {
        ZipInputStream zip = null;
        try
        {
            try
            {
                zip = new ZipInputStream(new BufferedInputStream(new FileInputStream(inJar)));
            }
            catch (FileNotFoundException e)
            {
                throw new FileNotFoundException("Could not open input file: " + e.getMessage());
            }
            
            JarInfo reader = new JarInfo();
            while (true)
            {
                ZipEntry entry = zip.getNextEntry();
                if (entry == null) break;
                if (entry.isDirectory() ||
                    !entry.getName().endsWith(".class")) continue;
                (new ClassReader(ByteStreams.toByteArray(zip))).accept(reader, 0);
            }
            return reader;
        } finally {
            if (zip != null) {
                try {
                    zip.close();
                } catch (IOException e){}
            }
        }
    }

    private void executeTask(AbstractTask task)
    {
        for (Object dep : task.getTaskDependencies().getDependencies(task))
        {
            executeTask((AbstractTask) dep);
        }

        if (!task.getState().getExecuted())
        {
            getLogger().lifecycle(task.getPath());
            task.execute();
        }
    }

    private void obfuscate(File inJar, FileCollection classpath, File srg) throws FileNotFoundException, IOException
    {
        // load mapping
        JarMapping mapping = new JarMapping();
        mapping.loadMappings(Files.newReader(srg, Charset.defaultCharset()), null, null, reverse);

        // make remapper
        JarRemapper remapper = new JarRemapper(null, mapping);

        // load jar
        Jar input = Jar.init(inJar);

        // ensure that inheritance provider is used
        JointProvider inheritanceProviders = new JointProvider();
        inheritanceProviders.add(new JarProvider(input));

        if (classpath != null)
            inheritanceProviders.add(new ClassLoaderProvider(new URLClassLoader(toUrls(classpath))));

        mapping.setFallbackInheritanceProvider(inheritanceProviders);

        File out = getOutJar();
        if (!out.getParentFile().exists()) //Needed because SS doesn't create it.
        {
            out.getParentFile().mkdirs();
        }

        // remap jar
        remapper.remapJar(input, getOutJar());
    }

    private Map<String, String> createClassMap(Map<String, String> markerMap, final List<String> interfaces) throws IOException
    {
        if (!getExc().exists()) return Maps.newHashMap();
        Map<String, String> excMap = Files.readLines(getExc(), Charset.defaultCharset(), new LineProcessor<Map<String, String>>()
        {
            Map<String, String> tmp = Maps.newHashMap();

            @Override
            public boolean processLine(String line) throws IOException
            {
                if (line.contains(".") ||
                   !line.contains("=") ||
                    line.startsWith("#")) return true;

                String[] s = line.split("=");
                if (!interfaces.contains(s[0])) tmp.put(s[0], s[1] + "_");

                return true;
            }

            @Override
            public Map<String, String> getResult()
            {
                return tmp;
            }
        });
        Map<String, String> map = Maps.newHashMap();
        for (Entry<String, String> e : excMap.entrySet())
        {
            String renamed = markerMap.get(e.getValue());
            if (renamed != null)
            {
                map.put(e.getKey(), renamed);
            }
        }
        return map;
    }

    private File createSrg(File base, Map<String, String> map, Map<String, String> access) throws IOException
    {
        File srg = new File(this.getTemporaryDir(), "reobf_cls.srg");
        if (srg.isFile())
            srg.delete();

        String fixed = Files.readLines(base, Charset.defaultCharset(), new SrgLineProcessor(map, access));
        Files.write(fixed.getBytes(), srg);
        return srg;
    }

    private static class SrgLineProcessor implements LineProcessor<String>
    {
        Map<String, String> map;
        Map<String, String> access;
        StringBuilder out = new StringBuilder();
        Pattern reg = Pattern.compile("L([^;]+);");

        private SrgLineProcessor(Map<String, String> map, Map<String, String> access)
        {
            this.map = map;
            this.access = access;
        }

        private String rename(String cls)
        {
            String rename = map.get(cls);
            return rename == null ? cls : rename;
        }

        private String[] rsplit(String value, String delim)
        {
            int idx = value.lastIndexOf(delim);
            return new String[]
            {
                value.substring(0, idx),
                value.substring(idx + 1)
            };
        }

        @Override
        public boolean processLine(String line) throws IOException
        {
            String[] split = line.split(" ");
            if (split[0].equals("CL:"))
            {
                split[2] = rename(split[2]);
            }
            else if (split[0].equals("FD:"))
            {
                String[] s = rsplit(split[2], "/");
                split[2] = rename(s[0]) + "/" + s[1];
            }
            else if (split[0].equals("MD:"))
            {
                String[] s = rsplit(split[3], "/");
                split[3] = rename(s[0]) + "/" + s[1];

                if (access.containsKey(split[3]))
                {
                    split[3] = access.get(split[3]);
                }

                Matcher m = reg.matcher(split[4]);
                StringBuffer b = new StringBuffer();
                while(m.find())
                {
                    m.appendReplacement(b, "L" + rename(m.group(1)).replace("$",  "\\$") + ";");
                }
                m.appendTail(b);
                split[4] = b.toString();
            }
            out.append(StringUtil.joinString(Arrays.asList(split), " ")).append('\n');
            return true;
        }

        @Override
        public String getResult()
        {
            return out.toString();
        }

    }

    public static URL[] toUrls(FileCollection collection) throws MalformedURLException
    {
        ArrayList<URL> urls = new ArrayList<URL>();

        for (File file : collection.getFiles())
            urls.add(file.toURI().toURL());

        return urls.toArray(new URL[urls.size()]);
    }

    public File getOutJar()
    {
        return outJar.call();
    }

    public void setOutJar(DelayedFile outJar)
    {
        this.outJar = outJar;
    }
    
    public File getPreFFJar()
    {
        return preFFJar.call();
    }

    public void setPreFFJar(DelayedFile preFFJar)
    {
        this.preFFJar = preFFJar;
    }

    public File getSrg()
    {
        return srg.call();
    }

    public void setSrg(DelayedFile srg)
    {
        this.srg = srg;
    }

    public File getExc()
    {
        return exc.call();
    }

    public void setExc(DelayedFile exc)
    {
        this.exc = exc;
    }

    public boolean isReverse()
    {
        return reverse;
    }

    public void setReverse(boolean reverse)
    {
        this.reverse = reverse;
    }

    public File getBuildFile()
    {
        return buildFile.call();
    }

    public void setBuildFile(DelayedFile buildFile)
    {
        this.buildFile = buildFile;
    }


    public File getMethodsCsv()
    {
        return methodsCsv.call();
    }

    public void setMethodsCsv(DelayedFile methodsCsv)
    {
        this.methodsCsv = methodsCsv;
    }

    public File getFieldsCsv()
    {
        return fieldsCsv.call();
    }

    public void setFieldsCsv(DelayedFile fieldsCsv)
    {
        this.fieldsCsv = fieldsCsv;
    }

    private static class JarInfo extends ClassVisitor
    {
        private final Map<String, String> map = Maps.newHashMap();
        private final List<String> interfaces = Lists.newArrayList();
        private final Map<String, AccessInfo> access = Maps.newHashMap();

        public JarInfo()
        {
            super(Opcodes.ASM4, null);
        }

        private String className;

        @Override
        public void visit(int version, int access, String name, String signature, String superName, String[] ints)
        {
            //System.out.println("Class: " + name);
            this.className = name;;
            if ((access & ACC_INTERFACE) == ACC_INTERFACE)
            {
                interfaces.add(className);
                //System.out.println("  Interface: True");
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc, String signature, Object value)
        {
            if (name.equals("__OBFID"))
            {
                map.put(String.valueOf(value) + "_", className);
                //System.out.println("  Marker:    " + String.valueOf(value));
            }
            return null;
        }
        
        @Override
        public MethodVisitor visitMethod(int acc, String name, String desc, String signature, String[] exceptions)
        {
            if (className.startsWith("net/minecraft/") && name.startsWith("access$"))
            {
                String path = className + "/" + name + desc;
                final AccessInfo info = new AccessInfo(className, name, desc);
                info.access = acc;
                access.put(path, info);
                
                return new MethodVisitor(Opcodes.ASM4)
                {
                    // GETSTATIC, PUTSTATIC, GETFIELD or PUTFIELD.
                    @Override
                    public void visitFieldInsn(int opcode, String owner, String name, String desc)
                    {
                        info.set(opcode, owner, name, desc);
                    }

                    // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC or INVOKEINTERFACE.
                    public void visitMethodInsn(int opcode, String owner, String name, String desc)
                    {
                        info.set(opcode, owner, name, desc);
                    }
                };
            }
            return null;
        }
    }

    @SuppressWarnings("unused")
    private static class AccessInfo
    {
        public String owner;
        public String name;
        public String desc;
        
        public int opcode;
        public int access;
        public String target_owner;
        public String target_name;
        public String target_desc;
        
        public AccessInfo(String owner, String name, String desc)
        {
            this.owner = owner;
            this.name = name;
            this.desc = desc;
        }

        public void set(int opcode, String owner, String name, String desc)
        {
            if (this.opcode != 0) throw new RuntimeException();
            this.opcode = opcode;
            this.target_owner = owner;
            this.target_name = name;
            this.target_desc = desc;
        }

        @Override
        public String toString()
        {
            String op = "UNKNOWN_" + opcode;
            switch (opcode)
            {
                case GETSTATIC:       op = "GETSTATIC";       break;
                case PUTSTATIC:       op = "PUTSTATIC";       break;
                case GETFIELD:        op = "GETFIELD";        break;
                case PUTFIELD:        op = "PUTFIELD";        break;
                case INVOKEVIRTUAL:   op = "INVOKEVIRTUAL";   break;
                case INVOKESPECIAL:   op = "INVOKESPECIAL";   break;
                case INVOKESTATIC:    op = "INVOKESTATIC";    break;
                case INVOKEINTERFACE: op = "INVOKEINTERFACE"; break;
            }
            return op + " " + target_owner + "/" + target_name + " " + target_desc;
        }
        
        public boolean targetEquals(AccessInfo o)
        {
            return toString().equals(o.toString());
        }
    }
}