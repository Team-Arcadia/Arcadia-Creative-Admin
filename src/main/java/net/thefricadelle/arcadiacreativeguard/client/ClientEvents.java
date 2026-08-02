/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.thefricadelle.arcadiacreativeguard.ArcadiaCreativeGuard;
import net.thefricadelle.arcadiacreativeguard.core.ReceivedTabPolicy;

/**
 * Drops the received policy when the player leaves a server.
 * <p>
 * Without this, a visit to a restricted server would keep filtering the creative inventory in every
 * singleplayer world afterwards, with nothing on screen explaining why.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeGuard.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ReceivedTabPolicy.clear();
    }
}
