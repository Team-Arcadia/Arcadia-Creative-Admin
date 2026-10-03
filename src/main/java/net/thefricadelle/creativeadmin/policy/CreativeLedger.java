/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * What a creative player has taken out of their own inventory and not put back yet.
 * <p>
 * The creative protocol has no "move" message. Picking a stack up sends the emptied slot, putting
 * it down sends the filled one, and the stack on the cursor only exists on the client. Judged
 * packet by packet, putting down an item the player already owned looks exactly like creating it,
 * and a profile that locks that item would destroy it.
 * <p>
 * The mod exists to lock what the creative menu hands out, not what players already carry. So
 * every stack that leaves an inventory slot through a creative packet is credited here, and a stack
 * that arrives matching a credit is a move: it passes without being judged and the credit is spent.
 * Only what no credit covers is a creation and goes through the policy. Since a credit is only
 * earned by removing the same amount from the server-side inventory, nothing can be duplicated
 * through it.
 * <p>
 * Server thread only, like every creative packet handler.
 *
 * @author THEFricadelle
 */
public final class CreativeLedger {

    /**
     * A player clearing their whole inventory at once earns one credit per slot. Beyond this the
     * oldest credits are forgotten, which at worst makes a very old move count as a creation.
     */
    private static final int MAX_CREDITS = 64;

    private static final Map<UUID, Deque<ItemStack>> CREDITS = new HashMap<>();

    private CreativeLedger() {}

    public static void credit(UUID player, ItemStack removed) {
        if (removed.isEmpty()) {
            return;
        }
        Deque<ItemStack> credits = CREDITS.computeIfAbsent(player, uuid -> new ArrayDeque<>());
        credits.addLast(removed.copy());
        while (credits.size() > MAX_CREDITS) {
            credits.removeFirst();
        }
    }

    /**
     * Spends credits covering {@code count} of the stack's exact item and components.
     *
     * @return whether they covered it; nothing is spent when they do not
     */
    public static boolean consume(UUID player, ItemStack stack, int count) {
        if (count <= 0) {
            return true;
        }
        Deque<ItemStack> credits = CREDITS.get(player);
        if (credits == null) {
            return false;
        }
        int available = 0;
        for (ItemStack credit : credits) {
            if (ItemStack.isSameItemSameComponents(credit, stack)) {
                available += credit.getCount();
                if (available >= count) {
                    break;
                }
            }
        }
        if (available < count) {
            return false;
        }
        int remaining = count;
        // Newest first: the stack on the cursor is almost always the one just picked up.
        for (Iterator<ItemStack> it = credits.descendingIterator(); it.hasNext() && remaining > 0; ) {
            ItemStack credit = it.next();
            if (!ItemStack.isSameItemSameComponents(credit, stack)) {
                continue;
            }
            int spent = Math.min(credit.getCount(), remaining);
            credit.shrink(spent);
            remaining -= spent;
            if (credit.isEmpty()) {
                it.remove();
            }
        }
        return true;
    }

    public static void forget(UUID player) {
        CREDITS.remove(player);
    }
}
