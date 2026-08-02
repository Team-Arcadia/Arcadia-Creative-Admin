# Contributing to Arcadia Creative Admin

Thanks for wanting to help. Arcadia Creative Admin is **source-available
proprietary software** — the code is public so you can read it, audit it, and
help fix it, but it is **not** open-source. This document explains exactly what
you may and may not do.

Read [LICENSE](LICENSE) for the binding terms. This file is a plain-language
guide, not a substitute for it.

## What you may do

- **Read, audit, and review** the full source code.
- **Open an issue** to report a bug, a crash, or a compatibility problem.
- **Fork the repository and open a pull request** — this is explicitly permitted
  by Section 5.1 of the LICENSE, under the conditions below.

## What you may not do

- **Publish or release any build made from your fork** — no .jar, no source
  archives, no "my improved version" on mod-hosting sites, modpack platforms,
  Discord, or anywhere else.
- **Rebrand the fork** into a separate or competing project, or change the mod
  name, mod id, or branding.
- **Reuse the source code** — in whole or in part, verbatim or adapted — inside
  another project or product.
- **Claim authorship** of Arcadia Creative Admin or any part of it.
- **Remove or alter** copyright, authorship, or license notices, including the
  SPDX headers at the top of source files.

The fork permission exists for one reason: letting you submit a pull request.
Once your PR is merged, closed, or abandoned, that permission covers nothing
further beyond keeping the fork as a historical record. A PR counts as
abandoned after **90 consecutive days** without a commit, comment, or other
activity from you, or if you say you are no longer pursuing it.

Using AI-assisted developer tooling while writing your contribution is fine —
the §3(h) restriction targets using the codebase as training data, not your
editor.

## Contributor terms (important)

By submitting a pull request, patch, or code suggestion, you agree that:

1. You grant THEFricadelle a perpetual, worldwide, irrevocable, royalty-free,
   sublicensable and transferable license to use, modify, relicense, and
   distribute your contribution as part of Arcadia Creative Admin, under this or
   any other license.
2. You are the author of the contribution and have the right to submit it.
3. Your contribution contains no third-party code you are not entitled to
   submit.
4. You keep the copyright on your own contribution, but submitting it gives you
   **no ownership, co-authorship, or any other right over Arcadia Creative Admin
   itself**.
5. Where the law allows it, you waive your moral rights in the contribution as
   against the author; where it does not, you agree not to assert them in a way
   that would block the license above. In return, your contribution will never
   be misattributed to someone else.
6. These terms apply identically whether or not you are a member of the team or
   organization hosting the repository. Membership, maintainer status, and write
   access to the repository grant no right over Arcadia Creative Admin and no
   authority to permit anything the LICENSE reserves to THEFricadelle (§1).

## What you get in return

Section 5.3 of the LICENSE gives every contributor two things:

- **Credit** in [CONTRIBUTORS.md](CONTRIBUTORS.md), which is not withdrawn later
  for any reason. Ask via the issue tracker if you want a different name or
  handle, no contact address, or no listing at all.
- **The modpack permission**, confirmed explicitly: once your PR has concluded,
  you may ship Arcadia Creative Admin in a modpack you publish — referencing an
  Official Channel, unmodified official file, notices preserved. Having forked
  the repo never costs you this.

You still may not ship a build made from your own fork. The permission covers
the official file only.

That credit is recognition of your work — it does not make you a co-owner or
co-maintainer of the project.

If you do not agree with these terms, do not submit a pull request — open an
issue describing the problem instead. That is just as useful.

## Contributions that are welcome

| Type | Welcome | Notes |
|------|---------|-------|
| Bug fixes | ✅ Yes | The best kind of PR. Include reproduction steps. |
| Crash fixes | ✅ Yes | Attach the crash report or stack trace. |
| Compatibility fixes | ✅ Yes | Arcadia Better Creative, and other mods touching the creative inventory screen or the creative slot packet. |
| Performance improvements | ✅ Yes | Explain the measurement, not just the theory. |
| Typos, localization fixes | ✅ Yes | Small and easy to merge. |
| Documentation corrections | ✅ Yes | README, guides, comments. |
| New features | ⚠️ Ask first | Open an issue before writing code — I may already have a design or a reason to refuse it. |
| Refactors / restyling | ⚠️ Ask first | Large diffs with no behavior change are usually rejected. |
| Dependency or build changes | ⚠️ Ask first | Affects distribution and the release pipeline. |

