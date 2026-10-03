# Changelog

All notable changes to Creative Admin (formerly Arcadia Creative Admin) are documented here.

---

## [Unreleased]

### Added (English first)

- **Mod icon** — The mod list now shows the Creative Admin logo.

### Changed (English first)

- **Renamed to Creative Admin** - The mod is now Creative Admin, mod id `creativeadmin`. The policy
  and the assignments move to `config/creative-admin/policy.json` and `assignments.json`; files
  written by an earlier version are moved there automatically on the first start. Permission nodes
  are now `creativeadmin.admin`, `creativeadmin.bypass` and `creativeadmin.profile`. The former
  `arcadiacreativeadmin.*` nodes keep working: they are read whenever the new node is not set for
  a player, explicit denials included, so no server loses its setup. Players need the 2.1.0 client
  too, since the network channel is named after the mod id; an older client keeps playing but no
  longer receives the locked tabs for display.

- **License updated to version 2.1** - The license text moves from version 1.0 to the current 2.1
  and follows the new name, with the identifier `LicenseRef-Creative-Admin-ARR`. The sections are
  regrouped (the old sections 10 to 15 become 10.1 to 10.5 and 11), it now states that contributing
  grants no right to redistribute, and the modpack permission can only be withdrawn from a given
  maintainer by written notice, never for pack versions already published. The protection of the
  name also covers the former name and mod id. This applies from this release on: 2.0.0 stays
  governed by the 1.0 text it shipped with.

### Fixed (English first)

- **Locked tabs reach Better Creative again after its rename** - Better Creative 2.1.0 changed its
  mod id. The tabs a server locks are handed to both the renamed mod and releases before the
  rename, so they stay out of its settings screen either way.

### Ajouts (French mirror)

- **Icône du mod** — La liste des mods affiche désormais le logo de Creative Admin.

### Modifications (French mirror)

- **Renommé en Creative Admin** - Le mod s'appelle désormais Creative Admin, identifiant
  `creativeadmin`. La politique et les affectations passent dans
  `config/creative-admin/policy.json` et `assignments.json` ; les fichiers écrits par une version
  précédente y sont déplacés automatiquement au premier démarrage. Les nœuds de permission sont
  désormais `creativeadmin.admin`, `creativeadmin.bypass` et `creativeadmin.profile`. Les anciens
  nœuds `arcadiacreativeadmin.*` fonctionnent toujours : ils sont lus quand le nouveau nœud n'est
  pas défini pour un joueur, refus explicites compris, aucun serveur ne perd sa configuration. Les
  joueurs ont aussi besoin du client 2.1.0, le canal réseau portant le nom de l'identifiant ; un
  client plus ancien continue de jouer mais ne reçoit plus les onglets verrouillés pour l'affichage.

- **Licence passée en version 2.1** - Le texte de licence passe de la version 1.0 à la 2.1 actuelle
  et suit le nouveau nom, avec l'identifiant `LicenseRef-Creative-Admin-ARR`. Les sections sont
  regroupées (les anciennes sections 10 à 15 deviennent 10.1 à 10.5 et 11), elle précise que
  contribuer ne donne aucun droit de redistribution, et la permission modpack ne peut être retirée
  à un mainteneur donné que par notification écrite, jamais pour les versions de pack déjà
  publiées. La protection du nom couvre aussi l'ancien nom et l'ancien identifiant. Cela vaut à
  partir de cette version : la 2.0.0 reste régie par le texte 1.0 livré avec elle.

### Corrections (French mirror)

- **Les onglets verrouillés atteignent de nouveau Better Creative après son renommage** - Better
  Creative 2.1.0 a changé d'identifiant. Les onglets verrouillés par le serveur sont transmis au mod
  renommé comme aux versions antérieures, ils restent donc absents de son écran de réglages dans
  les deux cas.

---

## [2.0.0] - 2026-09-29

### Added (English first)

- **Admin screen** — `/creativeadmin`, or the flag button above the creative inventory for admins,
  opens an in-game screen to manage everything: profiles, their mode, the tabs and items they lock,
  mods, tags and components, server settings, and player assignments. Edits are saved on demand, and
  a save based on an outdated copy is refused instead of overwriting another admin's work.
- **In-game help** — The **?** button in the admin screen's title bar opens a help page in English
  and French: how restriction works, profiles and modes, each page of the screen, groups and
  permissions with LuckPerms and CustomPerm examples, saving, the files and their failure mode, the
  commands, Better Creative, and what the mod does not cover. Pending edits are kept while it is open.
- **Whitelist and blacklist profiles** — Each profile has a mode. A whitelist locks everything except
  the selection, a blacklist opens everything except the selection. The selection combines whole
  creative tabs, whole mods, item tags and single items, none of them required; exceptions take
  single items out of a tab, mod or tag, and a single item always outranks the group rules.
- **Permission-based targeting** — Who is restricted goes through NeoForge permission nodes, so
  LuckPerms and CustomPerm work without a dependency: `arcadiacreativeadmin.bypass` is never
  restricted, `arcadiacreativeadmin.profile` set as group meta picks the profile, and
  `arcadiacreativeadmin.admin` opens the screen and the commands. An assignment made in game
  outranks the group, which outranks the default profile.
