/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.mixin;

import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.arcadiacreativeadmin.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeadmin.policy.Decision;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyEnforcer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The single point where a creative restriction is actually enforceable.
 * <p>
 * {@code ServerboundSetCreativeModeSlotPacket} is the only way a player in creative mode makes an
 * item exist: the creative inventory, middle-click pick-block and every client-side item browser
 * all end up here. A client can send it with any payload it likes, so validating it server-side is
 * what makes a whitelist real rather than cosmetic — a player who removes their client mods, edits
 * their config or joins with a vanilla client hits this check unchanged.
 * <p>
 * Injected after {@code PacketUtils.ensureRunningOnSameThread} rather than at {@code HEAD}. That
 * call re-schedules the handler onto the server thread the first time it runs, so {@code HEAD} can
 * execute on a netty thread; after it, the server thread is guaranteed and the player's inventory
 * can be touched safely.
 *
 * @author THEFricadelle
 */
@Mixin(net.minecraft.server.network.ServerGamePacketListenerImpl.class)
public abstract class ServerGamePacketListenerImplMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(
            method = "handleSetCreativeModeSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread("
                            + "Lnet/minecraft/network/protocol/Packet;"
                            + "Lnet/minecraft/network/PacketListener;"
                            + "Lnet/minecraft/server/level/ServerLevel;)V",
                    shift = At.Shift.AFTER
            ),
            cancellable = true
    )
    private void arcadiacreativeadmin$enforcePolicy(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        try {
            CreativeProfile profile = PolicyEnforcer.profileFor(this.player);
            if (profile == null) {
                return;
            }
            ItemStack stack = packet.itemStack();
            Decision decision = PolicyEnforcer.evaluate(profile, stack);
            if (decision.allowed()) {
                return;
            }
            // Cancelling covers both branches of the vanilla handler: writing into an inventory slot
            // and the slotNum < 0 path that drops the stack on the ground. Letting the drop through
            // would leave the whitelist bypassable by throwing the item instead of holding it.
            ci.cancel();
            PolicyEnforcer.refuse(this.player, stack, decision);
        } catch (Exception e) {
            // An enforcement component must not be disabled by its own bug. Cancel and log: a
            // creative action lost to a defect is recoverable, a silently open whitelist is not.
            ci.cancel();
            PolicyEnforcer.reportFailure(this.player, e);
        }
    }
}
