/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;

import java.util.List;

/**
 * Tells a client which creative tabs are worth displaying under its current profile.
 * <p>
 * Advisory only. The client is free to ignore it, which is why it carries no item list and no rule:
 * everything that matters is re-checked when the client asks for an item. Its purpose is to spare a
 * player an inventory full of things they cannot take.
 *
 * @param enforced whether a restriction applies at all; when false the tab list is meaningless and
 *                 the client restores its normal inventory
 * @param profile  profile name, for display
 * @param tabs     creative tab ids the client should keep
 *
 * @author THEFricadelle
 */
public record TabPolicyPayload(boolean enforced, String profile, List<ResourceLocation> tabs)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TabPolicyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(
                    ArcadiaCreativeAdmin.MOD_ID, "tab_policy"));

    /** Bounded so a malformed or hostile payload cannot make a client allocate without limit. */
    private static final int MAX_TABS = 4096;

    public static final StreamCodec<RegistryFriendlyByteBuf, TabPolicyPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, TabPolicyPayload::enforced,
                    ByteBufCodecs.STRING_UTF8, TabPolicyPayload::profile,
                    ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TABS)), TabPolicyPayload::tabs,
                    TabPolicyPayload::new);

    /** Sent when a player stops being restricted, so the client drops whatever it was holding. */
    public static TabPolicyPayload none() {
        return new TabPolicyPayload(false, "", List.of());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
