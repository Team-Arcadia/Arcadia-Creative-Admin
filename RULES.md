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
| Side | **Both** (`side = "BOTH"`): every enforcement path is server-side; the admin screen and the display filter are client-side |
| Dependencies | NeoForge `[21.1.241,)`, Minecraft `[1.21.1,1.22)` |

Companion project: **Arcadia Better Creative** (client-only tab layout). The two are independent
jars with no build or runtime dependency between them. They coexist by chaining on the same
`@ModifyExpressionValue` hook, at different mixin priorities, and `client.BetterCreativeBridge`
hands locked tabs to Better Creative's `api.ServerTabPolicy`, looked up by name at runtime. That
class name and its `set(String, Set)` / `clear()` signatures are a contract between the two repos.

The interface kit in `client/gui/kit/` is shared in style with Better Creative. Keep the two copies
visually aligned; a change to one is a change to propose for the other.

## 2. Git Workflow

- `dev` — where all work lands, features and fixes alike. No feature or fix branches.
- `main` — released, working state only. Reached by merging `dev` at release time.
- Pushes are grouped: commit each change as usual, push `dev` once enough commits have accumulated
  (10 small ones, or 5 substantial ones), when a remote-dependent step needs it, or at the end of a
  session. Never push after every commit.
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
- **Never call `CreativeModeTabs.tryRebuildTabContents` from server code on an integrated server.** The client shares
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
- **Never judge what a player already owns.** The mod locks what creative hands out. A stack that
  `CreativeLedger` credits (it left a slot through a creative packet) is a move and passes untouched;
  only the uncovered part of a write is a creation. Never credit anything the server-side inventory
  did not actually lose, or moves become a duplication path.
- **Never save from the admin screen without the three server checks:** the `admin` permission on
  every request, the base revision equal to the current one, and `PolicyManager.validationProblem`.
  Never save while the policy file is broken: it would overwrite the file the operator must repair.
- **Never decide who is restricted outside `CreativePermissions`.** Bypass, profile and admin all go
  through NeoForge permission nodes so any permission mod answers them; op levels are only the
  defaults of those nodes.
- **Never accept an unknown key in the policy file.** It is usually a typo of a key that matters, or
  a field from an old format; read loosely, either can widen a profile.
- **Never reference a client-only class from code loaded on a dedicated server**, in particular from
  the payload registration. Client-bound handlers go through `PolicyNetwork.ClientHandler`, installed
  at client setup; received advice lives in `core.ReceivedAdvice`, which holds no client type.

## 4. Project Structure

```
Arcadia-Creative-Admin/
├── build.gradle                  ModDevGradle setup: client, server and gameTestServer runs
├── gradle.properties             Version and metadata single source of truth
├── RULES.md                      This file
├── README.md                     Bilingual EN/FR documentation
├── CHANGELOG.md                  Bilingual EN/FR changelog
├── src/main/
│   ├── java/net/thefricadelle/arcadiacreativeadmin/
│   │   ├── ArcadiaCreativeAdmin.java             Entry point, server, login, permission and reload events
│   │   ├── PolicyLifecycle.java                  Refresh order: policy, tab index, advice, open screens
│   │   ├── policy/
│   │   │   ├── CreativeProfile.java              One profile: mode, selection, exceptions, component rules
│   │   │   ├── PolicyDocument.java               The whole policy file as one value
│   │   │   ├── PolicyCodec.java                  File format, both directions, strict on keys
│   │   │   ├── PolicyEvaluator.java              (profile, stack) -> Decision
│   │   │   ├── ComponentRules.java               Components every profile accepts
│   │   │   ├── CreativeLedger.java               What a player moved out of a slot: moves, not creations
│   │   │   ├── CreativePermissions.java          admin / bypass / profile permission nodes
│   │   │   ├── Decision.java                     Verdict plus the reason shown to the player
│   │   │   ├── PolicyManager.java                Files, revisions, assignments, profile resolution
│   │   │   └── PolicyEnforcer.java               Hook logic: ledger, refusal, resync, feedback
│   │   ├── core/
│   │   │   ├── TabItemIndex.java                 Tab id -> items, and creative menu variants
│   │   │   ├── AdviceResolver.java               Profile -> visible tabs and locked items, cached
│   │   │   └── ReceivedAdvice.java               Client-held advice; no client-only types
│   │   ├── command/CreativeAdminCommand.java     /creativeadmin
│   │   ├── network/
│   │   │   ├── PolicyNetwork.java                Payload registration, advice sending
│   │   │   ├── AdvicePayload.java                Server -> client display advice
│   │   │   ├── AdminPayloads.java                Admin screen messages
│   │   │   ├── AdminServer.java                  Server side of the admin screen
│   │   │   └── NetCodecs.java                    Bounded wire format of a policy
│   │   ├── client/
│   │   │   ├── ClientEvents.java                 Handler install, cleanup on disconnect
│   │   │   ├── BetterCreativeBridge.java         Locked tabs to Better Creative, by name
│   │   │   ├── gui/kit/                          Arcadia interface kit
│   │   │   └── screen/                           Admin screens and their working copy
│   │   └── mixin/
│   │       ├── ServerGamePacketListenerImplMixin.java   The enforcement hooks
│   │       └── client/CreativeModeInventoryScreenMixin.java  Display filter and admin button, priority 1300
│   └── resources/
│       ├── META-INF/neoforge.mods.toml
│       ├── arcadia-creative-admin.mixins.json
│       └── assets/arcadiacreativeadmin/lang/{en_us,fr_fr}.json
└── src/gametest/                 GameTests, never shipped in the jar
```

