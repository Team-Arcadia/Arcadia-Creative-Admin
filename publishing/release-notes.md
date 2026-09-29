## Arcadia Creative Admin 2.0.0

First public release. Lock creative tabs and items per group of players, from an in-game admin screen, with the server enforcing every rule.

### New

- **Admin screen** — `/creativeadmin`, or the flag button above the creative inventory for admins: profiles and their mode, the tabs and items they lock, mods, tags and components, server settings and player assignments. Edits are saved on demand, and a save based on an outdated copy is refused instead of overwriting another admin's work.
- **In-game help** — The **?** button in the screen's title bar explains every page, the rules, the permissions, the files and the commands, in English and French.
- **Whitelist and blacklist profiles** — A whitelist locks everything except the selection, a blacklist opens everything except it. The selection combines whole tabs, whole mods, item tags and single items, with exceptions.
- **Permission-based targeting** — `arcadiacreativeadmin.bypass`, `arcadiacreativeadmin.admin`, and `arcadiacreativeadmin.profile` set as group meta. Works with LuckPerms and CustomPerm without a dependency. An assignment made in game outranks the group, which outranks the default profile.
- **Server-side enforcement** — Every item a restricted player takes in creative is checked by the server, whatever the client runs: the creative menu, search, saved hotbars, pick-block, item browser mods, middle-click cloning in containers.
- **Owned items are left alone** — Moving, splitting or dropping an item a player already owns is never refused, and cannot be used to duplicate it.
- **Component guards** — An open item cannot smuggle a locked one through its data components; block entity data and filled containers each have their own switch, off by default.
- **Locked tabs and items hidden** — Players with the mod on their client no longer see what is locked. With Arcadia Better Creative, locked tabs are also left out of its settings screen.
- **Fail-closed files** — A broken policy or assignment file refuses creative items to restricted players until fixed, and the admin screen turns read-only so it cannot overwrite the file to repair.
- **Operator commands** — `status`, `reload`, `tabs`, `check [profile]` and `profile`, from the console and from vanilla clients.

### Requirements

Minecraft 1.21.1 · NeoForge 21.1.241+ · Java 21 · required on the server; on a client only for the admin screen and to hide locked content.

---
