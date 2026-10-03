/*
 * Arcadia Creative Admin - Copyright (C) 2026 THEFricadelle. All rights reserved.
 * SPDX-License-Identifier: LicenseRef-Arcadia-Creative-Admin-ARR
 *
 * Proprietary, source-available software. Public visibility of this source
 * grants no right to copy, reuse, redistribute, or create derivative works.
 * See LICENSE and CONTRIBUTING.md at the repository root.
 */

package net.thefricadelle.creativeadmin.network;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.thefricadelle.creativeadmin.CreativeAdmin;
import net.thefricadelle.creativeadmin.policy.PolicyDocument;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Messages of the admin screen.
 * <p>
 * The server is the only authority. The screen edits a copy of the policy and sends it back whole;
 * the server checks the sender's permission, the revision the edit started from and the content,
 * then either saves and pushes the new state to every open admin screen, or answers with a status
 * explaining why not.
 *
 * @author THEFricadelle
 */
public final class AdminPayloads {

    private AdminPayloads() {}

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(CreativeAdmin.MOD_ID, path));
    }

    /** Client asks for the screen; answered with a {@link State} that opens it, or with nothing. */
    public record Open() implements CustomPacketPayload {
        public static final Type<Open> TYPE = payloadType("admin_open");
        public static final StreamCodec<FriendlyByteBuf, Open> STREAM_CODEC = StreamCodec.unit(new Open());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * Client opened its creative screen and asks for fresh advice. A permission mod changing a
     * player's group fires no event the server can hear, so this is when such a change reaches the
     * display; enforcement itself reads permissions on every action and never waits for it.
     */
    public record Refresh() implements CustomPacketPayload {
        public static final Type<Refresh> TYPE = payloadType("advice_refresh");
        public static final StreamCodec<FriendlyByteBuf, Refresh> STREAM_CODEC = StreamCodec.unit(new Refresh());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /** Client closed the screen and no longer wants state updates. */
    public record Close() implements CustomPacketPayload {
        public static final Type<Close> TYPE = payloadType("admin_close");
        public static final StreamCodec<FriendlyByteBuf, Close> STREAM_CODEC = StreamCodec.unit(new Close());

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * @param baseRevision revision of the state the edit started from
     * @param document     the whole edited policy
     */
    public record Save(int baseRevision, PolicyDocument document) implements CustomPacketPayload {
        public static final Type<Save> TYPE = payloadType("admin_save");
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> STREAM_CODEC = StreamCodec.of(
                (buf, save) -> {
                    buf.writeVarInt(save.baseRevision);
                    NetCodecs.DOCUMENT.encode(buf, save.document);
                },
                buf -> new Save(buf.readVarInt(), NetCodecs.DOCUMENT.decode(buf)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * @param player  the player to assign
     * @param profile profile name, empty to remove the assignment
     */
    public record Assign(UUID player, String profile) implements CustomPacketPayload {
        public static final Type<Assign> TYPE = payloadType("admin_assign");
        public static final StreamCodec<FriendlyByteBuf, Assign> STREAM_CODEC = StreamCodec.of(
                (buf, assign) -> {
                    UUIDUtil.STREAM_CODEC.encode(buf, assign.player);
                    buf.writeUtf(assign.profile, NetCodecs.MAX_NAME);
                },
                buf -> new Assign(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(NetCodecs.MAX_NAME)));

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * One player as the assignment page shows them.
     *
     * @param assigned explicit assignment, empty when none
     * @param granted  profile a permission mod gives them, empty when none or when offline
     */
    public record PlayerEntry(UUID id, String name, boolean online, String assigned, String granted) {

        static void write(FriendlyByteBuf buf, PlayerEntry entry) {
            UUIDUtil.STREAM_CODEC.encode(buf, entry.id);
            buf.writeUtf(entry.name, NetCodecs.MAX_NAME);
            buf.writeBoolean(entry.online);
            buf.writeUtf(entry.assigned, NetCodecs.MAX_NAME);
            buf.writeUtf(entry.granted, NetCodecs.MAX_NAME);
        }

        static PlayerEntry read(FriendlyByteBuf buf) {
            return new PlayerEntry(UUIDUtil.STREAM_CODEC.decode(buf), buf.readUtf(NetCodecs.MAX_NAME),
                    buf.readBoolean(), buf.readUtf(NetCodecs.MAX_NAME), buf.readUtf(NetCodecs.MAX_NAME));
        }
    }

    /**
     * The policy as the server holds it.
     *
     * @param open              whether the client should open the screen, as opposed to refresh one
     * @param status            translation key of the outcome of the last request, empty for none
     * @param success           whether that outcome is a success, for its colour
     * @param policyBroken      the file is unreadable: the screen is read-only until it is fixed
     * @param assignmentsBroken the assignment file is unreadable: assignments are read-only
     */
    public record State(boolean open, String status, boolean success, int revision, boolean policyBroken,
                        boolean assignmentsBroken, PolicyDocument document, List<PlayerEntry> players)
            implements CustomPacketPayload {
        public static final Type<State> TYPE = payloadType("admin_state");
        public static final StreamCodec<RegistryFriendlyByteBuf, State> STREAM_CODEC = StreamCodec.of(
                State::write, State::read);

        private static void write(RegistryFriendlyByteBuf buf, State state) {
            buf.writeBoolean(state.open);
            buf.writeUtf(state.status, 256);
            buf.writeBoolean(state.success);
            buf.writeVarInt(state.revision);
            buf.writeBoolean(state.policyBroken);
            buf.writeBoolean(state.assignmentsBroken);
            NetCodecs.DOCUMENT.encode(buf, state.document);
            buf.writeVarInt(state.players.size());
            state.players.forEach(entry -> PlayerEntry.write(buf, entry));
        }

        private static State read(RegistryFriendlyByteBuf buf) {
            boolean open = buf.readBoolean();
            String status = buf.readUtf(256);
            boolean success = buf.readBoolean();
            int revision = buf.readVarInt();
            boolean policyBroken = buf.readBoolean();
            boolean assignmentsBroken = buf.readBoolean();
            PolicyDocument document = NetCodecs.DOCUMENT.decode(buf);
            int count = NetCodecs.boundedSize(buf, NetCodecs.MAX_ENTRIES);
            List<PlayerEntry> players = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                players.add(PlayerEntry.read(buf));
            }
            return new State(open, status, success, revision, policyBroken, assignmentsBroken, document, players);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
