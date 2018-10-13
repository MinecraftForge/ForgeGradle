package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;

import com.google.common.base.Joiner;

public class MappingFile {
    public enum Format {
        SRG, CSRG, TSRG;
        public static Format get(String value) {
            try {
                return Format.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private static final Joiner SPACE = Joiner.on(" ");
    private static final Pattern DESC = Pattern.compile("L([^;]+);");

    public static MappingFile load(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file)) {
            return load(input);
        }
    }
    public static MappingFile load(InputStream input) throws IOException {
        MappingFile ret = new MappingFile();
        List<String[]> lines = IOUtils.readLines(input).stream().map(line -> (line + '#').split("#")[0].replaceFirst("\\s++$", "")).filter(l -> !l.isEmpty()).map(line -> line.split(" ")).collect(Collectors.toList());
        String test = lines.get(0)[0];
        if ("PK:".equals(test) || "CL:".equals(test) || "FD:".equals(test) || "MD:".equals(test)) { //SRG
            for (String[] pts : lines) {
                switch (pts[0]) {
                    case "PK:": ret.addPackage(pts[1], pts[2]); break;
                    case "CL:": ret.addClass(pts[1], pts[2]); break;
                    case "FD:": ret.getClass(rsplit(pts[1], '/', 1)[0]).addField(rsplit(pts[1], '/', 1)[1], rsplit(pts[2], '/', 1)[1]); break;
                    case "MD:": ret.getClass(rsplit(pts[1], '/', 1)[0]).addMethod(rsplit(pts[1], '/', 1)[1], pts[2], rsplit(pts[3], '/', 1)[1]); break;
                    default:
                        throw new IOException("Invalid SRG file, Unknown type: " + SPACE.join(pts));
                }
            }
        } else {
            lines.stream().filter(p -> p.length == 2 && p[0].charAt(0) != '\t').forEach(pts -> {
                if (pts[0].endsWith("/"))
                    ret.addPackage(pts[0].substring(0, pts[0].length() - 1), pts[1].substring(0, pts[1].length() -1));
                else
                    ret.addClass(pts[0], pts[1]);
            });
            String cls = null;
            for (String[] pts : lines) {
                if (pts[0].charAt(0) == '\t') {
                    if (cls == null)
                        throw new IOException("Invalid TSRG line, missing class: " + SPACE.join(pts));
                    pts[0] = pts[0].substring(1);
                    if (pts.length == 2)
                        ret.getClass(cls).addField(pts[0], pts[1]);
                    else if (pts.length == 3)
                        ret.getClass(cls).addMethod(pts[0], pts[1], pts[2]);
                    else
                        throw new IOException("Invalid TSRG line, to many parts: " + SPACE.join(pts));
                } else {
                    if (pts.length == 2) {
                        if (!pts[0].endsWith("/"))
                            cls = pts[0];
                    }
                    else if (pts.length == 3)
                        ret.getClass(pts[0]).addField(pts[1], pts[2]);
                    else if (pts.length == 4)
                        ret.getClass(pts[0]).addMethod(pts[1], pts[2], pts[3]);
                    else
                        throw new IOException("Invalid CSRG line, to many parts: " + SPACE.join(pts));
                }
            }
        }
        return ret;
    }

    private static String[] rsplit(String str, char chr, int count) {
        List<String> pts = new ArrayList<>();
        int idx;
        while ((idx = str.lastIndexOf(chr)) != -1 && count > 0) {
            pts.add(str.substring(idx + 1));
            str = str.substring(0, idx);
            count--;
        }
        pts.add(str);
        Collections.reverse(pts);
        return pts.toArray(new String[pts.size()]);
    }

    private Map<String, Package> packages = new HashMap<>();
    private Map<String, Cls> classes = new HashMap<>();
    private Map<String, String> cache = new HashMap<>();

