/*
 * ForgeGradle
 * Copyright (C) 2018 Forge Development LLC
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

package net.minecraftforge.gradle.userdev.util;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Input;

import java.io.File;
import java.nio.file.Files;

public abstract class DeobfuscatorTransformer implements TransformAction<DeobfuscatorTransformer.Parameters> {

	private static Deobfuscator deobfuscator;

	public static void setDeobfuscator(final Deobfuscator deobfuscator) {
		DeobfuscatorTransformer.deobfuscator = deobfuscator;
	}

	public interface Parameters extends TransformParameters {
		@Input
		Provider<String> getMappings();

		void setMappings(Provider<String> mappingChannel);

	}

	@InputArtifact
	protected abstract Provider<FileSystemLocation> getInputArtifact();

	@Override
	public void transform(final TransformOutputs outputs) {

		final File input = getInputArtifact().get().getAsFile();
		final String inputName = input.getName().substring(0, input.getName().lastIndexOf(".jar"));

		final String fileName = String.format("%s_%s.jar", inputName,
				String.format("mapped_%s", getParameters().getMappings().get()));
		if (deobfuscator == null) {
			outputs.file(input);
		} else {
			try {
				final File file = deobfuscator.deobfBinary(input, getParameters().getMappings().get(), fileName);
				Files.copy(file.toPath(), Files.newOutputStream(outputs.file(fileName).toPath()));
			} catch (final Exception e) {
				throw new RuntimeException(e);
			}
		}
	}
}
