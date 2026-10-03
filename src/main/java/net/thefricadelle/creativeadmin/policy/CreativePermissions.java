/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.policy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.thefricadelle.creativeadmin.CreativeAdmin;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The permission nodes through which a permission mod decides who is restricted and how.
 * <p>
 * Going through NeoForge's permission API rather than a specific mod is what makes LuckPerms and
 * CustomPerm both work without a dependency on either: whichever is the active handler answers.
 * Without one, the defaults below apply, which reproduce plain op levels.
 * <ul>
 *     <li>{@code creativeadmin.admin}: may open the admin screen and use the commands.</li>
 *     <li>{@code creativeadmin.bypass}: is never restricted.</li>
 *     <li>{@code creativeadmin.profile}: a text value naming the profile, set as meta on a
 *     group, for example {@code /lp group builders meta set creativeadmin.profile event}.</li>
 * </ul>
 * The nodes named {@code arcadiacreativeadmin.*} before the rename are still registered and read
 * whenever the current node is left unset, so a server's existing permission setup keeps working.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = CreativeAdmin.MOD_ID)
public final class CreativePermissions {

    /** The level the commands have always required, kept as the default for the screen too. */
    public static final int DEFAULT_ADMIN_OP_LEVEL = 3;

    /**
     * Namespace of the nodes before the rename from Arcadia Creative Admin. Servers configured
     * their permission mod with these names, and a permission mod cannot be migrated from here.
     */
    private static final String LEGACY_NAMESPACE = "arcadiacreativeadmin";

    private static final PermissionNode<Boolean> LEGACY_ADMIN = new PermissionNode<>(
            LEGACY_NAMESPACE, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(DEFAULT_ADMIN_OP_LEVEL));

    private static final PermissionNode<Boolean> LEGACY_BYPASS = new PermissionNode<>(
            LEGACY_NAMESPACE, "bypass", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(PolicyManager.bypassOpLevel()));

    private static final PermissionNode<String> LEGACY_PROFILE = new PermissionNode<>(
            LEGACY_NAMESPACE, "profile", PermissionTypes.STRING,
            (player, uuid, context) -> "");

    // A current node left unset falls back to its legacy node, not to the op level. An OR of the
    // two would hand bypass back to an op the server explicitly denied under the old name; this
    // way an explicit value on the current node wins and anything else keeps the old answer.
    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(
            CreativeAdmin.MOD_ID, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> legacy(player, uuid, LEGACY_ADMIN));

    public static final PermissionNode<Boolean> BYPASS = new PermissionNode<>(
            CreativeAdmin.MOD_ID, "bypass", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> legacy(player, uuid, LEGACY_BYPASS));

    public static final PermissionNode<String> PROFILE = new PermissionNode<>(
            CreativeAdmin.MOD_ID, "profile", PermissionTypes.STRING,
            (player, uuid, context) -> legacy(player, uuid, LEGACY_PROFILE));

    static {
        LEGACY_ADMIN.setInformation(Component.literal("Creative Admin: admin (former name)"),
                Component.literal("Read when creativeadmin.admin is not set."));
        LEGACY_BYPASS.setInformation(Component.literal("Creative Admin: bypass (former name)"),
                Component.literal("Read when creativeadmin.bypass is not set."));
        LEGACY_PROFILE.setInformation(Component.literal("Creative Admin: profile (former name)"),
                Component.literal("Read when creativeadmin.profile is not set."));
        ADMIN.setInformation(Component.literal("Creative Admin: admin"),
                Component.literal("Open the creative admin screen and use /creativeadmin."));
        BYPASS.setInformation(Component.literal("Creative Admin: bypass"),
                Component.literal("Never restricted in creative mode."));
        PROFILE.setInformation(Component.literal("Creative Admin: profile"),
                Component.literal("Name of the creative profile that applies, set as meta on a group."));
    }

    private CreativePermissions() {}

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        event.addNodes(ADMIN, BYPASS, PROFILE, LEGACY_ADMIN, LEGACY_BYPASS, LEGACY_PROFILE);
    }

    private static <T> T legacy(@Nullable ServerPlayer player, UUID uuid, PermissionNode<T> node) {
        return player != null ? PermissionAPI.getPermission(player, node) : PermissionAPI.getOfflinePermission(uuid, node);
    }

    public static boolean isAdmin(ServerPlayer player) {
        return PermissionAPI.getPermission(player, ADMIN);
    }

    /** The console and command blocks keep the op level check; only players go through the node. */
    public static boolean isAdmin(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        return player != null ? isAdmin(player) : source.hasPermission(DEFAULT_ADMIN_OP_LEVEL);
    }

    public static boolean bypasses(ServerPlayer player) {
        return PermissionAPI.getPermission(player, BYPASS);
    }

    /** @return the profile a permission mod gives the player, normalized, empty when none */
    public static String grantedProfile(ServerPlayer player) {
        String value = PermissionAPI.getPermission(player, PROFILE);
        return value == null ? "" : PolicyCodec.normalizeName(value);
    }
}
