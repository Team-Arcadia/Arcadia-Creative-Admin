/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.creativeadmin.PolicyLifecycle;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happens around a refusal: putting the client back in sync, telling the player why, and
 * keeping both of those from turning into spam.
 * <p>
 * Kept out of the mixin on purpose. Mixin classes are transformed into someone else's class and are
 * awkward to change without re-verifying the injection, so they should hold the hook and nothing
 * else. Every entry point here fails closed: an exception refuses the action.
 *
 * @author THEFricadelle
 */
public final class PolicyEnforcer {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * A creative client sends a slot packet per click, and dragging a stack across the inventory
     * sends a burst of them. Without a floor, one refused drag would print a dozen identical lines.
     */
    private static final long FEEDBACK_INTERVAL_MS = 1500L;

    private static final Map<UUID, Long> LAST_FEEDBACK = new HashMap<>();

    private PolicyEnforcer() {}

    /**
     * Whether a creative write into an inventory slot may go through. Called with the stack as it
     * is about to land, after vanilla has finished rewriting it, while the slot still holds the old
     * content.
     * <p>
     * Only the part that did not come out of the player's own inventory is judged: see
     * {@link CreativeLedger}. What the slot loses is credited once the write is accepted.
     */
    public static boolean permitSlotWrite(ServerPlayer player, Slot slot, ItemStack incoming) {
        try {
            ItemStack current = slot.getItem();
            UUID id = player.getUUID();
            if (incoming.isEmpty()) {
                CreativeLedger.credit(id, current);
                return true;
            }
            if (ItemStack.isSameItemSameComponents(current, incoming)) {
                int delta = incoming.getCount() - current.getCount();
                if (delta <= 0) {
                    CreativeLedger.credit(id, current.copyWithCount(-delta));
                    return true;
                }
                return permitCreation(player, incoming, delta, player.inventoryMenu);
            }
            if (!permitCreation(player, incoming, incoming.getCount(), player.inventoryMenu)) {
                return false;
            }
            // The previous content went to the cursor, on the client only.
            CreativeLedger.credit(id, current);
            return true;
        } catch (Exception e) {
            // An enforcement component must not be disabled by its own bug. Refuse and log: a
            // creative action lost to a defect is recoverable, a silently open whitelist is not.
            reportFailure(player, player.inventoryMenu, e);
            return false;
        }
    }

    /** Whether a creative drop ({@code slotNum < 0}) may go through: a thrown stack is judged like a held one. */
    public static boolean permitDrop(ServerPlayer player, ItemStack stack) {
        try {
            return stack.isEmpty() || permitCreation(player, stack, stack.getCount(), player.inventoryMenu);
        } catch (Exception e) {
            reportFailure(player, player.inventoryMenu, e);
            return false;
        }
    }

    /**
     * A stack that credits cover is a move and passes untouched, whatever the profile says: what a
     * player already carries is not this mod's business. The rest is judged.
     */
    private static boolean permitCreation(ServerPlayer player, ItemStack stack, int count,
                                          AbstractContainerMenu menu) {
        if (CreativeLedger.consume(player.getUUID(), stack, count)) {
            return true;
        }
        Decision decision = decide(player, stack);
        if (decision.allowed()) {
            return true;
        }
        refuse(player, menu, decision);
        return false;
    }

    /**
     * Whether a middle-click clone in an open container may go through. In creative this copies a
     * full stack of whatever the slot holds, components included, without ever sending a creative
     * slot packet, so an item placed in the world before an event could otherwise be multiplied at
     * will.
     */
    public static boolean permitClone(ServerPlayer player, AbstractContainerMenu menu, int slotId) {
        try {
            if (!player.hasInfiniteMaterials() || slotId < 0 || !menu.isValidSlotIndex(slotId)) {
                return true;
            }
            ItemStack source = menu.getSlot(slotId).getItem();
            if (source.isEmpty()) {
                return true;
            }
            Decision decision = decide(player, source);
            if (decision.allowed()) {
                return true;
            }
            refuse(player, menu, decision);
            return false;
        } catch (Exception e) {
            reportFailure(player, menu, e);
            return false;
        }
    }

    private static Decision decide(ServerPlayer player, ItemStack stack) {
        CreativeProfile profile = PolicyManager.profileFor(player);
        if (profile == null) {
            return Decision.allow();
        }
        PolicyLifecycle.refreshTabIndexIfStale(player.server);
        return PolicyEvaluator.evaluate(profile, stack);
    }

    /**
     * Refusing leaves the client believing the action happened, because it applied the change
     * locally before sending. Re-sending the real menu state is what makes the refusal visible
     * instead of producing a ghost item that vanishes on the next reload.
     */
    private static void refuse(ServerPlayer player, AbstractContainerMenu menu, Decision decision) {
        menu.sendAllDataToRemote();
        if (decision.reason() != null && shouldSpeak(player)) {
            player.displayClientMessage(
                    Component.empty()
                            .append(Component.translatable("creativeadmin.prefix")
                                    .withStyle(ChatFormatting.GOLD))
                            .append(decision.reason().copy().withStyle(ChatFormatting.GRAY)),
                    true);
        }
    }

    /** Refusal caused by a defect rather than by a rule; the player still needs their client fixed. */
    private static void reportFailure(ServerPlayer player, AbstractContainerMenu menu, Exception cause) {
        LOGGER.error("Creative policy evaluation failed; the action was refused", cause);
        try {
            menu.sendAllDataToRemote();
            if (shouldSpeak(player)) {
                player.displayClientMessage(
                        Component.translatable("creativeadmin.deny.internal_error")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        } catch (Exception ignored) {
            // Nothing useful is left to do; the action is already refused, which is what matters.
        }
    }

    /** Drops a player's state on disconnect so the maps do not grow with the player list. */
    public static void forget(UUID player) {
        LAST_FEEDBACK.remove(player);
        CreativeLedger.forget(player);
    }

    private static boolean shouldSpeak(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long last = LAST_FEEDBACK.get(player.getUUID());
        if (last != null && now - last < FEEDBACK_INTERVAL_MS) {
            return false;
        }
        LAST_FEEDBACK.put(player.getUUID(), now);
        return true;
    }
}
