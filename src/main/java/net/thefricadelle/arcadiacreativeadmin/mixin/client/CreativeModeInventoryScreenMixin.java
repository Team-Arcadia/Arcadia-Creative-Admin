/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.thefricadelle.arcadiacreativeadmin.core.ReceivedTabPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;

/**
 * Hides the tabs the server said are pointless under the player's profile.
 * <p>
 * Cosmetic by design. The client is told what to hide and could simply not do it; every item is
 * re-checked server-side when it is actually requested. What this buys is an inventory a player can
 * browse during an event instead of one where most clicks are refused.
 * <p>
 * The priority is above Arcadia Better Creative's 1200 so this filter runs last when both mods are
 * installed. {@code ModifyExpressionValue} injections chain, so the player's ordering still applies;
 * it just applies to a list this has already narrowed.
 *
 * @author THEFricadelle
 */
@Mixin(value = CreativeModeInventoryScreen.class, priority = 1300)
public abstract class CreativeModeInventoryScreenMixin {

    @ModifyExpressionValue(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CreativeModeTabRegistry;"
                            + "getSortedCreativeModeTabs()Ljava/util/List;"
            )
    )
    private List<CreativeModeTab> arcadiacreativeadmin$hideForbiddenTabs(List<CreativeModeTab> original) {
        try {
            if (!ReceivedTabPolicy.enforced()) {
                return original;
            }
            List<CreativeModeTab> kept = new ArrayList<>(original.size());
            for (CreativeModeTab tab : original) {
                var id = CreativeModeTabRegistry.getName(tab);
                // A tab with no registry name cannot be matched against the policy. Keeping it is
                // harmless: the server still refuses whatever it contains.
                if (id == null || ReceivedTabPolicy.allows(id)) {
                    kept.add(tab);
                }
            }
            // CreativeModeInventoryScreen.init() dereferences element 0 without checking.
            return kept.isEmpty() ? original : kept;
        } catch (Exception e) {
            // Display filtering is never worth breaking the inventory over.
            return original;
        }
    }
}
