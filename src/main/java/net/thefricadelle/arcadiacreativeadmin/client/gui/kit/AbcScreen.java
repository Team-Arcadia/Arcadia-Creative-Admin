/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client.gui.kit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Base of every screen of the mod: the window chrome, the status line and the confirmation dialog.
 *
 * <p>Subclasses add their widgets in {@link #build()} using the regions of {@link #layout}, and draw
 * anything that is not a widget (section titles, list headers, details text) in
 * {@link #renderPage}.
 *
 * <p><strong>Confirmation.</strong> {@link #confirm} opens a modal dialog. While it is open, only its
 * two buttons receive input or focus. Cancel has the focus when it opens, so Enter alone never
 * confirms a destructive action: Tab to the confirm button first. Escape cancels.
 *
 * <p><strong>Status line.</strong> {@link #status} shows the outcome of the last action in the
 * footer, coloured by success. Server replies can arrive after the click, so the line names what
 * happened rather than saying "done".
 *
 * @author THEFricadelle
 */
public abstract class AbcScreen extends Screen {

    private static final int KEY_ESCAPE = 256;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_F = 70;
    private static final int DIALOG_WIDTH = 240;
    private static final int DIALOG_HEIGHT = 92;

    protected WindowLayout layout;

    private String statusText = "";
    private int statusColor = Palette.TEXT_DIM;
    private Icon statusIcon;

    private Dialog dialog;

    private record Dialog(String title, String message, AbcButton ok, AbcButton cancel) {
    }

    protected AbcScreen(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------ contract

    /** Whether this screen has a navigation sidebar. */
    protected boolean hasSidebar() {
        return false;
    }

    /** Adds the screen's widgets. Called on open and on every resize. */
    protected abstract void build();

    /** Draws non-widget content under the widgets. The window chrome is already drawn. */
    protected void renderPage(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** Draws over the widgets, e.g. badges on buttons. */
    protected void renderForeground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
    }

    /** The field Ctrl+F focuses, or null when the screen has no search. */
    protected AbcEditBox searchBox() {
        return null;
    }

    /** Icon shown before the title in the header. */
    protected Icon titleIcon() {
        return null;
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected final void init() {
        layout = WindowLayout.of(width, height, hasSidebar());
        build();
        if (dialog != null) placeDialogButtons();
    }

    /** The widget that should have focus when the screen opens or rebuilds, or null for vanilla's choice. */
    protected GuiEventListener initialFocus() {
        return null;
    }

    /**
     * Vanilla runs this after every {@link #init()} and, when the last input came from the keyboard,
     * tabs to the first widget (usually a toolbar button), so arrow keys would move between
     * widgets instead of through the page's main list.
     */
    @Override
    protected void setInitialFocus() {
        GuiEventListener target = initialFocus();
        if (target != null && dialog == null) {
            setInitialFocus(target);
            return;
        }
        super.setInitialFocus();
    }

    // ------------------------------------------------------------------ status

    public void status(String text, boolean success) {
        this.statusText = text;
        this.statusColor = success ? Palette.GOOD : Palette.DANGER;
        this.statusIcon = success ? Icon.CHECK : Icon.WARN;
    }

    /** A standing notice the player should not miss, such as a restriction in force. */
    public void warn(String text) {
        this.statusText = text;
        this.statusColor = Palette.WARN;
        this.statusIcon = Icon.WARN;
    }

    /** Neutral status, e.g. "Loading...". */
    public void info(String text) {
        this.statusText = text;
        this.statusColor = Palette.TEXT_DIM;
        this.statusIcon = null;
    }

    // ------------------------------------------------------------------ confirmation

    /**
     * Asks before running {@code action}. {@code confirmLabel} names the action ("Delete preset"),
     * never a bare "OK", so the button says what it will do.
     */
    protected void confirm(String title, String message, String confirmLabel, Runnable action) {
        AbcButton ok = AbcButton.danger(Component.literal(confirmLabel), () -> {
            closeDialog();
            action.run();
        });
        AbcButton cancel = AbcButton.neutral(Component.translatable("gui.cancel"), this::closeDialog);
        this.dialog = new Dialog(title, message, ok, cancel);
        placeDialogButtons();
        setFocused(cancel);
    }

    protected boolean dialogOpen() {
        return dialog != null;
    }

    private void closeDialog() {
        this.dialog = null;
        setFocused(null);
    }

    private Rect dialogRect() {
        return new Rect(0, 0, width, height).centered(DIALOG_WIDTH, DIALOG_HEIGHT);
    }

    private void placeDialogButtons() {
        Rect bar = dialogRect().inset(8).bottom(Atlas.BUTTON_HEIGHT);
        int bw = (bar.w() - 6) / 2;
        dialog.cancel().at(bar.left(bw));
        dialog.ok().at(bar.right(bw));
    }

    /**
     * While a dialog is open, focus stays on its buttons. Screen.mouseClicked focuses the clicked
     * widget after its action ran, which would otherwise hand focus back to the button that opened
     * the dialog.
     */
    @Override
    public void setFocused(GuiEventListener listener) {
        if (dialog != null && listener != dialog.ok() && listener != dialog.cancel()) {
            listener = dialog.cancel();
        }
        super.setFocused(listener);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        if (dialog != null) return List.of(dialog.cancel(), dialog.ok());
        return super.children();
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (dialog != null) {
            if (keyCode == KEY_ESCAPE) {
                closeDialog();
                return true;
            }
            if (keyCode == KEY_ENTER || keyCode == KEY_KP_ENTER) {
                if (getFocused() == dialog.ok()) {
                    dialog.ok().onPress();
                } else {
                    closeDialog();
                }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (keyCode == KEY_F && hasControlDown() && searchBox() != null) {
            AbcEditBox search = searchBox();
            setFocused(search);
            search.moveCursorToEnd(false);
            search.setHighlightPos(0);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Flat scrim instead of the vanilla blur: cheaper, and Screen.render already calls this
        // once, so there is no risk of the "blur once per frame" crash.
        g.fill(0, 0, width, height, Palette.SCRIM);
        renderChrome(g);
        renderPage(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // With a dialog open, widgets behind it must not show hover states.
        boolean modal = dialog != null;
        super.render(g, modal ? -1 : mouseX, modal ? -1 : mouseY, partialTick);
        renderForeground(g, mouseX, mouseY, partialTick);
        if (modal) renderDialog(g, mouseX, mouseY, partialTick);
    }

    private void renderChrome(GuiGraphics g) {
        Skin.window(g, layout.window());

        Rect header = layout.header();
        Skin.header(g, header);
        int x = header.x() + 8;
        Icon icon = titleIcon();
        if (icon != null) {
            Skin.icon(g, icon, x, header.y() + (header.h() - Atlas.ICON_SIZE) / 2, Palette.ACCENT_HI);
            x += Atlas.ICON_SIZE + 6;
        }
        Skin.text(g, font, title.getString(), x, header.y() + (header.h() - 8) / 2, header.right() - x - 8, Palette.TEXT);

        if (layout.hasSidebar()) {
            Rect side = layout.sidebar();
            g.fill(side.x(), side.y(), side.right(), side.bottom(), Palette.BG1);
            Skin.vDivider(g, side.right() - 1, side.y(), side.bottom());
        }

        Rect footer = layout.footer();
        Skin.footer(g, footer);
        if (!statusText.isEmpty()) {
            int sx = footer.x() + 6;
            int cy = footer.y() + (footer.h() - Atlas.ICON_SIZE) / 2 + 1;
            if (statusIcon != null) {
                Skin.icon(g, statusIcon, sx, cy, statusColor);
                sx += Atlas.ICON_SIZE + 4;
            }
            Skin.text(g, font, statusText, sx, footer.y() + (footer.h() - 8) / 2 + 1, footer.right() - sx - 6, statusColor);
        }
    }

    private void renderDialog(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.fill(0, 0, width, height, Palette.MODAL_SCRIM);

        Rect box = dialogRect();
        Skin.window(g, box);
        Rect inner = box.inset(8);
        Skin.icon(g, Icon.WARN, inner.x(), inner.y() + 2, Palette.WARN);
        Skin.text(g, font, dialog.title(), inner.x() + Atlas.ICON_SIZE + 6, inner.y() + 2,
                inner.w() - Atlas.ICON_SIZE - 6, Palette.TEXT);

        Rect body = inner.belowTop(16).aboveBottom(Atlas.BUTTON_HEIGHT + 4);
        int lineY = body.y();
        for (var line : font.split(Component.literal(dialog.message()), body.w())) {
            if (lineY + 9 > body.bottom()) break;
            g.drawString(font, line, body.x(), lineY, Palette.TEXT_DIM, false);
            lineY += 10;
        }

        dialog.cancel().render(g, mouseX, mouseY, partialTick);
        dialog.ok().render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();
    }
}
