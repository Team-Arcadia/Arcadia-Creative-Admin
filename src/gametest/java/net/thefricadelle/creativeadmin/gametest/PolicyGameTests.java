/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.gametest;

import net.thefricadelle.creativeadmin.policy.ConfigPaths;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.neoforged.neoforge.network.connection.ConnectionType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.thefricadelle.creativeadmin.CreativeAdmin;
import net.thefricadelle.creativeadmin.PolicyLifecycle;
import net.thefricadelle.creativeadmin.network.NetCodecs;
import net.thefricadelle.creativeadmin.policy.CreativeProfile;
import net.thefricadelle.creativeadmin.policy.Decision;
import net.thefricadelle.creativeadmin.policy.PolicyCodec;
import net.thefricadelle.creativeadmin.policy.PolicyDocument;
import net.thefricadelle.creativeadmin.policy.PolicyEvaluator;
import net.thefricadelle.creativeadmin.policy.PolicyManager;

import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Regression tests for the evaluation rules, the ways an open item id used to smuggle something
 * else through, the ledger that keeps owned items untouched, the file and wire formats, and the
 * enforcement hooks themselves.
 * <p>
 * Run with {@code ./gradlew runGameTestServer}. The GameTest server is not a dedicated server, so
 * like an integrated one it never builds creative tab contents on its own; {@link #tabsBuilt} does
 * it here, which has no client to disturb.
 * <p>
 * Tests touching the policy files share global state, so they live in one sequential test.
 *
 * @author THEFricadelle
 */
@GameTestHolder(CreativeAdmin.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PolicyGameTests {

    private static final String TEMPLATE = "empty";

    private PolicyGameTests() {}

    // ------------------------------------------------------------------
    // Modes and selection
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void whitelistOpensOnlyTheSelection(GameTestHelper helper) {
        CreativeProfile profile = whitelist().items("torch").build();
        allowed(helper, profile, new ItemStack(Items.TORCH));
        denied(helper, profile, new ItemStack(Items.DIRT));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void blacklistLocksOnlyTheSelection(GameTestHelper helper) {
        tabsBuilt(helper);
        CreativeProfile profile = blacklist().tabs("redstone_blocks").items("tnt").exceptions("lever").build();
        allowed(helper, profile, new ItemStack(Items.STONE));
        denied(helper, profile, new ItemStack(Items.TNT));
        denied(helper, profile, new ItemStack(Items.REPEATER));
        // An exception carves one item out of a locked page.
        allowed(helper, profile, new ItemStack(Items.LEVER));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void singleItemOutranksGroups(GameTestHelper helper) {
        // Whitelist: the mod is open, the exception locks one item of it.
        CreativeProfile trimmed = whitelist().namespaces("minecraft").exceptions("command_block").build();
        allowed(helper, trimmed, new ItemStack(Items.STONE));
        denied(helper, trimmed, new ItemStack(Items.COMMAND_BLOCK));
        // An item listed on its own wins even over an exception naming it.
        CreativeProfile both = whitelist().items("torch").exceptions("torch").build();
        allowed(helper, both, new ItemStack(Items.TORCH));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void tagAndTabRulesMatch(GameTestHelper helper) {
        tabsBuilt(helper);
        CreativeProfile profile = whitelist().tags("beds").tabs("building_blocks").build();
        allowed(helper, profile, new ItemStack(Items.RED_BED));
        allowed(helper, profile, new ItemStack(Items.STONE_BRICKS));
        denied(helper, profile, new ItemStack(Items.DIAMOND_SWORD));
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Components
    // ------------------------------------------------------------------

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
        allowed(helper, whitelist().namespaces("minecraft").components("attribute_modifiers").build(), stick);
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
        allowed(helper, everything(), book(Component.literal("Rules of the event")));
        denied(helper, everything(), book(Component.literal("Click me").withStyle(style ->
                style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/op someone")))));
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void containerContentsFollowTheFlag(GameTestHelper helper) {
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.STONE))));
        denied(helper, everything(), box);

        CreativeProfile contents = whitelist().items("shulker_box", "stone").containerContents().build();
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
    // Formats
    // ------------------------------------------------------------------

    @GameTest(template = TEMPLATE)
    public static void policyFileAndWireFormatsRoundTrip(GameTestHelper helper) {
        Map<String, CreativeProfile> profiles = new LinkedHashMap<>();
        profiles.put("event", blacklist().name("event").tabs("redstone_blocks").namespaces("create")
                .tags("beds").items("tnt").exceptions("lever").components("enchantments").containerContents().build());
        profiles.put("builders", whitelist().name("builders").tabs("building_blocks").build());
        PolicyDocument document = new PolicyDocument(true, "event", 3, profiles);

        PolicyDocument fromFile = PolicyCodec.read(JsonParser.parseString(PolicyCodec.write(document).toString())
                .getAsJsonObject());
        helper.assertTrue(document.equals(fromFile), "the policy file does not round-trip: " + fromFile);

        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().getServer().registryAccess(), ConnectionType.NEOFORGE);
        NetCodecs.DOCUMENT.encode(buf, document);
        PolicyDocument fromWire = NetCodecs.DOCUMENT.decode(buf);
        helper.assertTrue(document.equals(fromWire), "the wire format does not round-trip: " + fromWire);

        boolean rejected;
        try {
            PolicyCodec.read(JsonParser.parseString("{\"enforced\": true, \"denied_items\": []}").getAsJsonObject());
            rejected = false;
        } catch (IllegalArgumentException e) {
            rejected = true;
        }
        helper.assertTrue(rejected, "an unknown key was accepted instead of refusing the file");
        helper.succeed();
    }

    // ------------------------------------------------------------------
    // Hooks and files, sequential because they share global state
    // ------------------------------------------------------------------

    /**
     * Drives the real packet handler. Logging the mock player in is itself a check: it has no mod
     * channel, and sending it the advisory payload used to throw from the login event.
     */
    @GameTest(template = TEMPLATE)
    public static void enforcementHooksAndPolicyFiles(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        writePolicy("""
                {
                  "enforced": true,
                  "default_profile": "event",
                  "profiles": {
                    "event": {
                      "mode": "whitelist",
                      "items": ["minecraft:chest"],
                      "allow_block_entity_data": true,
                      "allow_container_contents": false
                    }
                  }
                }
                """);
        PolicyLifecycle.reload(server, false);
        helper.assertTrue(PolicyManager.enforced() && !PolicyManager.policyBroken(), "the test policy did not load");

        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        try {
            player.setGameMode(GameType.CREATIVE);
            finalStackIsJudged(helper, player);
            ownedItemsMoveFreely(helper, player);
            concurrentSaveIsRefused(helper);
        } finally {
            server.getPlayerList().remove(player);
            writePolicy("{ \"enforced\": false }");
            PolicyLifecycle.reload(server, false);
        }
        helper.succeed();
    }

    private static void finalStackIsJudged(GameTestHelper helper, ServerPlayer player) {
        BlockPos relative = new BlockPos(1, 1, 1);
        helper.setBlock(relative, Blocks.CHEST);
        BlockPos absolute = helper.absolutePos(relative);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(absolute);
        helper.assertTrue(chest != null, "chest block entity missing");
        chest.setItem(0, new ItemStack(Items.DIAMOND_BLOCK));

        // Vanilla replaces this data with the chest found at x/y/z, contents included, after the
        // packet arrives. Judging the stack as sent would let the diamond block through.
        CompoundTag pointer = new CompoundTag();
        pointer.putString("id", "minecraft:chest");
        pointer.putInt("x", absolute.getX());
        pointer.putInt("y", absolute.getY());
        pointer.putInt("z", absolute.getZ());
        ItemStack probe = new ItemStack(Items.CHEST);
        probe.set(DataComponents.BLOCK_ENTITY_DATA, CustomData.of(pointer));
        send(player, 36, probe);
        helper.assertTrue(slot(player, 36).isEmpty(), "a chest copied from the world with its contents was accepted");

        send(player, 36, new ItemStack(Items.CHEST));
        helper.assertTrue(slot(player, 36).is(Items.CHEST), "an open plain chest was refused");

        send(player, 37, new ItemStack(Items.DIAMOND_BLOCK));
        helper.assertTrue(slot(player, 37).isEmpty(), "a locked item reached an inventory slot");

        send(player, -1, new ItemStack(Items.DIAMOND_BLOCK));
        boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(4.0), entity -> entity.getItem().is(Items.DIAMOND_BLOCK)).isEmpty();
        helper.assertTrue(!dropped, "a locked item was dropped through the slotNum < 0 path");
    }

    /**
     * A locked item the player already owns can be moved around, but moving it never leaves more
     * than there was: the creative protocol sends a move as an emptied slot then a filled one.
     */
    private static void ownedItemsMoveFreely(GameTestHelper helper, ServerPlayer player) {
        player.inventoryMenu.getSlot(38).set(new ItemStack(Items.DIAMOND_BLOCK, 5));

        send(player, 38, ItemStack.EMPTY);
        send(player, 39, new ItemStack(Items.DIAMOND_BLOCK, 5));
        helper.assertTrue(slot(player, 39).getCount() == 5, "moving an owned locked stack destroyed it");

        send(player, 39, new ItemStack(Items.DIAMOND_BLOCK, 2));
        send(player, 40, new ItemStack(Items.DIAMOND_BLOCK, 3));
        helper.assertTrue(slot(player, 40).getCount() == 3, "splitting an owned locked stack was refused");

        send(player, 41, new ItemStack(Items.DIAMOND_BLOCK, 1));
        helper.assertTrue(slot(player, 41).isEmpty(), "a moved stack could be placed twice");

        send(player, 40, new ItemStack(Items.DIAMOND_BLOCK, 64));
        helper.assertTrue(slot(player, 40).getCount() == 3, "an owned locked stack could be grown");
    }

    private static void concurrentSaveIsRefused(GameTestHelper helper) {
        int revision = PolicyManager.revision();
        PolicyDocument edited = new PolicyDocument(true, "event", 4, PolicyManager.profiles());
        helper.assertTrue(PolicyManager.save(edited, revision - 1) == PolicyManager.SaveResult.CONFLICT,
                "a save based on an older revision was accepted");
        helper.assertTrue(PolicyManager.save(edited, revision) == PolicyManager.SaveResult.SAVED,
                "a save based on the current revision was refused");
        PolicyDocument invalid = new PolicyDocument(true, "missing", 4, PolicyManager.profiles());
        helper.assertTrue(PolicyManager.save(invalid, PolicyManager.revision()) == PolicyManager.SaveResult.INVALID,
                "a default profile that does not exist was accepted");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static ItemStack slot(ServerPlayer player, int index) {
        return player.inventoryMenu.getSlot(index).getItem();
    }

    private static void send(ServerPlayer player, int slot, ItemStack stack) {
        player.connection.handleSetCreativeModeSlot(new ServerboundSetCreativeModeSlotPacket(slot, stack));
    }

    private static void tabsBuilt(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
        PolicyLifecycle.refreshTabIndexIfStale(server);
    }

    private static CreativeProfile everything() {
        return whitelist().namespaces("minecraft").build();
    }

    private static Builder whitelist() {
        return new Builder(CreativeProfile.Mode.WHITELIST);
    }

    private static Builder blacklist() {
        return new Builder(CreativeProfile.Mode.BLACKLIST);
    }

    /** Profiles for tests, with vanilla ids written without their namespace. */
    private static final class Builder {
        private String name = "test";
        private final CreativeProfile.Mode mode;
        private final Set<ResourceLocation> tabs = new HashSet<>();
        private final Set<String> namespaces = new HashSet<>();
        private final Set<ResourceLocation> tags = new HashSet<>();
        private final Set<ResourceLocation> items = new HashSet<>();
        private final Set<ResourceLocation> exceptions = new HashSet<>();
        private final Set<ResourceLocation> components = new HashSet<>();
        private boolean containerContents;

        Builder(CreativeProfile.Mode mode) {
            this.mode = mode;
        }

        Builder name(String value) {
            name = value;
            return this;
        }

        Builder tabs(String... values) {
            for (String value : values) tabs.add(id(value));
            return this;
        }

        Builder namespaces(String... values) {
            namespaces.addAll(List.of(values));
            return this;
        }

        Builder tags(String... values) {
            for (String value : values) tags.add(id(value));
            return this;
        }

        Builder items(String... values) {
            for (String value : values) items.add(id(value));
            return this;
        }

        Builder exceptions(String... values) {
            for (String value : values) exceptions.add(id(value));
            return this;
        }

        Builder components(String... values) {
            for (String value : values) components.add(id(value));
            return this;
        }

        Builder containerContents() {
            containerContents = true;
            return this;
        }

        CreativeProfile build() {
            return new CreativeProfile(name, mode, tabs, namespaces, tags, items, exceptions,
                    false, containerContents, components);
        }
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
        Path file = FMLPaths.CONFIGDIR.get().resolve(ConfigPaths.FOLDER).resolve(ConfigPaths.POLICY);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not write the test policy", e);
        }
    }
}
