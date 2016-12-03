package net.minecraftforge.gradle.tasks.fernflower;

import org.jetbrains.java.decompiler.code.CodeConstants;
import org.jetbrains.java.decompiler.struct.StructMethod;
import org.jetbrains.java.decompiler.util.JADNameProvider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class AdvancedJadRenamer extends JADNameProvider {
    private StructMethod wrapper;
    private static final Pattern p = Pattern.compile("func_(\\d+)_.*");
    public AdvancedJadRenamer(StructMethod wrapper)
    {
        super(wrapper);
        this.wrapper = wrapper;
    }
    @Override
    public String renameAbstractParameter(String abstractParam, int index)
    {
        String result = abstractParam;
        if ((wrapper.getAccessFlags() & CodeConstants.ACC_ABSTRACT) != 0) {
            String methName = wrapper.getName();
            Matcher m = p.matcher(methName);
            if (m.matches()) {
                result = String.format("p_%s_%d_", m.group(1), index);
            }
        }
        return result;

    }
}
