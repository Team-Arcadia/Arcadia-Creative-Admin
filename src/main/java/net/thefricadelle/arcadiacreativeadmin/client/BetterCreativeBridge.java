/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client;

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
 * Hands the tabs the server locked to Arcadia Better Creative, when it is installed.
 * <p>
 * Better Creative applies its own tab policy everywhere a tab shows up: the tab bar, the search
 * scope and its settings screen. Feeding it the server's answer is what keeps a player from
 * re-enabling a locked tab from Better Creative's settings, where this mod's own display filter
 * does not reach.
 * <p>
 * The two jars have no dependency on each other in either direction. The contract is one class of
 * Better Creative, {@code net.thefricadelle.arcadiabettercreative.api.ServerTabPolicy}, with two
 * static methods that only take JDK types, looked up once by name. If Better Creative is absent,
 * too old to have it, or it changes shape, the lookup fails once, is logged, and this mod's own
 * filter keeps working alone.
 *
 * @author THEFricadelle
 */
public final class BetterCreativeBridge {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String MOD_ID = "arcadiabettercreative";
    private static final String API_CLASS = "net.thefricadelle.arcadiabettercreative.api.ServerTabPolicy";

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
            LOGGER.warn("Arcadia Better Creative rejected the server tab policy; its own settings may still list locked tabs", e);
        }
    }

    public static void clear() {
        if (!resolve() || clear == null) {
            return;
        }
        try {
            clear.invoke();
        } catch (Throwable e) {
            LOGGER.warn("Could not clear the server tab policy in Arcadia Better Creative", e);
        }
    }

    private static boolean resolve() {
        if (resolved) {
            return set != null;
        }
        resolved = true;
        if (!ModList.get().isLoaded(MOD_ID)) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            set = lookup.findStatic(api, "set", MethodType.methodType(void.class, String.class, Set.class));
            clear = lookup.findStatic(api, "clear", MethodType.methodType(void.class));
            LOGGER.info("Arcadia Better Creative found; locked tabs are applied to its tab bar and settings");
            return true;
        } catch (ReflectiveOperationException | LinkageError e) {
            set = null;
            clear = null;
            LOGGER.warn("Arcadia Better Creative is installed without the server tab policy API; "
                    + "locked tabs stay hidden, but its settings screen may still list them");
            return false;
        }
    }
}
