# Architecture — Supervision ALTN72

Ce document décrit l'architecture **cible** de l'application. Il est mis à jour au fil de l'implémentation :
un élément marqué *(à venir)* n'existe pas encore dans le code.

- Dépôt : <https://github.com/ctpnheee/altn72-supervision>
- Pile : Java 21 · Spring Boot 4 (MVC, Data JPA, Security, Validation) · Thymeleaf · MySQL 8.4 · Maven

## 1. Vue d'ensemble en couches

Chaque couche n'appelle que la couche située en dessous. Les règles métier (RG1 à RG10) vivent uniquement
dans la couche service et le modèle, jamais dans les contrôleurs ni dans les vues.

```mermaid
flowchart TB
    nav["Navigateur<br/>pages HTML · appels fetch (lot alpha)"]
    ext["Client REST<br/>Swagger UI, outils de test"]

    subgraph app["Application Spring Boot"]
        sec["Sécurité — Spring Security<br/>session, profils RESPONSABLE / AGENT, BCrypt"]
        subgraph web["Couche web"]
            mvc["@Controller<br/>pages Thymeleaf"]
            api["@RestController<br/>API REST /api (F12)"]
            dto["DTO + @Valid"]
            adv["@ControllerAdvice<br/>MvcExceptionHandler · ApiExceptionHandler"]
        end
        views["Vues Thymeleaf<br/>layout, fragments"]
        svc["Couche service<br/>cycle de vie des incidents, niveau d'attention,<br/>évaluation, tableau de bord · @Transactional"]
        repo["Couche persistance — Spring Data JPA<br/>requêtes dérivées + JPQL + 1 requête native"]
        model["Modèle — entités JPA + enums"]
    end

    db[("MySQL 8.4<br/>H2 en mémoire pour les tests")]

    nav -- HTTP --> sec
    ext -- HTTP / JSON --> sec
    sec --> mvc
    sec --> api
    mvc -- Model --> views
    views -. HTML .-> nav
    mvc --> svc
    api --> svc
    svc --> repo
    repo --> model
    repo -- JPA / SQL --> db
```

| Couche | Responsabilité | Ne fait jamais |
|---|---|---|
| Sécurité | Authentifier, refuser l'accès selon le profil (RG11) | Contenir de la logique métier |
| Web | Recevoir la requête, valider le DTO, appeler un service, choisir la vue ou le code HTTP | Calculer une règle métier, appeler un repository |
| Service | Appliquer RG1 à RG10, délimiter les transactions | Connaître HTTP, Thymeleaf ou les DTO web |
| Persistance | Lire et écrire en base | Décider d'une règle métier |
| Modèle | Représenter le domaine, garantir ses invariants simples | Dépendre de Spring MVC |

## 2. Organisation des packages

```
fr.efrei.altn72.supervision
├── config/        SecurityConfig, ClockConfig (bean java.time.Clock), OpenApiConfig
├── model/         Station, Equipement, Incident, Prestataire, Evaluation, Utilisateur
│   └── enums/     TypeEquipement, EtatEquipement, StatutIncident, NiveauAttention, Profil
├── repository/    StationRepository, EquipementRepository, IncidentRepository, …
├── service/       IncidentService, EquipementService, EvaluationService,
│                  TableauDeBordService, NiveauAttentionCalculator
├── exception/     exceptions métier personnalisées (messages en français)
├── web/           contrôleurs MVC, dto/, MvcExceptionHandler
├── api/           contrôleurs REST, dto/, ApiExceptionHandler
└── init/          DemoDataLoader (données de démonstration relatives à l'instant présent)
```

## 3. Modèle de données

```mermaid
erDiagram
    STATION ||--o{ EQUIPEMENT : "contient"
    EQUIPEMENT ||--o{ INCIDENT : "concerne"
    PRESTATAIRE |o--o{ INCIDENT : "prend en charge"
    INCIDENT ||--o| EVALUATION : "est évalué par"
    UTILISATEUR ||--o{ INCIDENT : "déclare"

    STATION {
        Long id PK
        String nom
    }
    EQUIPEMENT {
        Long id PK
        String reference UK "RG9"
        String libelle
        TypeEquipement type
        EtatEquipement etat
        Long station_id FK
    }
    INCIDENT {
        Long id PK
        Long equipement_id FK
        String description
        LocalDateTime dateOuverture
        StatutIncident statut
        Long prestataire_id FK "nullable"
        LocalDateTime dateResolution "nullable"
        Long declarant_id FK
    }
    PRESTATAIRE {
        Long id PK
        String nom
        String email
        String telephone
    }
    EVALUATION {
        Long id PK
        Long incident_id FK,UK "RG8 : une par incident"
        int note "1 à 5"
        String commentaire "nullable"
        LocalDate date
    }
    UTILISATEUR {
        Long id PK
        String login UK
        String motDePasse "haché BCrypt"
        String prenom
        Profil profil
    }
```

Enums :

| Enum | Valeurs |
|---|---|
| `TypeEquipement` | ASCENSEUR, ESCALIER_MECANIQUE, PORTIQUE, DISTRIBUTEUR, ECRAN_INFORMATION |
| `EtatEquipement` | EN_SERVICE, EN_PANNE |
| `StatutIncident` | NON_AFFECTE, AFFECTE, RESOLU (RG2) |
| `NiveauAttention` | VERT, ORANGE, ROUGE, PRIS_EN_CHARGE — **calculé, jamais stocké** |
| `Profil` | RESPONSABLE, AGENT |