### One invariant above all: never fail open

This mod exists to refuse. A missing profile, an unreadable file, a parse error
or an unexpected exception must all resolve to a refusal, never to a permitted
item. A patch that widens the whitelist when something goes wrong hands a full
creative inventory to whoever the broken entry pointed at, and will be rejected
regardless of how clean it otherwise is. When in doubt, deny.

## How to submit a pull request

1. **Open an issue first** for anything beyond a small fix — it avoids wasted
   work on both sides.
2. **Fork** the repository and branch from `main`.
3. **Name the branch** `fix/short-description` or `feat/short-description`.
4. **Write the code** following the conventions below.
5. **Build and test**:
   ```bash
   ./gradlew build
   ./gradlew runServer
   ```
   Everything must pass. A PR that does not build will not be reviewed. A
   successful `compileJava` proves nothing about whether a mixin resolves:
   `runServer` must reach "Done" with no `InvalidInjectionException` in the log,
   and a behavior change must be observed in game before you claim it works.
6. **Commit** with a conventional message: `fix: short description of the fix`.
7. **Open the pull request against `main`**, describing what it fixes and how
   you verified it.

## Code conventions

- **Language**: all code, identifiers, comments, and log messages in **English**.
- **Naming**: `PascalCase` types, `camelCase` members and methods,
  `UPPER_SNAKE_CASE` constants, and mixin injector methods prefixed
  `arcadiacreativeadmin$`.
- **Comments**: minimal and in English — explain *why*, not *what*.
- **SPDX headers**: keep the existing header on every source file. New files
  must carry the same header.
- **Localization**: every user-facing string goes into both `en_us.json` and
  `fr_fr.json` in the same change.
- **No version bumps**: never change `mod_version` (`gradle.properties`).
  Releases are handled by the author.
- **No new dependencies** without asking first.
- **Scope**: one logical change per pull request.

## Reporting a bug

Include, at minimum:

- Minecraft version and NeoForge version.
- The mod version, and whether any other mod touching the creative inventory is
  installed.
- The side it happened on: dedicated server, integrated server (singleplayer),
  or client display only.
- The relevant part of `latest.log` or the crash report, as text rather than a
  screenshot.
- The profile in `arcadia-creative-admin-policy.json` that was in effect, with
  any private detail removed.
- What you did, what you expected, and what happened instead — including whether
  the item was refused, allowed, or reappeared after a reconnect.
- The output of `/creativeadmin check` on the item concerned, when the report is
  about a rule matching or not matching.

## Contact

For redistribution requests, modpack inclusion beyond Section 3(b), commercial
hosting offers, or anything else not covered here, open an issue on the official
repository:

  https://github.com/Team-Arcadia/Arcadia-Creative-Admin/issues

**Author: THEFricadelle**

---

# Contribuer à Arcadia Creative Admin (Version Française)

Merci de vouloir aider. Arcadia Creative Admin est un **logiciel propriétaire à
source visible** — le code est public pour que vous puissiez le lire, l'auditer
et aider à le corriger, mais il n'est **pas** open-source. Ce document explique
précisément ce que vous pouvez et ne pouvez pas faire.

Lisez [LICENSE](LICENSE) pour les conditions contraignantes. Ce fichier est un
guide en langage clair, pas un substitut.

## Ce que vous pouvez faire

- **Lire, auditer et relire** l'intégralité du code source.
- **Ouvrir une issue** pour signaler un bug, un crash ou un problème de
  compatibilité.
- **Forker le dépôt et ouvrir une pull request** — c'est explicitement autorisé
  par la Section 5.1 de la LICENSE, dans les conditions ci-dessous.

## Ce que vous ne pouvez pas faire

- **Publier ou diffuser un build issu de votre fork** — aucun .jar, aucune
  archive source, aucune « version améliorée » sur des sites d'hébergement de
  mods, des plateformes de modpacks, Discord ou ailleurs.
- **Renommer/rebrander le fork** en projet séparé ou concurrent, ni modifier le
  nom du mod, son mod id ou son identité visuelle.
- **Réutiliser le code source** — en tout ou partie, tel quel ou adapté — dans
  un autre projet ou produit.
