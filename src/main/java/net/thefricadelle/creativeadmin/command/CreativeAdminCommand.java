/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.creativeadmin.PolicyLifecycle;
import net.thefricadelle.creativeadmin.core.TabItemIndex;
import net.thefricadelle.creativeadmin.network.AdminServer;
import net.thefricadelle.creativeadmin.network.PolicyNetwork;
import net.thefricadelle.creativeadmin.policy.CreativePermissions;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.Decision;
import net.thefricadelle.creativeadmin.policy.PolicyEvaluator;
import net.thefricadelle.creativeadmin.policy.PolicyManager;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

/**
 * Staff commands, gated by {@link CreativePermissions#ADMIN} (op level 3 without a permission mod).
 * Without arguments the command opens the admin screen, which needs the mod on the client; every
 * subcommand works from the console and from a vanilla client.
 * <p>
 * {@code check} and {@code tabs} exist because a whitelist is only as good as the operator's
 * ability to predict it. Writing tab and tag rules blind, then discovering during an event that a
 * rule matched more than intended, is the failure mode these two commands remove.
 *
 * @author THEFricadelle
 */
public final class CreativeAdminCommand {

    private static final SuggestionProvider<CommandSourceStack> PROFILES =
            (context, builder) -> SharedSuggestionProvider.suggest(PolicyManager.profileNames(), builder);

    private CreativeAdminCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("creativeadmin")
                .requires(CreativePermissions::isAdmin)
                .executes(CreativeAdminCommand::openScreen);

        root.then(Commands.literal("reload").executes(CreativeAdminCommand::reload));
        root.then(Commands.literal("status").executes(CreativeAdminCommand::status));
        root.then(Commands.literal("tabs").executes(CreativeAdminCommand::tabs));
        root.then(Commands.literal("check")
                .executes(context -> check(context, null))
                .then(Commands.argument("profile", StringArgumentType.word())
                        .suggests(PROFILES)
                        .executes(context -> check(context, StringArgumentType.getString(context, "profile")))));

        root.then(Commands.literal("profile")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("clear").executes(CreativeAdminCommand::clearProfile))
                        .then(Commands.argument("profile", StringArgumentType.word())
                                .suggests(PROFILES)
                                .executes(CreativeAdminCommand::setProfile))));

        dispatcher.register(root);
    }

    private static int openScreen(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        AdminServer.open(context.getSource().getPlayerOrException());
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int unmatched = PolicyLifecycle.reload(source.getServer(), false);
        source.sendSuccess(() -> Component.translatable(
                "creativeadmin.command.reloaded",
                PolicyManager.profileNames().size(),
                String.valueOf(PolicyManager.enforced())), true);
        if (unmatched > 0) {
            source.sendFailure(Component.translatable("creativeadmin.command.rules_unmatched", unmatched));
        }
        if (!PolicyManager.assignmentsWritable()) {
            source.sendFailure(Component.translatable("creativeadmin.command.assignments_broken"));
        }
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("creativeadmin.command.status",
                String.valueOf(PolicyManager.enforced()),
                PolicyManager.profileNames().isEmpty()
                        ? "-"
                        : String.join(", ", PolicyManager.profileNames()),
                PolicyManager.defaultProfile().isEmpty() ? "-" : PolicyManager.defaultProfile(),
                PolicyManager.bypassOpLevel()), false);
        if (!PolicyManager.assignmentsWritable()) {
            source.sendFailure(Component.translatable("creativeadmin.command.assignments_broken"));
        }
        return 1;
    }

    /** Lists what a {@code tabs} rule can name, since the ids are not discoverable in game. */
    private static int tabs(CommandContext<CommandSourceStack> context) {
        PolicyLifecycle.refreshTabIndexIfStale(context.getSource().getServer());
        String listed = TabItemIndex.indexedTabs().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "creativeadmin.command.tabs",
                TabItemIndex.indexedTabs().size(),
                listed.isEmpty() ? "-" : listed), false);
        return 1;
    }

    /**
     * Evaluates the held item against a profile and reports the verdict with its reason.
     * <p>
     * Staff normally bypass the policy, so evaluating against the caller's <em>effective</em>
     * profile would answer "allowed" for everything and be useless to the very people who write the
     * rules. Without an explicit argument this falls back to the profile the caller would be
     * assigned, and the argument form lets any profile be probed from a single staff account.
     */
    private static int check(CommandContext<CommandSourceStack> context, @Nullable String profileName)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        PolicyLifecycle.refreshTabIndexIfStale(source.getServer());

        String name = profileName != null ? profileName : PolicyManager.effectiveProfileName(player);
        CreativeProfile profile = name.isEmpty() ? null : PolicyManager.profile(name);
        if (profile == null) {
            source.sendFailure(Component.translatable(
                    "creativeadmin.command.unknown_profile", name.isEmpty() ? "-" : name));
            return 0;
        }

        Decision decision = PolicyEvaluator.evaluate(profile, held);
        Component verdict = decision.allowed()
                ? Component.translatable("creativeadmin.command.check_allowed",
                        held.getHoverName(), profile.name()).withStyle(ChatFormatting.GREEN)
                : Component.translatable("creativeadmin.command.check_denied",
                        held.getHoverName(), profile.name(), decision.reason())
                        .withStyle(ChatFormatting.RED);
        source.sendSuccess(() -> verdict, false);
        return decision.allowed() ? 1 : 0;
    }

    private static int setProfile(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        String profile = StringArgumentType.getString(context, "profile");
        if (!PolicyManager.hasProfile(profile)) {
            source.sendFailure(Component.translatable(
                    "creativeadmin.command.unknown_profile", profile));
            return 0;
        }
        if (!PolicyManager.assignmentsWritable()) {
            source.sendFailure(Component.translatable("creativeadmin.command.assignments_broken"));
            return 0;
        }
        int count = 0;
        boolean saved = true;
        for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
            saved &= PolicyManager.assign(target.getUUID(), profile);
            PolicyNetwork.sendTo(target);
            count++;
        }
        final int assigned = count;
        source.sendSuccess(() -> Component.translatable(
                "creativeadmin.command.assigned", assigned, profile), true);
        if (!saved) {
            source.sendFailure(Component.translatable("creativeadmin.command.save_failed"));
        }
        return count;
    }

    private static int clearProfile(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        if (!PolicyManager.assignmentsWritable()) {
            source.sendFailure(Component.translatable("creativeadmin.command.assignments_broken"));
            return 0;
        }
        int count = 0;
        boolean saved = true;
        for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
            if (PolicyManager.isAssigned(target.getUUID())) {
                saved &= PolicyManager.clearAssignment(target.getUUID());
                PolicyNetwork.sendTo(target);
                count++;
            }
        }
        final int cleared = count;
        source.sendSuccess(() -> Component.translatable(
                "creativeadmin.command.cleared", cleared), true);
        if (!saved) {
            source.sendFailure(Component.translatable("creativeadmin.command.save_failed"));
        }
        return count;
    }
}
