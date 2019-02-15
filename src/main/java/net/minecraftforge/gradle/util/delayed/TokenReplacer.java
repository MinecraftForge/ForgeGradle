/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013-2019 Minecraft Forge
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */
package net.minecraftforge.gradle.util.delayed;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenReplacer implements Serializable
{
    private static final long serialVersionUID = 1L;

    protected static Logger LOGGER = LoggerFactory.getLogger(DelayedBase.class);

    private final ReplacementProvider provider;
    private final String              input;
    private String                    outCache;

    public TokenReplacer(ReplacementProvider provider, String input)
    {
        this.provider = provider;
        this.input = input;
    }

    public String replace()
    {
        if (outCache != null)
            return outCache;

        LOGGER.debug("Resolving: {}", input);

        StringBuilder builder = new StringBuilder(input);
        boolean saveOutCache = true;

        int index = 0;
        int endIndex;

        while (true)
        {
            index = builder.indexOf("{", index);

            if (index < 0) // no more found
                break;

            endIndex = builder.indexOf("}", index);

            if (endIndex < 0) // random { with no closing brace?
                break;

            String key = builder.substring(index + 1, endIndex);// skip the {}
            String repl = provider.get(key);
            if (repl == null)
            {
                index = endIndex;// skip this {} token, we dont have the necessray info for it.
                saveOutCache = false;

                // eventually remove.. or keep forever?
                throw new RuntimeException("MISSING REPLACEMENT DATA FOR " + key);
            }
            else
            {
                // replace the {} too
                builder.replace(index, endIndex + 1, repl);
                // do not move the index or lastIndex pointers.
                // there is the chance that the replacement string has tokens in it that need replacing.
            }
        }

        String out = builder.toString();

        if (saveOutCache)
            outCache = out;

        LOGGER.debug("Resolved: {}", out);

        return out;
    }

    /**
     * Cleanes the cached replacedOutput.
     * This is only useful if the token replacements have changed since the data was cached.
     */
    public void cleanCache()
    {
        outCache = null;
    }
}
