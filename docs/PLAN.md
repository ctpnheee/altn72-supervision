# Plan d'implémentation — 2 développeurs

Échéance : **jeudi 5 novembre 2026, 23 h 59**. Gel du code : **mercredi 4 novembre au soir**.
Référence de conception : [ARCHITECTURE.md](ARCHITECTURE.md). Mise en place : [SETUP.md](SETUP.md).

## 1. Principes

- **Découpage vertical** : chaque développeur réalise ses fonctionnalités de bout en bout
  (service → contrôleur → vue → tests). Moins de blocages qu'un découpage back / front, et chacun
  maîtrise toutes les couches, ce que les contrôles oraux vérifient (§10 du sujet).
- **Socle commun d'abord** : le modèle, les données de démonstration et la sécurité débloquent tout le reste.
  Ils sont faits en semaine 1, en priorité.
- **Une branche et une pull request par ticket**, relue par l'autre sous 24 h. `main` est toujours déployable.
- **Chaque ticket arrive avec ses tests.** Un ticket sans test n'est pas terminé.

## 2. Répartition

| | Dev A — cycle de vie et API | Dev B — accès, consultation et supervision |
|---|---|---|
| Socle | Modèle, repositories, niveau d'attention, données de démo, exceptions | Sécurité, layout et identité visuelle, gestion d'erreurs des pages, déploiement |
| Socle commun | F4, F5, F6, F7, F8, F12 | F1, F2, F3, F9, F10, F11 |
| Lot alpha | Journal métier (piste 5) | Interface enrichie par l'API (piste 6) |
| Exigences | `@Transactional`, SOLID (événements) | Requête SQL native (tableau de bord) |

> Décision du 28/10/2026 : pas de rôles figés. Chacun s'assigne les tickets au fil de l'eau ; les colonnes
> A et B ci-dessus servent seulement à regrouper des tickets cohérents.

