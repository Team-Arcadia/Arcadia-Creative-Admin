# Creative Admin

[![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-blue.svg)](LICENSE)

Lock creative tabs and items, per group of players, from an in-game admin screen. Staff run building
events without handing out the items that break a server, and players browse a creative inventory
where locked pages and items are simply not there.

Enforcement is server-side and does not depend on the client. A player who removes their mods, edits
their config, or joins with a vanilla client is subject to the same rules.

## The admin screen

Open it with `/creativeadmin`, or with the flag button above the creative inventory, shown to admins
only. The screen needs this mod on the admin's client; players do not need it.

- **Profiles.** Create, duplicate and delete them. Each has a **mode**:
  - **Whitelist**: everything is locked except what you open.
  - **Blacklist**: everything is open except what you lock.
- **Tabs and items.** Pick a tab, then lock or open the whole page, or click single items in the
  grid. A search box looks through every item. The grid shows the final result of every rule of the
  profile, so what it shows locked is what players find locked.
- **Mods, tags, components.** Optional, for broader rules: whole mods, item tags, and which data
  components an item may carry.
- **Server settings.** Restrictions on or off, the default profile, and the op level from which
  players are never restricted.
- **Players.** Assign a profile to a player, or leave them to their group or the default.

Changes are kept locally until you press **Save**. If another admin saved in the meantime, your save
is refused rather than overwriting theirs.

The **?** button in the screen's title bar opens an in-game help: how restriction works, each page of
the screen, groups and permissions, the files, the commands, and what the mod does not cover.

## Who is restricted

For each player, first match wins:

1. The **bypass**: permission `creativeadmin.bypass`, or op level at or above the configured
   level when no permission mod is installed. Never restricted.
2. A profile **assigned** to that player in the admin screen or with `/creativeadmin profile`.
3. A profile given by the player's **group**, through the permission `creativeadmin.profile`
   set as meta. Works with LuckPerms and CustomPerm, or any mod implementing NeoForge's permission
   API:
   ```
   /lp group builders meta set creativeadmin.profile event
   ```
4. The **default profile**. If there is none, the player is not restricted.

The admin screen and the commands require `creativeadmin.admin`, or op level 3 without a
permission mod.

Servers set up before 2.1.0 used the nodes `arcadiacreativeadmin.admin`, `.bypass` and `.profile`.
They still work: whenever the `creativeadmin.*` node is not set for a player, the former node is
read instead, explicit denials included. Setting the new node overrides the old one.

## What is and is not restricted

The mod locks what **creative mode hands out**. It never touches what players already own: an item a
player carries can be moved, split and dropped in the creative inventory even if their profile locks
it. Only a creation is checked, and a stack cannot be duplicated by moving it.

Checked:
- every item taken from the creative menu, from search, from saved hotbars, by middle-click
  pick-block, or from any client-side item browser;
- middle-click cloning inside an open container, since it copies a stack.

An open item id can still carry a payload in its data components: a spawn egg or an item frame
holding another item, a chest with a loot table, a stick with attribute modifiers. A stack passes
when it is exactly one the creative menu offers (a potion, a painting variant, an enchanted book), or
when every component it carries is one ordinary play produces: a name, lore, damage, dye, map data,
banner patterns, fireworks, a player head, armor trims, a signed book in plain text. Anything else
needs the component to be listed in the profile. Two dedicated switches, both off by default, allow
block entity data and filled containers; container contents follow the same profile.

## Configuration file

`config/creative-admin/policy.json` is managed by the admin screen. It can still be
edited by hand and applied with `/creativeadmin reload`, but formatting is rewritten on the next save
from the screen.

```json
{
  "enforced": true,
  "default_profile": "event",
  "bypass_op_level": 4,
  "profiles": {
    "event": {
      "mode": "whitelist",
      "tabs": ["minecraft:building_blocks", "minecraft:colored_blocks"],
      "namespaces": [],
      "tags": ["#minecraft:beds"],
      "items": ["minecraft:torch"],
      "exceptions": ["minecraft:tnt"],
      "allow_block_entity_data": false,
      "allow_container_contents": false,
      "allowed_components": []
    }
  }
}
```

