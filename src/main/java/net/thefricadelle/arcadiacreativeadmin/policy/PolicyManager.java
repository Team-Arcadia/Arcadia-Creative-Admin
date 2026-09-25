/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.neoforged.fml.loading.FMLPaths;
import net.thefricadelle.arcadiacreativeadmin.core.TabItemIndex;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Server-side state: the profiles that exist, who is assigned to which, and whether enforcement is
 * on at all.
 * <p>
 * The policy and the assignments live in two files on purpose. The policy is written by hand by
 * whoever runs the event and is never rewritten by the mod, so comments, formatting and ordering
 * survive; assignments change through commands and are the only thing the mod writes back.
 * <p>
 * Every failure path denies rather than allows. A missing profile, an unreadable file, a parse
 * error or an out-of-range setting all resolve to {@link CreativeProfile#denyAll}, because the
 * opposite direction hands a full creative inventory to whoever the broken entry pointed at, which
 * is exactly the outcome this mod exists to prevent. The only case that disables enforcement is a
 * policy file that does not exist at server start: that is a fresh install, and it gets a disabled
 * sample to edit.
 *
 * @author THEFricadelle
 */
public final class PolicyManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("arcadia");
    private static final Path POLICY_FILE = DIR.resolve("arcadia-creative-admin-policy.json");
    private static final Path ASSIGNMENTS_FILE = DIR.resolve("arcadia-creative-admin-assignments.json");

    /** Level 4 is the console owner; the default leaves full creative to server owners only. */
    private static final int DEFAULT_BYPASS_OP_LEVEL = 4;
    /** Level 0 is every player: accepting it would switch the whole policy off through one digit. */
    private static final int MIN_BYPASS_OP_LEVEL = 1;
    private static final int MAX_BYPASS_OP_LEVEL = 4;

    /** Taken by the command syntax: {@code /creativeadmin profile <players> clear} never reaches it. */
    private static final Set<String> RESERVED_NAMES = Set.of("clear");

    private static final String BROKEN_POLICY = "broken-policy";
    private static final String BROKEN_ASSIGNMENTS = "broken-assignments";

    private static volatile Policy policy = Policy.disabled();
    private static final Map<UUID, String> ASSIGNMENTS = new LinkedHashMap<>();
    /** Set when the assignment file exists but cannot be read; it must then not be overwritten. */
    private static boolean assignmentsBroken;

    private PolicyManager() {}

    /** One consistent reading of the policy file, swapped in whole so no half-parsed state is seen. */
    private record Policy(boolean enforced, String defaultProfile, int bypassOpLevel,
                          Map<String, CreativeProfile> profiles) {

        static Policy disabled() {
            return new Policy(false, "", DEFAULT_BYPASS_OP_LEVEL, Map.of());
        }

        static Policy broken() {
            return new Policy(true, BROKEN_POLICY, DEFAULT_BYPASS_OP_LEVEL, Map.of());
        }
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
        return policy.profiles().containsKey(key(name));
    }

    /** Direct lookup, for tooling that evaluates against a named profile rather than a player's. */
    @Nullable
    public static CreativeProfile profile(String name) {
        return policy.profiles().get(key(name));
    }

    /**
     * The profile a player is subject to right now. Called for every creative action, so nothing
     * here logs: problems are reported once, when the files are loaded.
     *
     * @return {@code null} when the player is not restricted at all: enforcement off, or an op at
     *         or above the bypass level
     */
    @Nullable
    public static CreativeProfile profileFor(ServerPlayer player) {
        Policy current = policy;
        if (!current.enforced() || player.hasPermissions(current.bypassOpLevel())) {
            return null;
        }
        if (assignmentsBroken) {
            // Falling back to the default could widen a player the file had narrowed.
            return CreativeProfile.denyAll(BROKEN_ASSIGNMENTS);
        }
        String name = ASSIGNMENTS.getOrDefault(player.getUUID(), current.defaultProfile());
        if (name.isEmpty()) {
            return null;
        }
        CreativeProfile profile = current.profiles().get(name);
        return profile != null ? profile : CreativeProfile.denyAll(name);
    }

    /** @return the profile name a player is assigned to, or the default when none is set */
    public static String assignedProfile(UUID player) {
        return ASSIGNMENTS.getOrDefault(player, policy.defaultProfile());
    }

    /** False while the assignment file is unreadable: writing it then would erase every entry. */
    public static boolean assignmentsWritable() {
        return !assignmentsBroken;
    }

    /** @return whether the change reached the disk; it applies in memory either way */
    public static boolean assign(UUID player, String profile) {
        requireWritable();
        ASSIGNMENTS.put(player, key(profile));
        return writeAssignments();
    }

    /** @return whether the change reached the disk; it applies in memory either way */
    public static boolean clearAssignment(UUID player) {
        requireWritable();
        ASSIGNMENTS.remove(player);
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
    }

    private static void readPolicy(boolean startup) {
        if (Files.notExists(POLICY_FILE)) {
            if (startup) {
                writeSamplePolicy();
                policy = Policy.disabled();
                return;
            }
            policy = Policy.broken();
            LOGGER.error("{} is gone; creative mode is denied for everyone below op level {} until it is "
                    + "restored and reloaded", POLICY_FILE, DEFAULT_BYPASS_OP_LEVEL);
            return;
        }
        try (Reader reader = Files.newBufferedReader(POLICY_FILE, StandardCharsets.UTF_8)) {
            Policy parsed = parse(JsonParser.parseReader(reader).getAsJsonObject());
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
            policy = Policy.broken();
            LOGGER.error("Could not read {}; creative mode is denied for everyone below op level {} "
                    + "until this file is fixed", POLICY_FILE, DEFAULT_BYPASS_OP_LEVEL, e);
        }
    }

    private static Policy parse(JsonObject root) {
        Map<String, CreativeProfile> parsed = new LinkedHashMap<>();
        if (root.has("profiles")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
                String name = key(entry.getKey());
                if (RESERVED_NAMES.contains(name)) {
                    LOGGER.error("Ignoring profile '{}': the name is reserved by the profile command", name);
                    continue;
                }
                parsed.put(name, readProfile(name, entry.getValue().getAsJsonObject()));
            }
        }

        int bypass = root.has("bypass_op_level")
                ? root.get("bypass_op_level").getAsInt()
                : DEFAULT_BYPASS_OP_LEVEL;
        if (bypass < MIN_BYPASS_OP_LEVEL || bypass > MAX_BYPASS_OP_LEVEL) {
            throw new IllegalArgumentException("bypass_op_level must be between " + MIN_BYPASS_OP_LEVEL
                    + " and " + MAX_BYPASS_OP_LEVEL + ", got " + bypass);
        }

        return new Policy(
                bool(root, "enforced"),
                root.has("default_profile") ? key(root.get("default_profile").getAsString()) : "",
                bypass,
                Collections.unmodifiableMap(parsed));
    }

    private static CreativeProfile readProfile(String name, JsonObject json) {
        return new CreativeProfile(
                name,
                locations(json, "items"),
                strings(json, "namespaces"),
                tagLocations(json, "tags"),
                locations(json, "tabs"),
                locations(json, "denied_items"),
                strings(json, "denied_namespaces"),
                bool(json, "allow_block_entity_data"),
                bool(json, "allow_container_contents"),
                locations(json, "allowed_components"));
    }

    private static void readAssignments() {
        ASSIGNMENTS.clear();
        assignmentsBroken = false;
        if (Files.notExists(ASSIGNMENTS_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(ASSIGNMENTS_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    ASSIGNMENTS.put(UUID.fromString(entry.getKey()), key(entry.getValue().getAsString()));
                } catch (RuntimeException e) {
                    LOGGER.warn("Skipping a malformed assignment entry; that player falls back to the default profile");
                }
            }
        } catch (Exception e) {
            // The default profile is not necessarily the stricter side: an assignment is often how a
            // player is narrowed. Without the file, nobody restricted can be placed safely.
            ASSIGNMENTS.clear();
            assignmentsBroken = true;
            LOGGER.error("Could not read {}; creative mode is denied for every restricted player and the "
                    + "profile command is disabled until this file is fixed and reloaded", ASSIGNMENTS_FILE, e);
        }
    }

    /** Once per load, rather than on every creative click of every affected player. */
    private static void reportUnknownAssignments() {
        Policy current = policy;
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
     * Logs every rule entry that names nothing. An allowance that names nothing only narrows the
     * profile, but a denial that names nothing leaves open what the operator meant to close, so both
     * are worth an operator's attention before an event. Needs the tab index and the tags loaded.
     *
     * @return how many entries name nothing
     */
    public static int validateRules() {
        Set<String> namespaces = new HashSet<>();
        for (ResourceLocation id : BuiltInRegistries.ITEM.keySet()) {
            namespaces.add(id.getNamespace());
        }
        int problems = 0;
        for (CreativeProfile profile : policy.profiles().values()) {
            problems += report(profile, "items", profile.items(), id -> BuiltInRegistries.ITEM.containsKey(id));
            problems += report(profile, "denied_items", profile.deniedItems(),
                    id -> BuiltInRegistries.ITEM.containsKey(id));
            problems += report(profile, "namespaces", profile.namespaces(), namespaces::contains);
            problems += report(profile, "denied_namespaces", profile.deniedNamespaces(), namespaces::contains);
            problems += report(profile, "tags", profile.tags(),
                    id -> BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, id)).isPresent());
            problems += report(profile, "tabs", profile.tabs(), TabItemIndex::isIndexed);
            problems += report(profile, "allowed_components", profile.allowedComponents(),
                    id -> BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(id));
        }
        return problems;
    }

    private static <T> int report(CreativeProfile profile, String field, Set<T> values,
                                  Predicate<T> exists) {
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
        return writeAtomically(ASSIGNMENTS_FILE, root);
    }

    /**
     * Written once, disabled, so a fresh install has something to edit rather than a blank folder
     * and a wiki page. Opened with {@code CREATE_NEW}, so an existing file is never overwritten.
     */
    private static void writeSamplePolicy() {
        JsonObject sample = new JsonObject();
        sample.addProperty("_comment", "Strict whitelist. Anything no rule allows is refused. "
                + "Set enforced to true once the profiles below are ready.");
        sample.addProperty("enforced", false);
        sample.addProperty("default_profile", "event");
        sample.addProperty("bypass_op_level", DEFAULT_BYPASS_OP_LEVEL);

        JsonObject event = new JsonObject();
        event.add("tabs", array(List.of("minecraft:building_blocks", "minecraft:colored_blocks")));
        event.add("namespaces", new JsonArray());
        event.add("tags", array(List.of("#minecraft:beds")));
        event.add("items", array(List.of("minecraft:torch")));
        event.add("denied_items", array(List.of("minecraft:command_block", "minecraft:structure_block")));
        event.add("denied_namespaces", new JsonArray());
        event.addProperty("allow_block_entity_data", false);
        event.addProperty("allow_container_contents", false);
        event.add("allowed_components", new JsonArray());

        JsonObject profilesJson = new JsonObject();
        profilesJson.add("event", event);
        sample.add("profiles", profilesJson);

        try {
            Files.createDirectories(DIR);
            try (Writer writer = Files.newBufferedWriter(POLICY_FILE, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                GSON.toJson(sample, writer);
            }
            LOGGER.info("Wrote a disabled sample creative policy to {}", POLICY_FILE);
        } catch (IOException e) {
            LOGGER.error("Could not write the sample policy to {}", POLICY_FILE, e);
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

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    private static String key(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Only a real JSON boolean is accepted. Gson would read {@code "yes"} or {@code "on"} as false,
     * which for {@code enforced} silently switches the policy off.
     */
    private static boolean bool(JsonObject json, String field) {
        if (!json.has(field)) {
            return false;
        }
        JsonElement element = json.get(field);
        if (!(element instanceof JsonPrimitive primitive) || !primitive.isBoolean()) {
            throw new IllegalArgumentException("'" + field + "' must be true or false");
        }
        return primitive.getAsBoolean();
    }

    private static Set<String> strings(JsonObject json, String field) {
        Set<String> out = new LinkedHashSet<>();
        for (JsonElement element : arrayOf(json, field)) {
            out.add(key(element.getAsString()));
        }
        return out;
    }

    /** Malformed ids are dropped and logged: under a whitelist a typo narrows, it never widens. */
    private static Set<ResourceLocation> locations(JsonObject json, String field) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement element : arrayOf(json, field)) {
            String raw = key(element.getAsString());
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                LOGGER.warn("Ignoring malformed id '{}' in policy field '{}'", raw, field);
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /** Tags are written {@code #ns:path} to match command syntax; the hash is stripped here. */
    private static Set<ResourceLocation> tagLocations(JsonObject json, String field) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement element : arrayOf(json, field)) {
            String raw = key(element.getAsString());
            if (raw.startsWith("#")) {
                raw = raw.substring(1);
            }
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                LOGGER.warn("Ignoring malformed tag '{}' in policy field '{}'", raw, field);
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /**
     * A field written as a single string instead of a list is an error, not an empty list: read
     * as empty, a {@code denied_items} typo would leave open what it was meant to close.
     */
    private static JsonArray arrayOf(JsonObject json, String field) {
        if (!json.has(field)) {
            return new JsonArray();
        }
        if (!json.get(field).isJsonArray()) {
            throw new IllegalArgumentException("'" + field + "' must be a list");
        }
        return json.getAsJsonArray(field);
    }

    private static JsonArray array(List<String> values) {
        JsonArray out = new JsonArray();
        values.forEach(out::add);
        return out;
    }
}
