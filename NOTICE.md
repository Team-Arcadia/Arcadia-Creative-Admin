# NOTICE — Arcadia Creative Guard

**Copyright (C) 2026 THEFricadelle. All rights reserved.**
SPDX-License-Identifier: `LicenseRef-Arcadia-Creative-Guard-ARR`

Arcadia Creative Guard is **source-available proprietary software**. The source
code is public, but the project is **not open-source**. Reading the code grants
you no right to reuse it.

This file is a plain-language summary for convenience. The binding terms are in
[LICENSE](LICENSE); if the two ever disagree, the LICENSE wins.

## At a glance

| Action | Allowed? |
|--------|----------|
| Download the official build from CurseForge / Modrinth / GitHub Releases | ✅ Yes |
| Run it on Minecraft client(s) and server(s) you own or operate, in any singleplayer world and on any server you join, including monetized servers | ✅ Yes — as long as the mod itself isn't sold or paywalled |
| Read, audit, and review the source code | ✅ Yes |
| Report bugs, open issues | ✅ Yes |
| Fork the repo to submit a pull request | ✅ Yes — see [CONTRIBUTING.md](CONTRIBUTING.md) |
| Ship the mod in your own modpack **after** contributing a PR | ✅ Yes — §5.3, official file only |
| Be credited for a merged contribution | ✅ Yes — [CONTRIBUTORS.md](CONTRIBUTORS.md), §5.3(a) |
| Include it in a CurseForge / Modrinth modpack that **references** the official unmodified file | ✅ Yes, no need to ask |
| Send the official file to players joining **your own** server (launcher / host auto-sync) | ✅ Yes — see §2.2 |
| Mention it factually: "my pack includes Arcadia Creative Guard", tutorials, reviews | ✅ Yes |
| Bundle the .jar in an exported / offline modpack | ❌ Written permission required |
| Offer it as a "one-click install" product in a hosting panel catalogue | ❌ Written permission required |
| Re-upload or mirror it anywhere (mod-hosting sites, modpack platforms, forums, Discord, file lockers) | ❌ No |
| Modify it and distribute the result | ❌ No |
| Publish a build made from your fork | ❌ No |
| Reuse its code in another project | ❌ No |
| Sell it, rent it, or bundle it with a paid product | ❌ No |
| Claim you wrote it, or remove the copyright notices | ❌ No |
| Use the name, mod id, or logo for another project, or to imply endorsement | ❌ No |
| Use the code to train or fine-tune an AI / machine-learning model | ❌ No |
| Redistribute it, or authorize someone else to, because you are a team or organization member | ❌ No — §1, membership grants no permission |

## About revocation

The right to use the Mod is revocable — but **not arbitrarily**. Revocation is
**individual** (it takes effect only against a specific person or entity, upon
written notice), **prospective** (it never makes past compliant use unlawful),
and it does **not** silently kill a compliant modpack, nor other users' ability
to run an Official Build they lawfully obtained. It is a tool against abuse, not
a kill switch over the ecosystem. See §2.3 of the LICENSE.

## Why source-available and not open-source

Arcadia Creative Guard is an enforcement component: it decides what a player in
creative mode is allowed to make appear on a server. An operator who installs it
is trusting it with a security boundary, and that trust should be verifiable
rather than assumed. The source is public so that server owners can audit what
actually enforces their rules, so that interactions with other mods touching the
creative inventory can be diagnosed against the real implementation rather than
guessed at, and so that anyone who finds a way around the whitelist can report
it precisely or fix it through a pull request.

It is not open-source because the author retains exclusive control over
distribution and derivative works. Visibility is not a license.

## Ownership and maintenance

Arcadia Creative Guard is maintained by Team-Arcadia. Copyright in the mod is
held by **THEFricadelle** alone, who is the only party able to grant, withhold,
or withdraw any permission under the LICENSE.

Where the repository is hosted under an organization or a team account, that
hosting transfers nothing: being a member, a maintainer, or an administrator of
that organization gives no right to redistribute the mod, publish a build of it,
or authorize a third party to do either. Team members who contribute do so as
contributors, on the same terms as anyone else (§5.2). See §1 of the
[LICENSE](LICENSE).

