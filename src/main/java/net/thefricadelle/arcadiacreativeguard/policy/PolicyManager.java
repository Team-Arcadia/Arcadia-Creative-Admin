/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.policy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side state: the profiles that exist, who is assigned to which, and whether enforcement is
 * on at all.
 * <p>
 * The policy and the assignments live in two files on purpose. The policy is written by hand by
 * whoever runs the event and is never rewritten by the mod, so comments, formatting and ordering
 * survive; assignments change through commands and are the only thing the mod writes back.
 * <p>
 * Every failure path denies rather than allows. A missing profile, an unreadable file or a parse
 * error all resolve to {@link CreativeProfile#denyAll}, because the opposite direction hands a full
 * creative inventory to whoever the broken entry pointed at, which is exactly the outcome this mod
 * exists to prevent.
 *
 * @author THEFricadelle
 */
public final class PolicyManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Path DIR = FMLPaths.CONFIGDIR.get().resolve("arcadia");
    private static final Path POLICY_FILE = DIR.resolve("arcadia-creative-guard-policy.json");
    private static final Path ASSIGNMENTS_FILE = DIR.resolve("arcadia-creative-guard-assignments.json");

    /** Level 4 is the console owner; the default leaves full creative to server owners only. */
    private static final int DEFAULT_BYPASS_OP_LEVEL = 4;

    private static boolean enforced;
    private static String defaultProfile = "";
    private static int bypassOpLevel = DEFAULT_BYPASS_OP_LEVEL;
    private static Map<String, CreativeProfile> profiles = Map.of();
    private static final Map<UUID, String> ASSIGNMENTS = new LinkedHashMap<>();

    private PolicyManager() {}

    public static boolean enforced() {
        return enforced;
    }

    public static int bypassOpLevel() {
        return bypassOpLevel;
    }

    public static Set<String> profileNames() {
        return profiles.keySet();
    }

    public static boolean hasProfile(String name) {
        return profiles.containsKey(key(name));
    }

    /** Direct lookup, for tooling that evaluates against a named profile rather than a player's. */
    @Nullable
    public static CreativeProfile profile(String name) {
        return profiles.get(key(name));
    }

    /**
     * The profile a player is subject to right now.
     *
     * @return {@code null} when the player is not restricted at all — enforcement off, or an op at
     *         or above the bypass level
     */
    @Nullable
    public static CreativeProfile profileFor(ServerPlayer player) {
        if (!enforced) {
            return null;
        }
        if (player.hasPermissions(bypassOpLevel)) {
            return null;
        }
        String name = ASSIGNMENTS.getOrDefault(player.getUUID(), defaultProfile);
        if (name.isEmpty()) {
            return null;
        }
        CreativeProfile profile = profiles.get(key(name));
        if (profile == null) {
            LOGGER.warn("Player is assigned to unknown creative profile '{}'; denying everything", name);
            return CreativeProfile.denyAll(name);
        }
        return profile;
    }

    /** @return the profile name a player is assigned to, or the default when none is set */
    public static String assignedProfile(UUID player) {
        return ASSIGNMENTS.getOrDefault(player, defaultProfile);
    }

    public static void assign(UUID player, String profile) {
        ASSIGNMENTS.put(player, key(profile));
        writeAssignments();
    }

    public static boolean clearAssignment(UUID player) {
        if (ASSIGNMENTS.remove(player) == null) {
            return false;
        }
        writeAssignments();
        return true;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    /** Reads both files. Safe to call again at runtime; that is what the reload command does. */
    public static void load() {
        readPolicy();
        readAssignments();
    }

    private static void readPolicy() {
        if (!Files.isRegularFile(POLICY_FILE)) {
            writeSamplePolicy();
            enforced = false;
            profiles = Map.of();
            defaultProfile = "";
            bypassOpLevel = DEFAULT_BYPASS_OP_LEVEL;
            return;
        }
        try (Reader reader = Files.newBufferedReader(POLICY_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();

            Map<String, CreativeProfile> parsed = new LinkedHashMap<>();
            if (root.has("profiles")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
                    String name = key(entry.getKey());
                    parsed.put(name, readProfile(name, entry.getValue().getAsJsonObject()));
                }
            }

            profiles = Map.copyOf(parsed);
            defaultProfile = root.has("default_profile") ? key(root.get("default_profile").getAsString()) : "";
            bypassOpLevel = root.has("bypass_op_level")
                    ? root.get("bypass_op_level").getAsInt()
                    : DEFAULT_BYPASS_OP_LEVEL;
            enforced = root.has("enforced") && root.get("enforced").getAsBoolean();

            if (enforced && !defaultProfile.isEmpty() && !profiles.containsKey(defaultProfile)) {
                LOGGER.error("default_profile '{}' does not exist; unassigned players will be denied everything",
                        defaultProfile);
            }
            LOGGER.info("Creative policy loaded: enforced={}, {} profile(s), default '{}', bypass at op level {}",
                    enforced, profiles.size(), defaultProfile, bypassOpLevel);
        } catch (Exception e) {
            // Refusing to enforce a policy nobody can read would be the wrong direction here, but
            // enforcing an unknown one is impossible. Deny-all is the resolution: the server stays
            // restricted and the log says why.
            enforced = true;
            profiles = Map.of();
            defaultProfile = "broken-policy";
            LOGGER.error("Could not read {}; creative mode is denied for everyone below op level {} "
                    + "until this file is fixed", POLICY_FILE, bypassOpLevel, e);
        }
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
                bool(json, "allow_container_contents"));
    }

    private static void readAssignments() {
        ASSIGNMENTS.clear();
        if (!Files.isRegularFile(ASSIGNMENTS_FILE)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(ASSIGNMENTS_FILE, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                try {
                    ASSIGNMENTS.put(UUID.fromString(entry.getKey()), key(entry.getValue().getAsString()));
                } catch (IllegalArgumentException e) {
                    LOGGER.warn("Skipping assignment with a malformed player id");
                }
            }
        } catch (Exception e) {
            // Losing assignments falls back to the default profile, which is the stricter side.
            LOGGER.error("Could not read {}; every player falls back to the default profile",
                    ASSIGNMENTS_FILE, e);
        }
    }

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    private static void writeAssignments() {
        JsonObject root = new JsonObject();
        ASSIGNMENTS.forEach((uuid, profile) -> root.addProperty(uuid.toString(), profile));
        write(ASSIGNMENTS_FILE, root);
    }

    /**
     * Written once, disabled, so a fresh install has something to edit rather than a blank folder
     * and a wiki page. Never overwrites an existing file.
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

        JsonObject profilesJson = new JsonObject();
        profilesJson.add("event", event);
        sample.add("profiles", profilesJson);

        write(POLICY_FILE, sample);
        LOGGER.info("Wrote a disabled sample creative policy to {}", POLICY_FILE);
    }

    private static void write(Path file, JsonObject root) {
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        } catch (IOException e) {
            LOGGER.error("Could not write {}", file, e);
        }
    }

    // ------------------------------------------------------------------
    // Parsing helpers
    // ------------------------------------------------------------------

    private static String key(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean bool(JsonObject json, String field) {
        return json.has(field) && json.get(field).getAsBoolean();
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

    private static JsonArray arrayOf(JsonObject json, String field) {
        return json.has(field) && json.get(field).isJsonArray()
                ? json.getAsJsonArray(field)
                : new JsonArray();
    }

    private static JsonArray array(List<String> values) {
        JsonArray out = new JsonArray();
        values.forEach(out::add);
        return out;
    }
}
