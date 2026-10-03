/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hands the tabs the server locked to Better Creative, when it is installed.
 * <p>
 * Better Creative applies its own tab policy everywhere a tab shows up: the tab bar, the search
 * scope and its settings screen. Feeding it the server's answer is what keeps a player from
 * re-enabling a locked tab from Better Creative's settings, where this mod's own display filter
 * does not reach.
 * <p>
 * The two jars have no dependency on each other in either direction. The contract is one class of
 * Better Creative, {@code net.thefricadelle.bettercreative.api.ServerTabPolicy}, with two static
 * methods that only take JDK types, looked up once by name. Releases before 2.1.0 shipped it as
 * Arcadia Better Creative, under the mod id {@code arcadiabettercreative} and the package
 * {@code net.thefricadelle.arcadiabettercreative}; that pair is tried when the current one is absent. If Better Creative is absent,
 * too old to have it, or it changes shape, the lookup fails once, is logged, and this mod's own
 * filter keeps working alone.
 *
 * @author THEFricadelle
 */
public final class BetterCreativeBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mod id and API class, current name first, then the name used before 2.1.0. */
    private static final String[][] TARGETS = {
            {"bettercreative", "net.thefricadelle.bettercreative.api.ServerTabPolicy"},
            {"arcadiabettercreative", "net.thefricadelle.arcadiabettercreative.api.ServerTabPolicy"},
    };

    @Nullable
    private static MethodHandle set;
    @Nullable
    private static MethodHandle clear;
    private static boolean resolved;

    private BetterCreativeBridge() {}

    /** @param allowedTabs the tabs the server keeps visible; every other tab is locked */
    public static void push(String profile, Set<ResourceLocation> allowedTabs) {
        if (!resolve() || set == null) {
            return;
        }
        Set<String> ids = allowedTabs.stream().map(ResourceLocation::toString).collect(Collectors.toUnmodifiableSet());
        try {
            set.invoke(profile, ids);
        } catch (Throwable e) {
            LOGGER.warn("Better Creative rejected the server tab policy; its own settings may still list locked tabs", e);
        }
    }

    public static void clear() {
        if (!resolve() || clear == null) {
            return;
        }
        try {
            clear.invoke();
        } catch (Throwable e) {
            LOGGER.warn("Could not clear the server tab policy in Better Creative", e);
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return set != null;
        }
        resolved = true;
        String apiClass = null;
        for (String[] target : TARGETS) {
            if (ModList.get().isLoaded(target[0])) {
                apiClass = target[1];
                break;
            }
        }
        if (apiClass == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName(apiClass);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            set = lookup.findStatic(api, "set", MethodType.methodType(void.class, String.class, Set.class));
            clear = lookup.findStatic(api, "clear", MethodType.methodType(void.class));
            LOGGER.info("Better Creative found; locked tabs are applied to its tab bar and settings");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            set = null;
            clear = null;
            LOGGER.warn("Better Creative is installed without the server tab policy API; "
                    + "locked tabs stay hidden, but its settings screen may still list them");
            return false;
        }
    }
}
