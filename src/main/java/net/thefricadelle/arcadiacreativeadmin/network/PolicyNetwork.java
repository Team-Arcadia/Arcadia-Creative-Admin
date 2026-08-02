/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.network;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;
import net.thefricadelle.arcadiacreativeadmin.core.ReceivedTabPolicy;
import net.thefricadelle.arcadiacreativeadmin.core.VisibleTabResolver;
import net.thefricadelle.arcadiacreativeadmin.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyManager;

import java.util.List;

/**
 * Registers the advisory tab payload and pushes it to players.
 * <p>
 * The channel is registered as optional so a vanilla client, or one without this mod, can still
 * join. That is deliberate: enforcement never depends on the client having the mod, so requiring it
 * would lock out players for no gain in safety.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeAdmin.MOD_ID)
public final class PolicyNetwork {

    private static final String PROTOCOL_VERSION = "1";

    private PolicyNetwork() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION).optional();
        registrar.playToClient(TabPolicyPayload.TYPE, TabPolicyPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> ReceivedTabPolicy.accept(payload)));
    }

    /**
     * Sends a player the tab list matching their current profile. Called on login and again whenever
     * something that changes the answer happens: a reload, or an assignment.
     */
    public static void sendTo(ServerPlayer player) {
        CreativeProfile profile = PolicyManager.profileFor(player);
        if (profile == null) {
            PacketDistributor.sendToPlayer(player, TabPolicyPayload.none());
            return;
        }
        PacketDistributor.sendToPlayer(player, new TabPolicyPayload(
                true, profile.name(), List.copyOf(VisibleTabResolver.visibleTabs(profile))));
    }

    /** Re-sends to everyone, after the policy itself changed. */
    public static void sendToAll(Iterable<ServerPlayer> players) {
        for (ServerPlayer player : players) {
            sendTo(player);
        }
    }
}
