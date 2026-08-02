/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.thefricadelle.arcadiacreativeadmin.command.CreativeAdminCommand;
import net.thefricadelle.arcadiacreativeadmin.core.TabItemIndex;
import net.thefricadelle.arcadiacreativeadmin.core.VisibleTabResolver;
import net.thefricadelle.arcadiacreativeadmin.network.PolicyNetwork;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyEnforcer;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyManager;

/**
 * Entry point.
 * <p>
 * The mod runs on both sides but everything that enforces anything is server-side. A client copy
 * only ever receives the policy for display; it never decides.
 *
 * @author THEFricadelle
 */
@Mod(ArcadiaCreativeAdmin.MOD_ID)
public final class ArcadiaCreativeAdmin {

    public static final String MOD_ID = "arcadiacreativeadmin";

    public ArcadiaCreativeAdmin() {
        // Nothing to register at construction: the policy needs a running server before it can
        // resolve tab contents, so loading waits for ServerStartedEvent.
    }

    @EventBusSubscriber(modid = MOD_ID)
    public static final class GameEvents {

        private GameEvents() {}

        /**
         * Loading here rather than at mod construction is required, not stylistic: resolving a tab
         * rule into items needs a frozen registry access and the world's enabled feature flags,
         * neither of which exists earlier.
         */
        @SubscribeEvent
        public static void onServerStarted(ServerStartedEvent event) {
            PolicyManager.load();
            TabItemIndex.build(event.getServer());
            VisibleTabResolver.invalidate();
        }

        @SubscribeEvent
        public static void onCommandRegister(RegisterCommandsEvent event) {
            CreativeAdminCommand.register(event.getDispatcher());
        }

        @SubscribeEvent
        public static void onLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                PolicyNetwork.sendTo(player);
            }
        }

        @SubscribeEvent
        public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            PolicyEnforcer.forget(event.getEntity().getUUID());
        }
    }
}
