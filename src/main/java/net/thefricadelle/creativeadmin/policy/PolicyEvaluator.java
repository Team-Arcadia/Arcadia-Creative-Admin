/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

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
import net.thefricadelle.creativeadmin.core.TabItemIndex;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Decides whether a stack may be taken under a profile. A function of (profile, stack) and of the
 * server's registries and tab index, with no other state, so it can be exercised by GameTests.
 * <p>
 * Two questions, in order. Is the item open under the profile ({@link #isOpen}): the mode and the
 * selection decide, with a single-item entry always outranking a tab, mod or tag. Then, for an open
 * item, is the payload riding on this particular stack acceptable: components come last because
 * they turn an ordinary item id into something else.
 *
 * @author THEFricadelle
 */
public final class PolicyEvaluator {

    /**
     * Containers can nest. The limit is not a performance guard: it stops a hand-crafted stack of
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
            return Decision.deny("creativeadmin.deny.unregistered");
        }

        if (!isOpen(profile, item, id, TabItemIndex::itemsOf)) {
            return Decision.deny("creativeadmin.deny.locked", id.toString(), profile.name());
        }

        return evaluateComponents(profile, stack, id, depth);
    }

    /** Whether the item itself is open under the profile, whatever a stack of it carries. */
    public static boolean isOpen(CreativeProfile profile, Item item) {
        return isOpen(profile, item, TabItemIndex::itemsOf);
    }

    /**
     * Same question with the tab contents supplied by the caller: the admin screen previews a
     * profile on the client, where the server's tab index does not exist.
     */
    public static boolean isOpen(CreativeProfile profile, Item item, Function<ResourceLocation, Set<Item>> tabItems) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id != null && isOpen(profile, item, id, tabItems);
    }

    private static boolean isOpen(CreativeProfile profile, Item item, ResourceLocation id,
                                  Function<ResourceLocation, Set<Item>> tabItems) {
        boolean selected = isSelected(profile, item, id, tabItems);
        return profile.mode() == CreativeProfile.Mode.WHITELIST ? selected : !selected;
    }

    /**
     * An item listed on its own is selected outright. Otherwise a tab, mod or tag selects it unless
     * it is an exception.
     */
    private static boolean isSelected(CreativeProfile profile, Item item, ResourceLocation id,
                                      Function<ResourceLocation, Set<Item>> tabItems) {
        if (profile.items().contains(id)) {
            return true;
        }
        return !profile.exceptions().contains(id) && isGroupSelected(profile, item, id, tabItems);
    }

    /** What the tabs, mods and tags alone say, cheapest lookup first; exceptions and items aside. */
    public static boolean isGroupSelected(CreativeProfile profile, Item item, ResourceLocation id,
                                          Function<ResourceLocation, Set<Item>> tabItems) {
        if (profile.namespaces().contains(id.getNamespace())) {
            return true;
        }
        if (!profile.tags().isEmpty()) {
            var holder = item.builtInRegistryHolder();
            for (ResourceLocation tag : profile.tags()) {
                if (holder.is(TagKey.create(Registries.ITEM, tag))) {
                    return true;
                }
            }
        }
        for (ResourceLocation tab : profile.tabs()) {
            if (tabItems.apply(tab).contains(item)) {
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
                    return Decision.deny("creativeadmin.deny.block_entity_data", id.toString());
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
            return Decision.deny("creativeadmin.deny.component", id.toString(),
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
            return Decision.deny("creativeadmin.deny.container_contents", id.toString());
        }
        if (depth >= MAX_CONTAINER_DEPTH) {
            return Decision.deny("creativeadmin.deny.container_depth", id.toString());
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
