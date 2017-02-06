/*
 * A Gradle plugin for the creation of Minecraft mods and MinecraftForge plugins.
 * Copyright (C) 2013 Minecraft Forge
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
package net.minecraftforge.gradle.user.patcherUser.forge;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import groovy.lang.MissingMethodException;
import net.minecraftforge.gradle.tasks.AbstractJsonTask;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.internal.ClosureBackedAction;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class ForgeModInfo implements AbstractJsonTask.IModInfo {

    @SuppressWarnings("unused")
    private int modListVersion = 2;
    private List<FMLModInfo> modList = Lists.newArrayList();

    private transient Map<String, FMLModInfo> modidMap = Maps.newHashMap();

    private transient final String mcversion;

    public ForgeModInfo(String mcversion) {
        this.mcversion = mcversion;
    }

    @Override
    public void validate() throws InvalidUserDataException {
        // Nothing is required. Everything is completely optional.
    }

    public Object propertyMissing(String name) {
        return mod(name);
    }

    public Object methodMissing(String name, Object params) {
        // FIXME requires explicit reference to type. e.g. `it.botania {}`
        Object[] args = (Object[]) params;
        if (args.length == 1 && args[0] instanceof Closure) {
            mod(name, (Closure) args[0]);
            return null;
        }
        throw new MissingMethodException(name, this.getClass(), args);

    }

    /**
     * Gets the mod info from the list of mods using the modid. If one does not exist, it is created.
     *
     * @param id The modid
     * @return The mod info object
     */
    public FMLModInfo mod(String id) {
        if (!modidMap.containsKey(id)) {
            FMLModInfo info = new FMLModInfo(id, mcversion);
            modList.add(info);
            modidMap.put(id, info);
        }
        return modidMap.get(id);
    }

    /**
     * <p>Quickly assign the properties to the given modid.</p>
     * <strong>Example</strong>
     * <pre>
     *     mod('example', name: 'Example', version: '1.0.0')
     *     mod('sample',
     *          name: 'Sample Mod",
     *          version: '2.0.3',
     *          url: 'http://example.com/sample'
     *     )
     * </pre>
     *
     * @param id   the modid
     * @param args the properties to set
     */
    public void mod(Map<String, ?> args, String id) {
        InvokerHelper.setProperties(mod(id), args);
    }

    /**
     * Configures the given {@link FMLModInfo} from the modid with a closure backed action
     * <pre>
     *     mod('pipecraft') {
     *         name = 'Pipe Craft'
     *         version = '6.0.3'
     *         mcversion = '1.10.2'
     *         description = 'All the pipes!'
     *         // etc
     *     }
     * </pre>
     *
     * @param id     The modid
     * @param action the closure to use to configure the mod info
     */
    public void mod(String id, Closure<?> action) {
        ClosureBackedAction.execute(mod(id), action);
    }

    public List<FMLModInfo> getMods() {
        return this.modList;
    }

    public class FMLModInfo {
        private final String modid;
        private String name;
        private String description;
        private String version;
        private String mcversion;
        private String url;
        private String updateUrl;
        private Set<String> authorList;
        private Set<String> screenshots;
        private String credits;
        private String logoFile;
        private String parent;
        private Set<String> requiredMods;
        private Set<String> dependencies;
        private Set<String> dependants;
        private String useDependencyInformation;

        private FMLModInfo(String id, String mcversion) {
            this.modid = id;
            this.mcversion = mcversion;
        }

        public String getModid() {
            return modid;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getMcversion() {
            return mcversion;
        }

        public void setMcversion(String mcversion) {
            this.mcversion = mcversion;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUpdateUrl() {
            return updateUrl;
        }

        public void setUpdateUrl(String updateUrl) {
            this.updateUrl = updateUrl;
        }

        public Set<String> getAuthorList() {
            if (authorList == null) authorList = Sets.newHashSet();
            return authorList;
        }

        public void setAuthorList(Set<String> authorList) {
            this.authorList = authorList;
        }

        public Set<String> getScreenshots() {
            if (screenshots == null) screenshots = Sets.newHashSet();
            return screenshots;
        }

        public void setScreenshots(Set<String> screenshots) {
            this.screenshots = screenshots;
        }

        public String getCredits() {
            return credits;
        }

        public void setCredits(String credits) {
            this.credits = credits;
        }

        public String getLogoFile() {
            return logoFile;
        }

        public void setLogoFile(String logoFile) {
            this.logoFile = logoFile;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public Set<String> getRequiredMods() {
            if (requiredMods == null) requiredMods = Sets.newHashSet();
            return requiredMods;
        }

        public void setRequiredMods(Set<String> requiredMods) {
            this.requiredMods = requiredMods;
        }

        public Set<String> getDependencies() {
            if (dependencies == null) dependencies = Sets.newHashSet();
            return dependencies;
        }

        public void setDependencies(Set<String> dependencies) {
            this.dependencies = dependencies;
        }

        public Set<String> getDependants() {
            if (dependants == null) dependants = Sets.newHashSet();
            return dependants;
        }

        public void setDependants(Set<String> dependants) {
            this.dependants = dependants;
        }

        public String getUseDependencyInformation() {
            return useDependencyInformation;
        }

        public void setUseDependencyInformation(String useDependencyInformation) {
            this.useDependencyInformation = useDependencyInformation;
        }
    }
}
