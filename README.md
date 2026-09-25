# Arcadia Creative Admin

[![License](https://img.shields.io/badge/license-All%20Rights%20Reserved-blue.svg)](LICENSE)

Server-enforced creative restrictions. Define named profiles listing exactly what players may take in
creative mode, and let staff run building events without handing out the items that break a server.

Enforcement is server-side and does not depend on the client. A player who removes their mods, edits
their config, or joins with a vanilla client is subject to the same rules.

## How it works

Every item a player in creative mode makes appear travels through one packet,
`ServerboundSetCreativeModeSlotPacket`. The mod validates that packet against the player's profile
and refuses what no rule allows. The creative inventory, middle-click pick-block and any client-side
item browser all end up on that path. The check runs on the stack as it is about to land, after
vanilla has finished rewriting it. Middle-click cloning inside an open container, the other way
creative mode copies items, is checked the same way.

When the client also has the mod installed, the server additionally tells it which creative tabs are
worth displaying, so players browse a clean inventory instead of one where most clicks are rejected.
That part is advisory: the client may ignore it, and the refusal still holds.

## Profiles

A profile is a **strict whitelist** — anything no rule allows is refused. Four rule shapes open it
up, and they combine:

| Field | Opens | Example |
| --- | --- | --- |
| `items` | single items | `"minecraft:torch"` |
| `namespaces` | every item of a mod | `"create"` |
| `tags` | every member of an item tag | `"#minecraft:beds"` |
| `tabs` | every item of a creative tab | `"minecraft:building_blocks"` |

Two fields close it back down, and always win over the four above:

| Field | Refuses |
| --- | --- |
| `denied_items` | single items |
| `denied_namespaces` | every item of a mod |

This is what makes a broad rule usable: allow the redstone tab, then deny the command block, rather
than expanding a tab into hundreds of item ids.

An allowed item id can still carry a payload in its data components: a spawn egg or an item frame
holding another item, a chest with a loot table, a stick with attribute modifiers. Components are
therefore checked on an allowlist. A stack passes when it is exactly one the creative menu offers
(a potion, a painting variant, an enchanted book), or when every component it carries beyond its
defaults is one ordinary play produces: a custom name, lore, damage, dye, map data, banner patterns,
fireworks, a player head, armor trims, and a signed book in plain text. Anything else is refused
unless the profile opens it:

| Field | Opens | Default |
| --- | --- | --- |
| `allowed_components` | the listed component ids, for example `"minecraft:enchantments"` | `[]` |
| `allow_block_entity_data` | `minecraft:block_entity_data`, which can hold a command block, a spawner or a sign payload | `false` |
| `allow_container_contents` | filled containers and bundles; the contents are evaluated against the same profile, recursively | `false` |

Modded items that pick up components of their own during play (a filled backpack, a configured
tool) are refused until their component ids are listed in `allowed_components`.

## Configuration

Two files under `config/arcadia/`:

- `arcadia-creative-admin-policy.json` — profiles and settings. Written by hand, **never rewritten
  by the mod**, so comments and formatting survive. A disabled sample is generated on first start.
- `arcadia-creative-admin-assignments.json` — which player is on which profile. Written by the
  commands; not meant to be edited by hand.

```json
{
  "enforced": true,
  "default_profile": "event",
  "bypass_op_level": 4,
  "profiles": {
    "event": {
      "tabs": ["minecraft:building_blocks", "minecraft:colored_blocks"],
      "namespaces": [],
      "tags": ["#minecraft:beds"],
      "items": ["minecraft:torch"],
      "denied_items": ["minecraft:command_block"],
      "denied_namespaces": [],
      "allow_block_entity_data": false,
      "allow_container_contents": false,
      "allowed_components": []
    }
  }
}
```

Players at or above `bypass_op_level` (1 to 4) are not restricted at all. Players with no assignment
fall back to `default_profile`. A profile cannot be named `clear`, which the `profile` command
reserves.

The mod fails closed. A policy file that cannot be parsed, an out-of-range `bypass_op_level`, a flag
that is not `true` or `false`, a rule written as a string instead of a list, a policy file removed
before a reload, or an assignment file that cannot be read: each of these refuses creative items to
every restricted player until it is fixed and reloaded, and the log says why. Only a policy file
that does not exist when the server starts is treated as a fresh install: a disabled sample is
written and nothing is enforced. `/creativeadmin reload` also reports rule entries that match
nothing on the server, since a mistyped `denied_items` entry closes nothing.

## Commands

All gated at op level 3.

| Command | Effect |
| --- | --- |
| `/creativeadmin status` | Whether enforcement is on, which profiles exist, the bypass level |
| `/creativeadmin reload` | Re-read both files and re-index tab contents, without a restart |
| `/creativeadmin tabs` | List the creative tab ids a `tabs` rule can name |
| `/creativeadmin check [profile]` | Evaluate the held item, and print the rule that refused it |
| `/creativeadmin profile <players> <profile>` | Assign |
| `/creativeadmin profile <players> clear` | Back to the default profile |

`check` and `tabs` exist because a whitelist is only as good as the operator's ability to predict it.
Probe a rule before an event rather than discovering during it that a tab matched more than intended.

## What this does not cover

- **Items already in the world.** The policy governs what creative mode creates, not what a player
  picks up from a chest placed before the event.
- **Operator blocks.** Command, structure and jigsaw blocks are gated by vanilla at op level 2 and
  by `allowCommands`. Check those separately.
- **Lag built from allowed blocks.** A whitelist of harmless items still allows a redstone clock.
- **`/give`.** Vanilla gates it at op level 2; this mod does not touch it.
- **Refused stacks moved inside the creative inventory.** The creative protocol sends a move as a
  removal followed by a creation. When the profile refuses the stack, the creation is refused and
  the stack is lost. This only affects items the profile would not hand out in the first place.

## Companion mod

**Arcadia Better Creative** sorts, pins and hides creative tabs, client-side. The two are separate
jars with no dependency between them, in either direction, and neither needs the other to work. They
are built to run together: this mod decides which tabs are worth showing for a given profile, Better
Creative arranges what is left. Installed side by side, the tab bar ends up both filtered and
ordered.

## Requirements

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.241 or newer
- Installed on the **server**. Installing it on clients too is optional and only improves display.
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

# Arcadia Creative Admin (français)

Restrictions créatives appliquées côté serveur. Définissez des profils nommés listant exactement ce
que les joueurs peuvent prendre en créatif, pour organiser des events de construction sans distribuer
les objets qui cassent un serveur.

L'application est côté serveur et ne dépend pas du client. Un joueur qui retire ses mods, modifie sa
configuration ou se connecte avec un client vanilla est soumis aux mêmes règles.

## Fonctionnement

Tout objet qu'un joueur en créatif fait apparaître passe par un seul paquet,
`ServerboundSetCreativeModeSlotPacket`. Le mod valide ce paquet contre le profil du joueur et refuse
ce qu'aucune règle n'autorise. L'inventaire créatif, le clic-molette et n'importe quel navigateur
d'objets côté client aboutissent sur ce chemin. Le contrôle porte sur la pile telle qu'elle va être
posée, après que le vanilla a fini de la réécrire. Le clonage au clic-molette dans un conteneur
ouvert, l'autre façon dont le créatif copie des objets, est contrôlé de la même manière.

Quand le client a lui aussi le mod, le serveur lui indique en plus quels onglets créatifs valent la
peine d'être affichés, pour que les joueurs parcourent un inventaire propre plutôt qu'un inventaire
où la plupart des clics sont rejetés. Cette partie est indicative : le client peut l'ignorer, le
refus tient quand même.

## Profils

Un profil est une **liste blanche stricte** : tout ce qu'aucune règle n'autorise est refusé. Quatre
formes de règle l'ouvrent, et elles se combinent :

| Champ | Ouvre | Exemple |
| --- | --- | --- |
| `items` | des objets précis | `"minecraft:torch"` |
| `namespaces` | tous les objets d'un mod | `"create"` |
| `tags` | tous les membres d'un tag d'objet | `"#minecraft:beds"` |
| `tabs` | tous les objets d'un onglet créatif | `"minecraft:building_blocks"` |

Deux champs la referment, et l'emportent toujours sur les quatre précédents :

| Champ | Refuse |
| --- | --- |
| `denied_items` | des objets précis |
| `denied_namespaces` | tous les objets d'un mod |

C'est ce qui rend une règle large utilisable : autoriser l'onglet redstone, puis refuser le bloc de
commande, au lieu de développer un onglet en des centaines d'identifiants.

Un identifiant autorisé peut encore transporter une charge utile dans ses composants de données : un
œuf d'apparition ou un cadre contenant un autre objet, un coffre avec une table de butin, un bâton
avec des modificateurs d'attributs. Les composants sont donc contrôlés par liste blanche. Une pile
passe si elle est exactement une de celles que propose le menu créatif (une potion, une variante de
tableau, un livre enchanté), ou si chaque composant qu'elle porte en plus de ses valeurs par défaut
est de ceux que produit le jeu normal : un nom, une description, de l'usure, une teinture, des
données de carte, des motifs de bannière, des feux d'artifice, une tête de joueur, des garnitures
d'armure, et un livre signé en texte simple. Tout le reste est refusé, sauf si le profil l'ouvre :

| Champ | Ouvre | Défaut |
| --- | --- | --- |
| `allowed_components` | les identifiants de composants listés, par exemple `"minecraft:enchantments"` | `[]` |
| `allow_block_entity_data` | `minecraft:block_entity_data`, qui peut contenir un bloc de commande, un générateur de monstres ou une charge utile de panneau | `false` |
| `allow_container_contents` | les conteneurs et sacs remplis ; le contenu est évalué contre le même profil, de manière récursive | `false` |

Les objets moddés qui acquièrent leurs propres composants en jeu (un sac à dos rempli, un outil
configuré) sont refusés tant que leurs identifiants de composants ne figurent pas dans
`allowed_components`.

## Configuration

Deux fichiers dans `config/arcadia/` :

- `arcadia-creative-admin-policy.json` — profils et réglages. Écrit à la main, **jamais réécrit par
  le mod**, pour que commentaires et mise en forme survivent. Un exemple désactivé est généré au
  premier démarrage.
- `arcadia-creative-admin-assignments.json` — quel joueur sur quel profil. Écrit par les commandes,
  pas destiné à l'édition manuelle.

Les joueurs au niveau `bypass_op_level` (1 à 4) ou au-dessus ne sont pas restreints. Ceux sans
affectation retombent sur `default_profile`. Un profil ne peut pas s'appeler `clear`, nom réservé par
la commande `profile`.

Le mod échoue du côté fermé. Un fichier de politique illisible, un `bypass_op_level` hors limites,
un drapeau qui n'est ni `true` ni `false`, une règle écrite comme une chaîne au lieu d'une liste, un
fichier de politique supprimé avant un rechargement, ou un fichier d'affectations illisible : chacun
de ces cas refuse les objets créatifs à tous les joueurs restreints jusqu'à correction et
rechargement, et le log en donne la raison. Seul un fichier de politique absent au démarrage du
serveur est traité comme une première installation : un exemple désactivé est écrit et rien n'est
appliqué. `/creativeadmin reload` signale aussi les entrées de règle qui ne correspondent à rien sur
le serveur, puisqu'une faute de frappe dans `denied_items` ne ferme rien.

## Commandes

Toutes réservées au niveau d'op 3.

| Commande | Effet |
| --- | --- |
| `/creativeadmin status` | Application activée ou non, profils existants, niveau de contournement |
| `/creativeadmin reload` | Relit les deux fichiers et réindexe les onglets, sans redémarrage |
| `/creativeadmin tabs` | Liste les identifiants d'onglets qu'une règle `tabs` peut nommer |
| `/creativeadmin check [profil]` | Évalue l'objet en main et affiche la règle qui l'a refusé |
| `/creativeadmin profile <joueurs> <profil>` | Affecte |
| `/creativeadmin profile <joueurs> clear` | Retour au profil par défaut |

## Ce que ce mod ne couvre pas

- **Les objets déjà dans le monde.** La politique régit ce que le créatif crée, pas ce qu'un joueur
  récupère dans un coffre posé avant l'event.
- **Les blocs d'opérateur.** Blocs de commande, de structure et jigsaw sont déjà filtrés par le
  vanilla au niveau d'op 2 et par `allowCommands`. À vérifier séparément.
- **Le lag construit avec des blocs autorisés.** Une liste blanche d'objets inoffensifs autorise
  quand même une horloge redstone.
- **`/give`.** Le vanilla le filtre au niveau d'op 2 ; ce mod n'y touche pas.
- **Les piles refusées déplacées dans l'inventaire créatif.** Le protocole créatif envoie un
  déplacement comme une suppression suivie d'une création. Si le profil refuse la pile, la création
  est refusée et la pile est perdue. Cela ne touche que des objets que le profil n'aurait de toute
  façon pas donnés.

## Mod compagnon

**Arcadia Better Creative** trie, épingle et masque les onglets créatifs, côté client. Les deux sont
des jars distincts, sans dépendance de l'un vers l'autre ni dans l'autre sens, et aucun n'a besoin de
l'autre pour fonctionner. Ils sont conçus pour tourner ensemble : ce mod décide quels onglets valent
la peine d'être affichés pour un profil donné, Better Creative organise ce qu'il reste. Installés
côte à côte, la barre d'onglets se retrouve à la fois filtrée et ordonnée.

## Prérequis

- Minecraft 1.21.1
- [NeoForge](https://neoforged.net/) 21.1.241 ou plus récent
- Installé sur le **serveur**. L'installer aussi sur les clients est facultatif et n'améliore que
  l'affichage.
- Le solo et le LAN fonctionnent, mais les règles `tabs` ne s'appliquent qu'une fois que l'hôte a
  ouvert l'inventaire créatif, puisque c'est à ce moment que le jeu construit le contenu des onglets
  sur un serveur intégré. D'ici là elles ne correspondent à rien, ce qui refuse au lieu d'autoriser.

## Crédits

Écrit et maintenu par **THEFricadelle**. Les contributions sont créditées dans
[CONTRIBUTORS.md](CONTRIBUTORS.md) ; voir [CONTRIBUTING.md](CONTRIBUTING.md) avant d'ouvrir une pull
request.

Publié sous [All Rights Reserved](LICENSE). Les mentions tierces sont listées dans
[NOTICE.md](NOTICE.md).
