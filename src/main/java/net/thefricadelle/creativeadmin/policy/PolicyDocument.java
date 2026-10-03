/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything the policy file holds, as one value. It is what the file is parsed into, what the
 * admin screen edits and what is written back, so the three can never disagree on a field.
 *
 * @param enforced       whether any restriction applies at all
 * @param defaultProfile profile of players with no assignment and no permission-provided profile;
 *                       empty means such players are unrestricted
 * @param bypassOpLevel  op level at and above which a player is unrestricted, when no permission
 *                       mod answers the bypass node
 * @param profiles       profiles by name, in the order the admin arranged them
 *
 * @author THEFricadelle
 */
public record PolicyDocument(boolean enforced, String defaultProfile, int bypassOpLevel,
                             Map<String, CreativeProfile> profiles) {

    public static final int DEFAULT_BYPASS_OP_LEVEL = 4;
    /** Level 0 is every player: accepting it would switch the whole policy off through one digit. */
    public static final int MIN_BYPASS_OP_LEVEL = 1;
    public static final int MAX_BYPASS_OP_LEVEL = 4;

    public PolicyDocument {
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    public static PolicyDocument disabled() {
        return new PolicyDocument(false, "", DEFAULT_BYPASS_OP_LEVEL, Map.of());
    }

    /** Written on first start: disabled, with one example profile to edit. */
    public static PolicyDocument sample() {
        Map<String, CreativeProfile> profiles = new LinkedHashMap<>();
        profiles.put("event", CreativeProfile.empty("event"));
        return new PolicyDocument(false, "event", DEFAULT_BYPASS_OP_LEVEL, profiles);
    }
}
