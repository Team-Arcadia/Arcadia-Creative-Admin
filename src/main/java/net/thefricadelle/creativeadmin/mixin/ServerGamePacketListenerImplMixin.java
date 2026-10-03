/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.creativeadmin.policy.PolicyEnforcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import javax.annotation.Nullable;

/**
 * The points where a creative restriction is actually enforceable.
 * <p>
 * {@code ServerboundSetCreativeModeSlotPacket} is how a player in creative mode makes an item exist:
 * the creative inventory, middle-click pick-block and every client-side item browser all end up
 * here. A client can send it with any payload it likes, so validating it server-side is what makes
 * a whitelist real rather than cosmetic: a player who removes their client mods, edits their config
 * or joins with a vanilla client hits this check unchanged.
 * <p>
 * The two effects of that handler are wrapped, the slot write and the {@code slotNum < 0} drop,
 * rather than its entry. Before either runs, vanilla may rewrite the stack: a
 * {@code block_entity_data} carrying {@code x/y/z} is replaced by the block entity found at that
 * position, contents included. A check at the entry would judge the stack the client sent and let
 * through the one vanilla built from it.
 * <p>
 * The clone click in an open container is the other way creative produces items, and is wrapped for
 * the same reason.
 *
 * @author THEFricadelle
 */
@Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @WrapOperation(
            method = "handleSetCreativeModeSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/Slot;setByPlayer(Lnet/minecraft/world/item/ItemStack;)V"
            )
    )
    private void creativeadmin$guardSlotWrite(Slot slot, ItemStack stack, Operation<Void> original) {
        if (PolicyEnforcer.permitSlotWrite(this.player, slot, stack)) {
            original.call(slot, stack);
        }
    }

    /** Letting the drop through would leave the whitelist bypassable by throwing instead of holding. */
    @WrapOperation(
            method = "handleSetCreativeModeSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/level/ServerPlayer;drop(Lnet/minecraft/world/item/ItemStack;Z)"
                            + "Lnet/minecraft/world/entity/item/ItemEntity;"
            )
    )
    @Nullable
    private ItemEntity creativeadmin$guardDrop(ServerPlayer target, ItemStack stack, boolean traceItem,
                                                       Operation<ItemEntity> original) {
        return PolicyEnforcer.permitDrop(this.player, stack) ? original.call(target, stack, traceItem) : null;
    }

    @WrapOperation(
            method = "handleContainerClick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/inventory/AbstractContainerMenu;clicked("
                            + "IILnet/minecraft/world/inventory/ClickType;Lnet/minecraft/world/entity/player/Player;)V"
            )
    )
    private void creativeadmin$guardClone(AbstractContainerMenu menu, int slotId, int button,
                                                 ClickType clickType, Player clicker, Operation<Void> original) {
        if (clickType != ClickType.CLONE || PolicyEnforcer.permitClone(this.player, menu, slotId)) {
            original.call(menu, slotId, button, clickType, clicker);
        }
    }
}
