/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.resources;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The loader metadata the build generates, checked against the version the mod is built on and the
 * one its documentation announces, so the three cannot drift apart.
 *
 * @author THEFricadelle
 */
class ModMetadataTest {

    /** Written by processResources, which the test classpath depends on. */
    private static final Path GENERATED_TOML = Path.of("build", "resources", "main", "META-INF", "neoforge.mods.toml");
    private static final Pattern NEOFORGE_VERSION = Pattern.compile("\\b21\\.1\\.\\d+\\b");

    private static Properties gradleProperties() {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("gradle.properties"), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new AssertionError("Could not read gradle.properties", e);
        }
        return properties;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new AssertionError("Could not read " + path.toAbsolutePath(), e);
        }
    }

    /** The versionRange of the dependency block naming {@code modId}. */
    private static String dependencyRange(String toml, String modId) {
        Matcher matcher = Pattern.compile("modId\\s*=\\s*\"" + Pattern.quote(modId)
                + "\"[^\\[]*?versionRange\\s*=\\s*\"([^\"]+)\"").matcher(toml);
        assertTrue(matcher.find(), "No dependency on '" + modId + "' in " + GENERATED_TOML);
        return matcher.group(1);
    }

    @Test
    @DisplayName("the NeoForge requirement starts at the version the mod is built against")
    void neoForgeRangeStartsAtTheBuildVersion() {
        String built = gradleProperties().getProperty("neo_version");
        assertEquals("[" + built + ",)", dependencyRange(read(GENERATED_TOML), "neoforge"),
                "The mod would load on NeoForge builds it was never compiled or tested against");
    }

    @Test
    @DisplayName("the README announces the NeoForge version the metadata requires")
    void readmeMatchesTheMetadata() {
        String built = gradleProperties().getProperty("neo_version");
        Set<String> announced = new TreeSet<>();
        Matcher matcher = NEOFORGE_VERSION.matcher(read(Path.of("README.md")));
        while (matcher.find()) {
            announced.add(matcher.group());
        }
        assertEquals(Set.of(built), announced, "README.md names a NeoForge version other than " + built);
    }

    @Test
    @DisplayName("every placeholder of the metadata template is filled")
    void noUnexpandedPlaceholder() {
        String toml = read(GENERATED_TOML);
        assertFalse(toml.contains("${"), "Unexpanded placeholder left in " + GENERATED_TOML + ":\n" + toml);
    }

    @Test
    @DisplayName("the mixin config named by the metadata exists")
    void mixinConfigExists() {
        Matcher matcher = Pattern.compile("config\\s*=\\s*\"([^\"]+)\"").matcher(read(GENERATED_TOML));
        assertTrue(matcher.find(), "No [[mixins]] config in " + GENERATED_TOML);
        Path config = Path.of("src", "main", "resources", matcher.group(1));
        assertTrue(Files.isRegularFile(config), "Metadata names " + config + ", which does not exist");
    }
}
