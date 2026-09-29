# Arcadia Creative Admin

**Lock creative tabs and items, per group of players, from an in-game admin screen.**
**Verrouillez des onglets et objets créatifs, par groupe de joueurs, depuis une interface en jeu.**

🇬🇧 [English](#english) · 🇫🇷 [Français](#français)

---

## English

Creative mode on a server is all or nothing: a builder who gets it can take a wither spawn egg, a stack of TNT or lava buckets as easily as a plank. This mod gives each group of players a creative inventory with only what you decided, and the server checks every item they take.

Enforcement is server-side and does not depend on the client. A player who removes their mods, edits their config or joins with a vanilla client is held to the same rules.

**Contents**

1. [Features](#features) — admin screen, profiles, tabs and items, broader rules, players, help
2. [Who is restricted](#who-is-restricted) — bypass, assignments, groups, default profile
3. [What is and is not restricted](#what-is-and-is-not-restricted) — owned items, components, limits
4. [Compatibility](#compatibility) — LuckPerms, CustomPerm, Better Creative, vanilla clients
5. [Requirements](#requirements)

---

### Features

**Admin screen** — Open it with `/creativeadmin`, or with the flag button above the creative inventory, shown to admins only. Everything is managed there; changes are kept on your screen until you press Save, and a save based on an outdated copy is refused rather than overwriting another admin's work.

**Profiles** — Each profile has a mode. **Whitelist**: everything is locked except what you open. **Blacklist**: everything is open except what you lock. Switching the mode keeps the selection and flips its meaning.

**Tabs and items** — Pick a tab, lock or open the whole page, or click single items in the grid. A search box looks through every item. The grid shows the final result of every rule, so what it shows locked is what players find locked.

**Broader rules** *(optional)* — Whole mods, item tags, and which data components an item may carry.

**Players** — Assign a profile to a player in one click, or leave them to their group or the default profile.

**Built-in help** — A **?** button in the screen's title bar explains every page, the rules, the permissions, the files and the commands, in English and French.

**Commands** — `status`, `reload`, `tabs`, `check [profile]` and `profile`, from the console and from vanilla clients.

---

### Who is restricted

For each player, the first match wins:

1. The **bypass**: permission `arcadiacreativeadmin.bypass`, or an op level at or above the configured one when no permission mod is installed. Never restricted.
2. A profile **assigned** to that player in the admin screen or with `/creativeadmin profile`.
3. A profile given by the player's **group**, through `arcadiacreativeadmin.profile` set as meta: `/lp group builders meta set arcadiacreativeadmin.profile event`, or `/customperm grade meta builders set arcadiacreativeadmin.profile event`.
4. The **default profile**. Without one, the player is not restricted.

The admin screen and the commands require `arcadiacreativeadmin.admin`, or op level 3 without a permission mod.

---

### What is and is not restricted

The mod locks what **creative mode hands out**, from the creative menu, search, saved hotbars, pick-block and item browser mods, plus middle-click cloning in containers. It never touches what players already own: an item they carry can be moved, split and dropped even if their profile locks it, and a stack cannot be duplicated by moving it.

An open item cannot smuggle a locked one: spawn eggs, item frames, chests with a loot table and the like are checked by their data components. What the creative menu offers and what ordinary play produces passes; anything else needs the component to be allowed in the profile.

Not covered, on purpose: items already in the world, command and structure blocks (vanilla gates them at op level 2), `/give` (op level 2), and lag built from open blocks.

The mod **fails closed**: a broken rules file refuses creative items to restricted players until it is fixed, and the admin screen turns read-only so it cannot overwrite the file you need to repair.

---

### Compatibility

- **LuckPerms**, **CustomPerm**, or any mod implementing NeoForge's permission API: no dependency, no configuration.
- **Arcadia Better Creative**: installed together, the tab bar is both filtered and sorted, and a locked tab is also left out of Better Creative's settings screen. Neither mod needs the other.
- **Vanilla clients** can join and are restricted like everyone else. With the mod on their client, players simply stop seeing what is locked.

---

### Requirements

- Minecraft **1.21.1**
- **NeoForge** 21.1.241 or newer
- Java 21
- On the **server**: required. On a **client**: for admins who use the screen, and optionally for players, to hide locked tabs and items.

Rules are stored in `config/arcadia/arcadia-creative-admin-policy.json`, editable by hand and applied with `/creativeadmin reload`.

---
---

## Français

Le mode créatif sur un serveur, c'est tout ou rien : un constructeur qui l'obtient peut prendre un œuf d'apparition de wither, une pile de TNT ou des seaux de lave aussi facilement qu'une planche. Ce mod donne à chaque groupe de joueurs un inventaire créatif ne contenant que ce que vous avez décidé, et le serveur vérifie chaque objet qu'ils prennent.

L'application des règles se fait côté serveur et ne dépend pas du client. Un joueur qui retire ses mods, modifie sa configuration ou se connecte avec un client vanilla est soumis aux mêmes règles.

**Sommaire**

1. [Fonctionnalités](#fonctionnalités) — interface d'administration, profils, onglets et objets, règles larges, joueurs, aide
2. [Qui est restreint](#qui-est-restreint) — contournement, affectations, groupes, profil par défaut
3. [Ce qui est restreint, et ce qui ne l'est pas](#ce-qui-est-restreint-et-ce-qui-ne-lest-pas) — objets possédés, composants, limites
4. [Compatibilité](#compatibilité) — LuckPerms, CustomPerm, Better Creative, clients vanilla
5. [Prérequis](#prérequis)

---

### Fonctionnalités

**Interface d'administration** — Elle s'ouvre avec `/creativeadmin`, ou avec le bouton drapeau au-dessus de l'inventaire créatif, visible des seuls admins. Tout se gère depuis là ; les modifications restent sur votre écran jusqu'à Enregistrer, et un enregistrement fondé sur une copie périmée est refusé au lieu d'écraser le travail d'un autre admin.

**Profils** — Chaque profil a un mode. **Liste blanche** : tout est verrouillé sauf ce que vous ouvrez. **Liste noire** : tout est ouvert sauf ce que vous verrouillez. Changer de mode garde la sélection et inverse son sens.

**Onglets et objets** — Choisissez un onglet, verrouillez ou ouvrez toute la page, ou cliquez sur des objets de la grille. Un champ de recherche parcourt tous les objets. La grille montre le résultat final de toutes les règles : ce qu'elle montre verrouillé est ce que les joueurs trouvent verrouillé.

**Règles larges** *(facultatif)* — Des mods entiers, des tags d'objets, et les composants de données qu'un objet peut porter.

**Joueurs** — Affectez un profil à un joueur en un clic, ou laissez son groupe ou le profil par défaut décider.

**Aide intégrée** — Un bouton **?** dans la barre de titre explique chaque page, les règles, les permissions, les fichiers et les commandes, en français et en anglais.

**Commandes** — `status`, `reload`, `tabs`, `check [profil]` et `profile`, depuis la console et depuis un client vanilla.

---

### Qui est restreint

Pour chaque joueur, la première règle qui correspond l'emporte :

1. Le **contournement** : permission `arcadiacreativeadmin.bypass`, ou un niveau d'op égal ou supérieur à celui configuré quand aucun mod de permissions n'est installé. Jamais restreint.
2. Un profil **affecté** à ce joueur dans l'interface ou avec `/creativeadmin profile`.
3. Un profil donné par le **groupe** du joueur, via `arcadiacreativeadmin.profile` en meta : `/lp group builders meta set arcadiacreativeadmin.profile event`, ou `/customperm grade meta builders set arcadiacreativeadmin.profile event`.
4. Le **profil par défaut**. Sans lui, le joueur n'est pas restreint.

L'interface et les commandes demandent `arcadiacreativeadmin.admin`, ou le niveau d'op 3 sans mod de permissions.

---

### Ce qui est restreint, et ce qui ne l'est pas

Le mod verrouille ce que **distribue le mode créatif** : menu créatif, recherche, barres d'action sauvegardées, clic molette et mods de navigation d'objets, plus le clonage au clic molette dans les conteneurs. Il ne touche jamais à ce que les joueurs possèdent déjà : un objet porté peut être déplacé, divisé et jeté même si son profil le verrouille, et une pile ne peut pas être dupliquée en la déplaçant.

Un objet ouvert ne peut pas en faire passer un verrouillé : œufs d'apparition, cadres, coffres avec une table de butin et autres sont vérifiés d'après leurs composants de données. Ce qu'offre le menu créatif et ce que produit le jeu normal passe ; le reste demande que le composant soit autorisé dans le profil.

Non couvert, volontairement : les objets déjà présents dans le monde, les blocs de commande et de structure (la vanilla les réserve au niveau d'op 2), `/give` (niveau d'op 2), et le lag construit avec des blocs ouverts.

Le mod **échoue fermé** : un fichier de règles cassé refuse les objets créatifs aux joueurs restreints jusqu'à sa correction, et l'interface passe en lecture seule pour ne pas écraser le fichier à réparer.

---

### Compatibilité

- **LuckPerms**, **CustomPerm**, ou tout mod implémentant l'API de permissions de NeoForge : aucune dépendance, aucune configuration.
- **Arcadia Better Creative** : installés ensemble, la barre d'onglets est à la fois filtrée et triée, et un onglet verrouillé est aussi retiré de l'écran de réglages de Better Creative. Aucun des deux n'a besoin de l'autre.
- Les **clients vanilla** peuvent se connecter et sont restreints comme les autres. Avec le mod sur leur client, les joueurs ne voient simplement plus ce qui est verrouillé.

---

### Prérequis

- Minecraft **1.21.1**
- **NeoForge** 21.1.241 ou plus récent
- Java 21
- Sur le **serveur** : obligatoire. Sur un **client** : pour les admins qui utilisent l'interface, et en option pour les joueurs, afin de masquer les onglets et objets verrouillés.

Les règles sont stockées dans `config/arcadia/arcadia-creative-admin-policy.json`, modifiable à la main et appliqué avec `/creativeadmin reload`.

---

*Author: THEFricadelle — All rights reserved / Tous droits réservés.*

**Modpacks are welcome** — no need to ask, as long as your pack references the official CurseForge / Modrinth file, unmodified. Re-uploading it elsewhere, bundling the jar in an exported/offline pack, or shipping a modified build still needs written permission. The source is public and pull requests are welcome; it is not open-source. Full terms: [LICENSE](https://github.com/Team-Arcadia/Arcadia-Creative-Admin/blob/main/LICENSE) — plain-language summary: [NOTICE.md](https://github.com/Team-Arcadia/Arcadia-Creative-Admin/blob/main/NOTICE.md).

**Les modpacks sont les bienvenus** — sans rien demander, tant que votre pack référence le fichier officiel CurseForge / Modrinth, non modifié. Le ré-uploader ailleurs, empaqueter le jar dans un pack exporté / hors-ligne ou diffuser un build modifié requiert toujours une autorisation écrite. Le code est public et les pull requests sont bienvenues ; ce n'est pas pour autant de l'open-source. Conditions complètes : [LICENSE](https://github.com/Team-Arcadia/Arcadia-Creative-Admin/blob/main/LICENSE) — résumé en langage clair : [NOTICE.md](https://github.com/Team-Arcadia/Arcadia-Creative-Admin/blob/main/NOTICE.md).