`tabs`, `namespaces`, `tags` and `items` form the selection, whose meaning depends on `mode`.
`exceptions` take single items out of what the tabs, mods and tags select. An item listed in `items`
is selected whatever else says. Every field except `mode` is optional.

Player assignments live in `assignments.json`, written by the screen and the
commands.

The mod fails closed. An unreadable or malformed file, an unknown key, a flag that is not `true` or
`false`, a rule written as a string instead of a list, or a policy file removed before a reload: each
refuses creative items to every restricted player until it is fixed and reloaded, and the log says
why. The admin screen is read-only meanwhile, so it cannot overwrite the file you need to repair.
Only a policy file that does not exist when the server starts is a fresh install: a disabled sample
is written and nothing is enforced.

## Commands

All require `creativeadmin.admin`. Every subcommand works from the console and from a vanilla
client.

| Command | Effect |
| --- | --- |
| `/creativeadmin` | Open the admin screen |
| `/creativeadmin status` | Whether enforcement is on, profiles, default profile, bypass level |
| `/creativeadmin reload` | Re-read both files and re-index tab contents; reports rule entries that match nothing |
| `/creativeadmin tabs` | List the creative tab ids a rule can name |
| `/creativeadmin check [profile]` | Evaluate the held item, and print the rule that refused it |
| `/creativeadmin profile <players> <profile>` | Assign |
| `/creativeadmin profile <players> clear` | Remove the assignment |

## Not covered

- **Items already in the world** or already owned. See above: that is deliberate.
- **Operator blocks.** Command, structure and jigsaw blocks are gated by vanilla at op level 2 and by
  `allowCommands`. Check those separately.
- **Lag built from open blocks.** A profile of harmless items still allows a redstone clock.
- **`/give`.** Vanilla gates it at op level 2; this mod does not touch it.

## Companion mod

**Better Creative** sorts, pins and hides creative tabs, client-side. The two are separate
jars with no dependency between them, in either direction, and neither needs the other to work.
Installed together, the tab bar is both filtered and ordered, and a tab the server locks is also left
out of Better Creative's settings screen, so a player cannot switch it back on. This needs a Better
Creative version with the server tab policy API; older versions still get the tab bar filtered.

## Requirements

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.241 or newer
- On the **server**. On clients it is optional for players, and required for admins who use the
  screen.
- Singleplayer and LAN work, but tab rules only match once the host has opened the creative
  inventory, since that is when the game builds tab contents on an integrated server. Until then
  they match nothing, which refuses rather than allows.

## Credits

Authored and maintained by **THEFricadelle**. Contributions are credited in
[CONTRIBUTORS.md](CONTRIBUTORS.md); see [CONTRIBUTING.md](CONTRIBUTING.md) before opening a pull
request.

Released under [All Rights Reserved](LICENSE). Third-party notices are listed in
[NOTICE.md](NOTICE.md).

---

# Creative Admin (français)

Verrouillez des onglets et des objets créatifs, par groupe de joueurs, depuis une interface
d'administration en jeu. L'équipe organise des events de construction sans distribuer les objets qui
cassent un serveur, et les joueurs parcourent un inventaire créatif où les pages et objets verrouillés
ne sont tout simplement pas là.

L'application est côté serveur et ne dépend pas du client. Un joueur qui retire ses mods, modifie sa
configuration ou se connecte avec un client vanilla est soumis aux mêmes règles.

## L'interface d'administration

Elle s'ouvre avec `/creativeadmin`, ou avec le bouton drapeau au-dessus de l'inventaire créatif,
visible des seuls admins. L'interface nécessite ce mod sur le client de l'admin ; les joueurs n'en ont
pas besoin.

- **Profils.** Création, duplication, suppression. Chacun a un **mode** :
  - **Liste blanche** : tout est verrouillé sauf ce que vous ouvrez.
  - **Liste noire** : tout est ouvert sauf ce que vous verrouillez.
