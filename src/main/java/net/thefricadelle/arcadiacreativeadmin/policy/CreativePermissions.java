/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.policy;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;

/**
 * The permission nodes through which a permission mod decides who is restricted and how.
 * <p>
 * Going through NeoForge's permission API rather than a specific mod is what makes LuckPerms and
 * CustomPerm both work without a dependency on either: whichever is the active handler answers.
 * Without one, the defaults below apply, which reproduce plain op levels.
 * <ul>
 *     <li>{@code arcadiacreativeadmin.admin}: may open the admin screen and use the commands.</li>
 *     <li>{@code arcadiacreativeadmin.bypass}: is never restricted.</li>
 *     <li>{@code arcadiacreativeadmin.profile}: a text value naming the profile, set as meta on a
 *     group, for example {@code /lp group builders meta set arcadiacreativeadmin.profile event}.</li>
 * </ul>
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeAdmin.MOD_ID)
public final class CreativePermissions {

    /** The level the commands have always required, kept as the default for the screen too. */
    public static final int DEFAULT_ADMIN_OP_LEVEL = 3;

    public static final PermissionNode<Boolean> ADMIN = new PermissionNode<>(
            ArcadiaCreativeAdmin.MOD_ID, "admin", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(DEFAULT_ADMIN_OP_LEVEL));

    public static final PermissionNode<Boolean> BYPASS = new PermissionNode<>(
            ArcadiaCreativeAdmin.MOD_ID, "bypass", PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.hasPermissions(PolicyManager.bypassOpLevel()));

    public static final PermissionNode<String> PROFILE = new PermissionNode<>(
            ArcadiaCreativeAdmin.MOD_ID, "profile", PermissionTypes.STRING,
            (player, uuid, context) -> "");

    static {
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
        event.addNodes(ADMIN, BYPASS, PROFILE);
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
