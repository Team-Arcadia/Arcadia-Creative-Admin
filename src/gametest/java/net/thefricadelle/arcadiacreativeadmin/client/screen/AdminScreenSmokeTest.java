/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client.screen;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.thefricadelle.arcadiacreativeadmin.ArcadiaCreativeAdmin;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcButton;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcEditBox;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcList;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcScreen;
import net.thefricadelle.arcadiacreativeadmin.core.ReceivedAdvice;
import net.thefricadelle.arcadiacreativeadmin.network.AdminPayloads;
import net.thefricadelle.arcadiacreativeadmin.policy.CreativeProfile;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Drives the admin screen of a real client and writes a pass/fail report.
 * <p>
 * The GameTests prove the rules, the files and the permissions on a server; nothing there opens the
 * screen. This run does, in a singleplayer creative world where the host is an admin, the way an
 * admin uses it: it presses the real buttons, found by their translation key, types into the real
 * fields and waits for the integrated server's answers. It covers the flag button and
 * {@code /creativeadmin}, the profile list (invalid, reserved and taken names, create, duplicate,
 * delete behind its confirmation), the tabs and items page (a whole page, one item, the search, the
 * mode), the rules page (a mod, a tag, an unknown and a real component, both switches), saving,
 * undoing and the discard confirmation, the players page, and the read-only banner of a broken file.
 * <p>
 * Inert unless {@code -Darcadiacreativeadmin.adminSmoke=true}, which only the {@code adminSmoke}
 * run sets. The report lands in {@code run/adminsmoke/smoke-report.txt}; screenshots of every page
 * in {@code run/adminsmoke/screenshots}, for a person to look at.
 * <p>
 * In the package of the screens on purpose: it reads the session state they keep package-private,
 * and lives in the GameTest source set, so none of it ships.
 *
 * @author THEFricadelle
 */
@EventBusSubscriber(modid = ArcadiaCreativeAdmin.MOD_ID, value = Dist.CLIENT)
public final class AdminScreenSmokeTest {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final boolean ENABLED = Boolean.getBoolean("arcadiacreativeadmin.adminSmoke");
    private static final String WORLD = "admin-smoke";
    private static final int TIMEOUT_TICKS = 20 * 60 * 5;
    private static final String KEY = "arcadiacreativeadmin.admin.";
    private static final String PROFILE = "builders";
    private static final ResourceLocation BUILDING_BLOCKS = ResourceLocation.withDefaultNamespace("building_blocks");
    private static final ResourceLocation BEDS = ResourceLocation.withDefaultNamespace("beds");
    private static final ResourceLocation CUSTOM_NAME = ResourceLocation.withDefaultNamespace("custom_name");

    private static final List<String> REPORT = new ArrayList<>();
    private static final Deque<Step> STEPS = new ArrayDeque<>();

    private static boolean planned;
    private static boolean finished;
    private static int ticks;
    private static int wait;
    private static int failures;

    // Carried between steps.
    private static ResourceLocation clickedItem;
    private static String savedPolicy;

    private AdminScreenSmokeTest() {}

    private record Step(String name, int delayTicks, BooleanSupplier ready, Runnable action) {
        Step(String name, int delayTicks, Runnable action) {
            this(name, delayTicks, () -> true, action);
        }
    }

    @FunctionalInterface
    private interface Check {
        String run() throws Exception;
    }

