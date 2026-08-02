/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.policy;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.thefricadelle.arcadiacreativeguard.core.TabItemIndex;

/**
 * Decides whether a stack may be taken under a profile. Pure function of (profile, stack), so it can
 * be reasoned about and unit-tested without a running server.
 * <p>
 * Evaluation order is deliberate: denials are checked before allowances, because a broad allowance
 * exists precisely so it can be trimmed, and a rule that could be overridden by a wider one would
 * be useless. Component checks come last and apply to already-allowed items, since the payload
 * rides on a stack whose item is otherwise perfectly ordinary.
 *
 * @author THEFricadelle
 */
public final class PolicyEvaluator {

    /**
     * Containers can nest. The limit is not a performance guard — it stops a hand-crafted stack of
     * containers inside containers from turning one packet into unbounded server-side work.
     */
    private static final int MAX_CONTAINER_DEPTH = 4;

    private PolicyEvaluator() {}

    public static Decision evaluate(CreativeProfile profile, ItemStack stack) {
        return evaluate(profile, stack, 0);
    }

    private static Decision evaluate(CreativeProfile profile, ItemStack stack, int depth) {
        // Emptying a slot has to stay possible under any profile, or a player who picks up a
        // forbidden item can never put it back down.
        if (stack.isEmpty()) {
            return Decision.allow();
        }

        Item item = stack.getItem();
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id == null) {
            // An unregistered item cannot be named by any rule, so no whitelist can ever allow it.
            return Decision.deny("arcadiacreativeguard.deny.unregistered");
        }

        if (profile.deniedItems().contains(id) || profile.deniedNamespaces().contains(id.getNamespace())) {
            return Decision.deny("arcadiacreativeguard.deny.explicit", id.toString());
        }

        if (!isAllowed(profile, stack, item, id)) {
            return Decision.deny("arcadiacreativeguard.deny.not_whitelisted", id.toString(), profile.name());
        }

        return evaluateComponents(profile, stack, id, depth);
    }

    /** The four rule shapes, cheapest first: a set lookup before a tag lookup before a tab lookup. */
    private static boolean isAllowed(CreativeProfile profile, ItemStack stack, Item item, ResourceLocation id) {
        if (profile.namespaces().contains(id.getNamespace()) || profile.items().contains(id)) {
            return true;
        }
        for (ResourceLocation tag : profile.tags()) {
            if (stack.is(TagKey.create(Registries.ITEM, tag))) {
                return true;
            }
        }
        for (ResourceLocation tab : profile.tabs()) {
            if (TabItemIndex.itemsOf(tab).contains(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Guards the payload an allowed item can carry. A chest is a normal building block; a chest
     * holding a stack of bedrock, or a sign carrying block entity data, is the same item id with a
     * completely different effect on a server.
     */
    private static Decision evaluateComponents(CreativeProfile profile, ItemStack stack,
                                               ResourceLocation id, int depth) {
        if (!profile.allowBlockEntityData() && stack.has(DataComponents.BLOCK_ENTITY_DATA)) {
            return Decision.deny("arcadiacreativeguard.deny.block_entity_data", id.toString());
        }

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        boolean carriesContents = (container != null && container.nonEmptyStream().findAny().isPresent())
                || (bundle != null && !bundle.isEmpty());

        if (!carriesContents) {
            return Decision.allow();
        }
        if (!profile.allowContainerContents()) {
            return Decision.deny("arcadiacreativeguard.deny.container_contents", id.toString());
        }
        if (depth >= MAX_CONTAINER_DEPTH) {
            return Decision.deny("arcadiacreativeguard.deny.container_depth", id.toString());
        }

        // Contents are held to the same profile: a whitelist that stops at the outer stack is one
        // shulker box away from being no whitelist at all.
        if (container != null) {
            for (ItemStack inner : container.nonEmptyItems()) {
                Decision decision = evaluate(profile, inner, depth + 1);
                if (!decision.allowed()) {
                    return decision;
                }
            }
        }
        if (bundle != null) {
            for (ItemStack inner : bundle.items()) {
                Decision decision = evaluate(profile, inner, depth + 1);
                if (!decision.allowed()) {
                    return decision;
                }
            }
        }
        return Decision.allow();
    }
}
