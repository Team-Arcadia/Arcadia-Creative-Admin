/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.arcadiacreativeadmin.client.screen;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcButton;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.AbcScreen;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Icon;
import net.thefricadelle.arcadiacreativeadmin.client.gui.kit.Rect;
import net.thefricadelle.arcadiacreativeadmin.network.AdminPayloads;

import javax.annotation.Nullable;

/**
 * Common behaviour of the admin screens: reacting to server state, the status line, the close
 * button, and the way back to the parent page.
 *
 * @author THEFricadelle
 */
abstract class AdminPage extends AbcScreen {

    static final int GAP = 4;
    static final int CAPTION = 12;

    @Nullable
    protected final Screen parent;

    protected AdminPage(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    static String tr(String key, Object... args) {
        return Component.translatable("arcadiacreativeadmin.admin." + key, args).getString();
    }

    static MutableComponent text(String key, Object... args) {
        return Component.translatable("arcadiacreativeadmin.admin." + key, args);
    }

    /** A new state arrived from the server; the page rebuilds from the session. */
    void onServerState(AdminPayloads.State state, boolean replaced) {
        rebuildWidgets();
        if (!state.status().isEmpty()) {
            status(Component.translatable(state.status()).getString(), state.success());
        } else if (!replaced) {
            warn(tr("stale"));
        }
        showStandingNotice();
    }

    /** The notice that stays until something more important replaces it. */
    protected void showStandingNotice() {
        if (AdminSession.readOnly()) {
            warn(tr("broken"));
        } else if (AdminSession.stale()) {
            warn(tr("stale"));
        }
    }

    protected void addCloseButton() {
        Rect header = layout.header();
        Rect close = header.right(header.h()).inset(3);
        addRenderableWidget(AbcButton.ghost(text(parent instanceof AdminPage ? "back" : "close"), this::onClose)
                .iconOnly(parent instanceof AdminPage ? Icon.BACK : Icon.CROSS)
                .at(close));
    }

    static AbcButton toggle(Component label, boolean on, Runnable action) {
        return AbcButton.neutral(label, action).icon(on ? Icon.BOX_CHECKED : Icon.BOX).selected(on);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(parent);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
