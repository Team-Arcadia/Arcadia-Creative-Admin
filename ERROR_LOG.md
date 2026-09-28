# Error log

## [2026-09-25 16:12] - Tab advice payload throws for clients without the mod
**Context:** Logging in a player whose client never negotiated the `arcadiacreativeadmin:tab_policy` channel (vanilla client, GameTest mock player).
**Error:** `java.lang.UnsupportedOperationException: Payload arcadiacreativeadmin:tab_policy may not be sent to the client!` thrown from `PlayerLoggedInEvent`.
**Root cause:** `registrar.optional()` only lets such a client join. `NetworkRegistry.checkPacket` still rejects any modded payload sent on a channel the connection did not negotiate.
**Fix:** `PolicyNetwork.sendTo` returns early unless `player.connection.hasChannel(TabPolicyPayload.TYPE)`.
**Prevention:** Every server-to-client send goes through a `hasChannel` check; the GameTest mock player logs in without the channel and fails the suite if this regresses.

## [2026-09-25 16:12] - Allowed item ids carried forbidden payloads through components
**Context:** Audit of `PolicyEvaluator` against the 1.21.1 sources.
**Error:** A profile allowing `minecraft:pig_spawn_egg`, `minecraft:item_frame` or `minecraft:chest` let a restricted player obtain any item through `entity_data` or `container_loot`, and `allow_block_entity_data` let a chest copy a world chest's contents in after the check.
**Root cause:** Components were judged with a three-entry denylist, and the check ran at the handler entry, before vanilla rewrote `block_entity_data` carrying `x/y/z`.
**Fix:** Component allowlist (`ComponentRules`, creative menu variants, `allowed_components`), and hooks moved onto `Slot.setByPlayer` and `ServerPlayer.drop` so the final stack is judged.
**Prevention:** RULES.md forbids component denylists and pre-rewrite checks; GameTests cover each bypass.

## [2026-09-28 16:49] - First start held an empty policy while the file held the sample
**Context:** New GameTest `filesFailClosedAndPersist`, first-start step.
**Error:** After the sample policy was written, `/creativeadmin status` and the admin screen listed no profile, while the file on disk held the `event` example; a first save from the screen would have dropped it.
**Root cause:** `readPolicy(true)` wrote `PolicyDocument.sample()` to disk but kept `PolicyDocument.disabled()` in memory, so memory and file disagreed until the next reload.
**Fix:** Memory takes the sample when the write succeeds, `disabled()` only when it fails. Both are unenforced, so nothing changes for players.
**Prevention:** What is written to disk and what is held in memory come from the same document; the GameTest asserts the example profile is visible right after first start.