- **Revendiquer la paternité** d'Arcadia Creative Admin ou d'une quelconque de
  ses parties.
- **Supprimer ou altérer** les mentions de copyright, de paternité ou de
  licence, y compris les en-têtes SPDX en haut des fichiers source.

L'autorisation de fork existe pour une seule raison : vous permettre de soumettre
une pull request. Une fois votre PR fusionnée, fermée ou abandonnée, cette
autorisation ne couvre plus rien d'autre que la conservation du fork comme
archive. Une PR est réputée abandonnée après **90 jours consécutifs** sans
commit, commentaire ou autre activité de votre part, ou si vous déclarez ne plus
la poursuivre.

Utiliser des outils de développement assistés par IA pour rédiger votre
contribution ne pose aucun problème — la restriction du §3(h) vise l'usage du
code comme données d'entraînement, pas votre éditeur.

## Conditions applicables aux contributeurs (important)

En soumettant une pull request, un patch ou une suggestion de code, vous
acceptez que :

1. Vous accordez à THEFricadelle une licence perpétuelle, mondiale, irrévocable,
   gratuite, sous-licenciable et transférable pour utiliser, modifier,
   relicencier et distribuer votre contribution au sein d'Arcadia Creative
   Admin, sous cette licence ou toute autre.
2. Vous êtes l'auteur de la contribution et avez le droit de la soumettre.
3. Votre contribution ne contient aucun code tiers que vous n'auriez pas le
   droit de soumettre.
4. Vous conservez le copyright sur votre propre contribution, mais la soumettre
   ne vous donne **aucun droit de propriété, de co-paternité ou autre sur
   Arcadia Creative Admin lui-même**.
5. Dans la limite permise par la loi, vous renoncez à vos droits moraux sur la
   contribution à l'égard de l'auteur ; à défaut, vous vous engagez à ne pas les
   invoquer d'une manière qui ferait obstacle à la licence ci-dessus. En
   contrepartie, votre contribution ne sera jamais attribuée à un tiers.
6. Ces conditions s'appliquent à l'identique, que vous soyez ou non membre de
   l'équipe ou de l'organisation qui héberge le dépôt. L'appartenance, le statut
   de mainteneur et l'accès en écriture au dépôt ne donnent aucun droit sur
   Arcadia Creative Admin ni le pouvoir d'autoriser ce que la LICENSE réserve à
   THEFricadelle (§1).

## Ce que vous obtenez en retour

La Section 5.3 de la LICENSE accorde deux choses à tout contributeur :

- **Le crédit** dans [CONTRIBUTORS.md](CONTRIBUTORS.md), qui n'est retiré
  ultérieurement pour aucun motif. Demandez via le tracker d'issues si vous
  souhaitez un autre nom ou pseudonyme, aucune adresse de contact, ou aucune
  mention du tout.
- **La permission modpack**, confirmée explicitement : une fois votre PR
  terminée, vous pouvez diffuser Arcadia Creative Admin dans un modpack que vous
  publiez — en référençant un canal officiel, fichier officiel non modifié,
  mentions préservées. Avoir forké le dépôt ne vous en prive jamais.

Vous ne pouvez toujours pas diffuser un build issu de votre propre fork. La
permission ne couvre que le fichier officiel.

Ce crédit est une reconnaissance de votre travail — il ne fait pas de vous un
copropriétaire ni un co-mainteneur du projet.

Si vous n'acceptez pas ces conditions, ne soumettez pas de pull request —
ouvrez plutôt une issue décrivant le problème. C'est tout aussi utile.

## Contributions bienvenues

| Type | Bienvenue | Notes |
|------|-----------|-------|
| Corrections de bugs | ✅ Oui | Le meilleur type de PR. Incluez les étapes de reproduction. |
| Corrections de crash | ✅ Oui | Joignez le rapport de crash ou la stack trace. |
| Corrections de compatibilité | ✅ Oui | Arcadia Better Creative, et les autres mods touchant à l'écran d'inventaire créatif ou au paquet de slot créatif. |
| Améliorations de performance | ✅ Oui | Expliquez la mesure, pas seulement la théorie. |
| Fautes de frappe, localisation | ✅ Oui | Petit et facile à fusionner. |
| Corrections de documentation | ✅ Oui | README, guides, commentaires. |
| Nouvelles fonctionnalités | ⚠️ Demandez avant | Ouvrez une issue avant de coder — j'ai peut-être déjà une conception ou une raison de refuser. |
| Refactorisations / restylage | ⚠️ Demandez avant | Les gros diffs sans changement de comportement sont généralement refusés. |
| Changements de dépendances / build | ⚠️ Demandez avant | Impacte la distribution et le pipeline de release. |

