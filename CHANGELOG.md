# Changelog

All notable changes to Arcadia Creative Admin are documented here.

---

## [Unreleased]

### Added (English first)

- **Server-side creative enforcement** — Every creative item request is validated against the
  requesting player's profile in `handleSetCreativeModeSlot`, the single packet through which a
  player in creative mode makes an item exist. Both branches of the vanilla handler are covered,
  including the `slotNum < 0` path that drops the stack on the ground, which would otherwise leave
  the whitelist bypassable by throwing an item instead of holding it. Enforcement does not depend on
  the client having the mod.
- **Profiles as strict whitelists** — A profile allows items by id, by mod namespace, by item tag and
  by creative tab, in any combination, and closes back down with `denied_items` and
  `denied_namespaces` that always win. Allowing the redstone tab and then denying the command block
  is the intended shape; expanding a tab into hundreds of ids is not.
- **Component guards** — `allow_block_entity_data` and `allow_container_contents`, both off by
  default. An allowed item id can otherwise carry a command block through `block_entity_data`, or
  wrap a stack of forbidden items in a shulker box. Container contents are evaluated recursively
  against the same profile, with a nesting limit.
- **Per-player assignment with op bypass** — Profiles are assigned by command and stored by player
  id; unassigned players fall back to `default_profile`, and players at or above `bypass_op_level`
  are unrestricted.
- **Operator commands** — `status`, `reload`, `tabs`, `check [profile]` and `profile`. `check` and
  `tabs` exist so a broad rule can be probed before an event rather than during it.
- **Advisory tab filtering on the client** — When the client also has the mod, the server sends the
  list of tabs worth displaying, so players browse a clean inventory instead of one where most clicks
  are rejected. The channel is registered as optional, so a vanilla client can still connect, and the
  server-side refusal holds regardless of what the client does with the advice.

### Ajouts (French mirror)

- **Application des restrictions côté serveur** — Chaque demande d'objet en créatif est validée
  contre le profil du joueur dans `handleSetCreativeModeSlot`, l'unique paquet par lequel un joueur
  en créatif fait apparaître un objet. Les deux branches du gestionnaire vanilla sont couvertes, y
  compris le chemin `slotNum < 0` qui jette la pile au sol, sans quoi la liste blanche resterait
  contournable en lançant l'objet au lieu de le garder. L'application ne dépend pas de la présence du
  mod côté client.
- **Profils en liste blanche stricte** — Un profil autorise des objets par identifiant, par namespace
  de mod, par tag d'objet et par onglet créatif, dans n'importe quelle combinaison, et se referme
  avec `denied_items` et `denied_namespaces` qui l'emportent toujours. Autoriser l'onglet redstone
  puis refuser le bloc de commande est la forme prévue ; développer un onglet en des centaines
  d'identifiants ne l'est pas.
- **Gardes sur les composants** — `allow_block_entity_data` et `allow_container_contents`, toutes
  deux désactivées par défaut. Un identifiant autorisé peut sinon transporter un bloc de commande via
  `block_entity_data`, ou envelopper une pile d'objets interdits dans une shulker. Le contenu des
  conteneurs est évalué récursivement contre le même profil, avec une limite d'imbrication.
- **Affectation par joueur et contournement op** — Les profils sont affectés par commande et stockés
  par identifiant de joueur ; les joueurs non affectés retombent sur `default_profile`, et ceux au
  niveau `bypass_op_level` ou au-dessus ne sont pas restreints.
- **Commandes d'opérateur** — `status`, `reload`, `tabs`, `check [profil]` et `profile`. `check` et
  `tabs` existent pour qu'une règle large puisse être vérifiée avant un event plutôt que pendant.
- **Filtrage indicatif des onglets côté client** — Quand le client a lui aussi le mod, le serveur
  envoie la liste des onglets qui valent la peine d'être affichés, pour que les joueurs parcourent un
  inventaire propre plutôt qu'un inventaire où la plupart des clics sont rejetés. Le canal est
  enregistré comme optionnel, donc un client vanilla peut se connecter, et le refus côté serveur tient
  quoi que le client fasse de cette indication.
