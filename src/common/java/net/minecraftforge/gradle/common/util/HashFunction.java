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

package net.minecraftforge.gradle.common.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import org.apache.commons.io.IOUtils;

//These are all standard hashing functions the JRE is REQUIRED to have, so add a nice factory that doesnt require catching annoying exceptions;
public enum HashFunction {
    MD5("md5", 32),
    SHA1("SHA-1", 40),
    SHA256("SHA-256", 64);

    private String algo;
    private String pad;

    private HashFunction(String algo, int length) {
        this.algo = algo;
        this.pad = String.format("%0" + length + "d", 0);
    }

    public String getExtension() {
         return this.name().toLowerCase(Locale.ENGLISH);
    }

    public MessageDigest get() {
        try {
            return MessageDigest.getInstance(algo);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e); //Never happens
        }
    }

    public String hash(File file) throws IOException {
        try (FileInputStream fin = new FileInputStream(file)) {
            return hash(fin);
        }
    }

    public String hash(Iterable<File> files) throws IOException {
        MessageDigest hash = get();
        byte[] buf = new byte[1024];

        for (File file : files) {
            if (!file.exists())
                continue;

            try (FileInputStream fin = new FileInputStream(file)) {
                int count = -1;
                while ((count = fin.read(buf)) != -1)
                    hash.update(buf, 0, count);
            }
        }
        return pad(new BigInteger(1, hash.digest()).toString(16));
    }

    public String hash(String data) {
        return hash(data == null ? new byte[0] : data.getBytes(StandardCharsets.UTF_8));
    }

    public String hash(InputStream stream) throws IOException {
        return hash(IOUtils.toByteArray(stream));
    }

    public String hash(byte[] data) {
        return pad(new BigInteger(1, get().digest(data)).toString(16));
    }

    public String pad(String hash) {
        return (pad + hash).substring(hash.length());
    }
}
