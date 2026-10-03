/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;

import java.util.Set;

/**
 * Which data components a stack may carry without the profile saying so.
 * <p>
 * This is an allowlist on purpose. A component is a payload riding on an otherwise ordinary item
 * id: {@code entity_data} turns a spawn egg or an item frame into any item in the game,
 * {@code container_loot} turns a chest into a loot table, {@code attribute_modifiers} turns a stick
 * into a weapon. A denylist of those would be one modded component away from being open again.
 * <p>
 * What is listed here is what ordinary play puts on a stack (a name, a dye, a map, a banner, a
 * book) and cannot create an item or change combat. Anything else needs the stack to be exactly
 * one the creative menu offers, or the component to be named in the profile.
 *
 * @author THEFricadelle
 */
final class ComponentRules {

    private static final Set<DataComponentType<?>> HARMLESS = Set.of(
            DataComponents.CUSTOM_NAME,
            DataComponents.ITEM_NAME,
            DataComponents.LORE,
            DataComponents.RARITY,
            DataComponents.DAMAGE,
            DataComponents.REPAIR_COST,
            DataComponents.CUSTOM_MODEL_DATA,
            DataComponents.HIDE_ADDITIONAL_TOOLTIP,
            DataComponents.HIDE_TOOLTIP,
            DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
            DataComponents.DYED_COLOR,
            DataComponents.MAP_COLOR,
            DataComponents.MAP_ID,
            DataComponents.MAP_DECORATIONS,
            DataComponents.MAP_POST_PROCESSING,
            DataComponents.WRITABLE_BOOK_CONTENT,
            DataComponents.TRIM,
            DataComponents.BUCKET_ENTITY_DATA,
            DataComponents.LODESTONE_TRACKER,
            DataComponents.FIREWORK_EXPLOSION,
            DataComponents.FIREWORKS,
            DataComponents.PROFILE,
            DataComponents.NOTE_BLOCK_SOUND,
            DataComponents.BANNER_PATTERNS,
            DataComponents.BASE_COLOR,
            DataComponents.POT_DECORATIONS);

    private ComponentRules() {}

    static boolean isHarmless(DataComponentType<?> type) {
        return HARMLESS.contains(type);
    }

    /**
     * A book signed in game is plain text, and that is all this accepts. A click event runs its
     * command with the permissions of whoever clicks, which makes it a way to get an operator to
     * run something they never read; selector, score and NBT text resolve against the world when
     * the book is opened.
     */
    static boolean isHarmlessBook(WrittenBookContent content) {
        for (Filterable<Component> page : content.pages()) {
            if (!isPlainText(page.raw()) || !page.filtered().map(ComponentRules::isPlainText).orElse(true)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isPlainText(Component component) {
        if (!(component.getContents() instanceof PlainTextContents)
                || component.getStyle().getClickEvent() != null) {
            return false;
        }
        for (Component sibling : component.getSiblings()) {
            if (!isPlainText(sibling)) {
                return false;
            }
        }
        return true;
    }
}