    // ------------------------------------------------------------------ runner

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED || finished) {
            return;
        }
        if (!planned) {
            plan();
            planned = true;
        }
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            Step stuck = STEPS.peek();
            fail("runner.timeout", "stuck waiting on " + (stuck == null ? "nothing" : stuck.name()));
            finish();
            return;
        }
        if (wait > 0) {
            wait--;
            return;
        }
        Step step = STEPS.peek();
        if (step == null) {
            finish();
            return;
        }
        if (!step.ready().getAsBoolean()) {
            return;
        }
        STEPS.poll();
        LOGGER.info("[admin-smoke] {}", step.name());
        try {
            step.action().run();
        } catch (Throwable t) {
            LOGGER.error("[admin-smoke] step {} threw", step.name(), t);
            fail(step.name(), "threw " + t);
        }
        Step next = STEPS.peek();
        wait = next == null ? 0 : next.delayTicks();
    }

    private static void plan() {
        Minecraft mc = Minecraft.getInstance();
        STEPS.add(new Step("title", 0, () -> mc.getOverlay() == null && mc.screen != null && ticks > 20,
                AdminScreenSmokeTest::createWorld));
        STEPS.add(new Step("world", 40, () -> mc.player != null && mc.level != null && mc.screen == null
                && ReceivedAdvice.admin(), () -> check("world.admin", () -> "host is an admin of " + WORLD)));

        // A01: the flag button, then the command.
        STEPS.add(new Step("flag", 10, AdminScreenSmokeTest::pressFlag));
        STEPS.add(new Step("flag.opened", 0, () -> mc.screen instanceof AdminScreen,
                () -> check("open.flagButton", () -> "the flag button opens the admin screen")));
        STEPS.add(new Step("flag.close", 10, () -> mc.screen.onClose()));
        STEPS.add(new Step("command", 10, () -> mc.player.connection.sendCommand("creativeadmin")));
        STEPS.add(new Step("command.opened", 0, () -> mc.screen instanceof AdminScreen, AdminScreenSmokeTest::checkOpened));
        STEPS.add(new Step("shot.main", 10, () -> screenshot("main")));

        // A02: profiles.
        STEPS.add(new Step("profiles.names", 5, AdminScreenSmokeTest::checkRefusedNames));
        STEPS.add(new Step("profiles.create", 5, AdminScreenSmokeTest::checkCreateDuplicateDelete));
        STEPS.add(new Step("shot.delete", 5, AdminScreenSmokeTest::showDeleteDialog));
        STEPS.add(new Step("profiles.delete", 5, AdminScreenSmokeTest::confirmDelete));

        // A03: tabs and items.
        STEPS.add(new Step("items.open", 5, AdminScreenSmokeTest::openItemsPage));
        STEPS.add(new Step("items.page", 5, AdminScreenSmokeTest::checkWholePage));
        STEPS.add(new Step("items.single", 5, AdminScreenSmokeTest::checkSingleItem));
        STEPS.add(new Step("items.search", 5, AdminScreenSmokeTest::checkSearch));
        STEPS.add(new Step("shot.items", 5, () -> screenshot("items")));
        STEPS.add(new Step("items.back", 5, () -> current(ProfileScreen.class).onClose()));
        STEPS.add(new Step("mode", 5, AdminScreenSmokeTest::checkModeSwitch));

        // A04: mods, tags, components.
        STEPS.add(new Step("rules.open", 5, () -> press(current(AdminScreen.class), "edit_advanced")));
        STEPS.add(new Step("rules", 5, AdminScreenSmokeTest::checkRules));
        STEPS.add(new Step("shot.rules", 5, () -> screenshot("rules")));
        STEPS.add(new Step("rules.back", 5, () -> current(AdvancedScreen.class).onClose()));

        // A01: no button label or field hint cut, on every page, at real screen sizes, in both languages.
        STEPS.add(new Step("layout.en", 5, () -> checkLayout("layout.english")));
        STEPS.add(new Step("layout.fr.load", 5, () -> switchLanguage("fr_fr")));
        STEPS.add(new Step("layout.fr", 10, () -> languageReload == null || languageReload.isDone(),
                () -> checkLayout("layout.french")));
        STEPS.add(new Step("layout.en.load", 5, () -> switchLanguage("en_us")));
        STEPS.add(new Step("layout.en.back", 10, () -> languageReload == null || languageReload.isDone(),
                () -> Minecraft.getInstance().setScreen(new AdminScreen())));

        // A05: save, undo, discard.
        STEPS.add(new Step("save", 5, AdminScreenSmokeTest::saveSettings));
        STEPS.add(new Step("save.answer", 0, () -> !AdminSession.dirty(), AdminScreenSmokeTest::checkSaved));
        // A refused save keeps the edits. The screen cannot build an invalid document, so the refusal
        // is a conflict: the session's base revision is moved back one step before saving.
        STEPS.add(new Step("refused", 5, AdminScreenSmokeTest::saveOnOldRevision));
        STEPS.add(new Step("refused.answer", 0, () -> tr("status.conflict").equals(status(current(AdminScreen.class))),
                AdminScreenSmokeTest::checkRefusalKeepsEdits));
        STEPS.add(new Step("undo", 5, AdminScreenSmokeTest::checkUndo));
        STEPS.add(new Step("discard", 5, AdminScreenSmokeTest::checkDiscard));

        // A06: players.
        STEPS.add(new Step("players.command", 10, () -> mc.player.connection.sendCommand("creativeadmin")));
        // With an unsaved edit pending, so the assignment's answer must not read as someone else's save.
        STEPS.add(new Step("players.open", 0, () -> mc.screen instanceof AdminScreen, () -> {
            press(current(AdminScreen.class), "enforced");
            press(current(AdminScreen.class), "players");
        }));
        STEPS.add(new Step("players.click", 5, AdminScreenSmokeTest::cyclePlayer));
        STEPS.add(new Step("players.assigned", 0, () -> !hostAssignment().isEmpty(), AdminScreenSmokeTest::checkAssigned));
        STEPS.add(new Step("shot.players", 5, () -> screenshot("players")));
        // Each click reads the assignment shown, so the next one waits for the server's answer.
        STEPS.add(new Step("players.click2", 5, AdminScreenSmokeTest::cyclePlayer));
        STEPS.add(new Step("players.click3", 5, () -> hostAssignment().equals(PROFILE), AdminScreenSmokeTest::cyclePlayer));
        STEPS.add(new Step("players.cleared", 0, () -> hostAssignment().isEmpty(),
                () -> check("players.backToNone", () -> "the cycle returns to no assignment")));
        STEPS.add(new Step("players.close", 5, () -> {
            mc.screen.onClose();
            press(current(AdminScreen.class), "revert");
            mc.screen.onClose();
        }));

        // A08: a broken file.
        STEPS.add(new Step("broken.write", 10, AdminScreenSmokeTest::breakPolicy));
        STEPS.add(new Step("broken.open", 20, () -> mc.player.connection.sendCommand("creativeadmin")));
        STEPS.add(new Step("broken.opened", 0, () -> mc.screen instanceof AdminScreen && AdminSession.readOnly(),
                AdminScreenSmokeTest::checkReadOnly));
        STEPS.add(new Step("shot.broken", 5, () -> screenshot("broken")));
        STEPS.add(new Step("broken.restore", 5, AdminScreenSmokeTest::restorePolicy));
        STEPS.add(new Step("broken.reopen", 20, () -> mc.player.connection.sendCommand("creativeadmin")));
        STEPS.add(new Step("broken.fixed", 0, () -> mc.screen instanceof AdminScreen && !AdminSession.readOnly(),
                () -> check("broken.editableAgain", () -> "the screen is editable once the file is restored")));
        STEPS.add(new Step("end", 10, () -> mc.screen.onClose()));
    }

    // ------------------------------------------------------------------ world

    private static void createWorld() {
        Minecraft mc = Minecraft.getInstance();
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(0L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                mc.screen);
    }

    // ------------------------------------------------------------------ A01

    private static void pressFlag() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        CreativeModeInventoryScreen creative = new CreativeModeInventoryScreen(player,
                player.connection.enabledFeatures(), mc.options.operatorItemsTab().get());
        mc.setScreen(creative);
        AbstractButton flag = null;
        for (GuiEventListener child : creative.children()) {
            if (child instanceof AbstractButton button && "⚑".equals(button.getMessage().getString())) {
                flag = button;
            }
        }
        AbstractButton found = flag;
        check("open.flagPresent", () -> {
            require(found != null, "no flag button in the creative inventory of an admin");
            require(found.getTooltip() != null, "the flag button has no tooltip");
            return "flag button with its tooltip";
        });
        if (found != null) {
            found.onPress();
        }
    }

    private static void checkOpened() {
        check("open.command", () -> {
            require(AdminSession.profileNames().contains("event"), "the sample profile is missing: " + AdminSession.profileNames());
            return "/creativeadmin opens it, sample profile listed";
        });
    }

    // ------------------------------------------------------------------ A02

    private static void checkRefusedNames() {
        AdminScreen screen = current(AdminScreen.class);
        check("profiles.invalidName", () -> expectRefusal(screen, "Bad Name", "name_invalid"));
        check("profiles.reservedName", () -> expectRefusal(screen, "clear", "name_reserved"));
        check("profiles.takenName", () -> expectRefusal(screen, "event", "name_taken"));
    }

    private static String expectRefusal(AdminScreen screen, String name, String key) {
        List<String> before = AdminSession.profileNames();
        type(screen, 0, name);
        press(screen, "create");
        String status = status(screen);
        require(tr(key).equals(status), "'" + name + "' gave status '" + status + "', expected '" + tr(key) + "'");
        require(AdminSession.profileNames().equals(before), "'" + name + "' changed the profile list");
        return "'" + name + "' refused: " + status;
    }

    private static void checkCreateDuplicateDelete() {
        AdminScreen screen = current(AdminScreen.class);
        check("profiles.create", () -> {
            type(screen, 0, PROFILE);
            press(screen, "create");
            require(AdminSession.profileNames().contains(PROFILE), "no profile " + PROFILE + " after create");
            require(AdminSession.dirty(), "creating a profile did not mark the session unsaved");
            return PROFILE + " created and selected";
        });
        check("profiles.duplicate", () -> {
            press(screen, "duplicate");
            require(AdminSession.profileNames().contains(PROFILE + "_copy"), "no copy after duplicate");
            return PROFILE + "_copy created";
        });
    }

    private static void showDeleteDialog() {
        AdminScreen screen = current(AdminScreen.class);
        press(screen, "delete");
        check("profiles.deleteAsks", () -> {
            require(dialogOpen(screen), "delete did not ask first");
            require(AdminSession.profileNames().contains(PROFILE + "_copy"), "the copy was deleted before confirming");
            return "delete asks before removing";
        });
        screenshot("delete-confirm");
    }

    private static void confirmDelete() {
        AdminScreen screen = current(AdminScreen.class);
        check("profiles.deleted", () -> {
            dialogButton(screen, true).onPress();
            require(!AdminSession.profileNames().contains(PROFILE + "_copy"), "the copy is still listed after confirming");
            return "confirmed delete removes the copy";
        });
    }

    // ------------------------------------------------------------------ A03

    private static void openItemsPage() {
        select(PROFILE);
        press(current(AdminScreen.class), "edit_tabs");
    }

    private static void checkWholePage() {
        ProfileScreen screen = current(ProfileScreen.class);
        check("items.wholePage", () -> {
            AbcList<?> tabs = child(screen, AbcList.class);
            require(!tabs.items().isEmpty(), "no tab listed");
            require(tabs.selectByKey(BUILDING_BLOCKS), "building_blocks is not in the tab list");
            // selectByKey does not run onSelect; the page acts on the tab it holds.
            setField(screen, "selectedTab", tabs.getSelected());
            press(screen, "open_page");
            require(draft().tabs.contains(BUILDING_BLOCKS), "opening the page did not select building_blocks");
            return "Open whole tab adds building_blocks to the whitelist";
        });
    }

    private static void checkSingleItem() {
        ProfileScreen screen = current(ProfileScreen.class);
        check("items.singleItem", () -> {
            ItemGrid grid = child(screen, ItemGrid.class);
            ItemStack first = grid.stackAt(grid.getX() + 2, grid.getY() + 2);
            require(first != null, "the grid shows no item for the open page");
            clickedItem = BuiltInRegistries.ITEM.getKey(first.getItem());
            grid.mouseClicked(grid.getX() + 2, grid.getY() + 2, 0);
            require(draft().exceptions.contains(clickedItem), "clicking an open item did not lock it: "
                    + clickedItem + " not in exceptions");
            return clickedItem + " locked by one click, the page stays open";
        });
    }

    private static void checkSearch() {
        ProfileScreen screen = current(ProfileScreen.class);
        check("items.search", () -> {
            type(screen, 0, "torch");
            ItemGrid grid = child(screen, ItemGrid.class);
            ItemStack first = grid.stackAt(grid.getX() + 2, grid.getY() + 2);
            require(first != null, "searching 'torch' found nothing");
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(first.getItem());
            require(id.getPath().contains("torch") || first.getHoverName().getString().toLowerCase().contains("torch"),
                    "the search shows " + id);
            boolean before = draft().items.contains(id) || draft().exceptions.contains(id);
            grid.mouseClicked(grid.getX() + 2, grid.getY() + 2, 0);
            boolean after = draft().items.contains(id) || draft().exceptions.contains(id);
            require(before != after, "clicking the search result " + id + " did not change its rule");
            return "search finds " + id + " across every tab, one click flips it";
        });
    }

    private static void checkModeSwitch() {
        AdminScreen screen = current(AdminScreen.class);
        check("mode.switch", () -> {
            int tabs = draft().tabs.size();
            press(screen, "mode.whitelist");
            require(draft().mode == CreativeProfile.Mode.BLACKLIST, "the mode button did not switch to blacklist");
            require(button(screen, "mode.blacklist") != null, "the mode button still reads whitelist");
            require(draft().tabs.size() == tabs, "switching the mode changed the selection");
            press(screen, "mode.blacklist");
            require(draft().mode == CreativeProfile.Mode.WHITELIST, "the mode did not switch back");
            return "whitelist to blacklist and back, selection kept";
        });
    }

    // ------------------------------------------------------------------ A04

    private static void checkRules() {
        AdvancedScreen screen = current(AdvancedScreen.class);
        check("rules.tag", () -> {
            submit(screen, 0, "#minecraft:beds");
            require(draft().tags.contains(BEDS), "the tag #minecraft:beds was not added");
            return "#minecraft:beds added";
        });
        check("rules.unknownComponent", () -> {
            submit(screen, 1, "notreal:thing");
            require(tr("unknown_component", "notreal:thing").equals(status(screen)),
                    "an unknown component gave status '" + status(screen) + "'");
            require(draft().allowedComponents.isEmpty(), "an unknown component was added");
            return "notreal:thing refused as unknown";
        });
        check("rules.component", () -> {
            submit(screen, 1, "minecraft:custom_name");
            require(draft().allowedComponents.contains(CUSTOM_NAME), "minecraft:custom_name was not added");
            return "minecraft:custom_name added";
        });
        check("rules.switches", () -> {
            press(screen, "allow_block_entity_data");
            press(screen, "allow_container_contents");
            require(draft().allowBlockEntityData && draft().allowContainerContents, "a switch did not turn on");
            return "both component switches turn on";
        });
        check("rules.mod", () -> {
            AbcList<?> mods = child(screen, AbcList.class);
            require(!mods.items().isEmpty(), "no mod listed");
            Object first = mods.items().get(0);
            mods.mouseClicked(mods.getX() + 20, mods.getY() + 3, 0);
            require(draft().namespaces.contains(String.valueOf(first)), "clicking a mod row did not select " + first);
            return "mod " + first + " selected by a click";
        });
    }

    // ------------------------------------------------------------------ layout

    /**
     * Screen sizes in GUI pixels, as the game sees them: the default 854x480 window at GUI scale 2,
     * then a 1080p screen at scales 4, 3 and 2. The window is capped, so larger screens change
     * nothing beyond the last one.
     */
    private static final int[][] GUI_SIZES = {{427, 240}, {480, 270}, {640, 360}, {960, 540}};
    /** What a button keeps around its label (AbcButton draws from 3 px in and stops 3 px before the edge). */
    private static final int BUTTON_INSET = 6;
    /** A field's text area is its width minus the vanilla border and inner padding. */
    private static final int FIELD_INSET = 8;
    private static final int ICON_ROOM = 12;

    private static java.util.concurrent.CompletableFuture<Void> languageReload;

    private static void switchLanguage(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        languageReload = mc.reloadResourcePacks();
    }

    /** Every page of the admin screen, built at each size, with each label measured against its box. */
    private static void checkLayout(String name) {
        check(name, () -> {
            Minecraft mc = Minecraft.getInstance();
            AdminScreen main = new AdminScreen();
            List<Screen> pages = List.of(main, new ProfileScreen(main, PROFILE), new AdvancedScreen(main, PROFILE),
                    new PlayersScreen(main));
            List<String> cut = new ArrayList<>();
            int measured = 0;
            for (int[] size : GUI_SIZES) {
                // Every page in both modes: the notes, the status help and the mode button change with it.
                for (CreativeProfile.Mode mode : CreativeProfile.Mode.values()) {
                    CreativeProfile.Mode kept = draft().mode;
                    draft().mode = mode;
                    try {
                        main.init(mc, size[0], size[1]);
                        measured += checkNotes(main, size, cut);
                        for (Screen page : pages) {
                            page.init(mc, size[0], size[1]);
                            measured += checkStatusLine(page, size, cut);
                            if (page instanceof PlayersScreen players) {
                                measured += checkGroupHelp(players, size, cut);
                            }
                            for (GuiEventListener child : page.children()) {
                                String problem = null;
                                if (child instanceof AbcButton button && !button.isIconOnly()) {
                                    String label = button.getMessage().getString();
                                    int need = mc.font.width(label) + (field(button, AbcButton.class, "icon") != null ? ICON_ROOM : 0);
                                    measured++;
                                    if (need > button.getWidth() - BUTTON_INSET) {
                                        problem = "'" + label + "' needs " + need + " of " + (button.getWidth() - BUTTON_INSET);
                                    }
                                } else if (child instanceof AbcEditBox box) {
                                    String hint = (String) field(box, AbcEditBox.class, "hint");
                                    measured++;
                                    if (mc.font.width(hint) > box.getWidth() - FIELD_INSET) {
                                        problem = "hint '" + hint + "' needs " + mc.font.width(hint) + " of " + (box.getWidth() - FIELD_INSET);
                                    }
                                }
                                if (problem != null) {
                                    cut.add(page.getClass().getSimpleName() + "@" + size[0] + "x" + size[1] + ": " + problem);
                                }
                            }
                        }
                    } finally {
                        draft().mode = kept;
                    }
                }
            }
            require(cut.isEmpty(), cut.size() + " cut: " + String.join(" | ", new java.util.LinkedHashSet<>(cut)));
            return measured + " labels, hints and help lines fit, 4 pages in both modes at " + GUI_SIZES.length + " screen sizes";
        });
    }

    /** The help or notice a page opens with fits the footer, next to its icon when it has one. */
    private static int checkStatusLine(Screen page, int[] size, List<String> cut) {
        Minecraft mc = Minecraft.getInstance();
        String text = (String) field(page, AbcScreen.class, "statusText");
        if (text.isEmpty()) {
            return 0;
        }
        net.thefricadelle.arcadiacreativeadmin.client.gui.kit.WindowLayout layout =
                (net.thefricadelle.arcadiacreativeadmin.client.gui.kit.WindowLayout) field(page, AbcScreen.class, "layout");
        int room = layout.footer().w() - 12
                - (field(page, AbcScreen.class, "statusIcon") != null ? net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Atlas.ICON_SIZE + 4 : 0);
        if (mc.font.width(text) > room) {
            cut.add(page.getClass().getSimpleName() + "@" + size[0] + "x" + size[1] + ": status '" + text
                    + "' needs " + mc.font.width(text) + " of " + room);
        }
        return 1;
    }

    /** The group help under the player list is wrapped, never cut, and leaves the list at least three rows. */
    private static int checkGroupHelp(PlayersScreen page, int[] size, List<String> cut) {
        List<?> help = (List<?>) field(page, PlayersScreen.class, "help");
        AbcList<?> list = (AbcList<?>) field(page, PlayersScreen.class, "players");
        String where = "PlayersScreen@" + size[0] + "x" + size[1] + ": ";
        if (help.size() < 4) {
            cut.add(where + "only " + help.size() + " group help lines");
        }
        if (list.getHeight() < 3 * 18) {
            cut.add(where + "the group help leaves the player list " + list.getHeight() + " px");
        }
        return help.size();
    }

    /** Each note fits its width, and the notes overlap no widget of the page. */
    @SuppressWarnings("unchecked")
    private static int checkNotes(AdminScreen main, int[] size, List<String> cut) {
        Minecraft mc = Minecraft.getInstance();
        List<String> notes = (List<String>) field(main, AdminScreen.class, "notes");
        net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Rect area =
                (net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Rect) field(main, AdminScreen.class, "notesArea");
        String where = "AdminScreen@" + size[0] + "x" + size[1] + ": ";
        for (String note : notes) {
            if (mc.font.width(note) > area.w()) {
                cut.add(where + "note '" + note + "' needs " + mc.font.width(note) + " of " + area.w());
            }
        }
        for (GuiEventListener child : main.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget
                    && widget.getY() < area.bottom() && area.y() < widget.getY() + widget.getHeight()
                    && widget.getX() < area.right() && area.x() < widget.getX() + widget.getWidth()) {
                cut.add(where + "the notes overlap '" + widget.getMessage().getString() + "'");
            }
        }
        return notes.size();
    }

    // ------------------------------------------------------------------ A05

    private static void saveSettings() {
        AdminScreen screen = current(AdminScreen.class);
        if (!AdminSession.enforced()) {
            press(screen, "enforced");
        }
        for (int i = 0; i < 5 && !PROFILE.equals(AdminSession.defaultProfile()); i++) {
            press(screen, "default_profile");
        }
        press(screen, "save");
    }

    private static void checkSaved() {
        AdminScreen screen = current(AdminScreen.class);
        check("save.saved", () -> {
            require(tr("status.saved").equals(status(screen)), "status after save: '" + status(screen) + "'");
            savedPolicy = Files.readString(policyFile(), StandardCharsets.UTF_8);
            for (String expected : new String[] {"\"" + PROFILE + "\"", "minecraft:beds", "minecraft:custom_name",
                    "minecraft:building_blocks", clickedItem.toString(), "\"enforced\": true"}) {
                require(savedPolicy.contains(expected), "the policy file lacks " + expected);
            }
            return "saved, and the file holds the profile, its rules and enforced=true";
        });
    }

    private static void saveOnOldRevision() {
        AdminScreen screen = current(AdminScreen.class);
        press(screen, "enforced");
        try {
            Field base = AdminSession.class.getDeclaredField("baseRevision");
            base.setAccessible(true);
            base.setInt(null, base.getInt(null) - 1);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AdminSession.baseRevision is gone", e);
        }
        press(screen, "save");
    }

    private static void checkRefusalKeepsEdits() {
        check("save.refusalKeepsEdits", () -> {
            require(AdminSession.dirty(), "a refused save threw the edits away");
            require(!AdminSession.enforced(), "the pending edit was undone by the refusal");
            require(!AdminSession.stale(), "a refusal over an unchanged policy read as another admin's change");
            press(current(AdminScreen.class), "revert");
            require(AdminSession.enforced() && !AdminSession.dirty(), "undo did not restore the saved state");
            return "the refusal is shown and the edit stays until undone";
        });
    }

    private static void checkUndo() {
        AdminScreen screen = current(AdminScreen.class);
        check("undo", () -> {
            press(screen, "enforced");
            require(AdminSession.dirty() && !AdminSession.enforced(), "the toggle did not mark a change");
            press(screen, "revert");
            require(!AdminSession.dirty() && AdminSession.enforced(), "undo did not restore the saved state");
            require(tr("reverted").equals(status(screen)), "status after undo: '" + status(screen) + "'");
            return "Undo changes restores the saved state";
        });
    }

    private static void checkDiscard() {
        Minecraft mc = Minecraft.getInstance();
        AdminScreen screen = current(AdminScreen.class);
        check("discard", () -> {
            press(screen, "enforced");
            screen.onClose();
            require(mc.screen == screen && dialogOpen(screen), "closing with unsaved changes did not ask");
            dialogButton(screen, false).onPress();
            require(mc.screen == screen && AdminSession.dirty(), "cancelling the discard lost the change");
            screen.onClose();
            dialogButton(screen, true).onPress();
            require(mc.screen == null, "confirming the discard left the screen open");
            require(!AdminSession.dirty() && AdminSession.enforced(), "discarding kept the change");
            return "closing asks, Cancel keeps the edit, Discard drops it";
        });
    }

    // ------------------------------------------------------------------ A06

    private static void cyclePlayer() {
        PlayersScreen screen = current(PlayersScreen.class);
        AbcList<?> list = child(screen, AbcList.class);
        require(!list.items().isEmpty(), "no player listed");
        list.mouseClicked(list.getX() + 20, list.getY() + 3, 0);
    }

    private static String hostAssignment() {
        AdminPayloads.State state = AdminSession.state();
        if (state == null) {
            return "";
        }
        String host = Minecraft.getInstance().getUser().getName();
        return state.players().stream().filter(entry -> entry.name().equals(host)).findFirst()
                .map(AdminPayloads.PlayerEntry::assigned).orElse("");
    }

    private static void checkAssigned() {
        PlayersScreen screen = current(PlayersScreen.class);
        check("players.assign", () -> {
            require(tr("status.assigned").equals(status(screen)), "status after a click: '" + status(screen) + "'");
            return "one click assigns " + hostAssignment() + ", confirmed by the server";
        });
        check("players.assignKeepsEdits", () -> {
            require(AdminSession.dirty(), "assigning a player threw away an unsaved edit");
            require(!AdminSession.stale(), "assigning a player read as another admin's save");
            return "an assignment leaves the pending edit in place and nothing stale";
        });
    }

    // ------------------------------------------------------------------ A08

    private static void breakPolicy() {
        try {
            Files.writeString(policyFile(), savedPolicy.replaceFirst("\\{", "{ \"oops\": 1,"), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not break the policy file", e);
        }
        Minecraft.getInstance().player.connection.sendCommand("creativeadmin reload");
    }

    private static void checkReadOnly() {
        AdminScreen screen = current(AdminScreen.class);
        check("broken.readOnly", () -> {
            require(tr("broken").equals(status(screen)), "status with a broken file: '" + status(screen) + "'");
            require(!button(screen, "create").active, "Create stays enabled on a broken file");
            require(!button(screen, "enforced").active, "settings stay editable on a broken file");
            return "banner shown, editing disabled";
        });
    }

    private static void restorePolicy() {
        Minecraft mc = Minecraft.getInstance();
        mc.screen.onClose();
        try {
            Files.writeString(policyFile(), savedPolicy, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("could not restore the policy file", e);
        }
        mc.player.connection.sendCommand("creativeadmin reload");
    }

    // ------------------------------------------------------------------ helpers

    private static Path policyFile() {
        return FMLPaths.CONFIGDIR.get().resolve("arcadia").resolve("arcadia-creative-admin-policy.json");
    }

    private static AdminSession.Draft draft() {
        AdminSession.Draft draft = AdminSession.profile(PROFILE);
        require(draft != null, "profile " + PROFILE + " is gone");
        return draft;
    }

    /** Selects a profile the way a click on its row does, then rebuilds the page. */
    private static void select(String name) {
        AdminScreen screen = current(AdminScreen.class);
        try {
            Field selected = AdminScreen.class.getDeclaredField("selected");
            selected.setAccessible(true);
            selected.set(null, name);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("AdminScreen.selected is gone", e);
        }
        Minecraft mc = Minecraft.getInstance();
        screen.resize(mc, screen.width, screen.height);
    }

    private static String tr(String key, Object... args) {
        return Component.translatable(KEY + key, args).getString();
    }

    private static <T extends Screen> T current(Class<T> type) {
        Screen screen = Minecraft.getInstance().screen;
        require(type.isInstance(screen), "expected " + type.getSimpleName() + ", found "
                + (screen == null ? "no screen" : screen.getClass().getSimpleName()));
        return type.cast(screen);
    }

    private static AbcButton button(Screen screen, String key) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbcButton b && b.getMessage().getContents() instanceof TranslatableContents t
                    && t.getKey().equals(KEY + key)) {
                return b;
            }
        }
        return null;
    }

    private static void press(Screen screen, String key) {
        AbcButton b = button(screen, key);
        require(b != null, "no button " + key + " on " + screen.getClass().getSimpleName());
        require(b.active, "button " + key + " is disabled");
        b.onPress();
    }

    /** The {@code index}-th text field of the screen, in the order the page adds them. */
    private static AbcEditBox field(Screen screen, int index) {
        List<AbcEditBox> boxes = new ArrayList<>();
        for (GuiEventListener child : screen.children()) {
            if (child instanceof AbcEditBox box) {
                boxes.add(box);
            }
        }
        require(boxes.size() > index, "no text field " + index + " on " + screen.getClass().getSimpleName());
        return boxes.get(index);
    }

    private static void type(Screen screen, int index, String text) {
        field(screen, index).setValue(text);
    }

    /** Types and presses Enter, as a player does. */
    private static void submit(Screen screen, int index, String text) {
        AbcEditBox box = field(screen, index);
        box.setValue(text);
        box.setFocused(true);
        box.keyPressed(257, 0, 0);
        box.setFocused(false);
    }

    private static <T> T child(Screen screen, Class<T> type) {
        for (GuiEventListener child : screen.children()) {
            if (type.isInstance(child)) {
                return type.cast(child);
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " on " + screen.getClass().getSimpleName());
    }

    private static Object field(Object target, Class<?> owner, String name) {
        try {
            Field f = owner.getDeclaredField(name);
            f.setAccessible(true);
            return f.get(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(owner.getSimpleName() + "." + name + " is gone", e);
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(target.getClass().getSimpleName() + "." + name + " is gone", e);
        }
    }

    private static String status(AbcScreen screen) {
        return (String) field(screen, AbcScreen.class, "statusText");
    }

    private static boolean dialogOpen(AbcScreen screen) {
        return field(screen, AbcScreen.class, "dialog") != null;
    }

    /** While a dialog is open the screen's children are exactly its Cancel and its confirm button. */
    private static AbcButton dialogButton(AbcScreen screen, boolean confirm) {
        List<? extends GuiEventListener> children = screen.children();
        require(children.size() == 2, "no dialog open");
        return (AbcButton) children.get(confirm ? 1 : 0);
    }

    private static void screenshot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "admin-" + name + ".png", mc.getMainRenderTarget(),
                message -> LOGGER.info("[admin-smoke] screenshot {}: {}", name, message.getString()));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    // ------------------------------------------------------------------ report

    private static void check(String name, Check check) {
        try {
            String detail = check.run();
            REPORT.add("PASS " + name + " - " + detail);
            LOGGER.info("[admin-smoke] PASS {} - {}", name, detail);
        } catch (Throwable t) {
            fail(name, t.getMessage() == null ? t.toString() : t.getMessage());
            if (!(t instanceof AssertionError)) {
                LOGGER.error("[admin-smoke] {} threw", name, t);
            }
        }
    }

    private static void fail(String name, String detail) {
        failures++;
        REPORT.add("FAIL " + name + " - " + detail);
        LOGGER.error("[admin-smoke] FAIL {} - {}", name, detail);
    }

    private static void finish() {
        finished = true;
        List<String> lines = new ArrayList<>(REPORT);
        lines.add(failures == 0
                ? "RESULT PASS " + REPORT.size() + " checks"
                : "RESULT FAIL " + failures + " of " + REPORT.size() + " checks failed");
        Path report = FMLPaths.GAMEDIR.get().resolve("smoke-report.txt");
        try {
            Files.write(report, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            LOGGER.error("[admin-smoke] could not write {}", report, e);
        }
        Minecraft.getInstance().stop();
    }
}
