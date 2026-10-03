/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.screen;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.thefricadelle.creativeadmin.network.AdminPayloads;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.PolicyDocument;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The admin screens' working copy of the policy.
 * <p>
 * Edits accumulate here and only reach the server on Save, which sends the whole document with the
 * revision it was based on. A state arriving while nothing is edited replaces the copy; one
 * arriving over unsaved edits only records that the server moved on, so the edits are never
 * silently thrown away and the save that would overwrite someone else's change is refused.
 *
 * @author THEFricadelle
 */
public final class AdminSession {

    /** A profile being edited: the record's fields, mutable. */
    public static final class Draft {
        public String name;
        public CreativeProfile.Mode mode;
        public final Set<ResourceLocation> tabs;
        public final Set<String> namespaces;
        public final Set<ResourceLocation> tags;
        public final Set<ResourceLocation> items;
        public final Set<ResourceLocation> exceptions;
        public boolean allowBlockEntityData;
        public boolean allowContainerContents;
        public final Set<ResourceLocation> allowedComponents;

        Draft(CreativeProfile profile) {
            this.name = profile.name();
            this.mode = profile.mode();
            this.tabs = new LinkedHashSet<>(profile.tabs());
            this.namespaces = new LinkedHashSet<>(profile.namespaces());
            this.tags = new LinkedHashSet<>(profile.tags());
            this.items = new LinkedHashSet<>(profile.items());
            this.exceptions = new LinkedHashSet<>(profile.exceptions());
            this.allowBlockEntityData = profile.allowBlockEntityData();
            this.allowContainerContents = profile.allowContainerContents();
            this.allowedComponents = new LinkedHashSet<>(profile.allowedComponents());
        }

        public CreativeProfile toProfile() {
            return new CreativeProfile(name, mode, tabs, namespaces, tags, items, exceptions,
                    allowBlockEntityData, allowContainerContents, allowedComponents);
        }
    }

    /** The status the server answers a successful save with, to this client only. */
    private static final String SAVED = "creativeadmin.admin.status.saved";

    private static AdminPayloads.State state;
    private static int baseRevision;
    /** The document the edits started from: what "someone else changed the policy" is judged against. */
    @Nullable
    private static PolicyDocument baseDocument;
    private static boolean enforced;
    private static String defaultProfile = "";
    private static int bypassOpLevel = PolicyDocument.DEFAULT_BYPASS_OP_LEVEL;
    private static final Map<String, Draft> PROFILES = new LinkedHashMap<>();
    private static boolean dirty;
    private static boolean stale;

    private AdminSession() {}

    /**
     * Takes a state from the server.
     * <p>
     * Without edits, or when the state answers this client's own successful save, it replaces the
     * working copy. Over unsaved edits, the revision alone cannot tell what happened, since an
     * assignment or a refused save moves it too without touching the policy; the document can. If
     * the document the edits started from is unchanged, the edits are kept and simply rebased on the
     * new revision. Only a document someone else changed makes the edits stale.
     *
     * @return {@code false} when the edits are now stale, which the page announces
     */
    static boolean accept(AdminPayloads.State incoming) {
        state = incoming;
        boolean ownSave = incoming.success() && SAVED.equals(incoming.status());
        if (!dirty || ownSave) {
            load(incoming);
            return true;
        }
        if (incoming.document().equals(baseDocument)) {
            baseRevision = incoming.revision();
            return true;
        }
        stale = true;
        return false;
    }

    /** Drops the edits and takes the last state the server sent. */
    static void revert() {
        if (state != null) {
            load(state);
        }
    }

    private static void load(AdminPayloads.State incoming) {
        baseRevision = incoming.revision();
        PolicyDocument document = incoming.document();
        baseDocument = document;
        enforced = document.enforced();
        defaultProfile = document.defaultProfile();
        bypassOpLevel = document.bypassOpLevel();
        PROFILES.clear();
        document.profiles().forEach((name, profile) -> PROFILES.put(name, new Draft(profile)));
        dirty = false;
        stale = false;
    }

    static void save() {
        Map<String, CreativeProfile> profiles = new LinkedHashMap<>();
        PROFILES.forEach((name, draft) -> profiles.put(name, draft.toProfile()));
        PacketDistributor.sendToServer(new AdminPayloads.Save(baseRevision,
                new PolicyDocument(enforced, defaultProfile, bypassOpLevel, profiles)));
    }

    static void assign(UUID player, String profile) {
        PacketDistributor.sendToServer(new AdminPayloads.Assign(player, profile));
    }

    static void markDirty() {
        dirty = true;
    }

    // ------------------------------------------------------------------ reads

    @Nullable
    static AdminPayloads.State state() {
        return state;
    }

    static boolean dirty() {
        return dirty;
    }

    static boolean stale() {
        return stale;
    }

    static boolean readOnly() {
        return state == null || state.policyBroken();
    }

    static boolean enforced() {
        return enforced;
    }

    static String defaultProfile() {
        return defaultProfile;
    }

    static int bypassOpLevel() {
        return bypassOpLevel;
    }

    static List<String> profileNames() {
        return List.copyOf(PROFILES.keySet());
    }

    @Nullable
    static Draft profile(String name) {
        return PROFILES.get(name);
    }

    // ------------------------------------------------------------------ edits

    static void setEnforced(boolean value) {
        enforced = value;
        markDirty();
    }

    static void setDefaultProfile(String value) {
        defaultProfile = value;
        markDirty();
    }

    static void setBypassOpLevel(int value) {
        bypassOpLevel = value;
        markDirty();
    }

    /** @return the created draft, or {@code null} when the name is taken */
    @Nullable
    static Draft create(String name, @Nullable Draft copyOf) {
        if (PROFILES.containsKey(name)) {
            return null;
        }
        Draft draft = new Draft(copyOf == null ? CreativeProfile.empty(name) : copyOf.toProfile().withName(name));
        PROFILES.put(name, draft);
        markDirty();
        return draft;
    }

    static void delete(String name) {
        if (PROFILES.remove(name) != null) {
            if (defaultProfile.equals(name)) {
                defaultProfile = "";
            }
            markDirty();
        }
    }
}