## 4. Cycle de vie d'un incident

```mermaid
stateDiagram-v2
    [*] --> NON_AFFECTE : déclarer (F4, RG1)
    NON_AFFECTE --> AFFECTE : affecter (F7)
    AFFECTE --> AFFECTE : réaffecter (F7)
    NON_AFFECTE --> RESOLU : résoudre (F8)
    AFFECTE --> RESOLU : résoudre (F8)
    RESOLU --> [*]
    note right of RESOLU
        dateResolution >= dateOuverture (RG5)
        plus d'affectation possible (RG5)
        évaluable seulement s'il a un prestataire (RG8)
    end note
```

Toute transition non dessinée (par exemple affecter un incident résolu) lève une
`TransitionInterditeException`, traduite en HTTP 409 par l'API et en message lisible sur les pages.

Les transitions qui modifient plusieurs données sont `@Transactional` (RG10) : déclarer un incident
passe l'équipement `EN_PANNE`, résoudre le dernier incident ouvert le repasse `EN_SERVICE`.

## 5. Niveau d'attention (RG3, RG4)

Calculé à chaque lecture par `NiveauAttentionCalculator`, à partir d'un `Clock` injecté (testable avec
`Clock.fixed`). Les seuils sont deux constantes nommées, définies uniquement dans cette classe.

```mermaid
flowchart LR
    i["Incident non résolu"] --> a{"prestataire affecté ?"}
    a -- oui --> pc["PRIS_EN_CHARGE (RG4)"]
    a -- non --> d["durée = maintenant − dateOuverture"]
    d --> s1{"durée < 48 h ?"}
    s1 -- oui --> v["VERT"]
    s1 -- non --> s2{"durée < 72 h ?"}
    s2 -- oui --> o["ORANGE"]
    s2 -- non --> r["ROUGE"]
```

## 6. Exemple de requête traversant les couches : affecter un prestataire (F7)

```mermaid
sequenceDiagram
    actor R as Responsable
    participant S as Spring Security
    participant C as IncidentController
    participant IS as IncidentService
    participant Rep as IncidentRepository
    participant DB as MySQL

    R->>S: POST /incidents/42/prestataire
    S->>S: session valide et profil RESPONSABLE ? (RG11)
    S->>C: requête autorisée
    C->>C: @Valid AffectationForm
    C->>IS: affecter(42, prestataireId)
    IS->>Rep: findById(42)
    Rep->>DB: SELECT
    IS->>IS: statut != RESOLU ? (RG5)
    IS->>Rep: save(incident)
    Rep->>DB: UPDATE
    IS-->>C: incident
    C-->>R: redirect /incidents/42
```

## 7. Gestion des erreurs

```mermaid
flowchart LR
    svc["Service lève une exception métier"] --> t{"Origine de la requête"}
    t -- "@Controller" --> mvc["MvcExceptionHandler<br/>page d'erreur ou message flash"]
    t -- "@RestController" --> api["ApiExceptionHandler<br/>ProblemDetail JSON"]
```

| Exception | API | Pages |
|---|---|---|
| `RessourceIntrouvableException` (incident, équipement…) | 404 | page « introuvable » |
| Donnée invalide (`MethodArgumentNotValidException`) | 400 | formulaire réaffiché avec messages |
| `TransitionInterditeException`, `EvaluationDejaSaisieException` | 409 | message flash sur la page de l'incident |
| Accès refusé | 403 | page « accès refusé » |
| Non authentifié | 401 | redirection vers `/login` |

Aucune trace technique n'est affichée à l'utilisateur.

## 8. Sécurité

| Ressource | RESPONSABLE | AGENT |
|---|---|---|
| Équipements et fiches (F2, F3) | lecture | lecture |
| Déclarer un incident (F4) | oui | oui |
| Consulter / modifier un incident (F5, F6) | oui | lecture, modification de ses incidents non affectés |
| Affecter, résoudre, évaluer (F7, F8, F10) | oui | interdit |
| Historique (F9) | oui | lecture |
| Tableau de bord (F11) | oui | interdit |
| API `/api/**` (F12) | oui | lecture |

Restrictions appliquées côté serveur par `requestMatchers` dans `SecurityConfig` et `@PreAuthorize` sur
les services ; masquer un bouton dans la vue n'est qu'un confort.

## 9. Déploiement

```mermaid
flowchart LR
    dev["Développeur"] -- "push branche" --> gh["GitHub<br/>pull request"]
    gh -- "./mvnw verify" --> ci["GitHub Actions (CI)"]
    ci -- "vert + revue" --> main["merge sur main"]
    main --> cb["Cloud Build<br/>buildpacks Java 21"]
    cb --> run["Cloud Run<br/>application Spring Boot"]
    run -- "connecteur Cloud SQL" --> sql[("Cloud SQL<br/>MySQL 8.4")]
    sm["Secret Manager<br/>DB_PASSWORD"] -.-> run
    user["Utilisateurs"] -- HTTPS --> run
```

| Environnement | Base | Configuration |
|---|---|---|
| Poste développeur | MySQL local | valeurs par défaut de `application.properties` |
| Tests et CI | H2 en mémoire | `src/test/resources/application.properties` |
| Production | Cloud SQL | variables d'environnement Cloud Run + Secret Manager |

Procédure complète : [SETUP.md](SETUP.md).
