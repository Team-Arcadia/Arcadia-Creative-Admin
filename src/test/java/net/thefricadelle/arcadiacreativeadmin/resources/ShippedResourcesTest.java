/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The files shipped inside the jar, checked against the sources that use them.
 * <p>
 * A key missing in one language, a key the code asks for that no file declares, or a placeholder
 * count that differs between languages only shows up in game, as a raw key on an admin's screen or
 * a formatting exception in the server log. The admin screens build most keys from a short suffix
 * ({@code tr("stale")}) or from an enum ({@code "status." + result}), so both forms are resolved here.
 *
 * @author THEFricadelle
 */
class ShippedResourcesTest {

    private static final String MOD = "arcadiacreativeadmin.";
    private static final String ADMIN = MOD + "admin.";

    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path SOURCES = Path.of("src", "main", "java");
    private static final Path PACKAGE = SOURCES.resolve("net/thefricadelle/arcadiacreativeadmin");
    private static final Path LANG = RESOURCES.resolve("assets/arcadiacreativeadmin/lang");

    /** Admin-screen helpers whose first argument is a key relative to {@link #ADMIN}. */
    private static final Pattern RELATIVE_KEY = Pattern.compile("\\b(?:tr|text|caption)\\(\"([a-z0-9_.]+)\"\\s*[,)]");
    private static final Pattern FULL_KEY = Pattern.compile("\"(" + Pattern.quote(MOD) + "[a-z0-9_.]*[a-z0-9_])\"");
    private static final Pattern ANY_LITERAL = Pattern.compile("\"([a-z0-9_.]+)\"");

    /** Key prefixes completed at runtime, with the values the code can append. */
    private static Map<String, List<String>> computedKeys() {
        List<String> modes = enumIds("policy/CreativeProfile.java", "Mode");
        return Map.of(
                ADMIN + "mode.", modes,
                ADMIN + "mode_help.", modes,
                ADMIN + "selection.", modes,
                ADMIN + "profile_help.", modes,
                ADMIN + "status.", enumIds("policy/PolicyManager.java", "SaveResult"),
                ADMIN + "name_", returnedLiterals("policy/PolicyCodec.java", "nameProblem"),
                MOD + "help.", helpKeys());
    }

    /** The help title and each topic's heading and body, read from HelpScreen's own list. */
    private static List<String> helpKeys() {
        List<String> keys = new ArrayList<>(List.of("title"));
        Matcher topic = Pattern.compile("new Topic\\(\"([a-z_]+)\"").matcher(source("client/screen/HelpScreen.java"));
        while (topic.find()) {
            keys.add(topic.group(1) + ".heading");
            keys.add(topic.group(1) + ".body");
        }
        assertFalse(keys.size() == 1, "No help topic found in HelpScreen; the pattern no longer matches");
        return keys;
    }

    // ---------------------------------------------------------------- source parsing

