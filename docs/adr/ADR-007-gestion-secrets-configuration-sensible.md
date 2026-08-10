# ADR-007 - Gestion des secrets et de la configuration sensible

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.1
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

Les secrets sont injectés au runtime, séparés par environnement et absents de Git, des fixtures, des documents et des journaux. Le fichier local contenant les clés de test reste hors dépôt et n'est jamais copié dans les livrables.

## Contexte

Les trois clés API de benchmark existent sur le poste local. Elles permettent les tests mais ne doivent pas devenir une dépendance versionnée ou apparaître dans les preuves rejouables.

Le futur VPS devra aussi gérer des tokens de publication, OAuth, clés d'IA et clés de chiffrement, avec des niveaux de sensibilité et des cycles de rotation différents.

Les erreurs HTTP, journaux de clients et captures de requêtes peuvent divulguer involontairement un secret même si le fichier de configuration est ignoré par Git.

## Décision

L'application lit les secrets à partir de variables ou fichiers injectés au runtime. Le dépôt contient uniquement les noms de variables, valeurs factices et exemples sans secret.

Le fichier local actuel de clés de test reste hors dépôt. Le bootstrap ne le copie pas ; l'opérateur configure explicitement l'environnement local au moyen d'un mécanisme documenté et ignoré par Git.

Les environnements local, staging et production utilisent des secrets distincts lorsque le fournisseur le permet. Aucun secret de production n'est utilisé dans les tests ou confié à l'agent de développement.

Les en-têtes Authorization, paramètres de clé, cookies, tokens OAuth et valeurs sensibles sont masqués avant journalisation, persistance d'une requête ou création d'une fixture.

Le démarrage échoue clairement lorsqu'un secret requis pour un profil actif manque, sans afficher sa valeur. Un connecteur optionnel peut rester désactivé sans empêcher le démarrage du noyau.

La rotation et la révocation sont documentées. Une fuite présumée entraîne révocation, remplacement et recherche des traces dans Git, logs, artefacts et sauvegardes.

## Invariants

- Aucun secret réel dans Git, l'historique Git, les images de conteneur, les rapports de test, les documents ou les fixtures.
- Aucun secret dans les arguments de ligne de commande enregistrables par le système lorsque l'alternative par fichier ou environnement existe.
- Les logs n'impriment jamais les en-têtes d'autorisation ou les URL complètes contenant une clé.
- Les valeurs d'exemple sont manifestement factices et ne ressemblent pas à une clé réelle.
- Les droits d'accès aux fichiers de secrets sont limités au compte ou service nécessaire.
- La suppression d'un secret désactive ou met en erreur le connecteur concerné sans corrompre les données déjà persistées.

## Alternatives étudiées

### Clés dans application.properties

**Résultat :** Rejetée

Risque immédiat de versionnement et de diffusion dans les artefacts.

### Copier le fichier de clés dans le dépôt avec .gitignore

**Résultat :** Rejetée

Une erreur de règle ou de commande peut l'ajouter ; le dépôt ne doit pas être son emplacement normal.

### Gestionnaire de secrets externe dès le prototype

**Résultat :** Différée

Peut être pertinent en production, mais n'est pas nécessaire pour configurer les tests locaux initiaux.

### Même clé pour local, staging et production

**Résultat :** Rejetée lorsque des clés séparées sont possibles

Élargit inutilement l'impact d'une fuite ou d'une révocation.

## Conséquences

### Conséquences positives

- Réduction du risque de fuite et de révocation des comptes de test.
- Configuration reproductible sans exposer les valeurs.
- Séparation claire des environnements et des responsabilités.
- Fixtures et rapports partageables sans nettoyage manuel tardif.

### Coûts, limites et risques acceptés

- L'installation locale demande une étape explicite de configuration.
- La rotation et l'expiration peuvent provoquer des erreurs de connecteur à diagnostiquer.
- Une politique d'expurgation doit être appliquée à plusieurs couches : HTTP, logs, snapshots et tests.

## Critères d'acceptation

- [ ] Un clone propre contient un fichier d'exemple sans valeur sensible et une procédure de configuration locale.
- [ ] Une recherche automatisée ne détecte aucune des clés réelles dans Git ou les artefacts générés.
- [ ] Les tests de journalisation vérifient le masquage des en-têtes, paramètres et cookies sensibles.
- [ ] Les fixtures de benchmark restent exécutables après suppression de toutes les clés.
- [ ] Le profil d'un connecteur requis échoue avec un message explicite si sa clé manque, sans révéler de valeur.
- [ ] Une procédure de rotation/révocation et une checklist d'incident sont documentées avant le staging.

## Déclencheurs de réexamen

- Le staging ou la production nécessitent un gestionnaire de secrets dédié.
- Plusieurs opérateurs ou services doivent recevoir des droits différents.
- Un fournisseur impose OAuth, signature asymétrique ou rotation automatique.
- Une fuite réelle ou présumée révèle une faiblesse de la méthode d'injection ou d'expurgation.

## Références

- PROJECT_BRIEF v0.1, sections 6.2 et 9.2.
- Document de cadrage v0.5.1, sections 11, 12.1 et 13.1.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale acceptée sans modification par le porteur. |