- **Onglets et objets.** Choisissez un onglet, puis verrouillez ou ouvrez la page entière, ou cliquez
  sur des objets de la grille. Une recherche parcourt tous les objets. La grille montre le résultat
  final de toutes les règles du profil : ce qu'elle montre verrouillé, les joueurs le trouvent
  verrouillé.
- **Mods, tags, composants.** Facultatif, pour des règles plus larges : mods entiers, tags d'objets,
  et composants de données qu'un objet peut porter.
- **Réglages du serveur.** Restrictions actives ou non, profil par défaut, et niveau d'op à partir
  duquel un joueur n'est jamais restreint.
- **Joueurs.** Affectez un profil à un joueur, ou laissez son grade ou le profil par défaut décider.

Les modifications restent locales jusqu'à **Enregistrer**. Si un autre admin a enregistré entre-temps,
votre enregistrement est refusé au lieu d'écraser le sien.

Le bouton **?** de la barre de titre ouvre une aide en jeu : le principe des restrictions, chaque page
de l'interface, les groupes et permissions, les fichiers, les commandes, et ce que le mod ne couvre pas.

## Qui est restreint

Pour chaque joueur, la première règle qui s'applique l'emporte :

1. Le **contournement** : permission `creativeadmin.bypass`, ou niveau d'op supérieur ou égal
   au niveau réglé sans mod de permissions. Jamais restreint.
2. Un profil **affecté** à ce joueur dans l'interface ou avec `/creativeadmin profile`.
3. Un profil donné par le **grade** du joueur, via la permission `creativeadmin.profile`
   définie en meta. Fonctionne avec LuckPerms et CustomPerm, ou tout mod qui implémente l'API de
   permissions de NeoForge :
   ```
   /lp group builders meta set creativeadmin.profile event
   ```
4. Le **profil par défaut**. S'il n'y en a pas, le joueur n'est pas restreint.

L'interface et les commandes demandent `creativeadmin.admin`, ou le niveau d'op 3 sans mod de
permissions.

Les serveurs configurés avant la 2.1.0 utilisaient les nœuds `arcadiacreativeadmin.admin`, `.bypass`
et `.profile`. Ils fonctionnent toujours : quand le nœud `creativeadmin.*` n'est pas défini pour un
joueur, l'ancien nœud est lu à la place, refus explicites compris. Définir le nouveau nœud prend le
pas sur l'ancien.

## Ce qui est restreint, et ce qui ne l'est pas

Le mod verrouille ce que **le mode créatif distribue**. Il ne touche jamais à ce que les joueurs
possèdent déjà : un objet qu'un joueur porte peut être déplacé, divisé et jeté dans l'inventaire
créatif même si son profil le verrouille. Seule une création est contrôlée, et déplacer une pile ne
permet pas de la dupliquer.

Contrôlé :
- tout objet pris dans le menu créatif, dans la recherche, dans les barres sauvegardées, au
  clic-molette sur un bloc, ou depuis un navigateur d'objets côté client ;
- le clonage au clic-molette dans un conteneur ouvert, puisqu'il copie une pile.

Un objet ouvert peut encore transporter une charge utile dans ses composants de données : un œuf
d'apparition ou un cadre contenant un autre objet, un coffre avec une table de butin, un bâton avec
des modificateurs d'attributs. Une pile passe si elle est exactement une de celles que propose le
menu créatif (une potion, une variante de tableau, un livre enchanté), ou si chaque composant qu'elle
porte est de ceux que produit le jeu normal : un nom, une description, de l'usure, une teinture, des
données de carte, des motifs de bannière, des feux d'artifice, une tête de joueur, des garnitures
d'armure, un livre signé en texte simple. Tout le reste exige que le composant figure dans le profil.
Deux interrupteurs dédiés, désactivés par défaut, autorisent les données de bloc et les conteneurs
remplis ; le contenu des conteneurs suit le même profil.

## Fichier de configuration

`config/creative-admin/policy.json` est géré par l'interface. Il peut toujours être
modifié à la main puis appliqué avec `/creativeadmin reload`, mais sa mise en forme est réécrite au
prochain enregistrement depuis l'interface. Le format est celui de l'exemple de la section anglaise.

