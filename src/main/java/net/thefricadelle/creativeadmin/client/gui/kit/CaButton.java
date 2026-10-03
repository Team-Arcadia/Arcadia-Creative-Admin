/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.client.gui.kit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * The interface's only button. Extending {@link AbstractButton} is what gives it the behaviour a
 * hand-drawn clickable rectangle lacks: Tab navigation, Enter and Space activation, the click
 * sound, and narration. Built through the variant factories, then placed with {@link #at}.
 *
 * <p>An icon-only button ({@link #iconOnly}) keeps its label as the narration text and tooltip,
 * so it stays usable with the narrator and discoverable with the mouse.
 *
 * @author THEFricadelle
 */
public class CaButton extends AbstractButton {

    private static final int ICON_GAP = 4;
    /** Least room kept between a label and the edges of its button, the selected bar included. */
    public static final int MIN_PADDING = 2;

    private final Skin.Variant variant;
    private final Runnable action;
    private Icon icon;
    private boolean iconOnly;
    /** Pinned "on" state for navigation and toggle buttons, drawn like a hovered button with an accent edge. */
    private boolean selected;

    protected CaButton(Component label, Skin.Variant variant, Runnable action) {
        super(0, 0, 0, Atlas.BUTTON_HEIGHT, label);
        this.variant = variant;
        this.action = action;
    }

    public static CaButton neutral(Component label, Runnable action) {
        return new CaButton(label, Skin.Variant.NEUTRAL, action);
    }

    public static CaButton accent(Component label, Runnable action) {
        return new CaButton(label, Skin.Variant.ACCENT, action);
    }

    public static CaButton good(Component label, Runnable action) {
        return new CaButton(label, Skin.Variant.GOOD, action);
    }

    public static CaButton danger(Component label, Runnable action) {
        return new CaButton(label, Skin.Variant.DANGER, action);
    }

    public static CaButton ghost(Component label, Runnable action) {
        return new CaButton(label, Skin.Variant.GHOST, action);
    }

    /** Places the button; returns it for chaining. */
    public CaButton at(Rect r) {
        setRectangle(r.w(), r.h(), r.x(), r.y());
        return this;
    }

    public CaButton at(int x, int y, int w, int h) {
        setRectangle(w, h, x, y);
        return this;
    }

    public CaButton icon(Icon icon) {
        this.icon = icon;
        return this;
    }

    /** Shows only the icon; the label becomes the tooltip and stays the narration text. */
    public CaButton iconOnly(Icon icon) {
        this.icon = icon;
        this.iconOnly = true;
        setTooltip(Tooltip.create(getMessage()));
        return this;
    }

    /** Whether the icon is all this button shows: taking it away would leave nothing to draw. */
    public boolean isIconOnly() {
        return iconOnly;
    }

    public CaButton enabled(boolean enabled) {
        this.active = enabled;
        return this;
    }

    public CaButton selected(boolean selected) {
        this.selected = selected;
        return this;
    }

    public CaButton tooltip(Component text) {
        setTooltip(Tooltip.create(text));
        return this;
    }

    /** Natural width of this button's content with the given horizontal padding. */
    public int preferredWidth(Font font, int padding) {
        int content = iconOnly ? Atlas.ICON_SIZE : font.width(getMessage());
        if (icon != null && !iconOnly) content += Atlas.ICON_SIZE + ICON_GAP;
        return content + 2 * padding;
    }

    @Override
    public void onPress() {
        action.run();
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Rect r = new Rect(getX(), getY(), getWidth(), getHeight());
        boolean hot = isHovered() || selected;
        Skin.button(g, r, variant, hot, active);
        if (selected && active) g.fill(r.x(), r.y(), r.x() + 2, r.bottom(), Palette.ACCENT);

        Font font = Minecraft.getInstance().font;
        int color = Skin.buttonText(variant, hot, active);
        int iconY = r.y() + (r.h() - Atlas.ICON_SIZE) / 2;
        if (iconOnly) {
            Skin.icon(g, icon, r.x() + (r.w() - Atlas.ICON_SIZE) / 2, iconY, color);
        } else {
            String label = getMessage().getString();
            int contentW = font.width(label) + (icon != null ? Atlas.ICON_SIZE + ICON_GAP : 0);
            int x;
            if (variant == Skin.Variant.GHOST) {
                // Ghost buttons are navigation entries: left-aligned so a column of them lines up, closer to
                // the edge when a tight row gave the button no more than its label.
                x = r.x() + Math.min(6, Math.max(MIN_PADDING, (r.w() - contentW) / 2));
            } else {
                x = r.x() + Math.max(3, (r.w() - contentW) / 2);
            }
            if (icon != null) {
                Skin.icon(g, icon, x, iconY, color);
                x += Atlas.ICON_SIZE + ICON_GAP;
            }
            Skin.text(g, font, label, x, r.y() + (r.h() - 8) / 2, r.right() - x - Math.min(3, x - r.x()), color);
        }
        if (isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard()) {
            Skin.focusRing(g, r);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
