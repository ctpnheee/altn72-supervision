# Supervision ALTN72

Socle du projet final ALTN72 2026–2027 : supervision d'équipements et suivi d'incidents.

## Technologies

- Java 21
- Spring Boot, Spring MVC, Spring Data JPA et Spring Security
- Thymeleaf
- MySQL 8.4
- Maven Wrapper

## Démarrage

Prérequis : Java 21+ et Docker.

```bash
docker compose up -d
./mvnw spring-boot:run
```

L'application est disponible sur <http://localhost:8080>.

La configuration MySQL par défaut correspond à `compose.yaml`. En dehors du développement local, définir
`DB_URL`, `DB_USERNAME` et `DB_PASSWORD`; ne jamais versionner de secret. Voir `.env.example`.

## Tests

```bash
./mvnw verify
```

Les tests utilisent une base H2 en mémoire compatible MySQL et ne nécessitent pas Docker.

Le dépôt est volontairement vide de code métier. Il fournit uniquement le projet Maven, ses dépendances,
la configuration MySQL et le test de démarrage généré. Les fonctionnalités F1 à F12 restent à développer.
