/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, closed-source software. Access to this source is restricted and
 * grants no right to copy, share, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.gametest;

import net.thefricadelle.creativeadmin.policy.ConfigPaths;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.thefricadelle.creativeadmin.CreativeAdmin;
import net.thefricadelle.creativeadmin.PolicyLifecycle;
import net.thefricadelle.creativeadmin.policy.CreativePermissions;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.PolicyDocument;
import net.thefricadelle.creativeadmin.policy.PolicyManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Who is restricted, who administers, and what happens when the files go wrong, played through the
 * real packet handlers, the real command tree and NeoForge's permission API.
 * <p>
 * Permissions go through {@link TestPermissions}, which answers the way LuckPerms and CustomPerm
 * do: an explicit value wins, an unset node falls back to the op level default.
 * <p>
 * Every test here rewrites the policy files, so each sits in its own batch: batches run one after
 * another, never alongside each other or alongside {@link PolicyGameTests}.
 *
 * @author THEFricadelle
 */
@GameTestHolder(CreativeAdmin.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AccessGameTests {

    private static final String TEMPLATE = "empty";

    /** A narrow default, a wide alternative: every resolution step lands on a visibly different profile. */
    private static final String POLICY = """
            {
              "enforced": true,
              "default_profile": "event",
              "bypass_op_level": 4,
              "profiles": {
                "event": { "mode": "whitelist", "items": ["minecraft:torch"] },
                "open": { "mode": "blacklist", "items": ["minecraft:tnt"] }
              }
            }
            """;

    private AccessGameTests() {}

    // ------------------------------------------------------------------
    // Resolution: bypass, then assignment, then group, then default
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, batch = "access_precedence")
    public static void profileResolutionFollowsItsOrder(GameTestHelper helper) {
        helper.assertTrue(TestPermissions.active(), "the stand-in permission handler is not the active one");
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = restrictedPlayer(helper);
        try {
            expectProfile(helper, player, "event");
            expectTake(helper, player, Items.TORCH, true, "the default profile refused its own item");
            expectTake(helper, player, Items.STONE, false, "the default profile let a locked item through");

            // A group, through a permission mod's meta.
            TestPermissions.set(player, CreativePermissions.PROFILE, "open");
            expectProfile(helper, player, "open");
            expectTake(helper, player, Items.STONE, true, "the group profile did not replace the default");
            expectTake(helper, player, Items.TNT, false, "the group profile let its locked item through");

            // An assignment outranks the group, and clearing it hands the player back to the group.
            PolicyManager.assign(player.getUUID(), "event");
            expectProfile(helper, player, "event");
            PolicyManager.clearAssignment(player.getUUID());
            expectProfile(helper, player, "open");

            // A group naming a profile that does not exist refuses rather than widening.
            TestPermissions.set(player, CreativePermissions.PROFILE, "ghost");
            expectTake(helper, player, Items.TORCH, false, "a group naming a missing profile left the player unrestricted");
            TestPermissions.clear(player);

            // Op levels without a permission value: 2 and 3 restricted, 3 administers, 4 bypasses.
            opLevel(server, player, 2);
            helper.assertTrue(restricted(player) && !CreativePermissions.isAdmin(player), "op level 2 is not a restricted non-admin");
            opLevel(server, player, 3);
            helper.assertTrue(restricted(player) && CreativePermissions.isAdmin(player), "op level 3 is not a restricted admin");
            opLevel(server, player, 4);
            helper.assertTrue(!restricted(player), "op level 4 did not bypass at bypass_op_level 4");

            // An explicit value always beats the op level, in both directions.
            TestPermissions.set(player, CreativePermissions.BYPASS, false);
            helper.assertTrue(restricted(player), "an operator denied the bypass node was not restricted");
            opLevel(server, player, 0);
            TestPermissions.set(player, CreativePermissions.BYPASS, true);
            helper.assertTrue(!restricted(player), "a player granted the bypass node without op was restricted");
            TestPermissions.clear(player);

            // Administering is not bypassing.
            TestPermissions.set(player, CreativePermissions.ADMIN, true);
            helper.assertTrue(CreativePermissions.isAdmin(player) && restricted(player),
                    "the admin node alone lifted the restriction or did not grant administration");
        } finally {
            cleanUp(server, player);
        }
        helper.succeed();
    }

    /**
     * Servers configured their permission mod with the nodes named before the rename. Those must
     * keep working, and an explicit value on a current node must still win over them.
     */
    @GameTest(template = TEMPLATE, batch = "access_legacy_nodes")
    public static void nodesFromTheFormerNameStillApply(GameTestHelper helper) {
        helper.assertTrue(TestPermissions.active(), "the stand-in permission handler is not the active one");
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = restrictedPlayer(helper);
        try {
            TestPermissions.set(player, "arcadiacreativeadmin.profile", "open");
            expectProfile(helper, player, "open");

            TestPermissions.set(player, "arcadiacreativeadmin.admin", true);
            helper.assertTrue(CreativePermissions.isAdmin(player), "the former admin node no longer grants administration");

            // An operator denied the bypass under the former name must stay restricted: falling back
            // to the op level here would silently hand the bypass back.
            opLevel(server, player, 4);
            TestPermissions.set(player, "arcadiacreativeadmin.bypass", false);
            helper.assertTrue(restricted(player), "an operator denied the former bypass node was not restricted");

            // The current node outranks the former one.
            TestPermissions.set(player, CreativePermissions.BYPASS, true);
            helper.assertTrue(!restricted(player), "the current bypass node did not outrank the former one");
            TestPermissions.set(player, CreativePermissions.PROFILE, "event");
            opLevel(server, player, 0);
            TestPermissions.set(player, CreativePermissions.BYPASS, false);
            expectProfile(helper, player, "event");
        } finally {
            cleanUp(server, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Commands
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, batch = "access_commands")
    public static void commandsFollowTheAdminNode(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = restrictedPlayer(helper);
        CommandSourceStack console = server.createCommandSourceStack().withSuppressedOutput();
        String name = player.getScoreboardName();
        try {
            // The console always administers, and every subcommand runs without a player.
            helper.assertTrue(run(server, console, "creativeadmin status") == 1, "status failed from the console");
            helper.assertTrue(run(server, console, "creativeadmin reload") == 1, "reload failed from the console");
            helper.assertTrue(run(server, console, "creativeadmin tabs") >= 0, "tabs failed from the console");
            helper.assertTrue(run(server, console, "creativeadmin profile " + name + " open") == 1
                    && "open".equals(PolicyManager.assignment(player.getUUID())), "profile did not assign");
            helper.assertTrue(run(server, console, "creativeadmin profile " + name + " ghost") == 0
                    && "open".equals(PolicyManager.assignment(player.getUUID())), "an unknown profile was assigned");
            run(server, console, "creativeadmin profile " + name + " clear");
            helper.assertTrue(PolicyManager.assignment(player.getUUID()) == null, "clear left the assignment");

            // Players: the node decides, with op level 3 as its default.
            CommandSourceStack self = player.createCommandSourceStack().withSuppressedOutput();
            helper.assertTrue(refused(server, self, "creativeadmin status"), "a player without op ran /creativeadmin");
            opLevel(server, player, 2);
            helper.assertTrue(refused(server, player.createCommandSourceStack().withSuppressedOutput(),
                    "creativeadmin status"), "op level 2 ran /creativeadmin");
            opLevel(server, player, 3);
            helper.assertTrue(!refused(server, player.createCommandSourceStack().withSuppressedOutput(),
                    "creativeadmin status"), "op level 3 was refused /creativeadmin");
            opLevel(server, player, 4);
            TestPermissions.set(player, CreativePermissions.ADMIN, false);
            helper.assertTrue(refused(server, player.createCommandSourceStack().withSuppressedOutput(),
                    "creativeadmin status"), "an operator denied the admin node ran /creativeadmin");
            opLevel(server, player, 0);
            TestPermissions.set(player, CreativePermissions.ADMIN, true);
            CommandSourceStack admin = player.createCommandSourceStack().withSuppressedOutput();
            helper.assertTrue(!refused(server, admin, "creativeadmin status"), "the admin node without op was refused");

            // check judges the held item against a named profile.
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
            helper.assertTrue(run(server, admin, "creativeadmin check event") == 0, "check allowed a locked item");
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TORCH));
            helper.assertTrue(run(server, admin, "creativeadmin check event") == 1, "check refused an open item");
        } finally {
            cleanUp(server, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Files: first start, broken files, persistence
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, batch = "access_files")
    public static void filesFailClosedAndPersist(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = restrictedPlayer(helper);
        try {
            // First start: a disabled sample, nothing enforced.
            delete(policyFile());
            delete(assignmentsFile());
            PolicyManager.load(true);
            helper.assertTrue(Files.isRegularFile(policyFile()), "no sample policy was written on first start");
            helper.assertTrue(!PolicyManager.enforced() && !PolicyManager.policyBroken() && PolicyManager.hasProfile("event"),
                    "the sample policy is not a disabled one with an example profile");
            helper.assertTrue(!restricted(player), "the sample policy restricted a player");

            // An unknown key: every restricted player gets nothing, and saving is refused.
            write(policyFile(), POLICY.replace("\"enforced\": true,", "\"enforced\": true, \"oops\": 1,"));
            PolicyLifecycle.reload(server, false);
            helper.assertTrue(PolicyManager.policyBroken(), "a policy with an unknown key was accepted");
            expectTake(helper, player, Items.TORCH, false, "a broken policy let an item through");
            helper.assertTrue(PolicyManager.save(PolicyManager.document(), PolicyManager.revision())
                    == PolicyManager.SaveResult.BROKEN_FILE, "the screen could overwrite the file to repair");

            write(policyFile(), POLICY);
            PolicyLifecycle.reload(server, false);
            helper.assertTrue(!PolicyManager.policyBroken(), "a repaired policy stayed broken");
            expectTake(helper, player, Items.TORCH, true, "a repaired policy still refused an open item");

            // A file removed after start is not a fresh install.
            delete(policyFile());
            PolicyLifecycle.reload(server, false);
            helper.assertTrue(PolicyManager.policyBroken(), "a policy removed before a reload was read as a fresh install");
            write(policyFile(), POLICY);
            PolicyLifecycle.reload(server, false);

            // What the screen saves is what the next start reads.
            PolicyDocument edited = new PolicyDocument(true, "open", 4, PolicyManager.profiles());
            helper.assertTrue(PolicyManager.save(edited, PolicyManager.revision()) == PolicyManager.SaveResult.SAVED,
                    "a valid save was refused");
            PolicyLifecycle.reload(server, false);
            helper.assertTrue("open".equals(PolicyManager.defaultProfile()), "a saved default profile did not survive a reload");
            PolicyManager.assign(player.getUUID(), "event");
            PolicyLifecycle.reload(server, false);
            helper.assertTrue("event".equals(PolicyManager.assignment(player.getUUID())), "an assignment did not survive a reload");

            // An unreadable assignment file: nobody restricted can be placed, nothing is written over it.
            write(assignmentsFile(), "{ not json");
            PolicyLifecycle.reload(server, false);
            helper.assertTrue(!PolicyManager.assignmentsWritable(), "a broken assignment file was accepted");
            expectTake(helper, player, Items.STONE, false, "a broken assignment file fell back to the wider default");
            delete(assignmentsFile());
            PolicyLifecycle.reload(server, false);
            helper.assertTrue(PolicyManager.assignmentsWritable(), "assignments stayed disabled once the file was gone");
        } finally {
            cleanUp(server, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Creation paths other than the creative inventory
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE, batch = "access_paths")
    public static void cloneAndSavedHotbarAreJudged(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerPlayer player = restrictedPlayer(helper);
        try {
            BlockPos relative = new BlockPos(1, 1, 1);
            helper.setBlock(relative, Blocks.CHEST);
            BlockPos absolute = helper.absolutePos(relative);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absolute);
            helper.assertTrue(chest != null, "chest block entity missing");
            chest.setItem(0, new ItemStack(Items.TNT));
            chest.setItem(1, new ItemStack(Items.TORCH));
            player.moveTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5);
            player.openMenu(chest);
            AbstractContainerMenu menu = player.containerMenu;
            helper.assertTrue(menu != player.inventoryMenu, "the chest did not open");

            // Middle-click in an open container copies a full stack without a creative slot packet.
            cloneSlot(player, menu, 0);
            helper.assertTrue(menu.getCarried().isEmpty(), "a locked item was cloned out of a container");
            cloneSlot(player, menu, 1);
            helper.assertTrue(menu.getCarried().is(Items.TORCH), "an open item could not be cloned");
            menu.setCarried(ItemStack.EMPTY);
            player.closeContainer();

            // A saved hotbar is replayed as creative slot packets, components included.
            ItemStack named = new ItemStack(Items.TORCH);
            named.set(DataComponents.CUSTOM_NAME, Component.literal("Lamp"));
            send(player, 36, named);
            helper.assertTrue(slot(player, 36).is(Items.TORCH), "a renamed open item was refused");
            ItemStack forged = new ItemStack(Items.TORCH);
            forged.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                    .add(Attributes.ATTACK_DAMAGE, new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath("test", "forged"), 50.0,
                            AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
                    .build());
            send(player, 37, forged);
            helper.assertTrue(slot(player, 37).isEmpty(), "an open item with forged attributes was accepted");
        } finally {
            cleanUp(server, player);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A creative player with no op level, under {@link #POLICY}. */
    private static ServerPlayer restrictedPlayer(GameTestHelper helper) {
        write(policyFile(), POLICY);
        delete(assignmentsFile());
        PolicyLifecycle.reload(helper.getLevel().getServer(), false);
        helper.assertTrue(PolicyManager.enforced() && !PolicyManager.policyBroken(), "the test policy did not load");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.CREATIVE);
        return player;
    }

    private static void cleanUp(MinecraftServer server, ServerPlayer player) {
        TestPermissions.clear(player);
        opLevel(server, player, 0);
        server.getPlayerList().remove(player);
        delete(assignmentsFile());
        write(policyFile(), "{ \"enforced\": false }");
        PolicyLifecycle.reload(server, false);
    }

    private static boolean restricted(ServerPlayer player) {
        return PolicyManager.profileFor(player) != null;
    }

    private static void expectProfile(GameTestHelper helper, ServerPlayer player, String name) {
        CreativeProfile profile = PolicyManager.profileFor(player);
        helper.assertTrue(profile != null && name.equals(profile.name()),
                "expected profile '" + name + "', got " + (profile == null ? "none" : "'" + profile.name() + "'"));
    }

    /** Takes one item from the creative menu into a free hotbar slot, then empties the inventory. */
    private static void expectTake(GameTestHelper helper, ServerPlayer player,
                                   net.minecraft.world.item.Item item, boolean allowed, String message) {
        player.getInventory().clearContent();
        send(player, 36, new ItemStack(item));
        helper.assertTrue(slot(player, 36).is(item) == allowed, message);
        player.getInventory().clearContent();
    }

    private static void opLevel(MinecraftServer server, ServerPlayer player, int level) {
        server.getPlayerList().getOps().remove(player.getGameProfile());
        if (level > 0) {
            server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), level, false));
        }
    }

    private static int run(MinecraftServer server, CommandSourceStack source, String command) {
        CommandDispatcher<CommandSourceStack> dispatcher = server.getCommands().getDispatcher();
        try {
            return dispatcher.execute(command, source);
        } catch (CommandSyntaxException e) {
            throw new IllegalStateException("'" + command + "' was refused: " + e.getMessage(), e);
        }
    }

    /** Whether the command tree hides the command from this source, which is how a requirement refuses. */
    private static boolean refused(MinecraftServer server, CommandSourceStack source, String command) {
        try {
            server.getCommands().getDispatcher().execute(command, source);
            return false;
        } catch (CommandSyntaxException e) {
            return true;
        }
    }

    private static void cloneSlot(ServerPlayer player, AbstractContainerMenu menu, int slot) {
        player.connection.handleContainerClick(new ServerboundContainerClickPacket(menu.containerId,
                menu.getStateId(), slot, 2, ClickType.CLONE, ItemStack.EMPTY, new Int2ObjectOpenHashMap<>()));
    }

    private static void send(ServerPlayer player, int slot, ItemStack stack) {
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(slot, stack));
    }

    private static ItemStack slot(ServerPlayer player, int index) {
        return player.inventoryMenu.getSlot(index).getItem();
    }

    private static Path policyFile() {
        return FMLPaths.CONFIGDIR.get().resolve(ConfigPaths.FOLDER).resolve(ConfigPaths.POLICY);
    }

    private static Path assignmentsFile() {
        return FMLPaths.CONFIGDIR.get().resolve(ConfigPaths.FOLDER).resolve(ConfigPaths.ASSIGNMENTS);
    }

    private static void write(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write " + file, e);
        }
    }

    private static void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete " + file, e);
        }
    }
}