`tabs`, `namespaces`, `tags` et `items` forment la sélection, dont le sens dépend de `mode`.
`exceptions` retire des objets précis de ce que sélectionnent les onglets, mods et tags. Un objet listé
dans `items` est sélectionné quoi qu'en disent les autres règles. Tous les champs sauf `mode` sont
facultatifs.

Les affectations des joueurs sont dans `assignments.json`, écrit par
l'interface et les commandes.

Le mod échoue du côté fermé. Un fichier illisible ou mal formé, une clé inconnue, un drapeau qui n'est
ni `true` ni `false`, une règle écrite comme une chaîne au lieu d'une liste, ou un fichier de politique
supprimé avant un rechargement : chacun refuse les objets créatifs à tous les joueurs restreints
jusqu'à correction et rechargement, et le log en donne la raison. L'interface passe en lecture seule
d'ici là, pour ne pas écraser le fichier à réparer. Seul un fichier absent au démarrage du serveur est
une première installation : un exemple désactivé est écrit et rien n'est appliqué.

## Commandes

Toutes demandent `creativeadmin.admin`. Chaque sous-commande fonctionne depuis la console et
depuis un client vanilla.

| Commande | Effet |
| --- | --- |
| `/creativeadmin` | Ouvre l'interface d'administration |
| `/creativeadmin status` | Application activée ou non, profils, profil par défaut, niveau de contournement |
| `/creativeadmin reload` | Relit les deux fichiers et réindexe les onglets ; signale les entrées qui ne correspondent à rien |
| `/creativeadmin tabs` | Liste les identifiants d'onglets qu'une règle peut nommer |
| `/creativeadmin check [profil]` | Évalue l'objet en main et affiche la règle qui l'a refusé |
| `/creativeadmin profile <joueurs> <profil>` | Affecte |
| `/creativeadmin profile <joueurs> clear` | Retire l'affectation |

## Non couvert

- **Les objets déjà dans le monde** ou déjà possédés. Voir plus haut : c'est voulu.
- **Les blocs d'opérateur.** Blocs de commande, de structure et jigsaw sont déjà filtrés par le
  vanilla au niveau d'op 2 et par `allowCommands`. À vérifier séparément.
- **Le lag construit avec des blocs ouverts.** Un profil d'objets inoffensifs autorise quand même une
  horloge redstone.
- **`/give`.** Le vanilla le filtre au niveau d'op 2 ; ce mod n'y touche pas.

## Mod compagnon

**Better Creative** trie, épingle et masque les onglets créatifs, côté client. Ce sont deux
jars distincts, sans dépendance de l'un vers l'autre ni dans l'autre sens, et aucun n'a besoin de
l'autre pour fonctionner. Installés ensemble, la barre d'onglets est à la fois filtrée et ordonnée, et
un onglet verrouillé par le serveur est aussi absent de l'écran de réglages de Better Creative : un
joueur ne peut pas le réactiver. Il faut pour cela une version de Better Creative dotée de l'API de
politique serveur ; les versions plus anciennes ont quand même la barre d'onglets filtrée.

## Prérequis

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.241 ou plus récent
- Sur le **serveur**. Sur les clients, facultatif pour les joueurs et requis pour les admins qui
  utilisent l'interface.
- Le solo et le LAN fonctionnent, mais les règles d'onglets ne s'appliquent qu'une fois que l'hôte a
  ouvert l'inventaire créatif, puisque c'est à ce moment que le jeu construit le contenu des onglets
  sur un serveur intégré. D'ici là elles ne correspondent à rien, ce qui refuse au lieu d'autoriser.

## Crédits

Écrit et maintenu par **THEFricadelle**. Les contributions sont créditées dans
[CONTRIBUTORS.md](CONTRIBUTORS.md) ; voir [CONTRIBUTING.md](CONTRIBUTING.md) avant d'ouvrir une pull
request.

Publié sous [All Rights Reserved](LICENSE). Les mentions tierces sont listées dans
[NOTICE.md](NOTICE.md).
