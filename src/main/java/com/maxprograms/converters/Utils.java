/*******************************************************************************
 * Copyright (c) 2023 Maxprograms.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/epl-v10.html
 *
 * Contributors:
 *     Maxprograms - initial API and implementation
 *******************************************************************************/
package com.maxprograms.converters;

import com.maxprograms.languages.RegistryParser;
import com.maxprograms.xml.XMLUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Utils {

    protected static final Logger logger = LoggerFactory.getLogger(Utils.class.getName());
    private static RegistryParser registry;

    private Utils() {
        // do not instantiate this class
    }

    public static String cleanString(String string) {
        String result = string.replace("&", "&amp;");
        result = result.replace("<", "&lt;");
        result = result.replace(">", "&gt;");
        return XMLUtils.validChars(result);
    }

    public static String getAbsolutePath(File homeFile, String relative) throws IOException {
        return getAbsolutePath(homeFile.getAbsolutePath(), relative);
    }

    public static String getAbsolutePath(String homeFile, String relative) throws IOException {
        try {
            File result = relative.indexOf('%') != -1 ? new File(URLDecoder.decode(relative, StandardCharsets.UTF_8.toString()))
                    : new File(relative);
            if (!result.isAbsolute()) {
                File home = new File(homeFile);
                // If home is a file, get the parent
                if (!home.isDirectory()) {
                    home = home.getParentFile();
                }
                result = new File(home, relative);
            }
            return result.getCanonicalPath();
        } catch (IOException e) {
            logger.error("Invalid path", e);
            throw e;
        }
    }

    public static String makeRelativePath(String homeFile, String filename) throws IOException {
        File home = new File(homeFile);
        // If home is a file, get the parent
        if (!home.isDirectory()) {
            if (home.getParent() != null) {
                home = new File(home.getParent());
            } else {
                home = new File(System.getProperty("user.dir"));
            }

        }
        File file = new File(filename);
        if (!file.isAbsolute()) {
            return filename;
        }
        // Check for relative path
        if (!home.isAbsolute()) {
            throw new IOException("Path must be absolute.");
        }

        List<String> homelist = getPathList(home);
        List<String> filelist = getPathList(file);
        return matchPathLists(homelist, filelist);
    }

    private static List<String> getPathList(File file) throws IOException {
        List<String> result = new ArrayList<>();
        File r = file.getCanonicalFile();
        while (r != null) {
            result.add(r.getName());
            r = r.getParentFile();
        }
        return result;
    }

    private static String matchPathLists(List<String> home, List<String> file) {
        StringBuilder s = new StringBuilder();
        // start at the beginning of the lists
        // iterate while both lists are equal
        int i = home.size() - 1;
        int j = file.size() - 1;

        // first eliminate common root
        while (i >= 0 && j >= 0 && home.get(i).equals(file.get(j))) {
            i--;
            j--;
        }

        // for each remaining level in the home path, add a ..
        for (; i >= 0; i--) {
            s.append("..");
            s.append(File.separator);
        }

        // for each level in the file path, add the path
        for (; j >= 1; j--) {
            s.append(file.get(j));
            s.append(File.separator);
        }

        // file name
        if (j >= 0 && j < file.size()) {
            s.append(file.get(j));
        }
        return s.toString();
    }

    public static String[] getPageCodes() {
        TreeMap<String, Charset> charsets = new TreeMap<>(Charset.availableCharsets());
        Set<String> keys = charsets.keySet();
        String[] codes = new String[keys.size()];

        Iterator<String> i = keys.iterator();
        int j = 0;
        while (i.hasNext()) {
            Charset cset = charsets.get(i.next());
            codes[j++] = cset.displayName();
        }
        return codes;
    }

    public static void decodeZippedToFile(String dataToDecode, String filename) throws IOException {
        Decoder decoder = Base64.getMimeDecoder();
        byte[] decodedBytes = decoder.decode(dataToDecode);
        ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(decodedBytes));
        zipInputStream.getNextEntry();
        java.nio.file.Files.copy(
                zipInputStream,
                new File(filename).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        zipInputStream.close();

    }

    public static void decodeToFile(String dataToDecode, String filename) throws IOException {
        Decoder decoder = Base64.getMimeDecoder();
        try (FileOutputStream output = new FileOutputStream(filename)) {
            output.write(decoder.decode(dataToDecode));
        }
    }


    public static String encodeFromFileZipped(String filename) throws IOException {
        File file = new File(filename);
        int size = Math.max((int) (file.length() * 1.4), 4096);
        byte[] buffer = new byte[size]; // Need max() for math on small files (v2.2.1)
        int length = 0;
        int numBytes = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while ((numBytes = input.read(buffer, length, size - length)) != -1) {
                length += numBytes;
            }
        }
        Encoder encoder = Base64.getMimeEncoder();
        String encodedString = "";
        try (ByteArrayOutputStream bout = new ByteArrayOutputStream();
             ZipOutputStream zout = new ZipOutputStream(bout);
        ) {
            ZipEntry entry = new ZipEntry("zippedFile");
            zout.putNextEntry(entry);
            zout.write(Arrays.copyOf(buffer, length));
            zout.closeEntry();
            encodedString = encoder.encodeToString(bout.toByteArray());
        }
        return encodedString;
    }

    public static String encodeFromFile(String filename) throws IOException {
        File file = new File(filename);
        int size = Math.max((int) (file.length() * 1.4), 4096);
        byte[] buffer = new byte[size]; // Need max() for math on small files (v2.2.1)
        int length = 0;
        int numBytes = 0;
        try (FileInputStream input = new FileInputStream(file)) {
            while ((numBytes = input.read(buffer, length, size - length)) != -1) {
                length += numBytes;
            }
        }
        Encoder encoder = Base64.getMimeEncoder();
        return encoder.encodeToString(Arrays.copyOf(buffer, length));
    }

    public static boolean isValidLanguage(String lang) throws IOException {
        if (registry == null) {
            registry = new RegistryParser();
        }
        return !registry.getTagDescription(lang).isEmpty();
    }

    public static String[] fixPath(String[] args) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("-")) {
                if (StringUtils.isNotEmpty(current)) {
                    result.add(current.toString().trim());
                    current = new StringBuilder();
                }
                result.add(arg);
            } else {
                current.append(' ');
                current.append(arg);
            }
        }
        if (StringUtils.isNotEmpty(current)) {
            result.add(current.toString().trim());
        }
        return result.toArray(new String[result.size()]);
    }

    public static boolean lookingAt(String target, String text, int start) {
        if (target.length() > text.length() + start) {
            return false;
        }
        for (int i = 0; i < target.length(); i++) {
            if (target.charAt(i) != text.charAt(i + start)) {
                return false;
            }
        }
        return true;
    }
}
