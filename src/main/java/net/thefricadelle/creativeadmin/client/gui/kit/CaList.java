/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.gui.kit;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Scrollable, selectable list of fixed-height rows, scrolled with the wheel, the scrollbar or the
 * keyboard.
 *
 * <p>Interaction: click selects, double-click or Enter activates, Up/Down/Page Up/Page Down/Home/End
 * move the selection and keep it in view. A row can host inline controls (a toggle, a delete
 * cross) through {@link #onRowClick}, which receives the click before selection happens; row
 * layouts compute the same {@link Rect}s for drawing and hit testing so the two never disagree.
 *
 * <p><strong>Reordering.</strong> With {@link #draggable}, pressing a row outside its inline controls
 * picks it up. The drag reorders live, one neighbour swap at a time through the {@link Mover},
 * rather than lifting a floating copy and committing on release: the row the pointer holds is always
 * the row at its real position, so what the list shows mid-drag is already what will be saved.
 *
 * <p><strong>Plain.</strong> A {@link #plain} list is a scroll container for text: no striping, no
 * hover, no selection, and the arrow keys scroll instead of selecting.
 *
 * <p>{@link #setItems} keeps the selection on the same item across a refresh when an identity
 * function is set, so an edit that reorders or refills the list does not move the cursor.
 *
 * @author THEFricadelle
 */
public class CaList<T> extends AbstractWidget {

    /** Draws one row's content; the row background is already painted. */
    @FunctionalInterface
    public interface RowRenderer<T> {
        void render(GuiGraphics g, Font font, T item, Rect row, int mouseX, int mouseY, boolean hovered, boolean selected);
    }

    /** Inline control hit test. Return true to consume the click (selection is then left alone). */
    @FunctionalInterface
    public interface RowClick<T> {
        boolean click(T item, Rect row, double mouseX, double mouseY);
    }

    /** Moves an item one step ({@code delta} is -1 or 1) and refills the list; false when it cannot move. */
    @FunctionalInterface
    public interface Mover<T> {
        boolean move(T item, int delta);
    }

    private static final long DOUBLE_CLICK_MS = 300L;
    private static final int KEY_ENTER = 257;
    private static final int KEY_KP_ENTER = 335;
    private static final int KEY_PAGE_UP = 266;
    private static final int KEY_PAGE_DOWN = 267;
    private static final int KEY_HOME = 268;
    private static final int KEY_END = 269;
    private static final int KEY_UP = 265;
    private static final int KEY_DOWN = 264;

    /** Distance from a list edge at which a drag starts scrolling the list under the pointer. */
    private static final int EDGE_SCROLL_ZONE = 12;
    private static final int EDGE_SCROLL_SPEED = 3;

    private final int rowHeight;
    private List<T> items = List.of();
    private int selected = -1;
    private double scroll;
    private boolean draggingThumb;
    private double dragOffset;
    private long lastClickAt;
    private int lastClickIndex = -1;

    private RowRenderer<T> renderer = (g, font, item, row, mouseX, mouseY, hovered, selected) ->
            Skin.textIn(g, font, String.valueOf(item), row, 6, Palette.TEXT);
    private Function<T, String> label = String::valueOf;
    private Function<T, ?> identity;
    private Consumer<T> onSelect = item -> { };
    private Consumer<T> onActivate = item -> { };
    private RowClick<T> onRowClick;
    private String emptyText = "";
    private boolean plain;

    private Mover<T> mover;
    /** Tracked by item rather than by index: every swap refills the list. */
    private T dragged;
    /** Pointer offset inside the grabbed row, so the row stays anchored where it was picked up. */
    private double grabOffset;
    /** Index the drag started from, so a grab that ends where it began stays silent. */
    private int grabIndex;

    public CaList(Component narration, int rowHeight) {
        super(0, 0, 0, 0, narration);
        this.rowHeight = rowHeight;
    }

    // ------------------------------------------------------------------ configuration

    public CaList<T> at(Rect r) {
        setRectangle(r.w(), r.h(), r.x(), r.y());
        clampScroll();
        return this;
    }

    public CaList<T> renderer(RowRenderer<T> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** Plain-text label of an item, used for narration. */
    public CaList<T> label(Function<T, String> label) {
        this.label = label;
        return this;
    }

    /** Key that identifies an item across refreshes (a name, an id). */
    public CaList<T> identity(Function<T, ?> identity) {
        this.identity = identity;
        return this;
    }

    public CaList<T> onSelect(Consumer<T> onSelect) {
        this.onSelect = onSelect;
        return this;
    }

    public CaList<T> onActivate(Consumer<T> onActivate) {
        this.onActivate = onActivate;
        return this;
    }

    public CaList<T> onRowClick(RowClick<T> onRowClick) {
        this.onRowClick = onRowClick;
        return this;
    }

    public CaList<T> emptyText(String emptyText) {
        this.emptyText = emptyText;
        return this;
    }

    /** Lets rows be reordered by dragging them. */
    public CaList<T> draggable(Mover<T> mover) {
        this.mover = mover;
        return this;
    }

    /** Text only: no row surfaces, no selection, arrow keys scroll. */
    public CaList<T> plain() {
        this.plain = true;
        return this;
    }

    // ------------------------------------------------------------------ content

    /**
     * Replaces the rows. The selection follows the previously selected item when an identity is
     * set and the item is still present; otherwise it is cleared. The scroll position is kept.
     */
    public void setItems(List<T> newItems) {
        T previous = getSelected();
        this.items = List.copyOf(newItems);
        this.selected = -1;
        if (previous != null && identity != null) {
            Object key = identity.apply(previous);
            for (int i = 0; i < items.size(); i++) {
                if (Objects.equals(identity.apply(items.get(i)), key)) {
                    selected = i;
                    break;
                }
            }
        }
        clampScroll();
    }

    public List<T> items() {
        return items;
    }

    public T getSelected() {
        return selected >= 0 && selected < items.size() ? items.get(selected) : null;
    }

    /** Selects the item whose identity equals {@code key}, scrolling it into view. */
    public boolean selectByKey(Object key) {
        if (identity == null) return false;
        for (int i = 0; i < items.size(); i++) {
            if (Objects.equals(identity.apply(items.get(i)), key)) {
                select(i, false);
                return true;
            }
        }
        return false;
    }

    /** Scrolls so that row {@code index} is the first one shown, as far as the content allows. */
    public void scrollToRow(int index) {
        scroll = (double) index * rowHeight;
        clampScroll();
    }

    /** Whether a row is currently held by a drag. */
    public boolean isDragging() {
        return dragged != null;
    }

    /** The row held by a drag, or null. */
    public T dragged() {
        return dragged;
    }

    // ------------------------------------------------------------------ geometry

    private int contentHeight() {
        return items.size() * rowHeight;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - height);
    }

    private boolean scrollbarVisible() {
        return contentHeight() > height;
    }

    private Rect rowsArea() {
        int sb = scrollbarVisible() ? Atlas.SCROLLBAR_WIDTH : 0;
        return new Rect(getX(), getY(), width - sb, height);
    }

    private Rect track() {
        return new Rect(getRight() - Atlas.SCROLLBAR_WIDTH, getY(), Atlas.SCROLLBAR_WIDTH, height);
    }

    private int thumbHeight() {
        int content = Math.max(1, contentHeight());
        return Math.max(12, Math.min(height, height * height / content));
    }

    private int thumbY() {
        int max = maxScroll();
        if (max == 0) return getY();
        return getY() + (int) Math.round(scroll / max * (height - thumbHeight()));
    }

    private Rect rowRect(int index) {
        Rect area = rowsArea();
        return new Rect(area.x(), area.y() + index * rowHeight - (int) Math.round(scroll), area.w(), rowHeight);
    }

    private int indexAt(double mouseY) {
        int index = (int) Math.floor((mouseY - getY() + scroll) / rowHeight);
        return index >= 0 && index < items.size() ? index : -1;
    }

    private int indexOf(T item) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) == item) return i;
        }
        return -1;
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, maxScroll()));
    }

    private void ensureVisible(int index) {
        int top = index * rowHeight;
        if (top < scroll) {
            scroll = top;
        } else if (top + rowHeight > scroll + height) {
            scroll = top + rowHeight - height;
        }
        clampScroll();
    }

    private void select(int index, boolean fromUser) {
        if (items.isEmpty() || plain) return;
        int clamped = Math.max(0, Math.min(items.size() - 1, index));
        boolean changed = clamped != selected;
        selected = clamped;
        ensureVisible(clamped);
        if (changed || fromUser) onSelect.accept(items.get(clamped));
    }

    // ------------------------------------------------------------------ dragging

    private void beginDrag(T item, Rect row, double mouseY) {
        dragged = item;
        grabOffset = mouseY - row.y();
        grabIndex = indexOf(item);
    }

    /**
     * Ends a drag. Public because a button released outside the list never reaches the list widget:
     * the screen forwards every release, or a row released past an edge would stay stuck to the pointer.
     */
    public void endDrag() {
        if (dragged == null) return;
        boolean moved = indexOf(dragged) != grabIndex;
        dragged = null;
        if (moved) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    /**
     * Runs per frame instead of per mouse event: holding the pointer still against an edge produces
     * no further drag events, and edge scrolling has to keep going anyway.
     */
    private void updateDrag(double mouseY) {
        if (mouseY < getY() + EDGE_SCROLL_ZONE) {
            scroll -= EDGE_SCROLL_SPEED;
        } else if (mouseY > getBottom() - EDGE_SCROLL_ZONE) {
            scroll += EDGE_SCROLL_SPEED;
        }
        clampScroll();

        if (items.isEmpty()) return;
        // Rounded, not floored: the row swaps once it is half-way over its neighbour, in both directions.
        int target = (int) Math.round((mouseY - grabOffset - getY() + scroll) / rowHeight);
        target = Math.max(0, Math.min(items.size() - 1, target));
        int current = indexOf(dragged);
        while (current >= 0 && current != target) {
            mover.move(dragged, target > current ? 1 : -1);
            int moved = indexOf(dragged);
            if (moved == current) {
                // The item refused to move (a filtered list boundary): stop rather than spin.
                break;
            }
            current = moved;
        }
        if (current < 0) dragged = null;
    }

    // ------------------------------------------------------------------ rendering

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (dragged != null) updateDrag(mouseY);

        Font font = Minecraft.getInstance().font;
        Rect frame = new Rect(getX(), getY(), width, height);
        g.fill(frame.x(), frame.y(), frame.right(), frame.bottom(), Palette.BG1);

        if (items.isEmpty()) {
            if (!emptyText.isEmpty()) renderEmptyText(g, font, frame);
        } else {
            Rect area = rowsArea();
            g.enableScissor(area.x(), area.y(), area.right(), area.bottom());
            int first = Math.max(0, (int) Math.floor(scroll / rowHeight));
            int last = Math.min(items.size() - 1, (int) Math.ceil((scroll + height) / rowHeight));
            boolean mouseInRows = area.contains(mouseX, mouseY) && !draggingThumb && dragged == null;
            for (int i = first; i <= last; i++) {
                Rect row = rowRect(i);
                T item = items.get(i);
                boolean hovered = !plain && mouseInRows && row.contains(mouseX, mouseY);
                boolean isSelected = !plain && i == selected;
                if (item == dragged) {
                    Skin.liftedRow(g, row);
                } else if (!plain) {
                    Skin.row(g, row, i, hovered, isSelected);
                }
                // Inline controls must not light up under a pointer that is busy carrying a row.
                int mx = dragged == null ? mouseX : -1;
                int my = dragged == null ? mouseY : -1;
                renderer.render(g, font, item, row, mx, my, hovered, isSelected);
            }
            g.disableScissor();
        }

        if (scrollbarVisible()) {
            Rect track = track();
            Skin.scrollbar(g, track, thumbY(), thumbHeight(), draggingThumb || track.contains(mouseX, mouseY));
        }
        Skin.outline(g, frame, isFocused() && !plain ? Palette.LINE_STRONG : Palette.LINE);
    }

    /** The empty message, wrapped: it is often a full sentence explaining what to do next. */
    private void renderEmptyText(GuiGraphics g, Font font, Rect frame) {
        var lines = font.split(Component.literal(emptyText), Math.max(40, frame.w() - 24));
        int y = frame.y() + (frame.h() - lines.size() * 10) / 2;
        for (var line : lines) {
            g.drawString(font, line, frame.x() + (frame.w() - font.width(line)) / 2, y, Palette.TEXT_MUTE, false);
            y += 10;
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible || button != 0 || !isMouseOver(mouseX, mouseY)) return false;

        if (scrollbarVisible() && track().contains(mouseX, mouseY)) {
            int thumbTop = thumbY();
            if (mouseY >= thumbTop && mouseY < thumbTop + thumbHeight()) {
                draggingThumb = true;
                dragOffset = mouseY - thumbTop;
            } else {
                // Page towards the click, like a native scrollbar.
                scroll += mouseY < thumbTop ? -height : height;
                clampScroll();
            }
            return true;
        }

        int index = indexAt(mouseY);
        if (index < 0 || plain) return true;
        T item = items.get(index);
        Rect row = rowRect(index);
        if (onRowClick != null && onRowClick.click(item, row, mouseX, mouseY)) return true;

        long now = Util.getMillis();
        boolean doubleClick = index == lastClickIndex && now - lastClickAt <= DOUBLE_CLICK_MS;
        lastClickAt = now;
        lastClickIndex = index;
        select(index, true);
        // A second quick click must still pick the row up: players often click a row before dragging it.
        if (mover != null) beginDrag(item, row, mouseY);
        if (doubleClick) onActivate.accept(item);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // Swallowed while a row is held: the reorder itself runs per frame in renderWidget.
        if (dragged != null) return true;
        if (!draggingThumb) return false;
        int travel = height - thumbHeight();
        if (travel > 0) {
            scroll = (mouseY - dragOffset - getY()) / travel * maxScroll();
            clampScroll();
        }
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        boolean was = draggingThumb || dragged != null;
        draggingThumb = false;
        endDrag();
        return was;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!visible || !isMouseOver(mouseX, mouseY) || !scrollbarVisible()) return false;
        scroll -= scrollY * rowHeight * 2;
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!active || !visible || items.isEmpty()) return false;
        int page = Math.max(1, height / rowHeight - 1);
        if (plain) {
            switch (keyCode) {
                case KEY_UP -> scroll -= rowHeight;
                case KEY_DOWN -> scroll += rowHeight;
                case KEY_PAGE_UP -> scroll -= (double) page * rowHeight;
                case KEY_PAGE_DOWN -> scroll += (double) page * rowHeight;
                case KEY_HOME -> scroll = 0;
                case KEY_END -> scroll = maxScroll();
                default -> {
                    return false;
                }
            }
            clampScroll();
            return true;
        }
        switch (keyCode) {
            case KEY_UP -> select(selected < 0 ? 0 : selected - 1, true);
            case KEY_DOWN -> select(selected < 0 ? 0 : selected + 1, true);
            case KEY_PAGE_UP -> select(selected - page, true);
            case KEY_PAGE_DOWN -> select(selected + page, true);
            case KEY_HOME -> select(0, true);
            case KEY_END -> select(items.size() - 1, true);
            case KEY_ENTER, KEY_KP_ENTER -> {
                T item = getSelected();
                if (item == null) return false;
                onActivate.accept(item);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        T item = getSelected();
        if (item == null) {
            output.add(NarratedElementType.TITLE, Component.empty().append(getMessage())
                    .append(", " + items.size() + " entries"));
        } else {
            output.add(NarratedElementType.TITLE, Component.literal(label.apply(item)
                    + ", " + (selected + 1) + " of " + items.size()));
        }
        if (!plain) output.add(NarratedElementType.USAGE, Component.literal("Arrow keys to move, Enter to open"));
    }
}