    private static String source(String relative) {
        return read(PACKAGE.resolve(relative));
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path.toAbsolutePath(), e);
        }
    }

    /** The constants of {@code enum name} in a source file, lower-cased as {@code id()} returns them. */
    private static List<String> enumIds(String file, String name) {
        Matcher body = Pattern.compile("enum\\s+" + name + "\\s*\\{([^;}]*)").matcher(source(file));
        assertTrue(body.find(), "No enum " + name + " in " + file);
        List<String> ids = new ArrayList<>();
        Matcher constant = Pattern.compile("\\b([A-Z][A-Z0-9_]*)\\b").matcher(body.group(1).replaceAll("/\\*.*?\\*/", ""));
        while (constant.find()) {
            ids.add(constant.group(1).toLowerCase(Locale.ROOT));
        }
        assertFalse(ids.isEmpty(), "Enum " + name + " in " + file + " has no constant");
        return ids;
    }

    /** Every {@code return "literal"} inside the method named {@code method}. */
    private static List<String> returnedLiterals(String file, String method) {
        String text = source(file);
        int start = text.indexOf(" " + method + "(");
        assertTrue(start >= 0, "No method " + method + " in " + file);
        String body = text.substring(start, text.indexOf("\n    }", start));
        List<String> values = new ArrayList<>();
        Matcher matcher = Pattern.compile("return\\s+\"([a-z0-9_]+)\"").matcher(body);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        assertFalse(values.isEmpty(), method + " in " + file + " returns no literal");
        return values;
    }

    private static List<String> sources() {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            List<String> contents = new ArrayList<>();
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                contents.add(Files.readString(file, StandardCharsets.UTF_8));
            }
            return contents;
        } catch (IOException e) {
            throw new AssertionError("Could not read the sources under " + SOURCES.toAbsolutePath(), e);
        }
    }

    /** Every key the code can ask for: full literals, admin-relative helper arguments, computed families. */
    private static Set<String> requestedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (String source : sources()) {
            FULL_KEY.matcher(source).results().forEach(m -> keys.add(m.group(1)));
            RELATIVE_KEY.matcher(source).results().map(m -> m.group(1))
                    .filter(key -> !key.endsWith(".") && !key.endsWith("_"))
                    .forEach(key -> keys.add(ADMIN + key));
        }
        computedKeys().forEach((prefix, values) -> values.forEach(value -> keys.add(prefix + value)));
        return keys;
    }

    // ---------------------------------------------------------------- lang files

    private static JsonObject json(Path path) {
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path.toAbsolutePath(), e);
        }
    }

    private static Set<String> keys(String language) {
        return new TreeSet<>(json(LANG.resolve(language + ".json")).keySet());
    }

    private static long placeholders(String value) {
        return Pattern.compile("%(s|d|\\d+\\$s|\\d+\\$d)").matcher(value).results().count();
    }

    @Test
    @DisplayName("every translation key exists in both languages")
    void languagesAgree() {
        assertEquals(keys("en_us"), keys("fr_fr"), "en_us.json and fr_fr.json must declare the same keys");
    }

    @Test
    @DisplayName("a translated string keeps the placeholders of its English original")
    void placeholdersAgree() {
        JsonObject en = json(LANG.resolve("en_us.json"));
        JsonObject fr = json(LANG.resolve("fr_fr.json"));
        for (Map.Entry<String, JsonElement> entry : en.entrySet()) {
            String key = entry.getKey();
            assertTrue(fr.has(key), "fr_fr.json lacks " + key);
            assertEquals(placeholders(entry.getValue().getAsString()), placeholders(fr.get(key).getAsString()),
                    "Placeholder count differs for '" + key + "', which would throw when formatted");
        }
    }

    @Test
    @DisplayName("every key the code asks for is translated")
    void noMissingKey() {
        Set<String> missing = new TreeSet<>(requestedKeys());
        missing.removeAll(keys("en_us"));
        assertTrue(missing.isEmpty(), "Code references keys that no language file declares: " + missing);
    }

    @Test
    @DisplayName("no translation is shipped that nothing reads")
    void noUnusedKey() {
        Set<String> requested = requestedKeys();
        Set<String> literals = new TreeSet<>();
        for (String source : sources()) {
            ANY_LITERAL.matcher(source).results().forEach(m -> literals.add(m.group(1)));
        }
        Set<String> unused = new TreeSet<>();
        for (String key : keys("en_us")) {
            boolean relativeLiteral = key.startsWith(ADMIN) && literals.contains(key.substring(ADMIN.length()));
            if (!requested.contains(key) && !relativeLiteral) {
                unused.add(key);
            }
        }
        assertTrue(unused.isEmpty(), "Language files declare keys no source references: " + unused);
    }

    // ---------------------------------------------------------------- mixin config

    @Test
    @DisplayName("every mixin named in the config exists")
    void mixinClassesExist() {
        JsonObject config = json(RESOURCES.resolve("arcadia-creative-admin.mixins.json"));
        String pkg = config.get("package").getAsString().replace('.', '/');
        for (String side : List.of("mixins", "client", "server")) {
            if (!config.has(side)) {
                continue;
            }
            for (JsonElement element : config.getAsJsonArray(side)) {
                Path file = SOURCES.resolve(pkg).resolve(element.getAsString().replace('.', '/') + ".java");
                assertTrue(Files.isRegularFile(file),
                        "Mixin config lists '" + element.getAsString() + "' but " + file + " does not exist");
            }
        }
    }

    @Test
    @DisplayName("the mixin config declares no refmap, which this toolchain never generates")
    void noDanglingRefmap() {
        JsonObject config = json(RESOURCES.resolve("arcadia-creative-admin.mixins.json"));
        if (config.has("refmap")) {
            fail("Mixin config names refmap '" + config.get("refmap").getAsString()
                    + "', which is never generated and warns on every launch");
        }
    }
}
