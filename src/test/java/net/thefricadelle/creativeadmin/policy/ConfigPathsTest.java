/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The move from the files written under the Arcadia Creative Admin name, against a real folder.
 *
 * @author THEFricadelle
 */
class ConfigPathsTest {

    @TempDir
    Path config;

    private Path legacy(String name) {
        return config.resolve("arcadia").resolve(name);
    }

    private Path current(String name) {
        return config.resolve(ConfigPaths.FOLDER).resolve(name);
    }

    private static void write(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("the policy and the assignments written under the old name are moved")
    void movesBothFiles() throws IOException {
        write(legacy("arcadia-creative-admin-policy.json"), "policy");
        write(legacy("arcadia-creative-admin-assignments.json"), "assignments");

        List<ConfigPaths.Move> moves = ConfigPaths.migrate(config);

        assertEquals(2, moves.size());
        assertTrue(moves.stream().allMatch(move -> move.error() == null));
        assertEquals("policy", Files.readString(ConfigPaths.policy(config)));
        assertEquals("assignments", Files.readString(ConfigPaths.assignments(config)));
        assertEquals(current(ConfigPaths.POLICY), ConfigPaths.policy(config));
        assertFalse(Files.exists(legacy("arcadia-creative-admin-policy.json")));
    }

    @Test
    @DisplayName("a file already at the new path is never overwritten")
    void keepsExistingNewFile() throws IOException {
        write(legacy("arcadia-creative-admin-policy.json"), "old");
        write(current(ConfigPaths.POLICY), "new");

        ConfigPaths.migrate(config);

        assertEquals("new", Files.readString(ConfigPaths.policy(config)));
        assertTrue(Files.exists(legacy("arcadia-creative-admin-policy.json")));
    }

    @Test
    @DisplayName("a policy left at the old path is read there, never reported missing")
    void unmovedPolicyIsStillFound() throws IOException {
        // What a failed move leaves behind. Reporting the new, empty path would make the manager
        // write a disabled sample and stop enforcing.
        Path old = legacy("arcadia-creative-admin-policy.json");
        write(old, "policy");
        assertEquals(old, ConfigPaths.policy(config));
    }

    @Test
    @DisplayName("a fresh install creates nothing and points at the new folder")
    void freshInstall() {
        assertTrue(ConfigPaths.migrate(config).isEmpty());
        assertFalse(Files.exists(config.resolve(ConfigPaths.FOLDER)));
        assertEquals(current(ConfigPaths.ASSIGNMENTS), ConfigPaths.assignments(config));
    }
}