## Third-party components

Arcadia Creative Guard builds against, but does not include or redistribute, the
following:

| Component | Role | Licensing |
|---|---|---|
| Minecraft (1.21.1) | The game the mod runs inside | Mojang Synergies AB — supplied by the user's own installation |
| NeoForge (21.1.0 or newer) | Mod loader, event bus, network channel registration | LGPL-2.1, installed separately by the user |
| SpongePowered Mixin and MixinExtras | Bytecode injection used by the two mixins | Supplied by NeoForge at runtime, not shipped here |

No third-party code is bundled into the Arcadia Creative Guard jar. Every
component above is a compile-time or runtime dependency resolved on the user's
side: the jar contains only this project's own classes, resources, and the
LICENSE.

## Requesting permission

Anything marked ❌ above can still be granted case by case. Ask — the answer is
often yes for reasonable requests. Open an issue on the official repository:

  https://github.com/Team-Arcadia/Arcadia-Creative-Guard/issues

Permission must be **written** to be valid. Silence is not consent: no reply, or
no objection to a use, never counts as permission. A permission granted in one
case applies to that case only.

**Author: THEFricadelle**

---

# NOTICE — Arcadia Creative Guard (Version Française)

**Copyright (C) 2026 THEFricadelle. Tous droits réservés.**
SPDX-License-Identifier: `LicenseRef-Arcadia-Creative-Guard-ARR`

Arcadia Creative Guard est un **logiciel propriétaire à source visible**. Le code
source est public, mais le projet n'est **pas open-source**. Lire le code ne vous
donne aucun droit de le réutiliser.

Ce fichier est un résumé en langage clair, fourni par commodité. Les conditions
contraignantes se trouvent dans [LICENSE](LICENSE) ; en cas de divergence, la
LICENSE prévaut.

## En un coup d'œil