- **Server-side creative enforcement** — Every item a creative player takes is checked against their
  profile in `handleSetCreativeModeSlot`, including the `slotNum < 0` drop path and middle-click
  cloning in open containers. Enforcement does not depend on the client having the mod.
- **Owned items are left alone** — The mod locks what creative mode hands out, not what players
  carry. Moving, splitting or dropping an item the player already owns is never refused, and cannot
  be used to duplicate it.
- **Component guards** — Data components are checked on an allowlist, so an open item cannot smuggle
  a locked one through `entity_data`, `container_loot` or a filled container, or carry forged combat
  stats. Stacks the creative menu offers and components ordinary play produces pass; anything else
  needs `allowed_components`. Block entity data and filled containers have their own switches, off
  by default.
- **Locked tabs and items hidden on the client** — Players with the mod installed no longer see
  locked tabs, nor locked items inside open tabs or in search. With Arcadia Better Creative, locked
  tabs are also left out of its settings screen so they cannot be switched back on.
- **Fail-closed loading** — An unreadable or malformed policy or assignment file, an unknown key, a
  non-boolean flag or a removed policy file denies creative items to restricted players until fixed,
  and makes the admin screen read-only so it cannot overwrite the file to repair. Files are written
  atomically, and `/creativeadmin reload` reports rule entries that match nothing.
- **Operator commands** — `status`, `reload`, `tabs`, `check [profile]` and `profile`, usable from
  the console and from vanilla clients.

### Ajouts (French mirror)

- **Interface d'administration** — `/creativeadmin`, ou le bouton drapeau au-dessus de l'inventaire
  créatif pour les admins, ouvre une interface en jeu qui gère tout : profils, leur mode, les onglets
  et objets qu'ils verrouillent, mods, tags et composants, réglages du serveur et affectation des
  joueurs. Les modifications sont enregistrées à la demande, et un enregistrement fondé sur une copie
  périmée est refusé au lieu d'écraser le travail d'un autre admin.
- **Aide en jeu** — Le bouton **?** de la barre de titre de l'interface ouvre une page d'aide en
  anglais et en français : le principe des restrictions, les profils et modes, chaque page de
  l'interface, les groupes et permissions avec des exemples LuckPerms et CustomPerm, l'enregistrement,
  les fichiers et leur comportement en cas d'erreur, les commandes, Better Creative, et ce que le mod
  ne couvre pas. Les modifications en cours sont conservées pendant qu'elle est ouverte.
- **Profils en liste blanche ou liste noire** — Chaque profil a un mode. Une liste blanche verrouille
  tout sauf la sélection, une liste noire ouvre tout sauf la sélection. La sélection combine des
  onglets créatifs entiers, des mods entiers, des tags d'objets et des objets seuls, aucun n'étant
  obligatoire ; les exceptions retirent des objets d'un onglet, d'un mod ou d'un tag, et un objet seul
  l'emporte toujours sur les règles de groupe.
- **Ciblage par permissions** — Qui est restreint passe par les nœuds de permission de NeoForge, si
  bien que LuckPerms et CustomPerm fonctionnent sans dépendance : `arcadiacreativeadmin.bypass` n'est
  jamais restreint, `arcadiacreativeadmin.profile` défini en meta de grade choisit le profil, et
  `arcadiacreativeadmin.admin` ouvre l'interface et les commandes. Une affectation faite en jeu
  l'emporte sur le grade, qui l'emporte sur le profil par défaut.
- **Application côté serveur** — Chaque objet pris par un joueur en créatif est contrôlé contre son
  profil dans `handleSetCreativeModeSlot`, y compris le lâcher `slotNum < 0` et le clonage au
  clic-molette dans un conteneur ouvert. L'application ne dépend pas de la présence du mod côté
  client.
- **Les objets possédés ne sont pas touchés** — Le mod verrouille ce que le créatif distribue, pas ce
  que les joueurs portent. Déplacer, diviser ou jeter un objet déjà possédé n'est jamais refusé, et
  ne permet pas de le dupliquer.
- **Gardes sur les composants** — Les composants de données sont contrôlés par liste blanche : un
  objet ouvert ne peut pas faire passer un objet verrouillé via `entity_data`, `container_loot` ou un
  conteneur rempli, ni porter des statistiques de combat forgées. Les piles proposées par le menu
  créatif et les composants du jeu normal passent ; le reste exige `allowed_components`. Données de
  bloc et conteneurs remplis ont leurs propres interrupteurs, désactivés par défaut.
- **Onglets et objets verrouillés masqués côté client** — Les joueurs équipés du mod ne voient plus
  les onglets verrouillés, ni les objets verrouillés dans les onglets ouverts ou la recherche. Avec
  Arcadia Better Creative, les onglets verrouillés sont aussi absents de son écran de réglages et ne
  peuvent pas être réactivés.
- **Chargement fermé par défaut** — Un fichier de politique ou d'affectations illisible ou mal formé,
  une clé inconnue, un drapeau non booléen ou un fichier de politique supprimé refuse les objets
  créatifs aux joueurs restreints jusqu'à correction, et met l'interface en lecture seule pour ne pas
  écraser le fichier à réparer. Les fichiers sont écrits de façon atomique, et
  `/creativeadmin reload` signale les entrées de règle qui ne correspondent à rien.
- **Commandes d'opérateur** — `status`, `reload`, `tabs`, `check [profil]` et `profile`, utilisables
  depuis la console et depuis un client vanilla.
