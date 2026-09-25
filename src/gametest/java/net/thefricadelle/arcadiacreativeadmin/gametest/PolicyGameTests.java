/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;
import net.thefricadelle.arcadiacreativeadmin.PolicyLifecycle;
import net.thefricadelle.arcadiacreativeadmin.policy.CreativeProfile;
import net.thefricadelle.arcadiacreativeadmin.policy.Decision;
import net.thefricadelle.arcadiacreativeadmin.policy.PolicyEvaluator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Regression tests for the ways an allowed item id used to smuggle something else through, and for
 * the enforcement hooks themselves.
 * <p>
 * Run with {@code ./gradlew runGameTestServer}. The GameTest server is not a dedicated server, so
 * like an integrated one it never builds creative tab contents on its own; {@link #tabsBuilt} does
 * it here, which has no client to disturb.
 *
 * @author THEFricadelle
 */
@GameTestHolder(ArcadiaCreativeAdmin.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyGameTests {

    private static final String TEMPLATE = "empty";

    private PolicyGameTests() {}

    // ------------------------------------------------------------------
    // Evaluator
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void itemRuleAllowsOnlyThatItem(GameTestHelper helper) {
        CreativeProfile profile = profile(Set.of(id("torch")), Set.of(), Set.of(), false, false, Set.of());
        allowed(helper, profile, new ItemStack(Items.TORCH));
        denied(helper, profile, new ItemStack(Items.DIRT));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void denialWinsOverNamespace(GameTestHelper helper) {
        CreativeProfile profile = new CreativeProfile("test", Set.of(), Set.of("minecraft"), Set.of(), Set.of(),
                Set.of(id("command_block")), Set.of(), false, false, Set.of());
        allowed(helper, profile, new ItemStack(Items.STONE));
        denied(helper, profile, new ItemStack(Items.COMMAND_BLOCK));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tagAndTabRulesMatch(GameTestHelper helper) {
        tabsBuilt(helper);
        CreativeProfile profile = new CreativeProfile("test", Set.of(), Set.of(), Set.of(id("beds")),
                Set.of(id("building_blocks")), Set.of(), Set.of(), false, false, Set.of());
        allowed(helper, profile, new ItemStack(Items.RED_BED));
        allowed(helper, profile, new ItemStack(Items.STONE_BRICKS));
        denied(helper, profile, new ItemStack(Items.DIAMOND_SWORD));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void entityDataCannotSmuggleItems(GameTestHelper helper) {
        CreativeProfile profile = everything();
        allowed(helper, profile, new ItemStack(Items.PIG_SPAWN_EGG));

        CompoundTag inner = new CompoundTag();
        inner.putString("id", "minecraft:command_block");
        inner.putInt("count", 1);
        CompoundTag entity = new CompoundTag();
        entity.putString("id", "minecraft:item");
        entity.put("Item", inner);

        ItemStack egg = new ItemStack(Items.PIG_SPAWN_EGG);
        egg.set(DataComponents.ENTITY_DATA, CustomData.of(entity));
        denied(helper, profile, egg);

        CompoundTag framed = new CompoundTag();
        framed.putString("id", "minecraft:item_frame");
        framed.put("Item", inner.copy());
        ItemStack frame = new ItemStack(Items.ITEM_FRAME);
        frame.set(DataComponents.ENTITY_DATA, CustomData.of(framed));
        denied(helper, profile, frame);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void lootTableOnChestIsRefused(GameTestHelper helper) {
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(
                ResourceKey.create(Registries.LOOT_TABLE, id("chests/end_city_treasure")), 0L));
        denied(helper, everything(), chest);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void combatComponentsNeedTheProfile(GameTestHelper helper) {
        ItemStack stick = new ItemStack(Items.STICK);
        stick.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(ResourceLocation.fromNamespaceAndPath("test", "boost"), 100.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build());
        denied(helper, everything(), stick);

        CreativeProfile opened = new CreativeProfile("test", Set.of(), Set.of("minecraft"), Set.of(), Set.of(),
                Set.of(), Set.of(), false, false, Set.of(id("attribute_modifiers")));
        allowed(helper, opened, stick);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void harmlessComponentsPass(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.TORCH);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Lamp"));
        allowed(helper, everything(), named);

        ItemStack worn = new ItemStack(Items.IRON_PICKAXE);
        worn.set(DataComponents.DAMAGE, 10);
        allowed(helper, everything(), worn);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void creativeVariantPassesButCustomEffectsDoNot(GameTestHelper helper) {
        tabsBuilt(helper);
        allowed(helper, everything(), PotionContents.createItemStack(Items.POTION, Potions.STRONG_HEALING));

        ItemStack forged = new ItemStack(Items.POTION);
        forged.set(DataComponents.POTION_CONTENTS, new PotionContents(Optional.empty(), Optional.empty(),
                List.of(new MobEffectInstance(MobEffects.HARM, 1, 125))));
        denied(helper, everything(), forged);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void signedBooksPassButCommandBooksDoNot(GameTestHelper helper) {
        ItemStack plain = book(Component.literal("Rules of the event"));
        allowed(helper, everything(), plain);

        ItemStack trap = book(Component.literal("Click me").withStyle(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/op someone"))));
        denied(helper, everything(), trap);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void containerContentsFollowTheFlag(GameTestHelper helper) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
        denied(helper, everything(), box);

        CreativeProfile contents = profile(Set.of(id("shulker_box"), id("stone")), Set.of(), Set.of(),
                false, true, Set.of());
        allowed(helper, contents, box);

        ItemStack smuggler = new ItemStack(Items.SHULKER_BOX);
        smuggler.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(new ItemStack(Items.COMMAND_BLOCK))));
        denied(helper, contents, smuggler);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blockEntityDataIsRefusedByDefault(GameTestHelper helper) {
        CompoundTag data = new CompoundTag();
        data.putString("id", "minecraft:chest");
        ItemStack chest = new ItemStack(Items.CHEST);
        chest.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(data));
        denied(helper, everything(), chest);
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Hooks
    // ------------------------------------------------------------------

    /**
     * Drives the real packet handler. Logging the mock player in is itself a check: it has no mod
     * channel, and sending it the advisory payload used to throw from the login event.
     */
    @GameTest(template = TEMPLATE)
    public static void creativeSlotPacketIsJudgedOnTheFinalStack(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        writePolicy("""
                {
                  "enforced": true,
                  "default_profile": "event",
                  "profiles": {
                    "event": {
                      "items": ["minecraft:chest"],
                      "allow_block_entity_data": true,
                      "allow_container_contents": false
                    }
                  }
                }
                """);
        PolicyLifecycle.reload(server, false);

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.CREATIVE);

            BlockPos relative = new BlockPos(1, 1, 1);
            helper.setBlock(relative, Blocks.CHEST);
            BlockPos absolute = helper.absolutePos(relative);
            ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absolute);
            helper.assertTrue(chest != null, "chest block entity missing");
            chest.setItem(0, new ItemStack(Items.DIAMOND_BLOCK));

            // Vanilla replaces this data with the chest found at x/y/z, contents included, after
            // the packet arrives. Judging the stack as sent would let the diamond block through.
            CompoundTag pointer = new CompoundTag();
            pointer.putString("id", "minecraft:chest");
            pointer.putInt("x", absolute.getX());
            pointer.putInt("y", absolute.getY());
            pointer.putInt("z", absolute.getZ());
            ItemStack probe = new ItemStack(Items.CHEST);
            probe.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(pointer));
            send(player, 36, probe);
            helper.assertTrue(player.inventoryMenu.getSlot(36).getItem().isEmpty(),
                    "a chest copied from the world with its contents was accepted");

            send(player, 36, new ItemStack(Items.CHEST));
            helper.assertTrue(player.inventoryMenu.getSlot(36).getItem().is(Items.CHEST),
                    "an allowed plain chest was refused");

            send(player, 37, new ItemStack(Items.DIAMOND_BLOCK));
            helper.assertTrue(player.inventoryMenu.getSlot(37).getItem().isEmpty(),
                    "a forbidden item reached an inventory slot");

            send(player, -1, new ItemStack(Items.DIAMOND_BLOCK));
            boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    player.getBoundingBox().inflate(4.0), entity -> entity.getItem().is(Items.DIAMOND_BLOCK)).isEmpty();
            helper.assertTrue(!dropped, "a forbidden item was dropped through the slotNum < 0 path");
        } finally {
            server.getPlayerList().remove(player);
            writePolicy("{ \"enforced\": false }");
            PolicyLifecycle.reload(server, false);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void send(ServerPlayer player, int slot, ItemStack stack) {
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(slot, stack));
    }

    private static void tabsBuilt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
        PolicyLifecycle.refreshTabIndexIfStale(server);
    }

    private static CreativeProfile everything() {
        return new CreativeProfile("test", Set.of(), Set.of("minecraft"), Set.of(), Set.of(),
                Set.of(), Set.of(), false, false, Set.of());
    }

    private static CreativeProfile profile(Set<ResourceLocation> items, Set<String> namespaces,
                                           Set<ResourceLocation> denied, boolean blockEntityData,
                                           boolean containerContents, Set<ResourceLocation> components) {
        return new CreativeProfile("test", items, namespaces, Set.of(), Set.of(), denied, Set.of(),
                blockEntityData, containerContents, components);
    }

    private static ItemStack book(Component page) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Book"), "Author", 0, List.of(Filterable.passThrough(page)), true));
        return stack;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.withDefaultNamespace(path);
    }

    private static void allowed(GameTestHelper helper, CreativeProfile profile, ItemStack stack) {
        Decision decision = PolicyEvaluator.evaluate(profile, stack);
        helper.assertTrue(decision.allowed(), "expected " + stack + " to be allowed, refused with "
                + (decision.reason() == null ? "no reason" : decision.reason().getString()));
    }

    private static void denied(GameTestHelper helper, CreativeProfile profile, ItemStack stack) {
        helper.assertTrue(!PolicyEvaluator.evaluate(profile, stack).allowed(), "expected " + stack + " to be refused");
    }

    private static void writePolicy(String json) {
        Path file = FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("arcadia-creative-admin-policy.json");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the test policy", e);
        }
    }
}
