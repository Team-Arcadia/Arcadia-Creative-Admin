# Project Rules & AI/IDE Instructions

## 1. Project Identity

| Field | Value |
| --- | --- |
| Project name | Arcadia Creative Admin |
| Mod ID | `arcadiacreativeadmin` (FML `ModInfo` enforces `^[a-z][a-z0-9_]{1,63}$` — **hyphens are rejected**) |
| File slug | `arcadia-creative-admin` (`mod_slug`, used for jar and file names only) |
| Package | `net.thefricadelle.arcadiacreativeadmin` |
| Tech stack | Java 21, Minecraft 1.21.1, NeoForge 21.1.241, ModDevGradle 2.0.142, Gradle 8.12, SpongePowered Mixin + MixinExtras |
| Author | THEFricadelle |
| License | All Rights Reserved |
| Side | **Both** (`side = "BOTH"`), but every enforcement path is server-side |
| Dependencies | NeoForge `[21.1.0,)`, Minecraft `[1.21.1,1.22)` |

Companion project: **Arcadia Better Creative** (client-only tab layout). The two are independent
jars with no build or runtime dependency between them. They coexist by chaining on the same
`@ModifyExpressionValue` hook, at different mixin priorities.

## 2. Git Workflow

- `main` — released, working state only.
- `feat/<name>` — new features. `fix/<name>` — bug fixes.
- Commit convention: `type: short imperative message` (`feat:`, `fix:`, `docs:`, `refactor:`, `chore:`).
- **Never** add co-author trailers or any AI/tool attribution to a commit.
- Version bumps happen **only** on explicit request.

## 3. Code Conventions

- All code, comments, logs and identifiers in **English**. User-facing strings go through lang files.
- Naming: `PascalCase` types, `camelCase` members, `UPPER_SNAKE_CASE` constants.
- Mixin injector methods **must** be prefixed `arcadiacreativeadmin$`.
- Comments explain *why*, never *what*.

**What NOT to do — project-specific:**

- **Never fail open.** This is an enforcement component. A missing profile, an unreadable file, a
  parse error or an unexpected exception must all resolve to a refusal. Failing open hands a full
  creative inventory to whoever the broken entry pointed at, which is the exact outcome the mod
  exists to prevent. This is the inverse of Better Creative's rule, where a broken config must never
  stop the inventory from opening — there, the worst case is a cosmetic annoyance.
- **Never trust the client.** The tab payload is advisory. Nothing may be decided from what a client
  sends or from whether it has the mod at all.
- **Never call `CreativeModeTabs.tryRebuildTabContents` on an integrated server.** The client shares
  the JVM and the same static `CACHED_PARAMETERS`; rebuilding with a forced operator flag changes
  what the player's own creative screen displays. Guard it with `server.isDedicatedServer()`.
- **Never evaluate only the outer stack.** A whitelist that stops before container components is one
  shulker box away from being no whitelist at all.
- **Never judge components with a denylist.** `ComponentRules` lists what is harmless; everything
  else needs a creative menu variant or `allowed_components`. A denylist is one modded component
  away from being open again.
- **Never check a creative stack before vanilla is done with it.** `handleSetCreativeModeSlot`
  rewrites `block_entity_data` carrying `x/y/z` into the world's block entity, contents included.
  The hooks wrap `setByPlayer` and `drop`, not the handler entry.
- **Never send a payload without `connection.hasChannel`.** An optional registration lets a vanilla
  client join; sending it an unnegotiated payload still throws.
- **Never let the policy file be rewritten by the mod.** It is hand-authored. Assignments go in the
  separate file.
- **Never reference a client-only class from code loaded on a dedicated server** — in particular from
  the payload registration. Received client state lives in `core.ReceivedTabPolicy`, which holds no
  client type on purpose.

## 4. Project Structure

```
Arcadia-Creative-Admin/
├── build.gradle                  ModDevGradle setup, client + dedicated server runs
├── gradle.properties             Version and metadata single source of truth
├── RULES.md                      This file
├── README.md                     Bilingual EN/FR documentation
├── CHANGELOG.md                  Bilingual EN/FR changelog
└── src/main/
    ├── java/net/thefricadelle/arcadiacreativeadmin/
    │   ├── ArcadiaCreativeAdmin.java             Entry point, server + command + login events
    │   ├── PolicyLifecycle.java                  Reload order: policy, tab index, client advice
    │   ├── policy/
    │   │   ├── CreativeProfile.java              One named strict whitelist
    │   │   ├── PolicyEvaluator.java              (profile, stack) -> Decision, no other state
    │   │   ├── ComponentRules.java               Components every profile accepts
    │   │   ├── Decision.java                     Verdict plus the reason shown to the player
    │   │   ├── PolicyManager.java                Files, profiles, assignments, op bypass
    │   │   └── PolicyEnforcer.java               Refusal side effects: resync, feedback, throttle
    │   ├── core/
    │   │   ├── TabItemIndex.java                 Creative tab id -> items, built server-side
    │   │   ├── VisibleTabResolver.java           Profile -> tabs worth displaying, cached
    │   │   └── ReceivedTabPolicy.java            Client-held advice; no client-only types
    │   ├── command/CreativeAdminCommand.java     /creativeadmin
    │   ├── network/
    │   │   ├── TabPolicyPayload.java             Advisory server -> client payload
    │   │   └── PolicyNetwork.java                Optional channel registration and sending
    │   ├── client/ClientEvents.java              Drops received advice on disconnect
    │   └── mixin/
    │       ├── ServerGamePacketListenerImplMixin.java   The enforcement hooks
    │       └── client/CreativeModeInventoryScreenMixin.java  Display filter, priority 1300
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── arcadia-creative-admin.mixins.json
        └── assets/arcadiacreativeadmin/lang/{en_us,fr_fr}.json
src/gametest/                     GameTests, never shipped in the jar
```

