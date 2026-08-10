# ADR-004 - Environnement de développement Windows, WSL2 et Docker

- **Statut :** Accepté
- **Date :** 2026-08-10
- **Décideur :** Porteur du Betting Project
- **Portée :** Betting Project principal - Jalon 0
- **Version :** 0.2
- **Documents liés :** PROJECT_BRIEF v0.1; cadrage v0.5.1

## Résumé de la décision

Le développement quotidien s'effectue sous Windows avec Eclipse et le wrapper Maven ; Docker Desktop exécute les dépendances locales, WSL2 sert aux validations Linux et le VPS reste réservé au staging puis à la production.

## Contexte

Le poste Windows 11, WSL2, Docker Desktop et Eclipse sont déjà disponibles et maîtrisés par le porteur.

La production doit fonctionner sur Linux sans dépendre du poste Windows. Le développement direct sur le VPS augmenterait le risque opérationnel.

Le porteur a confirmé le 10 août 2026 le socle du projet principal : Java 25.0.4 LTS, Spring Framework 7.0.8 et Spring Boot 4.1.0. Le laboratoire SofaScore ne fixe pas ce choix pour le projet principal.

## Décision

Windows 11 héberge Eclipse, l'application Codex, Git, les outils bureautiques et les tâches d'administration locale.

L'application peut être lancée depuis Eclipse ou avec mvnw.cmd. Les dépendances d'infrastructure, notamment PostgreSQL, sont exécutées dans Docker Desktop via Compose.

WSL2 fournit un environnement Linux de validation pour les scripts, les droits, les chemins, les fins de ligne et les commandes nécessaires au futur VPS. Un clone Linux facultatif peut être utilisé pour ces contrôles.

Aucune machine virtuelle locale complète n'est ajoutée. Aucun développement normal n'est effectué directement en production.

Le dépôt versionne mvnw et mvnw.cmd, une configuration d'édition commune, des scripts portables et une documentation de démarrage pour Windows et WSL2.

Le JDK de compilation et d'exécution cible est Java 25.0.4 LTS.

Le socle applicatif utilise Spring Boot 4.1.0 et Spring Framework 7.0.8. La gestion de dépendances de Spring Boot reste l'autorité ; aucune surcharge manuelle de la version Spring Framework n'est introduite sans justification documentée.

Le wrapper Maven utilise une version compatible avec Spring Boot 4.1.0, au minimum Maven 3.6.3.

La compatibilité du triplet est validée par les exigences officielles de Spring Boot 4.1.0 : Java 17 au minimum, compatibilité jusqu'à Java 26 et Spring Framework 7.0.8 ou version ultérieure.

## Invariants

- Le build et les tests standards s'exécutent sans chemin absolu propre au poste du porteur.
- Les dépendances de service ne nécessitent pas d'installation manuelle dans Windows lorsqu'elles peuvent être conteneurisées.
- La configuration locale n'est jamais supposée identique à la production ; les écarts sont documentés et testés.
- Le VPS ne contient aucun outil requis uniquement pour l'édition ou le débogage local.
- Les commandes de référence passent par les wrappers et fichiers Compose versionnés.
- Le build effectif ne dérive pas silencieusement de Java 25.0.4 LTS, Spring Boot 4.1.0 ou Spring Framework 7.0.8.

## Alternatives étudiées

### Développement dans une VM Linux complète

**Résultat :** Rejetée

Doublonne WSL2 et Docker sans bénéfice proportionné pour le workflow courant.

### Développement directement sur le VPS

**Résultat :** Rejetée

Risque pour la production, reproductibilité faible et séparation des environnements insuffisante.

### Tout exécuter dans Docker, y compris l'IDE

**Résultat :** Différée

Complexifie le débogage quotidien sans exigence démontrée.

### Installer PostgreSQL directement sous Windows

**Résultat :** Rejetée

Réduit la reproductibilité et augmente les écarts de configuration.

## Conséquences

### Conséquences positives

- Réutilisation des outils déjà installés et maîtrisés.
- Dépendances locales reproductibles et faciles à réinitialiser.
- Validation Linux avant promotion vers le VPS.
- Séparation claire entre développement, staging et production.

### Coûts, limites et risques acceptés

- Des différences de chemins, permissions et réseau entre Windows, WSL2 et Docker doivent être testées.
- Docker Desktop et WSL2 ajoutent une couche de diagnostic lors des incidents locaux.
- Deux commandes de lancement, mvnw.cmd et mvnw, doivent rester cohérentes.

## Critères d'acceptation

- [ ] Le projet compile et exécute ses tests avec mvnw.cmd sous Windows.
- [ ] Les contrôles Linux définis s'exécutent avec mvnw sous WSL2.
- [ ] Docker Compose démarre PostgreSQL et les dépendances sans secret versionné.
- [ ] La documentation permet de reconstruire l'environnement local à partir d'un clone propre.
- [ ] Aucun chemin utilisateur absolu n'apparaît dans le code ou la configuration versionnée.
- [ ] java -version signale Java 25.0.4 LTS sur le poste Windows de référence.
- [ ] mvnw.cmd -version signale Java 25.0.4 LTS et une version Maven au moins égale à 3.6.3.
- [ ] Le modèle Maven effectif identifie Spring Boot 4.1.0 et résout Spring Framework 7.0.8.
- [ ] Les tests de démarrage et d'intégration passent avec ce triplet sous Windows et lors de la validation Linux sous WSL2.

## Déclencheurs de réexamen

- Le porteur change d'IDE ou de système principal.
- La compatibilité Windows/WSL2 devient un coût supérieur à un environnement de développement conteneurisé.
- La production adopte une plateforme différente de Linux/Docker Compose.
- Une alerte de sécurité, une fin de support ou une version corrective impose la révision de Java, Spring Boot ou Spring Framework.
- Une dépendance indispensable est incompatible avec le triplet Java 25.0.4 LTS, Spring Boot 4.1.0 et Spring Framework 7.0.8.

## Références

- PROJECT_BRIEF v0.1, sections 1.3, 6.1, 8.1 et 8.3.
- Document de cadrage v0.5.1, sections 1.5, 3.1, 3.3, 3.5 et 13.1.
- Spring Boot 4.1.0 - System Requirements : https://docs.spring.io/spring-boot/system-requirements.html.
- Spring Boot 4.1.0 disponible : https://spring.io/blog/2026/06/10/spring-boot-4/.
- Spring Framework 7.0.8 disponible : https://spring.io/blog/2026/06/08/spring-framework-7-0-8-and-6-2-19-available-now/.
- Contrôle local java -version fourni par le porteur, 10 août 2026.

## Historique

| Version | Date | Évolution |
|---|---|---|
| 0.1 | 2026-08-10 | Proposition initiale pour validation. |
| 0.2 | 2026-08-10 | Acceptation et fixation de Java 25.0.4 LTS, Spring Framework 7.0.8 et Spring Boot 4.1.0. |
