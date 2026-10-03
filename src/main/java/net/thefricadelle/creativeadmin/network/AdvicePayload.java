/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.network;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.thefricadelle.creativeadmin.CreativeAdmin;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells a client what its creative screen should show under its current profile, and whether its
 * player may open the admin screen.
 * <p>
 * Advisory only. The client is free to ignore it: every item is re-checked when the client asks
 * for it. Its purpose is an inventory where a locked tab or item simply is not there, rather than
 * one where the click is refused.
 * <p>
 * The item list is whichever side is shorter. A whitelist opening one tab would otherwise send
 * every other item of the game, and a blacklist locking one tab every item but that tab's.
 *
 * @param enforced     whether a restriction applies; when false the lists are meaningless and the
 *                     client shows its normal inventory
 * @param profile      profile name, for display
 * @param tabs         creative tab ids that keep at least one open item
 * @param lockedListed whether {@code items} lists the locked items, or else the open ones
 * @param items        the items the flag above describes, among those the creative menu offers
 * @param admin        whether the player may open the admin screen, so the button can be shown
 *
 * @author THEFricadelle
 */
public record AdvicePayload(boolean enforced, String profile, List<ResourceLocation> tabs,
                            boolean lockedListed, List<Item> items, boolean admin)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AdvicePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreativeAdmin.MOD_ID, "advice"));

    private static final StreamCodec<RegistryFriendlyByteBuf, Item> ITEM = ByteBufCodecs.registry(Registries.ITEM);

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvicePayload> STREAM_CODEC =
            StreamCodec.of(AdvicePayload::write, AdvicePayload::read);

    public static AdvicePayload unrestricted(boolean admin) {
        return new AdvicePayload(false, "", List.of(), true, List.of(), admin);
    }

    private static void write(RegistryFriendlyByteBuf buf, AdvicePayload payload) {
        buf.writeBoolean(payload.enforced);
        buf.writeUtf(payload.profile, NetCodecs.MAX_NAME);
        buf.writeVarInt(payload.tabs.size());
        payload.tabs.forEach(buf::writeResourceLocation);
        buf.writeBoolean(payload.lockedListed);
        buf.writeVarInt(payload.items.size());
        payload.items.forEach(item -> ITEM.encode(buf, item));
        buf.writeBoolean(payload.admin);
    }

    /** Bounded on read so a malformed or hostile payload cannot make a client allocate without limit. */
    private static AdvicePayload read(RegistryFriendlyByteBuf buf) {
        boolean enforced = buf.readBoolean();
        String profile = buf.readUtf(NetCodecs.MAX_NAME);
        int tabCount = NetCodecs.boundedSize(buf, NetCodecs.MAX_ENTRIES);
        List<ResourceLocation> tabs = new ArrayList<>(tabCount);
        for (int i = 0; i < tabCount; i++) {
            tabs.add(buf.readResourceLocation());
        }
        boolean lockedListed = buf.readBoolean();
        int itemCount = NetCodecs.boundedSize(buf, NetCodecs.MAX_ENTRIES);
        List<Item> items = new ArrayList<>(itemCount);
        for (int i = 0; i < itemCount; i++) {
            items.add(ITEM.decode(buf));
        }
        return new AdvicePayload(enforced, profile, tabs, lockedListed, items, buf.readBoolean());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
