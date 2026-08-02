/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.policy;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happens around a refusal: putting the client back in sync, telling the player why, and
 * keeping both of those from turning into spam.
 * <p>
 * Kept out of the mixin on purpose. Mixin classes are transformed into someone else's class and are
 * awkward to change without re-verifying the injection, so they should hold the hook and nothing
 * else.
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

    @Nullable
    public static CreativeProfile profileFor(ServerPlayer player) {
        return PolicyManager.profileFor(player);
    }

    public static Decision evaluate(CreativeProfile profile, ItemStack stack) {
        return PolicyEvaluator.evaluate(profile, stack);
    }

    /**
     * Cancelling the packet leaves the client believing it holds the item, because it applied the
     * change locally before sending. Re-sending the real inventory is what makes the refusal visible
     * instead of producing a ghost item that vanishes on the next reload.
     */
    public static void refuse(ServerPlayer player, ItemStack refused, Decision decision) {
        player.inventoryMenu.sendAllDataToRemote();
        if (decision.reason() != null && shouldSpeak(player)) {
            player.displayClientMessage(
                    Component.empty()
                            .append(Component.translatable("arcadiacreativeguard.prefix")
                                    .withStyle(ChatFormatting.GOLD))
                            .append(decision.reason().copy().withStyle(ChatFormatting.GRAY)),
                    true);
        }
    }

    /** Refusal caused by a defect rather than by a rule; the player still needs their client fixed. */
    public static void reportFailure(ServerPlayer player, Exception cause) {
        LOGGER.error("Creative policy evaluation failed; the action was refused", cause);
        try {
            player.inventoryMenu.sendAllDataToRemote();
            if (shouldSpeak(player)) {
                player.displayClientMessage(
                        Component.translatable("arcadiacreativeguard.deny.internal_error")
                                .withStyle(ChatFormatting.RED),
                        true);
            }
        } catch (Exception ignored) {
            // Nothing useful is left to do; the packet is already cancelled, which is what matters.
        }
    }

    /** Drops a player's throttle state on disconnect so the map does not grow with the player list. */
    public static void forget(UUID player) {
        LAST_FEEDBACK.remove(player);
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
