/*
 * Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.thefricadelle.creativeadmin.client.gui.kit.Atlas;
import net.thefricadelle.creativeadmin.client.gui.kit.Icon;
import net.thefricadelle.creativeadmin.client.gui.kit.Palette;
import net.thefricadelle.creativeadmin.client.gui.kit.Rect;
import net.thefricadelle.creativeadmin.client.gui.kit.Skin;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A scrollable grid of items, each drawn open or locked, toggled by a click.
 *
 * @author THEFricadelle
 */
final class ItemGrid extends AbstractWidget {

    static final int CELL = 18;
    private static final int LOCKED_SHADE = 0xB0000000;

    private List<ItemStack> stacks = List.of();
    private Predicate<ItemStack> locked = stack -> false;
    private Consumer<ItemStack> onToggle = stack -> { };
    private boolean editable = true;
    private double scroll;
    private boolean draggingThumb;

    ItemGrid() {
        super(0, 0, 0, 0, Component.empty());
    }

    ItemGrid at(Rect r) {
        setRectangle(r.w(), r.h(), r.x(), r.y());
        clampScroll();
        return this;
    }

    void setStacks(List<ItemStack> stacks, boolean resetScroll) {
        this.stacks = stacks;
        if (resetScroll) {
            this.scroll = 0;
        }
        clampScroll();
    }

    void setLocked(Predicate<ItemStack> locked) {
        this.locked = locked;
    }

    void onToggle(Consumer<ItemStack> onToggle) {
        this.onToggle = onToggle;
    }

    void setEditable(boolean editable) {
        this.editable = editable;
    }

    private int columns() {
        return Math.max(1, (getWidth() - Atlas.SCROLLBAR_WIDTH - 2) / CELL);
    }

    private int rows() {
        return (stacks.size() + columns() - 1) / columns();
    }

    private int contentHeight() {
        return rows() * CELL;
    }

    private void clampScroll() {
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentHeight() - getHeight()));
    }

    @Nullable
    ItemStack stackAt(double mouseX, double mouseY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return null;
        }
        int col = (int) ((mouseX - getX()) / CELL);
        int row = (int) ((mouseY - getY() + scroll) / CELL);
        if (col < 0 || col >= columns()) {
            return null;
        }
        int index = row * columns() + col;
        return index >= 0 && index < stacks.size() ? stacks.get(index) : null;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect frame = new Rect(getX(), getY(), getWidth(), getHeight());
        Skin.panel(g, frame);
        g.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());
        int columns = columns();
        int first = (int) (scroll / CELL) * columns;
        int last = Math.min(stacks.size(), first + (getHeight() / CELL + 2) * columns);
        ItemStack hovered = stackAt(mouseX, mouseY);
        for (int i = Math.max(0, first); i < last; i++) {
            int x = getX() + (i % columns) * CELL;
            int y = getY() + (i / columns) * CELL - (int) scroll;
            ItemStack stack = stacks.get(i);
            if (stack == hovered) {
                g.fill(x, y, x + CELL, y + CELL, Palette.BG3);
            }
            g.renderItem(stack, x + 1, y + 1);
            if (locked.test(stack)) {
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, LOCKED_SHADE);
                Skin.icon(g, Icon.LOCK, x + CELL - Atlas.ICON_SIZE - 1, y + CELL - Atlas.ICON_SIZE - 1, Palette.DANGER);
                g.pose().popPose();
            }
        }
        g.disableScissor();

        if (contentHeight() > getHeight()) {
            Rect track = frame.right(Atlas.SCROLLBAR_WIDTH);
            int thumbHeight = Math.max(12, getHeight() * getHeight() / contentHeight());
            int thumbY = track.y() + (int) ((track.h() - thumbHeight) * (scroll / (contentHeight() - getHeight())));
            Skin.scrollbar(g, track, thumbY, thumbHeight, track.contains(mouseX, mouseY));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) {
            return false;
        }
        if (mouseX >= getX() + getWidth() - Atlas.SCROLLBAR_WIDTH && contentHeight() > getHeight()) {
            draggingThumb = true;
            scrollToMouse(mouseY);
            return true;
        }
        ItemStack stack = stackAt(mouseX, mouseY);
        if (stack != null && editable) {
            onToggle.accept(stack);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingThumb) {
            scrollToMouse(mouseY);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        draggingThumb = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void scrollToMouse(double mouseY) {
        double ratio = (mouseY - getY()) / Math.max(1, getHeight());
        scroll = ratio * Math.max(0, contentHeight() - getHeight());
        clampScroll();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!isMouseOver(mouseX, mouseY)) {
            return false;
        }
        scroll -= scrollY * CELL;
        clampScroll();
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
