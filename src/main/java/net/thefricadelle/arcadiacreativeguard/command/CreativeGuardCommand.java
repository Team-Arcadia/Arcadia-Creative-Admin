/*
 * Arcadia Creative Guard - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Guard-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeguard.command;

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
import net.thefricadelle.arcadiacreativeguard.core.TabItemIndex;
import net.thefricadelle.arcadiacreativeguard.core.VisibleTabResolver;
import net.thefricadelle.arcadiacreativeguard.network.PolicyNetwork;
import net.thefricadelle.arcadiacreativeguard.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeguard.policy.Decision;
import net.thefricadelle.arcadiacreativeguard.policy.PolicyEvaluator;
import net.thefricadelle.arcadiacreativeguard.policy.PolicyManager;

import javax.annotation.Nullable;
import java.util.stream.Collectors;

/**
 * Operator commands. Everything here is staff tooling, gated at op level 3.
 * <p>
 * {@code check} and {@code tabs} exist because a whitelist is only as good as the operator's
 * ability to predict it. Writing tab and tag rules blind, then discovering during an event that a
 * rule matched more than intended, is the failure mode these two commands remove.
 *
 * @author THEFricadelle
 */
public final class CreativeGuardCommand {

    private static final int PERMISSION_LEVEL = 3;

    private static final SuggestionProvider<CommandSourceStack> PROFILES =
            (context, builder) -> SharedSuggestionProvider.suggest(PolicyManager.profileNames(), builder);

    private CreativeGuardCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("creativeguard")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL));

        root.then(Commands.literal("reload").executes(CreativeGuardCommand::reload));
        root.then(Commands.literal("status").executes(CreativeGuardCommand::status));
        root.then(Commands.literal("tabs").executes(CreativeGuardCommand::tabs));
        root.then(Commands.literal("check")
                .executes(context -> check(context, null))
                .then(Commands.argument("profile", StringArgumentType.word())
                        .suggests(PROFILES)
                        .executes(context -> check(context, StringArgumentType.getString(context, "profile")))));

        root.then(Commands.literal("profile")
                .then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("clear").executes(CreativeGuardCommand::clearProfile))
                        .then(Commands.argument("profile", StringArgumentType.word())
                                .suggests(PROFILES)
                                .executes(CreativeGuardCommand::setProfile))));

        dispatcher.register(root);
    }

    private static int reload(CommandContext<CommandSourceStack> context) {
        PolicyManager.load();
        TabItemIndex.build(context.getSource().getServer());
        VisibleTabResolver.invalidate();
        // Every connected client is holding advice derived from the policy that just changed.
        PolicyNetwork.sendToAll(context.getSource().getServer().getPlayerList().getPlayers());
        context.getSource().sendSuccess(() -> Component.translatable(
                "arcadiacreativeguard.command.reloaded",
                PolicyManager.profileNames().size(),
                String.valueOf(PolicyManager.enforced())), true);
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.translatable("arcadiacreativeguard.command.status",
                String.valueOf(PolicyManager.enforced()),
                PolicyManager.profileNames().isEmpty()
                        ? "-"
                        : String.join(", ", PolicyManager.profileNames()),
                PolicyManager.bypassOpLevel()), false);
        return 1;
    }

    /** Lists what a {@code tabs} rule can name, since the ids are not discoverable in game. */
    private static int tabs(CommandContext<CommandSourceStack> context) {
        String listed = TabItemIndex.indexedTabs().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .collect(Collectors.joining(", "));
        context.getSource().sendSuccess(() -> Component.translatable(
                "arcadiacreativeguard.command.tabs",
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

        String name = profileName != null ? profileName : PolicyManager.assignedProfile(player.getUUID());
        CreativeProfile profile = name.isEmpty() ? null : PolicyManager.profile(name);
        if (profile == null) {
            source.sendFailure(Component.translatable(
                    "arcadiacreativeguard.command.unknown_profile", name.isEmpty() ? "-" : name));
            return 0;
        }

        Decision decision = PolicyEvaluator.evaluate(profile, held);
        Component verdict = decision.allowed()
                ? Component.translatable("arcadiacreativeguard.command.check_allowed",
                        held.getHoverName(), profile.name()).withStyle(ChatFormatting.GREEN)
                : Component.translatable("arcadiacreativeguard.command.check_denied",
                        held.getHoverName(), profile.name(), decision.reason())
                        .withStyle(ChatFormatting.RED);
        source.sendSuccess(() -> verdict, false);
        return decision.allowed() ? 1 : 0;
    }

    private static int setProfile(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        String profile = StringArgumentType.getString(context, "profile");
        if (!PolicyManager.hasProfile(profile)) {
            context.getSource().sendFailure(Component.translatable(
                    "arcadiacreativeguard.command.unknown_profile", profile));
            return 0;
        }
        int count = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
            PolicyManager.assign(target.getUUID(), profile);
            PolicyNetwork.sendTo(target);
            count++;
        }
        final int assigned = count;
        context.getSource().sendSuccess(() -> Component.translatable(
                "arcadiacreativeguard.command.assigned", assigned, profile), true);
        return count;
    }

    private static int clearProfile(CommandContext<CommandSourceStack> context)
            throws CommandSyntaxException {
        int count = 0;
        for (ServerPlayer target : EntityArgument.getPlayers(context, "targets")) {
            if (PolicyManager.clearAssignment(target.getUUID())) {
                PolicyNetwork.sendTo(target);
                count++;
            }
        }
        final int cleared = count;
        context.getSource().sendSuccess(() -> Component.translatable(
                "arcadiacreativeguard.command.cleared", cleared), true);
        return count;
    }
}
