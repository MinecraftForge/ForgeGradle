package net.minecraftforge.gradle;

import net.minecraftforge.util.os.OS;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.provider.ValueSource;
import org.gradle.api.provider.ValueSourceParameters;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

abstract class OperatingSystemName implements ValueSource<String, OperatingSystemName.Parameters> {
    interface Parameters extends ValueSourceParameters {
        SetProperty<OS> getAllowedOperatingSystems();
    }

    @Inject
    public OperatingSystemName() { }

    @Override
    public @Nullable String obtain() {
        var allowed = getParameters().getAllowedOperatingSystems().get().toArray(new OS[0]);
        return allowed.length > 0
            ? OS.current(allowed).key()
            : OS.current().key();
    }
}
