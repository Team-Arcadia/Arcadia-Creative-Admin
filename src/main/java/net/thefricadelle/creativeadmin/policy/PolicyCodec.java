/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The policy file format, both directions.
 * <p>
 * Reading is strict about structure and lenient about content. A key nobody knows, a flag that is
 * not a real boolean or a rule written as a string instead of a list is an error that refuses the
 * whole file: each of those, read loosely, could leave open what the file meant to close. A single
 * malformed id inside a list is only dropped and logged, since under a whitelist it narrows.
 *
 * @author THEFricadelle
 */
public final class PolicyCodec {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Profile names travel through commands, file keys and chat, so they stay simple. */
    private static final Pattern NAME = Pattern.compile("[a-z0-9_\\-]{1,32}");

    /** Taken by the command syntax: {@code /creativeadmin profile <players> clear} never reaches it. */
    private static final Set<String> RESERVED_NAMES = Set.of("clear");

    private static final Set<String> ROOT_KEYS = Set.of(
            "_comment", "enforced", "default_profile", "bypass_op_level", "profiles");
    private static final Set<String> PROFILE_KEYS = Set.of(
            "mode", "tabs", "namespaces", "tags", "items", "exceptions",
            "allow_block_entity_data", "allow_container_contents", "allowed_components");

    private PolicyCodec() {}

    public static String normalizeName(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /** @return why the name cannot be used, or {@code null} when it can */
    public static String nameProblem(String name) {
        if (!NAME.matcher(name).matches()) {
            return "invalid";
        }
        if (RESERVED_NAMES.contains(name)) {
            return "reserved";
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    public static PolicyDocument read(JsonObject root) {
        requireKnownKeys(root, ROOT_KEYS, "the policy");

        Map<String, CreativeProfile> profiles = new LinkedHashMap<>();
        if (root.has("profiles")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("profiles").entrySet()) {
                String name = normalizeName(entry.getKey());
                String problem = nameProblem(name);
                if (problem != null) {
                    throw new IllegalArgumentException("Profile name '" + name + "' is " + problem);
                }
                if (!entry.getValue().isJsonObject()) {
                    throw new IllegalArgumentException("Profile '" + name + "' must be an object");
                }
                profiles.put(name, readProfile(name, entry.getValue().getAsJsonObject()));
            }
        }

        int bypass = root.has("bypass_op_level")
                ? root.get("bypass_op_level").getAsInt()
                : PolicyDocument.DEFAULT_BYPASS_OP_LEVEL;
        if (bypass < PolicyDocument.MIN_BYPASS_OP_LEVEL || bypass > PolicyDocument.MAX_BYPASS_OP_LEVEL) {
            throw new IllegalArgumentException("bypass_op_level must be between "
                    + PolicyDocument.MIN_BYPASS_OP_LEVEL + " and " + PolicyDocument.MAX_BYPASS_OP_LEVEL
                    + ", got " + bypass);
        }

        return new PolicyDocument(
                bool(root, "enforced"),
                root.has("default_profile") ? normalizeName(root.get("default_profile").getAsString()) : "",
                bypass,
                profiles);
    }

    private static CreativeProfile readProfile(String name, JsonObject json) {
        requireKnownKeys(json, PROFILE_KEYS, "profile '" + name + "'");
        if (!json.has("mode")) {
            throw new IllegalArgumentException("Profile '" + name + "' has no mode");
        }
        return new CreativeProfile(
                name,
                CreativeProfile.Mode.byId(normalizeName(json.get("mode").getAsString())),
                locations(json, "tabs"),
                strings(json, "namespaces"),
                tagLocations(json, "tags"),
                locations(json, "items"),
                locations(json, "exceptions"),
                bool(json, "allow_block_entity_data"),
                bool(json, "allow_container_contents"),
                locations(json, "allowed_components"));
    }

    /** A key nobody reads is most often a typo of one that matters, or a field from an old format. */
    private static void requireKnownKeys(JsonObject json, Set<String> known, String where) {
        for (String key : json.keySet()) {
            if (!known.contains(key)) {
                throw new IllegalArgumentException("Unknown key '" + key + "' in " + where);
            }
        }
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
            String raw = normalizeName(element.getAsString());
            if (!ResourceLocation.isValidNamespace(raw)) {
                LOGGER.warn("Ignoring malformed namespace '{}' in policy field '{}'", raw, field);
                continue;
            }
            out.add(raw);
        }
        return out;
    }

    private static Set<ResourceLocation> locations(JsonObject json, String field) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement element : arrayOf(json, field)) {
            String raw = normalizeName(element.getAsString());
            ResourceLocation id = ResourceLocation.tryParse(raw);
            if (id == null) {
                LOGGER.warn("Ignoring malformed id '{}' in policy field '{}'", raw, field);
                continue;
            }
            out.add(id);
        }
        return out;
    }

    /** Tags are written {@code #ns:path} to match command syntax; the hash is optional. */
    private static Set<ResourceLocation> tagLocations(JsonObject json, String field) {
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (JsonElement element : arrayOf(json, field)) {
            String raw = normalizeName(element.getAsString());
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
     * A field written as a single string instead of a list is an error, not an empty list: read as
     * empty, a blacklist typo would leave open what it was meant to close.
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

    // ------------------------------------------------------------------
    // Writing
    // ------------------------------------------------------------------

    public static JsonObject write(PolicyDocument document) {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Managed by /creativeadmin. Edits made here are kept, "
                + "but formatting and comments are rewritten on the next save from the admin screen.");
        root.addProperty("enforced", document.enforced());
        root.addProperty("default_profile", document.defaultProfile());
        root.addProperty("bypass_op_level", document.bypassOpLevel());
        JsonObject profiles = new JsonObject();
        document.profiles().forEach((name, profile) -> profiles.add(name, writeProfile(profile)));
        root.add("profiles", profiles);
        return root;
    }

    private static JsonObject writeProfile(CreativeProfile profile) {
        JsonObject json = new JsonObject();
        json.addProperty("mode", profile.mode().id());
        json.add("tabs", ids(profile.tabs()));
        json.add("namespaces", strings(profile.namespaces()));
        JsonArray tags = new JsonArray();
        profile.tags().stream().map(tag -> "#" + tag).sorted().forEach(tags::add);
        json.add("tags", tags);
        json.add("items", ids(profile.items()));
        json.add("exceptions", ids(profile.exceptions()));
        json.addProperty("allow_block_entity_data", profile.allowBlockEntityData());
        json.addProperty("allow_container_contents", profile.allowContainerContents());
        json.add("allowed_components", ids(profile.allowedComponents()));
        return json;
    }

    private static JsonArray ids(Collection<ResourceLocation> values) {
        JsonArray out = new JsonArray();
        values.stream().map(ResourceLocation::toString).sorted().forEach(out::add);
        return out;
    }

    private static JsonArray strings(Collection<String> values) {
        JsonArray out = new JsonArray();
        values.stream().sorted().forEach(out::add);
        return out;
    }
}