| Action | Autorisé ? |
|--------|-----------|
| Télécharger le build officiel depuis CurseForge / Modrinth / GitHub Releases | ✅ Oui |
| L'exécuter sur le(s) client(s) et serveur(s) Minecraft que vous possédez ou opérez, dans n'importe quel monde solo et sur n'importe quel serveur que vous rejoignez, serveurs monétisés compris | ✅ Oui — tant que le mod lui-même n'est ni vendu ni derrière un paywall |
| Lire, auditer et relire le code source | ✅ Oui |
| Signaler des bugs, ouvrir des issues | ✅ Oui |
| Forker le dépôt pour soumettre une pull request | ✅ Oui — voir [CONTRIBUTING.md](CONTRIBUTING.md) |
| Diffuser le mod dans votre propre modpack **après** avoir contribué une PR | ✅ Oui — §5.3, fichier officiel uniquement |
| Être crédité pour une contribution fusionnée | ✅ Oui — [CONTRIBUTORS.md](CONTRIBUTORS.md), §5.3(a) |
| L'inclure dans un modpack CurseForge / Modrinth qui **référence** le fichier officiel non modifié | ✅ Oui, sans demander |
| Transmettre le fichier officiel aux joueurs rejoignant **votre propre** serveur (auto-sync launcher / hébergeur) | ✅ Oui — voir §2.2 |
| Le mentionner factuellement : « mon pack inclut Arcadia Creative Guard », tutoriels, tests | ✅ Oui |
| Empaqueter le .jar dans un modpack exporté / hors-ligne | ❌ Autorisation écrite requise |
| Le proposer en « installation en un clic » dans le catalogue d'un hébergeur | ❌ Autorisation écrite requise |
| Le ré-uploader ou le mirrorer ailleurs (sites d'hébergement de mods, plateformes de modpacks, forums, Discord, hébergeurs de fichiers) | ❌ Non |
| Le modifier et en distribuer le résultat | ❌ Non |
| Publier un build issu de votre fork | ❌ Non |
| Réutiliser son code dans un autre projet | ❌ Non |
| Le vendre, le louer, ou le lier à un produit payant | ❌ Non |
| Prétendre l'avoir écrit, ou retirer les mentions de copyright | ❌ Non |
| Utiliser le nom, le mod id ou le logo pour un autre projet, ou pour suggérer une caution | ❌ Non |
| Utiliser le code pour entraîner ou affiner un modèle d'IA / d'apprentissage automatique | ❌ Non |
| Le redistribuer, ou autoriser quelqu'un à le faire, au motif que vous êtes membre de l'équipe ou de l'organisation | ❌ Non — §1, l'appartenance ne donne aucune autorisation |

## À propos de la révocation

Le droit d'utiliser le mod est révocable — mais **pas arbitrairement**. La
révocation est **individuelle** (elle ne prend effet qu'à l'encontre d'une
personne ou entité déterminée, sur notification écrite), **non rétroactive**
(elle ne rend jamais illicite un usage passé conforme), et elle ne tue **pas**
silencieusement un modpack conforme, ni la possibilité pour les autres
utilisateurs d'exécuter un build officiel obtenu licitement. C'est un outil
contre l'abus, pas un interrupteur sur l'écosystème. Voir §2.3 de la LICENSE.

## Pourquoi source visible et pas open-source

Arcadia Creative Guard est un composant d'application de règles : il décide ce
qu'un joueur en créatif a le droit de faire apparaître sur un serveur. Un
opérateur qui l'installe lui confie une frontière de sécurité, et cette confiance
devrait pouvoir se vérifier plutôt que se supposer. Le code est public pour que
les propriétaires de serveurs puissent auditer ce qui applique réellement leurs
règles, pour que les interactions avec d'autres mods touchant à l'inventaire
créatif se diagnostiquent contre l'implémentation réelle plutôt qu'au jugé, et
pour que quiconque trouve un contournement de la liste blanche puisse le signaler
précisément ou le corriger par une pull request.

Ce n'est pas open-source parce que l'auteur conserve le contrôle exclusif de la
distribution et des œuvres dérivées. La visibilité n'est pas une licence.

## Propriété et maintenance

Arcadia Creative Guard est maintenu par Team-Arcadia. Les droits d'auteur sur le
mod sont détenus par **THEFricadelle** seul, unique partie en mesure d'accorder,
de refuser ou de retirer une autorisation au titre de la LICENSE.

Lorsque le dépôt est hébergé sous une organisation ou un compte d'équipe, cet
hébergement ne transfère rien : être membre, mainteneur ou administrateur de
cette organisation ne donne aucun droit de redistribuer le mod, d'en publier un
build, ni d'autoriser un tiers à le faire. Les membres de l'équipe qui
contribuent le font en tant que contributeurs, aux mêmes conditions que n'importe
qui d'autre (§5.2). Voir §1 de la [LICENSE](LICENSE).

## Composants tiers

Arcadia Creative Guard compile contre les composants suivants, sans les inclure
ni les redistribuer :

| Composant | Rôle | Licence |
|---|---|---|
| Minecraft (1.21.1) | Le jeu dans lequel le mod s'exécute | Mojang Synergies AB — fourni par l'installation de l'utilisateur |
| NeoForge (21.1.0 ou plus récent) | Chargeur de mods, bus d'événements, enregistrement du canal réseau | LGPL-2.1, installé séparément par l'utilisateur |
| SpongePowered Mixin et MixinExtras | Injection bytecode utilisée par les deux mixins | Fournis par NeoForge à l'exécution, non embarqués ici |

Aucun code tiers n'est embarqué dans le jar d'Arcadia Creative Guard. Chaque
composant ci-dessus est une dépendance de compilation ou d'exécution résolue du
côté de l'utilisateur : le jar ne contient que les classes et ressources propres
au projet, ainsi que la LICENSE.

## Demander une autorisation

Tout ce qui est marqué ❌ ci-dessus peut malgré tout être accordé au cas par cas.
Demandez — la réponse est souvent oui pour les demandes raisonnables. Ouvrez une
issue sur le dépôt officiel :

  https://github.com/Team-Arcadia/Arcadia-Creative-Guard/issues

L'autorisation doit être **écrite** pour être valable. Le silence ne vaut pas
accord : l'absence de réponse, ou l'absence d'objection à un usage, ne constitue
jamais une autorisation. Une autorisation accordée dans un cas ne vaut que pour
ce cas.

**Author: THEFricadelle**
