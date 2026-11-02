# Journal — Supervision ALTN72 (2026–27)

**Groupe :** à compléter

## README

- Prérequis : Java 21, Maven Wrapper et MySQL 8.4 installé localement (avec MariaDB, voir le README du dépôt)
- Initialiser la base : créer la base et l'utilisateur MySQL `supervision` (commandes SQL dans `README.md`) ;
  les tables sont créées par Hibernate au premier lancement
- Lancer : `./mvnw spring-boot:run`, puis ouvrir http://localhost:8080 (connexion obligatoire)
- Application déployée : https://supervision-elblh4yp7a-ew.a.run.app (Cloud Run, base Cloud SQL MySQL 8.4 ; redéployée automatiquement à chaque merge sur `main` par un déclencheur Cloud Build)
- Comptes de test : `responsable` / `demo1234` (Alice, RESPONSABLE) et `agent` / `demo1234` (Bruno, AGENT)
- Données de démonstration : chargées au démarrage si la base est vide ; `APP_DEMO_RESET=true` les recrée datées de l'instant présent
- Limites connues : modèle, connexion par profil, layout, données de démo et consultation des équipements (F2–F3) en place ; la déclaration (F4), le détail (F5) et la modification de la description d'un incident (F6) sont disponibles ; F7 à F12 restent à développer

## ARCHITECTURE

