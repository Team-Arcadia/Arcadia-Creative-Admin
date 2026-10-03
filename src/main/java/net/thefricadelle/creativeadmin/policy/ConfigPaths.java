/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the policy and the assignments live under {@code config/}, and the move away from the
 * paths used before the mod was renamed from Arcadia Creative Admin.
 * <p>
 * Both files are moved once, at mod construction, before anything reads them. A file that could
 * not be moved is read and written in place instead: {@link PolicyManager} treats a missing policy
 * as a fresh install and writes a disabled sample, so a failed move must never look like a missing
 * file, or enforcement would silently switch off on the first start after the update.
 * <p>
 * Free of Minecraft types, logging included, so the move is unit tested against a real folder.
 *
 * @author THEFricadelle
 */
public final class ConfigPaths {

    /** Folder under {@code config/} holding every file of the mod. */
    public static final String FOLDER = "creative-admin";
    public static final String POLICY = "policy.json";
    public static final String ASSIGNMENTS = "assignments.json";

    private static final String LEGACY_FOLDER = "arcadia";
    private static final String LEGACY_POLICY = "arcadia-creative-admin-policy.json";
    private static final String LEGACY_ASSIGNMENTS = "arcadia-creative-admin-assignments.json";

    private ConfigPaths() {}

    public static Path policy(Path configDir) {
        return resolve(configDir, POLICY, LEGACY_POLICY);
    }

    public static Path assignments(Path configDir) {
        return resolve(configDir, ASSIGNMENTS, LEGACY_ASSIGNMENTS);
    }

    /** The current path, or the old one when only the old one exists because its move failed. */
    private static Path resolve(Path configDir, String name, String legacyName) {
        Path current = configDir.resolve(FOLDER).resolve(name);
        Path legacy = configDir.resolve(LEGACY_FOLDER).resolve(legacyName);
        return !Files.exists(current) && Files.exists(legacy) ? legacy : current;
    }

    /**
     * Moves the files written under the old name. A file already present at the new path wins and
     * the old one is left in place: nothing is ever overwritten.
     *
     * @return one entry per file it tried to move, for the caller to log
     */
    public static List<Move> migrate(Path configDir) {
        Path legacyDir = configDir.resolve(LEGACY_FOLDER);
        Path dir = configDir.resolve(FOLDER);
        List<Move> moves = new ArrayList<>(2);
        move(legacyDir.resolve(LEGACY_POLICY), dir.resolve(POLICY), moves);
        move(legacyDir.resolve(LEGACY_ASSIGNMENTS), dir.resolve(ASSIGNMENTS), moves);
        return moves;
    }

    private static void move(Path from, Path to, List<Move> moves) {
        if (!Files.isRegularFile(from) || Files.exists(to)) {
            return;
        }
        try {
            Files.createDirectories(to.getParent());
            try {
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomic) {
                Files.move(from, to);
            }
            moves.add(new Move(from, to, null));
        } catch (IOException e) {
            moves.add(new Move(from, to, e));
        }
    }

    /** @param error why the file stayed where it was, or {@code null} when it moved */
    public record Move(Path from, Path to, IOException error) {}
}
