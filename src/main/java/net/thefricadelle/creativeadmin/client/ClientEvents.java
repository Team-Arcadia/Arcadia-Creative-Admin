/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.thefricadelle.creativeadmin.CreativeAdmin;
import net.thefricadelle.creativeadmin.client.screen.AdminScreen;
import net.thefricadelle.creativeadmin.core.ReceivedAdvice;
import net.thefricadelle.creativeadmin.network.AdminPayloads;
import net.thefricadelle.creativeadmin.network.AdvicePayload;
import net.thefricadelle.creativeadmin.network.PolicyNetwork;

/**
 * Client wiring: what happens to server messages, and cleanup when leaving a server.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = CreativeAdmin.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PolicyNetwork.setClientHandler(new PolicyNetwork.ClientHandler() {
            @Override
            public void onAdvice(AdvicePayload payload) {
                ReceivedAdvice.accept(payload);
                if (ReceivedAdvice.enforced()) {
                    BetterCreativeBridge.push(payload.profile(), ReceivedAdvice.visibleTabs());
                } else {
                    BetterCreativeBridge.clear();
                }
            }

            @Override
            public void onAdminState(AdminPayloads.State state) {
                AdminScreen.onState(Minecraft.getInstance(), state);
            }
        });
    }

    /**
     * Without this, a visit to a restricted server would keep filtering the creative inventory in
     * every singleplayer world afterwards, with nothing on screen explaining why.
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ReceivedAdvice.clear();
        BetterCreativeBridge.clear();
    }
}
