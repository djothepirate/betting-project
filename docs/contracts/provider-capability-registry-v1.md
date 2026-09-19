# Registre de capacités fournisseur v1

## Portée et activation

Contrat `provider-capability-registry-v1`, livré par MVP-001 lot 1. Le document classpath
`collection/provider-capability-registry-v1.json` est l'unique configuration d'exécution.
Il est chargé une fois au démarrage, puis conservé sous forme immuable. Il n'existe ni
rechargement dynamique, ni registre PostgreSQL, ni fallback vers la politique CAT-002 historique.
La baseline réelle contient **zéro entrée** : aucun fournisseur réel n'est activé.

Les [fondations](mvp-001-collection-foundations-v1.md) et
l'[inventaire des références](../benchmark/mvp-001-capability-evidence-v1.md) distinguent les
preuves réelles, les fixtures synthétiques et les contextes manquants. Une lacune ne devient
jamais une affectation générique. L'activation réelle reste une modification versionnée et revue.

## Document et identités

L'enveloppe contient exactement `schemaVersion`, `registryVersion` et `entries` :

```json
{
  "schemaVersion": "provider-capability-registry-v1",
  "registryVersion": "mvp-001-inactive-v1",
  "entries": []
}
```

Chaque entrée contient exactement les champs suivants, tous obligatoires :

| Objet | Champs |
|---|---|
| entrée | `key`, `route`, `status`, `authorityRole`, `enabled`, `evidence` |
| `key` | `provider`, `providerCompetitionId`, `sourceSeason`, `sourcePhase`, `dataType` |
| `route` | `competitionCode`, `season`, `phase`, `dataType` |
| chaque référence de `evidence` | `logicalId`, `observedAt`, `sha256` |

La clé fournisseur utilise la référence exacte du fournisseur, jamais un UUID canonique.
La route utilise un code logique comme PPL, PD, DED ou ELC. Ce code ne crée aucun mapping.
Les deux contextes sont explicites ; leur association est une décision configurée et non une
substitution implicite de saison ou de phase. Leurs types de données doivent être identiques.

Les textes de clé sont non vides, sans espace périphérique ni caractère de contrôle ; ils sont
sensibles à la casse. Les bornes sont de 64 caractères, sauf `providerCompetitionId` (200).
`registryVersion` est une version métier non vide bornée à 64 caractères. Les affectations
refusent `*`, `?` et `%` dans tous les champs textuels des clés. Les clés de recherche runtime
acceptent ces caractères littéralement : aucune interprétation de motif ou recherche approchée.

Les six types sont `CALENDAR`, `MATCH_DETAIL`, `LINEUP`, `TEAM_STATS`, `EVENTS` et `PLAYER_STATS`.
`enabled` est un booléen JSON strict. Une entrée active exige au moins une preuve : identifiant
logique non vide borné à 200 caractères, instant ISO-8601 `observedAt`, SHA-256 hexadécimal
minuscule de 64 caractères. Ces métadonnées rendent la décision traçable ; le démarrage ne lit
ni ne vérifie le corpus externe. Les preuves synthétiques sont exclusivement des données de test.

## Couverture, autorité et sélection

| `status` | `authorityRole` permis | Routage opérationnel |
|---|---|---|
| `PRIMARY` | `PRIMARY` | si actif |
| `CONTROL` | `CONTROL` | si actif |
| `CALENDAR_ONLY` | rôle explicite `PRIMARY`, `CONTROL` ou `UNASSIGNED` | `CALENDAR` uniquement, actif et rôle non `UNASSIGNED` |
| `PILOT` | `UNASSIGNED` | jamais ; candidat pilote visible |
| `NON_APPLICABLE` | `UNASSIGNED` | jamais |
| `BLOCKED_BY_PLAN` | `UNASSIGNED` | jamais |

Une entrée inactive n'accorde aucune autorité. Le chargement rejette les clés fournisseur
dupliquées, même inactives, et plusieurs primaires opérationnels pour une même route exacte.
Les primaires inactifs ne réservent pas la route. Les contrôles ne sont jamais promus.

`ProviderRoutingService` retourne `READY` avec un primaire ou `NO_PRIMARY` sans primaire,
les contrôles et les exclusions. Les candidats sont ordonnés par fournisseur, référence,
saison source puis phase source. Les exclusions sont `INACTIVE`, `PILOT`, `NON_APPLICABLE`,
`BLOCKED_BY_PLAN` ou `UNASSIGNED_AUTHORITY` ; l'inactivité est prioritaire. Une route inconnue
retourne `NO_PRIMARY` avec des listes vides. Les listes et le registre sont immuables.
Le routeur ne réserve aucun budget et ne vérifie ou n'appelle aucun client HTTP.

## Parsing fermé et provenance technique

Un parseur Jackson local refuse les champs inconnus ou dupliqués à chaque niveau, les types
incorrects, les valeurs nulles interdites, les schémas inconnus et les données après l'objet.
Aucune coercition n'est autorisée ; la configuration JSON globale n'est pas modifiée.
Une erreur invalide le document entier et fait échouer le démarrage sans recopier son contenu
dans l'erreur. Il n'existe pas de chargement partiel ou de fallback.

Le SHA-256 des **octets exacts** chargés constitue la version technique. Un changement d'octets,
même de mise en forme, change cette empreinte. La version métier reste distincte. Le résultat
de routage expose les deux versions ; la politique calendrier utilise l'empreinte de 64 caractères
dans les colonnes existantes du journal et du watermark, sans migration.

## Catalogue et profils

`RegistryCalendarAuthorityPolicy` implémente le port `CalendarAuthorityPolicy` et consulte
exclusivement le registre pour la clé calendrier exacte. Une entrée opérationnelle donne son
rôle explicite ; toute absence, inactivité ou exclusion donne `UNASSIGNED`. Les clés historiques
hors des bornes du registre restent non affectées, sans interrompre la conservation du snapshot.
Les politiques temporelles, identités, mappings et anomalies restent les chemins CAT-002 existants.
Voir le [contrat calendrier v3](calendar-replay-v3.md), dont le wire format reste inchangé.

Le registre, le routeur et le raccordement ne sont assemblés que sous `control-api` et
`batch-worker`. Le profil `replay` n'en charge aucun. `collection` définit les modèles et ports ;
`catalog` dépend de ces ports, sans dépendance inverse ni accès aux adaptateurs depuis l'application.

## Preuves du lot 1

`ProviderCapabilityTest`, `ProviderRoutingServiceTest` et `ProviderCapabilityDocumentParserTest`
couvrent les invariants, l'immuabilité, le JSON strict, les types, rôles et versions.
`RegistryCalendarAuthorityPolicyTest` couvre le raccordement exact. La fixture
`src/test/resources/fixtures/mvp001/provider-capabilities.synthetic.json` démontre les quatre
compétitions avec des références explicitement synthétiques, dont PD en `CALENDAR_ONLY`.

`RegistryCalendarAuthorityIT` utilise PostgreSQL et le vrai raccordement : primaire, contrôle
contradictoire sans mutation, inactivité, saison/phase sans affectation et les douze combinaisons
des trois caractères littéraux dans les quatre champs source. Les tests des profils et ArchUnit
vérifient l'assemblage et les frontières. Les résultats exécutés figurent dans le
[Work Order](../work-orders/MVP-001.md), sans activer la baseline réelle.
