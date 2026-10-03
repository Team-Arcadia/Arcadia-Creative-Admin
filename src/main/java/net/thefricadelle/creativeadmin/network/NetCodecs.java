/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.PolicyDocument;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Wire format of a policy, for the admin screen.
 * <p>
 * Every collection is bounded on read. Admin payloads come from clients, and a client can send
 * anything: an unbounded length prefix would let one packet make the server allocate at will
 * before the permission check even runs.
 *
 * @author THEFricadelle
 */
public final class NetCodecs {

    /** Far above any real policy: the whole item registry of a large modpack is around 30 000 ids. */
    public static final int MAX_ENTRIES = 65_536;
    public static final int MAX_PROFILES = 256;
    public static final int MAX_NAME = 64;

    public static final StreamCodec<RegistryFriendlyByteBuf, CreativeProfile> PROFILE = StreamCodec.of(
            NetCodecs::writeProfile, NetCodecs::readProfile);

    public static final StreamCodec<RegistryFriendlyByteBuf, PolicyDocument> DOCUMENT = StreamCodec.of(
            NetCodecs::writeDocument, NetCodecs::readDocument);

    private NetCodecs() {}

    private static void writeDocument(RegistryFriendlyByteBuf buf, PolicyDocument document) {
        buf.writeBoolean(document.enforced());
        buf.writeUtf(document.defaultProfile(), MAX_NAME);
        buf.writeVarInt(document.bypassOpLevel());
        buf.writeVarInt(document.profiles().size());
        for (CreativeProfile profile : document.profiles().values()) {
            writeProfile(buf, profile);
        }
    }

    private static PolicyDocument readDocument(RegistryFriendlyByteBuf buf) {
        boolean enforced = buf.readBoolean();
        String defaultProfile = buf.readUtf(MAX_NAME);
        int bypass = buf.readVarInt();
        int count = boundedSize(buf, MAX_PROFILES);
        Map<String, CreativeProfile> profiles = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            CreativeProfile profile = readProfile(buf);
            profiles.put(profile.name(), profile);
        }
        return new PolicyDocument(enforced, defaultProfile, bypass, profiles);
    }

    private static void writeProfile(RegistryFriendlyByteBuf buf, CreativeProfile profile) {
        buf.writeUtf(profile.name(), MAX_NAME);
        buf.writeEnum(profile.mode());
        writeIds(buf, profile.tabs());
        buf.writeVarInt(profile.namespaces().size());
        profile.namespaces().forEach(namespace -> buf.writeUtf(namespace, MAX_NAME));
        writeIds(buf, profile.tags());
        writeIds(buf, profile.items());
        writeIds(buf, profile.exceptions());
        buf.writeBoolean(profile.allowBlockEntityData());
        buf.writeBoolean(profile.allowContainerContents());
        writeIds(buf, profile.allowedComponents());
    }

    private static CreativeProfile readProfile(RegistryFriendlyByteBuf buf) {
        String name = buf.readUtf(MAX_NAME);
        CreativeProfile.Mode mode = buf.readEnum(CreativeProfile.Mode.class);
        Set<ResourceLocation> tabs = readIds(buf);
        int namespaceCount = boundedSize(buf, MAX_ENTRIES);
        Set<String> namespaces = new LinkedHashSet<>();
        for (int i = 0; i < namespaceCount; i++) {
            namespaces.add(buf.readUtf(MAX_NAME));
        }
        Set<ResourceLocation> tags = readIds(buf);
        Set<ResourceLocation> items = readIds(buf);
        Set<ResourceLocation> exceptions = readIds(buf);
        boolean blockEntityData = buf.readBoolean();
        boolean containerContents = buf.readBoolean();
        Set<ResourceLocation> components = readIds(buf);
        return new CreativeProfile(name, mode, tabs, namespaces, tags, items, exceptions,
                blockEntityData, containerContents, components);
    }

    private static void writeIds(FriendlyByteBuf buf, Collection<ResourceLocation> ids) {
        buf.writeVarInt(ids.size());
        ids.forEach(buf::writeResourceLocation);
    }

    private static Set<ResourceLocation> readIds(FriendlyByteBuf buf) {
        int count = boundedSize(buf, MAX_ENTRIES);
        Set<ResourceLocation> out = new LinkedHashSet<>();
        for (int i = 0; i < count; i++) {
            out.add(buf.readResourceLocation());
        }
        return out;
    }

    static int boundedSize(FriendlyByteBuf buf, int max) {
        int size = buf.readVarInt();
        if (size < 0 || size > max) {
            throw new IllegalArgumentException("Collection of " + size + " entries exceeds the limit of " + max);
        }
        return size;
    }
}
