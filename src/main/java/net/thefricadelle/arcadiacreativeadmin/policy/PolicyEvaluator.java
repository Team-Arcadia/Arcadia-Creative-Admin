/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.policy;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.WrittenBookContent;
import net.thefricadelle.arcadiacreativeadmin.core.TabItemIndex;

import java.util.Map;
import java.util.Optional;

/**
 * Decides whether a stack may be taken under a profile. A function of (profile, stack) and of the
 * server's registries and tab index, with no other state, so it can be exercised by GameTests.
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
            return Decision.deny("arcadiacreativeadmin.deny.unregistered");
        }

        if (profile.deniedItems().contains(id) || profile.deniedNamespaces().contains(id.getNamespace())) {
            return Decision.deny("arcadiacreativeadmin.deny.explicit", id.toString());
        }

        if (!isAllowed(profile, stack, item, id)) {
            return Decision.deny("arcadiacreativeadmin.deny.not_whitelisted", id.toString(), profile.name());
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
     * holding a stack of bedrock, a spawn egg carrying another entity or a sword carrying attribute
     * modifiers is the same item id with a completely different effect on a server.
     * <p>
     * Every component the stack carries beyond its item's defaults has to be accounted for. A stack
     * that is exactly one the creative menu offers (a potion, a painting variant, an enchanted
     * book) passes as a whole; otherwise each component must be harmless or named by the profile.
     * Block entity data and contents keep their own dedicated flags whatever else applies.
     */
    private static Decision evaluateComponents(CreativeProfile profile, ItemStack stack,
                                               ResourceLocation id, int depth) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) {
            return Decision.allow();
        }
        boolean creativeVariant = TabItemIndex.isCreativeVariant(stack);

        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> type = entry.getKey();
            if (type == DataComponents.BLOCK_ENTITY_DATA) {
                if (!profile.allowBlockEntityData()) {
                    return Decision.deny("arcadiacreativeadmin.deny.block_entity_data", id.toString());
                }
                continue;
            }
            if (type == DataComponents.CONTAINER || type == DataComponents.BUNDLE_CONTENTS) {
                // Judged below on what they hold; an empty container is an ordinary block.
                continue;
            }
            if (creativeVariant) {
                continue;
            }
            ResourceLocation componentId = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (componentId != null && profile.allowedComponents().contains(componentId)) {
                continue;
            }
            if (entry.getValue().isPresent() && isHarmless(type, entry.getValue().get())) {
                continue;
            }
            return Decision.deny("arcadiacreativeadmin.deny.component", id.toString(),
                    componentId == null ? "?" : componentId.toString());
        }

        ItemContainerContents container = stack.get(DataComponents.CONTAINER);
        BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
        boolean carriesContents = (container != null && container.nonEmptyStream().findAny().isPresent())
                || (bundle != null && !bundle.isEmpty());

        if (!carriesContents) {
            return Decision.allow();
        }
        if (!profile.allowContainerContents()) {
            return Decision.deny("arcadiacreativeadmin.deny.container_contents", id.toString());
        }
        if (depth >= MAX_CONTAINER_DEPTH) {
            return Decision.deny("arcadiacreativeadmin.deny.container_depth", id.toString());
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

    /** A removed default component is never harmless: nothing in ordinary play strips one. */
    private static boolean isHarmless(DataComponentType<?> type, Object value) {
        if (type == DataComponents.WRITTEN_BOOK_CONTENT) {
            return ComponentRules.isHarmlessBook((WrittenBookContent) value);
        }
        return ComponentRules.isHarmless(type);
    }
}
