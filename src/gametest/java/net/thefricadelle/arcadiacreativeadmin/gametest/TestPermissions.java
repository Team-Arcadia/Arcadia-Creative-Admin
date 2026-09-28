/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.gametest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeConfig;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.handler.IPermissionHandler;
import net.neoforged.neoforge.server.permission.nodes.PermissionDynamicContext;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A permission handler for GameTests, standing in for LuckPerms or CustomPerm.
 * <p>
 * Those mods answer the mod's nodes through NeoForge's permission API, which is the only contract
 * this mod relies on. This handler answers the same way: an explicit value set by a test wins, and
 * a node nobody set falls back to the default its mod declared, as both of them do. That covers
 * group meta naming a profile, a bypass granted without op, and a bypass denied to an operator,
 * without a dependency on either mod.
 * <p>
 * Only installed when the {@value #PROPERTY} system property is set, which only the GameTest run
 * does: this source set is also on the classpath of the dev client and server.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeAdmin.MOD_ID)
public final class TestPermissions {

    public static final String PROPERTY = "arcadiacreativeadmin.testPermissions";
    public static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(ArcadiaCreativeAdmin.MOD_ID, "test_permissions");

    private static final Map<UUID, Map<String, Object>> VALUES = new ConcurrentHashMap<>();

    private TestPermissions() {}

    @SubscribeEvent
    public static void onGatherHandler(PermissionGatherEvent.Handler event) {
        if (!Boolean.getBoolean(PROPERTY)) {
            return;
        }
        event.addPermissionHandler(ID, Handler::new);
        // In memory only, the way CustomPerm selects itself: the file keeps NeoForge's default.
        NeoForgeConfig.SERVER.permissionHandler.set(ID.toString());
    }

    public static boolean active() {
        return ID.equals(PermissionAPI.getActivePermissionHandler());
    }

    public static <T> void set(ServerPlayer player, PermissionNode<T> node, T value) {
        VALUES.computeIfAbsent(player.getUUID(), id -> new ConcurrentHashMap<>()).put(node.getNodeName(), value);
    }

    public static void clear(ServerPlayer player) {
        VALUES.remove(player.getUUID());
    }

    private record Handler(Set<PermissionNode<?>> nodes) implements IPermissionHandler {

        Handler(Collection<PermissionNode<?>> nodes) {
            this(Set.copyOf(nodes));
        }

        @Override
        public ResourceLocation getIdentifier() {
            return ID;
        }

        @Override
        public Set<PermissionNode<?>> getRegisteredNodes() {
            return nodes;
        }

        @Override
        public <T> T getPermission(ServerPlayer player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            T value = explicit(player.getUUID(), node);
            return value != null ? value : node.getDefaultResolver().resolve(player, player.getUUID(), context);
        }

        @Override
        public <T> T getOfflinePermission(UUID player, PermissionNode<T> node, PermissionDynamicContext<?>... context) {
            T value = explicit(player, node);
            return value != null ? value : node.getDefaultResolver().resolve(null, player, context);
        }

        @Nullable
        @SuppressWarnings("unchecked")
        private static <T> T explicit(UUID player, PermissionNode<T> node) {
            Map<String, Object> values = VALUES.get(player);
            return values == null ? null : (T) values.get(node.getNodeName());
        }
    }
}
