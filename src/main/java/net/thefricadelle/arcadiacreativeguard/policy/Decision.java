/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.policy;

import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Outcome of evaluating one stack against one profile.
 * <p>
 * A refusal always carries a reason. Told only that "this item is not allowed", a player asks staff,
 * and staff cannot answer without reading the policy file; naming the rule that refused turns most
 * of those questions into something the player resolves alone.
 *
 * @param allowed whether the stack may be taken
 * @param reason  player-facing explanation, {@code null} when allowed
 *
 * @author THEFricadelle
 */
public record Decision(boolean allowed, @Nullable Component reason) {

    private static final Decision ALLOWED = new Decision(true, null);

    public static Decision allow() {
        return ALLOWED;
    }

    public static Decision deny(Component reason) {
        return new Decision(false, reason);
    }

    public static Decision deny(String translationKey, Object... args) {
        return new Decision(false, Component.translatable(translationKey, args));
    }
}
