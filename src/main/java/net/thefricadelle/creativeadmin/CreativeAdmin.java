/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PermissionsChangedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.thefricadelle.creativeadmin.command.CreativeAdminCommand;
import net.thefricadelle.creativeadmin.network.AdminServer;
import net.thefricadelle.creativeadmin.network.PolicyNetwork;
import net.thefricadelle.creativeadmin.policy.ConfigPaths;
import net.thefricadelle.creativeadmin.policy.PolicyEnforcer;
import org.slf4j.Logger;

/**
 * Entry point.
 * <p>
 * The mod runs on both sides but everything that enforces anything is server-side. A client copy
 * only ever receives the policy for display; it never decides.
 *
 * @author THEFricadelle
 */
@Mod(CreativeAdmin.MOD_ID)
public final class CreativeAdmin {

    public static final String MOD_ID = "creativeadmin";

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Only the move from the old file names happens here, before anything can read them: the
     * policy needs a running server before it can resolve tab contents, so loading waits for
     * ServerStartedEvent.
     */
    public CreativeAdmin() {
        for (ConfigPaths.Move move : ConfigPaths.migrate(FMLPaths.CONFIGDIR.get())) {
            if (move.error() == null) {
                LOGGER.info("Moved {} to {} after the rename to Creative Admin", move.from(), move.to());
            } else {
                LOGGER.warn("Could not move {} to {}; it is used where it is", move.from(), move.to(), move.error());
            }
        }
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
            PolicyLifecycle.reload(event.getServer(), true);
        }

        /** Fired for {@code /reload} with no player, and per player on login, which is covered below. */
        @SubscribeEvent
        public static void onDatapackSync(OnDatapackSyncEvent event) {
            if (event.getPlayer() == null) {
                PolicyLifecycle.onDataReloaded(event.getPlayerList().getServer());
            }
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

        /**
         * An op or deop can move a player across the bypass level. The event fires before the new
         * level is stored, so the advice is sent on the next tick, once it is; {@code execute}
         * would run it immediately when already on the server thread.
         */
        @SubscribeEvent
        public static void onPermissionsChanged(PermissionsChangedEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) {
                MinecraftServer server = player.server;
                server.tell(new TickTask(server.getTickCount(), () -> {
                    if (!player.hasDisconnected()) {
                        PolicyNetwork.sendTo(player);
                    }
                }));
            }
        }

        @SubscribeEvent
        public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            PolicyEnforcer.forget(event.getEntity().getUUID());
            AdminServer.forget(event.getEntity().getUUID());
            PolicyNetwork.forget(event.getEntity().getUUID());
        }
    }
}
