/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;
import net.thefricadelle.arcadiacreativeadmin.core.ReceivedTabPolicy;

/**
 * Drops the received policy when the player leaves a server.
 * <p>
 * Without this, a visit to a restricted server would keep filtering the creative inventory in every
 * singleplayer world afterwards, with nothing on screen explaining why.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeAdmin.MOD_ID, value = Dist.CLIENT)
public final class ClientEvents {

    private ClientEvents() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ReceivedTabPolicy.clear();
    }
}
