# Mise en place (jour 0)

Trois parties : poste de chaque développeur, GitHub, Google Cloud. Docker n'est pas utilisé.

```
branche feature → pull request → CI (./mvnw verify) → revue → merge main → Cloud Build déploie sur Cloud Run
```

## 1. Poste développeur (chaque membre)

Prérequis : Java 21+ et Homebrew (macOS).

```bash
brew install mysql@8.4
brew services start mysql@8.4
mysql -u root -e "CREATE DATABASE IF NOT EXISTS supervision; CREATE USER IF NOT EXISTS 'supervision'@'localhost' IDENTIFIED BY 'supervision'; GRANT ALL ON supervision.* TO 'supervision'@'localhost';"
```

Ces identifiants correspondent aux valeurs par défaut de `application.properties` : aucune variable à définir.
Pour d'autres identifiants, définir `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` dans la configuration d'exécution
de l'IDE (Spring ne lit pas le fichier `.env`). Ne jamais les versionner.

```bash
./mvnw verify            # tests (H2 en mémoire, MySQL inutile)
./mvnw spring-boot:run   # http://localhost:8080
```

## 2. GitHub (une seule fois, par le propriétaire du dépôt)

1. **Settings → Collaborators** : inviter le second développeur et l'enseignant.
2. **Settings → Branches → Add branch ruleset** (ou *branch protection rule*) sur `main` :
   - Require a pull request before merging, 1 approbation ;
   - Require status checks to pass : cocher `verify` (visible après la première exécution de la CI) ;
   - Block force pushes.
3. Le workflow `.github/workflows/ci.yml` lance `./mvnw verify` sur chaque pull request et chaque push sur `main`.

Convention de branches : `feature/F4-declaration-incident`, `fix/…`, `docs/…`.
Convention de commits : préfixer par la fonctionnalité, par exemple `F7: réaffectation d'un prestataire`.

## 3. Google Cloud (une seule fois)

Architecture : **Cloud Run** (application, construite depuis le code Maven par les buildpacks, Java 21 fixé
dans `project.toml`) + **Cloud SQL for MySQL 8.4** (base) + **Cloud Build** (déploiement à chaque merge sur `main`).
Cloud Run rejoint Cloud SQL via le connecteur `mysql-socket-factory-connector-j-8` (déclaré dans `pom.xml`),
sans exposer la base sur Internet.

### 3.1 Compte et projet (console, par le propriétaire)

1. <https://console.cloud.google.com> : activer l'essai gratuit (300 $ de crédit pendant 90 jours, carte requise).
2. Créer le projet `altn72-supervision` (noter son **ID de projet**).
3. **Facturation → Budgets et alertes** : créer un budget de 10 € avec alertes à 50 %, 90 %, 100 %.
4. **IAM** : ajouter le second développeur avec le rôle *Éditeur*.

### 3.2 Outil en ligne de commande

```bash
brew install --cask google-cloud-sdk
gcloud auth login
gcloud config set project ID_DU_PROJET
gcloud config set run/region europe-west1
```

### 3.3 Activer les services

```bash
gcloud services enable run.googleapis.com sqladmin.googleapis.com cloudbuild.googleapis.com \
  artifactregistry.googleapis.com secretmanager.googleapis.com
```

### 3.4 Base Cloud SQL (≈ 10 min de création)

```bash
gcloud sql instances create supervision-db --database-version=MYSQL_8_4 --edition=ENTERPRISE \
  --tier=db-f1-micro --region=europe-west1 --storage-size=10
gcloud sql databases create supervision --instance=supervision-db
```

Le mot de passe est généré puis placé directement dans Secret Manager : il n'est jamais affiché ni versionné.

```bash
DB_PASSWORD="$(openssl rand -base64 24)"
gcloud sql users create supervision --instance=supervision-db --password="$DB_PASSWORD"
printf '%s' "$DB_PASSWORD" | gcloud secrets create db-password --data-file=-
unset DB_PASSWORD
```

### 3.5 Droits du compte de service de Cloud Run

```bash
PROJECT_NUMBER=$(gcloud projects describe "$(gcloud config get project)" --format='value(projectNumber)')
SA="$PROJECT_NUMBER-compute@developer.gserviceaccount.com"
for ROLE in roles/cloudsql.client roles/secretmanager.secretAccessor roles/run.builder; do
  gcloud projects add-iam-policy-binding "$(gcloud config get project)" --member="serviceAccount:$SA" --role="$ROLE"
done
```

### 3.6 Premier déploiement (depuis la racine du dépôt)

```bash
INSTANCE=$(gcloud sql instances describe supervision-db --format='value(connectionName)')
gcloud run deploy supervision --source . --allow-unauthenticated --memory=1Gi \
  --add-cloudsql-instances="$INSTANCE" \
  --set-env-vars="^@^DB_URL=jdbc:mysql:///supervision?cloudSqlInstance=$INSTANCE&socketFactory=com.google.cloud.sql.mysql.SocketFactory@DB_USERNAME=supervision@JPA_DDL_AUTO=update" \
  --set-secrets=DB_PASSWORD=db-password:latest
```

`^@^` change le séparateur des variables, car l'URL contient déjà `&`. La commande affiche l'URL publique
(`https://supervision-….run.app`) à reporter dans `journal.md`. `PORT` est fourni par Cloud Run.

### 3.7 Déploiement continu depuis `main` (console)

**Cloud Run → supervision → Configurer le déploiement continu** :
connecter GitHub, dépôt `ctpnheee/altn72-supervision`, branche `^main$`, type de build *Go, Node.js, Python,
Java, .NET Core, Ruby ou PHP via Google Cloud Buildpacks*. Les variables et le secret de 3.6 sont conservés.

`main` étant protégée (CI obligatoire avant merge), seul du code testé y arrive.

### 3.8 Utilisation courante

| Besoin | Commande |
|---|---|
| Journaux de l'application | `gcloud run services logs read supervision --limit=100` |
| Révisions déployées | `gcloud run revisions list --service=supervision` |
| Revenir à une révision | `gcloud run services update-traffic supervision --to-revisions=RÉVISION=100` |
| Modifier une variable | `gcloud run services update supervision --update-env-vars=CLE=valeur` |
| Ouvrir un client MySQL | `gcloud sql connect supervision-db --user=supervision --database=supervision` |

Après l'évaluation : `gcloud sql instances delete supervision-db` pour arrêter la facturation.

## 4. Vérification

- [ ] `./mvnw verify` passe chez les deux développeurs
- [ ] La CI est verte sur une première pull request
- [ ] L'URL Cloud Run affiche la page de connexion
- [ ] `main` est protégée