**Fichiers partagés et propriétaire** (l'autre propose ses changements par pull request) :
`model/` et `DemoDataLoader` → A · `SecurityConfig` et `templates/layout.html` → B ·
`journal.md` → chacun sa section, B rédige README, A rédige CHOIX TECHNIQUES.

## 3. Tickets

Préfixe de branche : `feature/<ticket>-<sujet>`, par exemple `feature/A4-declarer-incident`.

### Semaine 1 (mer. 14 → dim. 20 oct.) — socle

| Ticket | Dev | Contenu | Dépend de | Tests attendus |
|---|---|---|---|---|
| **0** | A + B | **Session commune (1 h 30)** : valider ARCHITECTURE.md, droits par profil, nom du projet ; fusionner la mise en place (CI, Cloud Run) | — | CI verte |
| A1 | A | Entités, enums, repositories (dérivées + JPQL), contrainte d'unicité de la référence | 0 | `@DataJpaTest` : référence unique (RG9) |
| A2 | A | Bean `Clock`, `NiveauAttentionCalculator` avec constantes 48 h / 72 h | A1 | 47 h vert, 48 h orange, 71 h 59 orange, 72 h rouge, affecté → `PRIS_EN_CHARGE` |
| A3 | A | `DemoDataLoader` : stations, types variés, incidents à 47 h / 48 h / 72 h, affectés, résolus ; 2 comptes ; option `APP_DEMO_RESET` | A1, B1 (encodeur) | Le contexte démarre ; nombre d'incidents par statut |
| A4 | A | Exceptions métier + `ApiExceptionHandler` (`ProblemDetail`) | A1 | — (testé avec A8) |
| B1 | B | `SecurityConfig`, `UserDetailsService`, BCrypt, **F1** (connexion, erreur, « Bonjour Prénom (profil) \| Déconnectez-vous ») | A1 | MockMvc : non connecté → `/login` ; mauvais mot de passe → erreur ; déconnexion |
| B2 | B | Layout Thymeleaf, fragments, CSS, logo et nom du projet | 0 | — |
| B3 | B | `MvcExceptionHandler`, pages 403 / 404 / erreur, sans trace technique | A4 | MockMvc : page 404 lisible |
| B4 | B | **F2, F3** : équipements par station, fiche équipement (incidents triés par ouverture, RG7) | A1, A3 | Service : tri des incidents ; MockMvc : page affichée |
| B5 | B | Premier déploiement Cloud Run + déploiement continu | 0 | URL publique affiche `/login` |

**Jalon dim. 20** : connexion des deux profils sur l'URL publique, données de démo visibles, F2 / F3 en ligne.

### Semaine 2 (lun. 21 → dim. 27 oct.) — fonctionnalités du socle

| Ticket | Dev | Contenu | Dépend de | Tests attendus |
|---|---|---|---|---|
| A5 | A | **F4** : `declarer()` (RG1, équipement → `EN_PANNE`, `@Transactional`), formulaire + `@Valid` | A1–A4 | Démarre sans prestataire ; formulaire invalide réaffiché |
| A6 | A | **F5, F6** : détail (durée d'ouverture, prestataire, évaluation), modification selon le profil | A5 | AGENT ne modifie pas un incident affecté (403) |
| A7 | A | **F7, F8** : affecter / réaffecter / résoudre (RG5 ; dernier incident → équipement `EN_SERVICE`) | A5 | Résolu non réaffectable (exception) ; date résolution ≥ ouverture |
| A8 | A | **F12** : `GET /api/incidents`, `GET /api/incidents/{id}`, `PUT /api/incidents/{id}/prestataire`, `PATCH /api/incidents/{id}/resolution` + Swagger (springdoc) | A7 | 404 sur incident inexistant ; 409 sur transition interdite ; 401 sans session |
| B6 | B | **F9** : historique des résolus, du plus récent au plus ancien (RG7) | A3 | `@DataJpaTest` : ordre de l'historique |
| B7 | B | **F10** : `EvaluationService` (RG8 : résolu + affecté, une seule, note 1–5), formulaire, note moyenne sur la fiche prestataire | A3 | Refus si non résolu ; refus si déjà évalué ; note hors bornes |
| B8 | B | Fiche prestataire avec note moyenne et interventions | B7 | Moyenne calculée |

**Jalon dim. 27** : F1 à F10 et F12 fonctionnent en ligne ; démonstration complète d'un incident de bout en bout.

### Semaine 3 (lun. 28 oct. → dim. 2 nov.) — supervision et lot alpha

| Ticket | Dev | Contenu | Dépend de | Tests attendus |
|---|---|---|---|---|
| B9 | B | **F11** : `TableauDeBordService`, incidents actifs (RG6), badges, distinction affecté (RG4), filtres station / type / niveau, tri par ancienneté, indicateurs | A2, A7 | Résolu absent du tableau ; filtres ; AGENT → 403 |
| B10 | B | **Requête native** : incidents actifs par station (indicateur du tableau de bord) + analyse critique dans le journal | B9 | `@DataJpaTest` sur la requête |
| A9 | A | **Alpha 5 — journal métier** : entité `EvenementIncident`, `ApplicationEventPublisher` + `@EventListener`, affichage sur le détail | A7 | Affecter un incident crée un événement (qui, quand, quoi) |
| B11 | B | **Alpha 6 — interface enrichie** : fenêtre modale d'affectation en `fetch` sur l'API F12, mise à jour du badge sans rechargement | A8, B9 | Test MockMvc de l'endpoint utilisé ; essai manuel décrit dans le journal |
| A10 | A | Durcissement : revue Clean Code (constantes, imports, code mort), tests manquants | — | `./mvnw verify` vert |

**Jalon dim. 2** : toutes les fonctionnalités et les deux lots alpha en ligne.

### Finalisation (lun. 3 → jeu. 5 nov.)

| Quand | Qui | Quoi |
|---|---|---|
| lun. 3 | A + B | **Échange de code** : chacun explique à l'autre une partie qu'il n'a pas écrite (préparation aux contrôles oraux) |
| lun. 3 – mar. 4 | A | `journal.md` : CHOIX TECHNIQUES (exceptions, SOLID, native, `@Transactional`), LOT ALPHA 1 |
| lun. 3 – mar. 4 | B | `journal.md` : README (URL, comptes), TESTS, LOT ALPHA 2 ; mise à jour d'ARCHITECTURE.md |
| mer. 4 | A + B | Clone propre → suivre le README → `./mvnw verify` → liste de contrôle de l'annexe B ; **gel du code** |
| mer. 4 | A + B | Répétition de la démonstration (agent signale → responsable voit rouge → affecte → résout → évalue) |
| jeu. 5 | A + B | BILAN, redéploiement final (données de démo fraîches), dépôt Moodle |

## 4. Points de synchronisation

- **Chaque jour** : message de 3 lignes (fait / prévu / bloqué).
- **Dimanche soir** : vérification du jalon sur l'URL publique, ajustement du plan de la semaine suivante.
- **Dépendances critiques** à livrer tôt, car elles débloquent l'autre : A1 (modèle), A3 (démo), B1 (sécurité),
  A8 (API, pour le lot alpha de B).

## 5. Risques

| Risque | Parade |
|---|---|
| A1 en retard bloque tout | Fait pendant ou juste après la session commune ; B travaille sur B2 en attendant |
| Conflits Git sur les fichiers partagés | Un propriétaire par fichier partagé ; petites pull requests fusionnées vite |
| Premier déploiement qui échoue tard | B5 dès la semaine 1, sur une application presque vide |
| Seuils horaires faux à la démo | Dates de démo relatives à l'instant présent + redéploiement juste avant la démo |
| Un membre ne connaît pas une partie | Relecture croisée de chaque pull request + échange de code le 3 nov. |