### Un invariant avant tous les autres : ne jamais s'ouvrir en cas d'erreur

Ce mod existe pour refuser. Un profil manquant, un fichier illisible, une erreur
d'analyse ou une exception inattendue doivent tous aboutir à un refus, jamais à
un objet autorisé. Un correctif qui élargit la liste blanche quand quelque chose
échoue distribue un inventaire créatif complet à celui que l'entrée cassée
désignait, et sera refusé quelle que soit sa qualité par ailleurs. Dans le doute,
refusez.

## Comment soumettre une pull request

1. **Ouvrez d'abord une issue** pour tout ce qui dépasse une petite correction —
   cela évite du travail perdu des deux côtés.
2. **Forkez** le dépôt et créez une branche depuis `main`.
3. **Nommez la branche** `fix/description-courte` ou `feat/description-courte`.
4. **Écrivez le code** en suivant les conventions ci-dessous.
5. **Compilez et testez** :
   ```bash
   ./gradlew build
   ./gradlew runServer
   ```
   Tout doit passer. Une PR qui ne compile pas ne sera pas relue. Un
   `compileJava` réussi ne prouve rien quant à la résolution d'un mixin :
   `runServer` doit atteindre « Done » sans `InvalidInjectionException` dans le
   log, et un changement de comportement doit être observé en jeu avant d'être
   annoncé comme fonctionnel.
6. **Committez** avec un message conventionnel : `fix: short description of the fix`.
7. **Ouvrez la pull request vers `main`**, en décrivant ce qu'elle corrige et
   comment vous l'avez vérifié.

## Conventions de code

- **Langue** : tout le code, les identifiants, les commentaires et les messages
  de log en **anglais**.
- **Nommage** : types en `PascalCase`, membres et méthodes en `camelCase`,
  constantes en `UPPER_SNAKE_CASE`, et méthodes d'injection de mixin préfixées
  `arcadiacreativeadmin$`.
- **Commentaires** : minimalistes et en anglais — expliquez le *pourquoi*, pas
  le *quoi*.
- **En-têtes SPDX** : conservez l'en-tête existant sur chaque fichier source.
  Les nouveaux fichiers doivent porter le même en-tête.
- **Localisation** : toute chaîne visible par l'utilisateur va dans `en_us.json`
  et `fr_fr.json` dans le même changement.
- **Aucun changement de version** : ne modifiez jamais `mod_version`
  (`gradle.properties`). Les releases sont gérées par l'auteur.
- **Aucune nouvelle dépendance** sans demander au préalable.
- **Périmètre** : un seul changement logique par pull request.

## Signaler un bug

Incluez au minimum :

- La version de Minecraft et celle de NeoForge.
- La version du mod, et la présence éventuelle d'un autre mod touchant à
  l'inventaire créatif.
- Le côté concerné : serveur dédié, serveur intégré (solo), ou affichage client
  uniquement.
- La partie pertinente de `latest.log` ou du rapport de crash, en texte plutôt
  qu'en capture d'écran.
- Le profil de `arcadia-creative-admin-policy.json` en vigueur, expurgé de tout
  élément privé.
- Ce que vous avez fait, ce que vous attendiez, et ce qui s'est produit — en
  précisant si l'objet a été refusé, autorisé, ou est réapparu après une
  reconnexion.
- La sortie de `/creativeadmin check` sur l'objet concerné, lorsque le rapport
  porte sur une règle qui correspond ou ne correspond pas.

## Contact

Pour toute demande de redistribution, d'autorisation modpack au-delà de ce que
la LICENSE permet déjà, d'offre d'hébergement commercial, ou tout autre point non
couvert ici, ouvrez une issue sur le dépôt officiel :

  https://github.com/Team-Arcadia/Arcadia-Creative-Admin/issues

**Author: THEFricadelle**