## 5. Adding a New Rule Shape (Step by Step)

1. Work on `dev`.
2. Add the field to `CreativeProfile`: the record is the contract.
3. Read and write it in `PolicyCodec` (and add the key to `PROFILE_KEYS`), then in `NetCodecs`.
   Malformed entries are dropped and logged; a malformed structure refuses the file.
4. Match it in `PolicyEvaluator.isGroupSelected`, cheapest lookup first, and report entries that
   match nothing in `PolicyManager.validateRules`.
5. Expose it in the admin screen: `AdminSession.Draft`, then the page that edits it.
6. Add every user-facing string to **both** `en_us.json` and `fr_fr.json`.
7. Document the field in the README, EN and FR, and cover it with a GameTest.
8. Run the testing checklist below, then update `CHANGELOG.md` (EN + FR). Do **not** bump the version.

## 6. Testing Checklist

Anything that can be automated is automated; what is left manual says why.

### Automated

```bash
./gradlew testAll              # everything below, in order; fails on any failure
./gradlew build                # compiles, unit tests, verifyJar
./gradlew runGameTestServer    # GameTests on a server (18)
./gradlew runAdminSmoke        # the admin screen on a real client (opens a window)
```

- **Unit tests** (`src/test`): language files agree key for key and placeholder for placeholder,
  every key the code asks for exists (computed ones included) and none is unused, the mixin config
  names real classes and no refmap, and the generated metadata requires the NeoForge version the
  mod is built against.
- **`verifyJar`**: the release jar holds the licensing files, both languages and the mixin config,
  and no class compiled from `src/gametest`, whatever its package.
- **GameTests** (`src/gametest/.../gametest`): the evaluation rules, every component bypass, the
  file and wire formats, the packet hooks (slot write, drop, clone), owned items, profile
  resolution (bypass, assignment, group meta, default), op levels and the admin and bypass nodes
  through `TestPermissions` (a stand-in handler answering like LuckPerms and CustomPerm), every
  subcommand from the console and its refusal for players, first start, broken and removed files,
  persistence, stale and valid saves.
- **Admin screen smoke run** (`src/gametest/.../client/screen/AdminScreenSmokeTest`): a
  singleplayer world where the host is an admin; the flag button and `/creativeadmin`, refused
  profile names, create, duplicate and delete behind its confirmation, a whole page and a single
  item in the grid, the search, the mode, a mod, a tag, an unknown and a real component, both
  switches, save (and the file on disk), a refused save that keeps the edits, undo, the discard
  confirmation, the players page with an edit pending, the help page opened from the title bar and
  closed with the pending edits intact, and the read-only banner of a broken file. In English and
  French, at GUI sizes 427x240, 480x270, 640x360 and 960x540 and in both modes, every button label,
  field hint, status help, note, group help line and help topic heading of the five pages is
  measured against its room; text is ellipsized rather than overflowing, so only this check sees a
  cut label.
  Screenshots of each page land in `run/adminsmoke/screenshots` for a person to look at. It lives
  in the screens' package, in the GameTest source set, to read their session state; `verifyJar`
  keeps it out of the jar.
- In the Arcadia Better Creative repository, `runPairSmoke` (both mods on a dedicated server) and
  `runArcadiaSmoke` (both mods in the Arcadia pack, LuckPerms and spark included) cover this mod
  next to Better Creative and inside the pack.

### Manual

- [ ] Bytecode target check: `javap -c` on the compiled `ServerGamePacketListenerImpl` still shows
      `Slot.setByPlayer(ItemStack)` and `ServerPlayer.drop(ItemStack, boolean)` exactly once inside
      `handleSetCreativeModeSlot`, after `BlockEntity.saveToItem`, and
      `AbstractContainerMenu.clicked(int, int, ClickType, Player)` exactly once inside
      `handleContainerClick`; same for `CreativeModeTabRegistry.getSortedCreativeModeTabs` inside
      `CreativeModeInventoryScreen.init`. Only when the Minecraft or NeoForge version changes.
- [ ] How the admin screens look: the screenshots of the smoke run, at the default window size and
      at a larger one, in English and in French. Labels must not be cut.
- [ ] A **vanilla client** can connect and is refused a locked item (the channel is optional).
- [ ] Singleplayer: the screen opens and nothing is enforced for the host.
- [ ] A restricted player sees no flag button, and locked items are not drawn inside an open tab.
- [ ] Two admins at once: the second one sees the stale banner (the refusal itself is a GameTest).
- [ ] JEI cheat mode is refused a locked item.

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