- Dépôt : https://github.com/ctpnheee/altn72-supervision
- Schéma et description détaillée : [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
  (couches, packages, modèle de données, cycle de vie d'un incident, niveau d'attention, erreurs, sécurité, déploiement)

```mermaid
flowchart TB
    nav["Navigateur"] -- HTTP --> sec["Spring Security"]
    sec --> web["Couche web : @Controller (Thymeleaf) · @RestController (API) · DTO · @ControllerAdvice"]
    web -- Model --> views["Vues Thymeleaf"]
    web --> svc["Couche service : règles métier RG1–RG10"]
    svc --> repo["Couche persistance : Spring Data JPA"]
    repo --> db[("MySQL")]
```

## TESTS

- Stratégie : tests unitaires métier, tests MVC et tests d'intégration à compléter progressivement
- Commande : `./mvnw verify`
- Couvert : chargement du contexte
- Non couvert : cas d'usage métier, encore non implémentés

## CHOIX TECHNIQUES

### Profils et droits

Deux profils : **RESPONSABLE** (responsable d'exploitation, vision globale) et **AGENT** (signale les pannes
sur le terrain). Toutes les restrictions sont appliquées côté serveur (RG11) : `requestMatchers` dans
`SecurityConfig` et `@PreAuthorize` sur les services ; masquer un bouton n'est qu'un confort.

| N° | Fonctionnalité | Profil 1 : RESPONSABLE | Profil 2 : AGENT |
|---|---|---|---|
| F2–F3 | Consulter équipements et fiches | Lecture seule | Lecture seule |
| F4 | Déclarer un incident | Accès complet | Accès complet |
| F5–F6 | Consulter / modifier un incident | Accès complet (description des incidents non résolus) | Lecture ; modification de la description de ses propres incidents non affectés |
| F7 | Affecter ou réaffecter un prestataire | Accès complet | Interdit |
| F8 | Déclarer un incident résolu | Accès complet | Interdit |
| F9 | Consulter l'historique | Lecture seule | Lecture seule |
| F10 | Évaluer un prestataire | Accès complet | Interdit |
| F11 | Tableau de bord | Accès complet | Interdit |
| F12 | API REST | Accès complet | Lecture seule |

Justification de F6 : l'agent qui a signalé une panne peut corriger sa description tant que personne ne l'a
prise en charge ; une fois l'incident affecté, seul le responsable la modifie, pour que le prestataire
travaille sur une information stable.

### Choix de conception validés

- Un incident peut être résolu sans avoir été affecté (panne réglée en interne) ; il n'est alors pas
  évaluable, puisque RG8 exige un prestataire.
- Déclarer un incident passe l'équipement à `EN_PANNE` ; résoudre son dernier incident ouvert le repasse
  à `EN_SERVICE`. Ces opérations modifient deux entités et sont donc `@Transactional` (RG10).
- Le niveau d'attention (vert, orange, rouge, pris en charge) est calculé à la lecture à partir d'un
  `java.time.Clock` injecté ; il n'est jamais stocké.
- Organisation : pas de rôles figés, chacun s'assigne les tickets GitHub au fil de l'eau ; toute modification
  passe par une pull request dont la CI (`./mvnw verify`) doit être verte.

### Autres choix

- Exceptions : exceptions métier dans `exception/` (`RessourceIntrouvableException`,
  `TransitionInterditeException`, `EvaluationDejaSaisieException`), avec un message en français, traduites
  en réponses `ProblemDetail` par un `@RestControllerAdvice` (`api/ApiExceptionHandler`) : 404 ressource
  introuvable, 409 transition interdite ou évaluation déjà saisie, 400 donnée invalide (avec le champ en cause).
  Choix de `@ControllerAdvice` plutôt que `ResponseStatusException` :
  - la traduction vers HTTP est écrite à un seul endroit ; changer un code ou un format de message ne touche
    qu'une classe ;
  - les services ignorent HTTP (règle de dépendance de la couche service) : ils lèvent une exception qui dit
    ce qui s'est passé, sans choisir de code, et restent testables sans contexte web ;
  - avec `ResponseStatusException`, chaque service devrait connaître les codes HTTP et ses messages seraient
    dispersés dans tout le code ;
  - une même exception métier peut être traduite différemment selon l'origine : JSON pour l'API, page ou
    message flash pour les pages (ticket B3).
  Aucune trace technique n'est renvoyée : seul le message en français l'est. Pas de `catch` vide.
- SOLID : à choisir, implémenter et justifier
- Requête SQL native : à ajouter une seule fois puis analyser
- `@Transactional` : posé sur `IncidentService.declarer`, qui enregistre l'incident et passe l'équipement
  à `EN_PANNE` dans la même transaction (RG10). Résoudre le dernier incident ouvert sera traité de la même façon.
  Posé aussi sur `modifierDescription` : le contrôle du droit relit le statut et le déclarant, puis écrit
  la description. Sans cette transaction, une affectation intercalée laisserait enregistrer la correction d'un agent.
  `consulter` est en lecture seule : `open-in-view` est désactivé, et le détail doit lire l'incident, le prestataire
  et l'évaluation dans la même vue.
- Durée d'ouverture (F5) : calculée dans `IncidentService` avec le `Clock` injecté (incident ouvert : maintenant −
  ouverture ; incident résolu : résolution − ouverture). La vue reçoit une chaîne déjà prête. Sans cela, le template
  comparerait des dates, ce que l'architecture interdit, et un test ne pourrait pas fixer l'instant.
- Droit de modifier la description (F6, RG11) : `@PreAuthorize` sur `modifierDescription` et
  `controlerDroitModification`, délégué à `AutorisationsIncident`. Un `requestMatchers` ne distingue pas un incident
  affecté d'un incident ouvert. Masquer le formulaire ne suffit pas : un POST direct d'un agent sur un incident
  affecté répond 403. La même règle est rejouée dans la transaction, pour le cas où l'incident est affecté entre
  le contrôle de sécurité et l'écriture.

## LOT ALPHA

### Fonctionnalité transverse 1 : à choisir

- Besoin, périmètre, essais, résultats et limites : à compléter

### Fonctionnalité transverse 2 : à choisir

- Besoin, périmètre, essais, résultats et limites : à compléter

## BILAN

- Contribution de chacun : à compléter
- Plus grande difficulté et solution : à compléter
- Fonctionnalités non réalisées et pourquoi : à compléter
- Trois points à retenir : à compléter
