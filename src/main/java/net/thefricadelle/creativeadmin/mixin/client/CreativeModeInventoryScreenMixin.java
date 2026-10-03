/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.common.CreativeModeTabRegistry;
import net.neoforged.neoforge.network.PacketDistributor;
import net.thefricadelle.creativeadmin.core.ReceivedAdvice;
import net.thefricadelle.creativeadmin.network.AdminPayloads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Hides what the server said is locked under the player's profile: whole tabs, and single items
 * inside the tabs that stay, search results included.
 * <p>
 * Cosmetic by design. The client is told what to hide and could simply not do it; every item is
 * re-checked server-side when it is actually requested. What this buys is an inventory where a
 * locked item is not there, rather than one where clicking it is refused.
 * <p>
 * Every injection here is optional ({@code require = 0}). The global defaultRequire is right for
 * the enforcement hooks and wrong here: if a NeoForge update moves one of these targets, losing the
 * filter is the acceptable outcome, a client that crashes when opening its inventory is not.
 * <p>
 * The priority is above Better Creative's 1200 so this filter runs last when both mods are
 * installed. {@code ModifyExpressionValue} injections chain, so the player's ordering still applies;
 * it just applies to a list this has already narrowed.
 *
 * @author THEFricadelle
 */
@Mixin(value = CreativeModeInventoryScreen.class, priority = 1300)
public abstract class CreativeModeInventoryScreenMixin
        extends AbstractContainerScreen<CreativeModeInventoryScreen.ItemPickerMenu> {

    @Shadow
    private static CreativeModeTab selectedTab;

    @Shadow
    private float scrollOffs;

    private CreativeModeInventoryScreenMixin(CreativeModeInventoryScreen.ItemPickerMenu menu,
                                             Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @ModifyExpressionValue(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/neoforged/neoforge/common/CreativeModeTabRegistry;"
                            + "getSortedCreativeModeTabs()Ljava/util/List;"
            ),
            require = 0
    )
    private List<CreativeModeTab> creativeadmin$hideLockedTabs(List<CreativeModeTab> original) {
        try {
            if (!ReceivedAdvice.enforced()) {
                return original;
            }
            List<CreativeModeTab> kept = new ArrayList<>(original.size());
            for (CreativeModeTab tab : original) {
                var id = CreativeModeTabRegistry.getName(tab);
                // A tab with no registry name cannot be matched against the policy. Keeping it is
                // harmless: the server still refuses whatever it contains.
                if (id == null || ReceivedAdvice.allowsTab(id)) {
                    kept.add(tab);
                }
            }
            // A filter that leaves nothing would present an empty tab bar; showing everything is
            // the lesser evil, since the server refuses what is not allowed anyway.
            return kept.isEmpty() ? original : kept;
        } catch (Exception e) {
            // Display filtering is never worth breaking the inventory over.
            return original;
        }
    }

    /**
     * Asks for fresh advice, since a permission mod may have moved the player to another group
     * without any event the server hears, and adds the admin button for players allowed to use it.
     */
    @Inject(method = "init", at = @At("TAIL"), require = 0)
    private void creativeadmin$onInit(CallbackInfo ci) {
        try {
            if (this.minecraft == null || this.minecraft.gameMode == null
                    || !this.minecraft.gameMode.hasInfiniteItems()) {
                return;
            }
            if (this.minecraft.getConnection() != null
                    && this.minecraft.getConnection().hasChannel(AdminPayloads.Refresh.TYPE)) {
                PacketDistributor.sendToServer(new AdminPayloads.Refresh());
            }
            if (ReceivedAdvice.admin()) {
                // Left of Better Creative's settings button, which sits at imageWidth - 44.
                addRenderableWidget(Button.builder(Component.literal("⚑"),
                                b -> PacketDistributor.sendToServer(new AdminPayloads.Open()))
                        .bounds(this.leftPos + this.imageWidth - 66, this.topPos - 50, 20, 20)
                        .tooltip(Tooltip.create(Component.translatable("creativeadmin.admin.button_tooltip")))
                        .build());
            }
        } catch (Exception e) {
            // The button and the refresh are conveniences; the inventory must open regardless.
        }
    }

    @Inject(method = "selectTab", at = @At("TAIL"), require = 0)
    private void creativeadmin$filterSelectedTab(CreativeModeTab tab, CallbackInfo ci) {
        creativeadmin$removeLockedItems();
    }

    @Inject(method = "refreshSearchResults", at = @At("TAIL"), require = 0)
    private void creativeadmin$filterSearch(CallbackInfo ci) {
        creativeadmin$removeLockedItems();
    }

    @Inject(method = "refreshCurrentTabContents", at = @At("TAIL"), require = 0)
    private void creativeadmin$filterRefreshed(CallbackInfo ci) {
        creativeadmin$removeLockedItems();
    }

    /** Category and search pages only: the hotbar and inventory pages show what the player owns. */
    private void creativeadmin$removeLockedItems() {
        try {
            if (!ReceivedAdvice.enforced() || selectedTab == null) {
                return;
            }
            CreativeModeTab.Type type = selectedTab.getType();
            if (type != CreativeModeTab.Type.CATEGORY && type != CreativeModeTab.Type.SEARCH) {
                return;
            }
            if (this.menu.items.removeIf(ReceivedAdvice::isLocked)) {
                this.menu.scrollTo(this.scrollOffs);
            }
        } catch (Exception e) {
            // Display filtering is never worth breaking the inventory over.
        }
    }
}
