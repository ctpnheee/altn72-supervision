# Supervision ALTN72

Socle du projet final ALTN72 2026–2027 : supervision d'équipements et suivi d'incidents.

## Technologies

- Java 21
- Spring Boot, Spring MVC, Spring Data JPA et Spring Security
- Thymeleaf
- MySQL 8.4
- Maven Wrapper

## Démarrage

Prérequis : Java 21+ et MySQL 8.4 installé localement.

```bash
./mvnw spring-boot:run
```

L'application est disponible sur <http://localhost:8080>.

### Base de données locale

Créer une fois la base et le compte utilisateur (en tant que `root`) :

```sql
CREATE DATABASE IF NOT EXISTS supervision;
CREATE USER IF NOT EXISTS 'supervision'@'localhost' IDENTIFIED BY 'supervision';
GRANT ALL PRIVILEGES ON supervision.* TO 'supervision'@'localhost';
FLUSH PRIVILEGES;
```

Ces valeurs correspondent aux valeurs par défaut de `application.properties`. Pour en utiliser d'autres,
définir `DB_URL`, `DB_USERNAME` et `DB_PASSWORD`. Ne jamais versionner de secret. Voir `.env.example`.

Les tables sont créées automatiquement par Hibernate au premier lancement.

Avec MariaDB au lieu de MySQL, ajouter la variable d'environnement
`SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT=org.hibernate.dialect.MariaDBDialect`.

## Connexion

Toutes les pages exigent une connexion (formulaire sur `/login`). Deux profils existent :

| Profil | Droits |
|---|---|
| `RESPONSABLE` | Tous, y compris le tableau de bord |
| `AGENT` | Consultation et déclaration d'incidents ; pas d'affectation, de résolution ni de tableau de bord |

Il n'y a pas d'inscription : les comptes viennent des données de démonstration,
chargées au démarrage si la base est vide. Les mots de passe sont stockés hachés
avec BCrypt, jamais en clair.

| Login | Mot de passe | Profil |
|---|---|---|
| `responsable` | `demo1234` | RESPONSABLE (Alice) |
| `agent` | `demo1234` | AGENT (Bruno) |

Les dates des incidents sont calculées par rapport à l'heure de démarrage (2 h, 47 h, 48 h, 71 h, 72 h, 100 h)
pour que chaque niveau d'attention soit visible. Pour recréer un jeu frais (par exemple juste avant une démo) :

```bash
APP_DEMO_RESET=true ./mvnw spring-boot:run
```

## Tests

```bash
./mvnw verify
```

Les tests utilisent une base H2 en mémoire et ne nécessitent pas MySQL.

## Intégration et déploiement

Chaque pull request est vérifiée par GitHub Actions (`./mvnw verify`). Chaque merge sur `main`
est déployé automatiquement sur Google Cloud Run. Procédure complète : [docs/SETUP.md](docs/SETUP.md).