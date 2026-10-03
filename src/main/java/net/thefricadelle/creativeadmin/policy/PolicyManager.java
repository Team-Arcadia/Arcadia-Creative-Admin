/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.loading.FMLPaths;
import net.thefricadelle.creativeadmin.core.TabItemIndex;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Server-side state: the profiles that exist, who is under which, and whether enforcement is on.
 * <p>
 * The policy file is owned by the mod: the admin screen edits it and the mod writes it back. Hand
 * edits still work and are picked up by {@code /creativeadmin reload}, but formatting is rewritten
 * on the next save. Assignments live in a second file because they change far more often and a
 * broken one must not take the profiles down with it.
 * <p>
 * Which profile applies to a player, first match wins: the bypass permission (nothing), an
 * assignment made from the screen or the command, a profile given by a permission mod through
 * {@link CreativePermissions#PROFILE}, then the default profile.
 * <p>
 * Every failure path denies rather than allows. An unknown profile, an unreadable file, a parse
 * error or an out-of-range setting all resolve to {@link CreativeProfile#denyAll}, because the
 * opposite direction hands a full creative inventory to whoever the broken entry pointed at. The
 * only case that disables enforcement is a policy file that does not exist at server start: that is
 * a fresh install, and it gets a disabled sample. A broken file also blocks saving from the screen,
 * which would otherwise overwrite the file the operator needs to repair.
 *
 * @author THEFricadelle
 */
public final class PolicyManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    private static final String BROKEN_POLICY = "broken-policy";
    private static final String BROKEN_ASSIGNMENTS = "broken-assignments";

    private static volatile PolicyDocument policy = PolicyDocument.disabled();
    private static boolean policyBroken;
    private static final Map<UUID, String> ASSIGNMENTS = new LinkedHashMap<>();
    /** Set when the assignment file exists but cannot be read; it must then not be overwritten. */
    private static boolean assignmentsBroken;
    /** Bumped on every change, so an admin screen editing an older state is told instead of overwriting. */
    private static int revision;

    private PolicyManager() {}

    /** Resolved on each use: the file sits at the old path only while a failed move left it there. */
    private static Path policyFile() {
        return ConfigPaths.policy(FMLPaths.CONFIGDIR.get());
    }

    private static Path assignmentsFile() {
        return ConfigPaths.assignments(FMLPaths.CONFIGDIR.get());
    }

    public enum SaveResult { SAVED, CONFLICT, INVALID, BROKEN_FILE, WRITE_FAILED }

    public static PolicyDocument document() {
        return policy;
    }

    public static int revision() {
        return revision;
    }

    public static boolean policyBroken() {
        return policyBroken;
    }

    public static boolean enforced() {
        return policy.enforced();
    }

    public static int bypassOpLevel() {
        return policy.bypassOpLevel();
    }

    public static String defaultProfile() {
        return policy.defaultProfile();
    }

    public static Set<String> profileNames() {
        return policy.profiles().keySet();
    }

    public static Map<String, CreativeProfile> profiles() {
        return policy.profiles();
    }

    public static boolean hasProfile(String name) {
        return policy.profiles().containsKey(PolicyCodec.normalizeName(name));
    }

    /** Direct lookup, for tooling that evaluates against a named profile rather than a player's. */
    @Nullable
    public static CreativeProfile profile(String name) {
        return policy.profiles().get(PolicyCodec.normalizeName(name));
    }

    /**
     * The profile a player is subject to right now. Called for every creative action, so nothing
     * here logs: problems are reported once, when the files are loaded.
     *
     * @return {@code null} when the player is not restricted at all
     */
    @Nullable
    public static CreativeProfile profileFor(ServerPlayer player) {
        PolicyDocument current = policy;
        if (!current.enforced() || CreativePermissions.bypasses(player)) {
            return null;
        }
        if (assignmentsBroken) {
            // Falling back to the default could widen a player the file had narrowed.
            return CreativeProfile.denyAll(BROKEN_ASSIGNMENTS);
        }
        String name = effectiveProfileName(player);
        if (name.isEmpty()) {
            return null;
        }
        CreativeProfile profile = current.profiles().get(name);
        return profile != null ? profile : CreativeProfile.denyAll(name);
    }

    /** The name {@link #profileFor} resolves to, bypass aside; empty means unrestricted. */
    public static String effectiveProfileName(ServerPlayer player) {
        String assigned = ASSIGNMENTS.get(player.getUUID());
        if (assigned != null) {
            return assigned;
        }
        String granted = CreativePermissions.grantedProfile(player);
        return granted.isEmpty() ? policy.defaultProfile() : granted;
    }

    /** @return the explicit assignment of a player, or {@code null} when none is set */
    @Nullable
    public static String assignment(UUID player) {
        return ASSIGNMENTS.get(player);
    }

    public static Map<UUID, String> assignments() {
        return Collections.unmodifiableMap(ASSIGNMENTS);
    }

    /** False while the assignment file is unreadable: writing it then would erase every entry. */
    public static boolean assignmentsWritable() {
        return !assignmentsBroken;
    }

    /** @return whether the change reached the disk; it applies in memory either way */
    public static boolean assign(UUID player, String profile) {
        requireWritable();
        ASSIGNMENTS.put(player, PolicyCodec.normalizeName(profile));
        revision++;
        return writeAssignments();
    }

    /** @return whether the change reached the disk; it applies in memory either way */
    public static boolean clearAssignment(UUID player) {
        requireWritable();
        ASSIGNMENTS.remove(player);
        revision++;
        return writeAssignments();
    }

    public static boolean isAssigned(UUID player) {
        return ASSIGNMENTS.containsKey(player);
    }

    private static void requireWritable() {
        if (assignmentsBroken) {
            throw new IllegalStateException("The assignment file is unreadable; refusing to overwrite it");
        }
    }

    // ------------------------------------------------------------------
    // Saving from the admin screen
    // ------------------------------------------------------------------

    /**
     * Replaces the policy with an edited one.
     *
     * @param baseRevision the revision the editor started from; a different current revision means
     *                     someone else saved in between, and their change must not be silently lost
     */
    public static SaveResult save(PolicyDocument edited, int baseRevision) {
        if (policyBroken) {
            return SaveResult.BROKEN_FILE;
        }
        if (baseRevision != revision) {
            return SaveResult.CONFLICT;
        }
        if (validationProblem(edited) != null) {
            return SaveResult.INVALID;
        }
        if (!writeAtomically(policyFile(), PolicyCodec.write(edited))) {
            return SaveResult.WRITE_FAILED;
        }
        policy = edited;
        revision++;
        LOGGER.info("Creative policy saved from the admin screen: enforced={}, {} profile(s)",
                edited.enforced(), edited.profiles().size());
        reportUnknownAssignments();
        return SaveResult.SAVED;
    }

    /** The checks {@link PolicyCodec#read} applies to a file, applied to a document from the network. */
    @Nullable
    public static String validationProblem(PolicyDocument document) {
        if (document.bypassOpLevel() < PolicyDocument.MIN_BYPASS_OP_LEVEL
                || document.bypassOpLevel() > PolicyDocument.MAX_BYPASS_OP_LEVEL) {
            return "bypass_op_level";
        }
        for (Map.Entry<String, CreativeProfile> entry : document.profiles().entrySet()) {
            if (PolicyCodec.nameProblem(entry.getKey()) != null || !entry.getKey().equals(entry.getValue().name())) {
                return "profile_name";
            }
        }
        if (!document.defaultProfile().isEmpty() && !document.profiles().containsKey(document.defaultProfile())) {
            return "default_profile";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /**
     * Reads both files. Safe to call again at runtime; that is what the reload command does.
     *
     * @param startup whether the server is starting; only then may a missing policy file be taken
     *                for a fresh install rather than for a file that was removed
     */
    public static void load(boolean startup) {
        readPolicy(startup);
        readAssignments();
        reportUnknownAssignments();
        revision++;
    }

    private static void readPolicy(boolean startup) {
        policyBroken = false;
        if (Files.notExists(policyFile())) {
            if (startup) {
                // Hold what the file now says, so the screen and status show the example profile
                // and a first save from the screen keeps it. Disabled either way.
                policy = writeSamplePolicy() ? PolicyDocument.sample() : PolicyDocument.disabled();
                return;
            }
            markBroken();
            LOGGER.error("{} is gone; creative mode is denied for every restricted player until it is "
                    + "restored and reloaded", policyFile());
            return;
        }
        try (Reader reader = Files.newBufferedReader(policyFile(), StandardCharsets.UTF_8)) {
            PolicyDocument parsed = PolicyCodec.read(JsonParser.parseReader(reader).getAsJsonObject());
            policy = parsed;
            if (parsed.enforced() && !parsed.defaultProfile().isEmpty()
                    && !parsed.profiles().containsKey(parsed.defaultProfile())) {
                LOGGER.error("default_profile '{}' does not exist; unassigned players will be denied everything",
                        parsed.defaultProfile());
            }
            LOGGER.info("Creative policy loaded: enforced={}, {} profile(s), default '{}', bypass at op level {}",
                    parsed.enforced(), parsed.profiles().size(), parsed.defaultProfile(), parsed.bypassOpLevel());
        } catch (Exception e) {
            // Refusing to enforce a policy nobody can read would be the wrong direction here, but
            // enforcing an unknown one is impossible. Deny-all is the resolution: the server stays
            // restricted and the log says why.
            markBroken();
            LOGGER.error("Could not read {}; creative mode is denied for every restricted player until this "
                    + "file is fixed and reloaded", policyFile(), e);
        }
    }

    private static void markBroken() {
        policyBroken = true;
        policy = new PolicyDocument(true, BROKEN_POLICY, PolicyDocument.DEFAULT_BYPASS_OP_LEVEL, Map.of());
    }

    private static void readAssignments() {
        ASSIGNMENTS.clear();
        assignmentsBroken = false;
        if (Files.notExists(assignmentsFile())) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(assignmentsFile(), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    ASSIGNMENTS.put(UUID.fromString(entry.getKey()),
                            PolicyCodec.normalizeName(entry.getValue().getAsString()));
                } catch (RuntimeException e) {
                    LOGGER.warn("Skipping a malformed assignment entry; that player falls back to the default profile");
                }
            }
        } catch (Exception e) {
            // The default profile is not necessarily the stricter side: an assignment is often how a
            // player is narrowed. Without the file, nobody restricted can be placed safely.
            ASSIGNMENTS.clear();
            assignmentsBroken = true;
            LOGGER.error("Could not read {}; creative mode is denied for every restricted player and "
                    + "assignments are disabled until this file is fixed and reloaded", assignmentsFile(), e);
        }
    }

    /** Once per change, rather than on every creative click of every affected player. */
    private static void reportUnknownAssignments() {
        PolicyDocument current = policy;
        if (!current.enforced()) {
            return;
        }
        Map<String, Long> unknown = ASSIGNMENTS.values().stream()
                .filter(name -> !current.profiles().containsKey(name))
                .collect(Collectors.groupingBy(name -> name, TreeMap::new, Collectors.counting()));
        unknown.forEach((name, count) -> LOGGER.warn(
                "{} player(s) assigned to unknown creative profile '{}' will be denied everything", count, name));
    }

    /**
     * Logs every rule entry that names nothing. A mistyped entry does the opposite of what was meant
     * in one of the two modes, so both directions are worth an operator's attention before an event.
     * Needs the tab index and the tags loaded.
     *
     * @return how many entries name nothing
     */
    public static int validateRules() {
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(id.getNamespace());
        }
        Predicate<ResourceLocation> itemExists = BuiltInRegistries.ITEM::containsKey;
        int problems = 0;
        for (CreativeProfile profile : policy.profiles().values()) {
            problems += report(profile, "items", profile.items(), itemExists);
            problems += report(profile, "exceptions", profile.exceptions(), itemExists);
            problems += report(profile, "namespaces", profile.namespaces(), namespaces::contains);
            problems += report(profile, "tags", profile.tags(),
                    id -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).isPresent());
            problems += report(profile, "tabs", profile.tabs(), TabItemIndex::isIndexed);
            problems += report(profile, "allowed_components", profile.allowedComponents(),
                    BuiltInRegistries.DATA_COMPONENT_TYPE::containsKey);
        }
        return problems;
    }

    private static <T> int report(CreativeProfile profile, String field, Set<T> values, Predicate<T> exists) {
        int problems = 0;
        for (T value : values) {
            if (!exists.test(value)) {
                LOGGER.warn("Profile '{}': '{}' in {} matches nothing on this server", profile.name(), value, field);
                problems++;
            }
        }
        return problems;
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    private static boolean writeAssignments() {
        JsonObject root = new JsonObject();
        ASSIGNMENTS.forEach((uuid, profile) -> root.addProperty(uuid.toString(), profile));
        return writeAtomically(assignmentsFile(), root);
    }

    /**
     * Opened with {@code CREATE_NEW}, so an existing file is never overwritten by the sample.
     *
     * @return whether the sample is now on disk
     */
    private static boolean writeSamplePolicy() {
        try {
            Files.createDirectories(policyFile().getParent());
            try (Writer writer = Files.newBufferedWriter(policyFile(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                GSON.toJson(PolicyCodec.write(PolicyDocument.sample()), writer);
            }
            LOGGER.info("Wrote a disabled sample creative policy to {}", policyFile());
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not write the sample policy to {}", policyFile(), e);
            return false;
        }
    }

    /**
     * Writes through a temporary file and a move, so a crash or a full disk mid-write leaves the
     * previous file intact instead of a truncated one.
     */
    private static boolean writeAtomically(Path file, JsonObject root) {
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            LOGGER.error("Could not write {}", file, e);
            try {
                Files.deleteIfExists(temp);
            } catch (IOException ignored) {
                // The temporary file is harmless; the next successful write replaces it.
            }
            return false;
        }
    }
}
