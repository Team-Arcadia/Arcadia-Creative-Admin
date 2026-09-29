# Publishing checklist

Everything needed to release Arcadia Creative Admin on CurseForge, Modrinth and GitHub Releases.

## Current state

| | |
| --- | --- |
| CurseForge project | not created yet |
| Modrinth project | not created yet |
| Next upload | `2.0.0`, the first public release |
| Git tags | none |

Uploading is irreversible in practice: a published file can be hidden but the version number is
burnt. Do a `-PdryRun` pass first, every time.

---

## 1. Create the projects (a person, once)

**CurseForge** — <https://legacy.curseforge.com/project/create?game=minecraft>
- Category: **Server Utility** (and **Addons → Utility & QoL** if wanted)
- Game version: Minecraft 1.21.1 · Mod loader: NeoForge
- License: **All Rights Reserved** (must match `mod_license`)

**Modrinth** — <https://modrinth.com/dashboard/projects>
- Project type: **Mod**
- Server side: **Required** · Client side: **Optional** (the admin screen, and hiding locked content)
- Categories: **Management**, **Utility**
- License: **All Rights Reserved** (custom / ARR)

Paste `description.md` into both project pages. Upload a 400x400 icon on both platforms; the jar has
no `logoFile` until `src/main/resources/icon.png` exists (add `logoFile = "icon.png"` to
`neoforge.mods.toml` in the same commit, never before the file).

## 2. Fill in the project ids

In `gradle.properties`, public identifiers, safe to commit:

```properties
curseforge_project_id=123456
modrinth_project_id=AbCdEfGh
```

Until both are set, `publishMods` stops with a message naming the missing one.

## 3. Tokens

Read from the environment, never written to disk by the build. PowerShell, current session only:

```powershell
$env:CURSEFORGE_TOKEN = "..."
$env:MODRINTH_TOKEN = "..."
```

Never put them in `gradle.properties`, in a committed file, or in a shell history you push.

## 4. Pre-flight

- [ ] `./gradlew testAll` is green
- [ ] `mod_version` in `gradle.properties` is the version you intend to release
- [ ] `CHANGELOG.md` has a dated section for it
- [ ] `publishing/release-notes.md` starts with that version: its first section is the uploaded changelog
- [ ] `build/libs/` holds only that version's jar

## 5. Dry run, then publish

```bash
./gradlew publishMods -PdryRun     # validates payload, uploads nothing
./gradlew publishMods              # real upload to both platforms
```

## 6. GitHub release and tag

```bash
git tag v2.0.0 && git push origin v2.0.0
gh release create v2.0.0 build/libs/arcadia-creative-admin-2.0.0.jar --title "Arcadia Creative Admin 2.0.0" --notes-file <2.0.0 section of release-notes.md>
```

## 7. After publishing

- [ ] Both store pages show Minecraft 1.21.1, NeoForge, and the server-side environment
- [ ] The changelog shown is the 2.0.0 section only
- [ ] The downloaded jar has the same SHA-256 as `build/libs`

## Version bumps

`mod_version` in `gradle.properties` is the single source of truth. When it changes, update in the
same commit `CHANGELOG.md` (new dated section, English then French) and
`publishing/release-notes.md` (new section on top). Never bump the version as a side effect of a fix.