    public void addPackage(String original, String mapped) {
        if (original.equals(".")) original = "";
        if (mapped.equals(".")) mapped = "";
        Package old = packages.put(original, new Package(original, mapped));
        //TODO: Validate not changed?
    }
    public void addClass(String original, String mapped) {
        Cls old = classes.put(original, new Cls(original, mapped));
        //TODO: Validate not changed?
    }
    public Cls getClass(String original) {
        return classes.computeIfAbsent(original, k -> new Cls(original, original));
    }
    public Collection<Package> getPackages() {
        return this.packages.values();
    }
    public Collection<Cls> getClasses() {
        return this.classes.values();
    }

    public void write(Format format, File file) throws IOException {
        write(format, file, false);
    }
    public void write(Format format, File file, boolean reversed) throws IOException {
        List<String> lines = write(format, reversed);
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        try (FileOutputStream out = new FileOutputStream(file)){
            for (String line : lines) {
                out.write(line.getBytes());
                out.write('\n');
            }
        }
    }

    public List<String> write(Format format) {
        return write(format, false);
    }
    public List<String> write(Format format, boolean reversed) {
        List<String> ret = new ArrayList<>();
        for (String name : sort(packages, reversed))
            ret.add(packages.get(name).write(format, reversed));

        for (String name : sort(classes, reversed)) {
            Cls cls = classes.get(name);
            ret.add(cls.write(format, reversed));

            for (String fld : sort(cls.fields, reversed))
                ret.add(cls.fields.get(fld).write(format, reversed));

            for (String mtd : sort(cls.methods, reversed))
                ret.add(cls.methods.get(mtd).write(format, reversed));
        }
        return ret;
    }

    public String remapClass(String cls) {
        String ret = cache.get(cls);
        if (ret == null) {
            int idx = cls.lastIndexOf('$');
            if (idx != -1)
                ret = remapClass(cls.substring(0, idx)) + '$' + cls.substring(idx + 1);
            else
                ret = cls;
            //TODO: Package bulk moves? Issue: moving default package will move EVERYTHING, it's what its meant to do but we shouldn't.
            cache.put(cls, ret);
        }
        return ret;
    }

    public String remapDesc(String desc) {
        Matcher matcher = DESC.matcher(desc);
        StringBuffer buf = new StringBuffer();
        while (matcher.find())
            matcher.appendReplacement(buf, Matcher.quoteReplacement(remapClass(matcher.group())));
            matcher.appendTail(buf);
        return buf.toString();
    }

    private static List<String> sort(Map<String, ? extends Node> map, boolean reversed) {
        if (!reversed)
            return map.keySet().stream().sorted().collect(Collectors.toList());
        return map.values().stream().sorted((n1, n2) -> n1.getMapped().compareTo(n2.getMapped())).map(Node::getOriginal).collect(Collectors.toList());
    }

    public abstract class Node {
        protected String original;
        protected String mapped;

        protected Node(String original, String mapped) {
            this.original = original;
            this.mapped = mapped;
        }

        public String getOriginal() {
            return this.original;
        }

        public String getMapped() {
            return this.mapped;
        }

        public abstract String write(Format format, boolean reversed);
    }