## 5. Adding a New Rule Shape (Step by Step)

1. `git checkout -b feat/<name>` from `main`.
2. Add the field to `CreativeProfile` — the record is the contract.
3. Parse it in `PolicyManager.readProfile`. Malformed entries are dropped and logged: under a
   whitelist a typo must narrow, never widen.
4. Match it in `PolicyEvaluator.isAllowed`, cheapest lookup first.
5. Add every user-facing string to **both** `en_us.json` and `fr_fr.json`.
6. Document the field in the README table, EN and FR.
7. Run the testing checklist below.
8. Update `CHANGELOG.md` (EN + FR). Do **not** bump the version.

## 6. Testing Checklist

- [ ] `./gradlew build` succeeds.
- [ ] `./gradlew runGameTestServer` reports every required test passed.
- [ ] Bytecode target check: `javap -c` on the compiled `ServerGamePacketListenerImpl` still shows
      `Slot.setByPlayer(ItemStack)` and `ServerPlayer.drop(ItemStack, boolean)` exactly once inside
      `handleSetCreativeModeSlot`, after `BlockEntity.saveToItem`, and
      `AbstractContainerMenu.clicked(int, int, ClickType, Player)` exactly once inside
      `handleContainerClick`. Compilation does **not** validate this.
- [ ] Same check for `CreativeModeTabRegistry.getSortedCreativeModeTabs` inside
      `CreativeModeInventoryScreen.init`.
- [ ] `./gradlew runServer` reaches "Done" with no `InvalidInjectionException` in the log.
- [ ] First start on a fresh config writes a disabled sample policy.
- [ ] With `enforced: false`, creative mode behaves exactly like vanilla.
- [ ] With `enforced: true`, a non-op player is refused an item outside their profile, and the item
      does not stay in their inventory after a reconnect (client resync works).
- [ ] Refusal by the `slotNum < 0` drop path: the item does not land on the ground either.
- [ ] A filled shulker box is refused with `allow_container_contents: false`, and its **contents**
      are what gets refused when the flag is on and they are not whitelisted.
- [ ] An item carrying `block_entity_data` is refused while the same item without it is allowed.
- [ ] A spawn egg or item frame carrying `entity_data` is refused; a renamed allowed item is not.
- [ ] Middle-click in an open chest does not clone a forbidden stack for a restricted player.
- [ ] Deleting the policy file then running `/creativeadmin reload` denies everything.
- [ ] A `tabs` rule matches on a **dedicated** server, where tab contents are not built by default.
- [ ] On an integrated server (singleplayer), the player's own creative screen is unchanged when the
      policy is off — no forced tab rebuild.
- [ ] A player above `bypass_op_level` is unrestricted.
- [ ] `/creativeadmin reload` applies a changed profile without a restart, and connected clients get
      the new tab advice.
- [ ] A **vanilla client** can still connect (the channel is registered optional).
- [ ] With both this mod and Arcadia Better Creative installed, the tab bar is both filtered and
      ordered, and neither mod throws.
- [ ] Spamming refused clicks produces at most one message per 1.5 s.

## 7. AI Assistant Instructions

1. **Read the decompiled source before writing or editing a mixin.** Never write injection points
   from memory.
2. **Verify injection targets at the bytecode level** with `javap -c`. A successful `compileJava`
   proves nothing about whether a mixin resolves.
3. **Never claim a runtime behaviour is verified** unless a server was actually started and the
   refusal actually observed in game. Reaching "Done" only proves the mod loads.
4. Never bump the version number without an explicit request.
5. Never add AI or co-author attribution anywhere in the codebase or in commits.
6. Every user-facing string must land in both `en_us.json` and `fr_fr.json` in the same change.
7. **Prefer denying over widening.** This is the opposite of the sibling project's fallback rule and
   is the single most important invariant here.