    public class Package extends Node {
        private Package(String original, String mapped) {
            super(original, mapped);
        }
        @Override
        public String write(Format format, boolean reversed) {
            String sorig = getOriginal().isEmpty() ? "." : getOriginal();
            String smap = getMapped().isEmpty() ? "." : getMapped();
            if (reversed) {
                switch (format) {
                    case SRG: return "PK: " + smap + ' ' + sorig;
                    case CSRG:
                    case TSRG: return getMapped() + ' ' + getOriginal();
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            } else {
                switch (format) {
                    case SRG: return "PK: " + sorig + ' ' + smap;
                    case CSRG:
                    case TSRG: return getOriginal() + "/ " + getMapped() + '/';
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            }
        }
    }

    public class Cls extends Node {
        private Map<String, Field> fields = new HashMap<>();
        private Map<String, Method> methods = new HashMap<>();

        private Cls(String original, String mapped) {
            super(original, mapped);
        }

        public void addField(String original, String mapped) {
            Field old = fields.put(original, new Field(original, mapped));
            //TODO: Validate not changed?
        }

        public void addMethod(String original, String desc, String mapped) {
            Method old = methods.put(original, new Method(original, desc, mapped));
            //TODO: Validate not changed?
        }

        public Collection<Field> getFields() {
            return this.fields.values();
        }
        public Collection<Method> getMethods() {
            return this.methods.values();
        }

        @Override
        public String write(Format format, boolean reversed) {
            if (reversed) {
                switch (format) {
                    case SRG: return "CL: " + getMapped() + ' ' + getOriginal();
                    case CSRG:
                    case TSRG: return getMapped() + ' ' + getOriginal();
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            } else {
                switch (format) {
                    case SRG: return "CL: " + getOriginal() + ' ' + getMapped();
                    case CSRG:
                    case TSRG: return getOriginal() + ' ' + getMapped();
                    default: throw new UnsupportedOperationException("Unknown format: " + format);
                }
            }
        }

        public class Field extends Node {
            private Field(String original, String mapped) {
                super(original, mapped);
            }

            @Override
            public String write(Format format, boolean reversed) {
                if (reversed) {
                    switch (format) {
                        case SRG:  return "FD: " + Cls.this.getMapped() + '/' + getMapped() + ' ' + Cls.this.getOriginal() + '/' + getOriginal();
                        case CSRG: return Cls.this.getMapped() + ' ' + getMapped() + ' ' + getOriginal();
                        case TSRG: return '\t' + getMapped() + ' ' + getOriginal();
                        default: throw new UnsupportedOperationException("Unknown format: " + format);
                    }
                } else {
                    switch (format) {
                        case SRG:  return "FD: " + Cls.this.getOriginal() + '/' + getOriginal() + ' ' + Cls.this.getMapped() + '/' + getMapped();
                        case CSRG: return Cls.this.getOriginal() + ' ' + getOriginal() + ' ' + getMapped();
                        case TSRG: return '\t' + getOriginal() + ' ' + getMapped();
                        default: throw new UnsupportedOperationException("Unknown format: " + format);
                    }
                }
            }
        }

        public class Method extends Node {
            private String desc;
            private Method(String original, String desc, String mapped) {
                super(original, mapped);
                this.desc = desc;
            }
            public String getDescriptor() {
                return this.desc;
            }
            public String getMappedDescriptor() {
                return MappingFile.this.remapDesc(desc);
            }

            @Override
            public String write(Format format, boolean reversed) {
                if (reversed) {
                    String mapedDesc = getMappedDescriptor();
                    switch (format) {
                        case SRG:  return "MD: " + Cls.this.getMapped() + '/' + getMapped() + ' ' + mapedDesc + Cls.this.getOriginal() + '/' + getOriginal() + ' ' + desc;
                        case CSRG: return Cls.this.getMapped() + ' ' + getMapped() + ' ' + mapedDesc + ' ' + getOriginal();
                        case TSRG: return '\t' + getMapped() + ' ' + mapedDesc + ' ' + getOriginal();
                        default: throw new UnsupportedOperationException("Unknown format: " + format);
                    }
                } else {
                    switch (format) {
                        case SRG:  return "MD: " + Cls.this.getOriginal() + '/' + getOriginal() + ' ' + desc + ' ' + Cls.this.getMapped() + '/' + getMapped() + ' ' + MappingFile.this.remapDesc(desc);
                        case CSRG: return Cls.this.getOriginal() + ' ' + getOriginal() + ' ' + desc + ' ' + getMapped();
                        case TSRG: return '\t' + getOriginal() + ' ' +  desc + ' ' + getMapped();
                        default: throw new UnsupportedOperationException("Unknown format: " + format);
                    }
                }
            }
        }
    }

}
