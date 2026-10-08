# Backend — Drug Accelerator

## Requisitos
Requisitos: Java 21+, Gradle. Perfil por defecto: local (BD y core en application-local.yaml).

API del proyecto **Drug Accelerator** (diseño acelerado de fármacos). Expone los endpoints que usa el frontend para proyectos, backbones (RFdiffusion) y trabajos de generación de secuencias.

## Qué hace este backend

- **Proyectos**: CRUD de proyectos (nombre, descripción, proteína diana y complejo, opción de descarga desde PDB).
- **Backbones**: Crear, listar y eliminar backbones por proyecto; consultar estado por `runId`; ejecución con contigs, hotspots, cadenas a eliminar, etc.
- **Trabajos de generación**: Crear, listar y eliminar jobs por proyecto; consultar estado por `runId`; descarga de resultados (CSV, FASTA, mejor PDB).

El frontend se conecta a esta API (puerto 8080). CORS se configura con `CORS_ORIGINS` (orígenes permitidos separados por coma).

## Propiedades de minibatches, métricas y carga

En `application.yaml` (perfil `test` alarga los intervalos a 1 h para que los schedulers no disparen durante los tests):

| Propiedad | Default | Uso |
|-----------|---------|-----|
| `app.generation.minibatch-size` | 100 | Secuencias por minibatch (`GENERATION_MINIBATCH_SIZE`) |
| `app.generation.dispatch-interval-ms` | 15000 | Cadencia de despacho y recolección (`GENERATION_DISPATCH_INTERVAL_MS`) |
| `app.generation.max-attempts` | 3 | Reintentos de un minibatch tras ERROR, instancia `UNAVAILABLE` o run huérfano (`GENERATION_MAX_ATTEMPTS`) |
| `app.core.metrics.poll-interval-ms` | 300000 | Poll de `GET /metrics/system` por instancia (`CORE_METRICS_POLL_INTERVAL_MS`) |
| `app.core.metrics.max-failures` | 3 | Fallos seguidos antes de marcar la instancia `UNAVAILABLE` (`CORE_METRICS_MAX_FAILURES`) |
| `app.core.metrics.retention-days` | 7 | Días de `core_instance_metrics_history` (`CORE_METRICS_RETENTION_DAYS`) |
| `app.core.load.cpu-enter` / `memory-enter` / `gpu-util-enter` / `gpu-mem-enter` / `disk-enter` | 90 / 90 / 95 / 90 / 90 | Umbral estricto `>` para pasar a `OVERLOADED` |
| `app.core.load.cpu-recover` / `memory-recover` / `gpu-util-recover` / `gpu-mem-recover` / `disk-recover` | 80 / 80 / 80 / 80 / 85 | Hay que quedar por debajo de todos para volver a `AVAILABLE` |

`GET/POST/PATCH /api/cores` (rol admin) administra el registro de instancias, el historial y un refresh manual de métricas.

---

## Instalar y desplegar el backend (admin en GCP)

Esta guía asume que el **sistema core** de Drug Accelerator está montado en un servidor accesible desde internet. Si está en una workstation del Tecnológico de Monterrey a la que se accede por **VPN Tailscale**, instala la VPN en la instancia antes de desplegar:

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up
```

Se mostrará un enlace para autenticar en el navegador. Tras autenticar, la terminal mostrará **Success** y la instancia podrá comunicarse con la workstation.

### 1. Crear la base de datos en GCP (Cloud Shell)

```bash
gcloud sql instances create drug-accelerator-mysql \
  --database-version=MYSQL_8_0 \
  --region=us-central1 \
  --tier=db-g1-small \
  --storage-type=SSD \
  --storage-size=10 \
  --backup-start-time=03:00 \
  --authorized-networks=0.0.0.0/0
```

Ver la IP de la BD:

```bash
gcloud sql instances describe drug-accelerator-mysql \
  --format="value(ipAddresses)"
```

Crear contraseña de root:

```bash
gcloud sql users set-password root \
  --host=% \
  --instance=drug-accelerator-mysql \
  --password="PASSWORD"
```

Crea la base de datos y las tablas con Flyway al arrancar la aplicación (migraciones en `src/main/resources/db/migration/`). El archivo `db.sql` es solo referencia. Si la instancia ya tiene el schema completo creado manualmente, usar `FLYWAY_BASELINE_VERSION=2` en el primer arranque.

### 2. Crear la instancia VM en GCP

```bash
gcloud compute instances create drug-accelerator-admin \
  --machine-type e2-small \
  --zone us-central1-a \
  --image-family ubuntu-2204-lts \
  --image-project ubuntu-os-cloud \
  --maintenance-policy MIGRATE \
  --boot-disk-size 30GB \
  --boot-disk-type pd-balanced \
  --tags http-server,https-server
```

Abrir puerto 8080 (backend):

```bash
gcloud compute instances add-tags drug-accelerator-admin \
  --zone us-central1-a \
  --tags backend-8080

gcloud compute firewall-rules create allow-backend-8080 \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:8080 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=backend-8080
```

Abrir puerto 4200 (frontend):

```bash
gcloud compute instances add-tags drug-accelerator-admin \
  --zone us-central1-a \
  --tags frontend-4200

gcloud compute firewall-rules create allow-frontend-4200 \
  --direction=INGRESS \
  --priority=1000 \
  --network=default \
  --action=ALLOW \
  --rules=tcp:4200 \
  --source-ranges=0.0.0.0/0 \
  --target-tags=frontend-4200
```

### 3. En la VM: instalar Docker y Docker Compose

Conéctate por SSH a la instancia y ejecuta:

```bash
sudo apt update
sudo apt install -y ca-certificates curl gnupg

# Llave oficial de Docker
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

# Repositorio
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
  https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo "$VERSION_CODENAME") stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Instalar
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo systemctl enable docker
sudo systemctl start docker

docker --version
docker compose version
```

### 4. Desplegar el backend

Clonar el repositorio:

```bash
git clone https://github.com/A01795943/accelerated-drug-design-backend.git
cd accelerated-drug-design-backend
```

Construir la imagen (sustituye las URLs, IP de la BD, usuario, contraseña y CORS por los tuyos):

```bash
sudo docker build \
  --build-arg CORE_URL=http://100.76.235.84:8000 \
  --build-arg DB_URL="jdbc:mysql://IP_BD:3306/drug_accelerator?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&connectionCollation=utf8_general_ci" \
  --build-arg DB_USERNAME=root \
  --build-arg DB_PASSWORD="TU_PASSWORD" \
  --build-arg CORS_ORIGINS=http://IP_VM:4200 \
  -t backend \
  .
```

Si construyes desde una carpeta que **contiene** `accelerated-drug-design-backend`:

```bash
sudo docker build \
  --build-arg CORE_URL=http://100.76.235.84:8000 \
  --build-arg DB_URL="jdbc:mysql://IP_BD:3306/drug_accelerator?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8&connectionCollation=utf8_general_ci" \
  --build-arg DB_USERNAME=root \
  --build-arg DB_PASSWORD="TU_PASSWORD" \
  --build-arg CORS_ORIGINS=http://IP_VM:4200 \
  -f accelerated-drug-design-backend/Dockerfile \
  -t backend \
  accelerated-drug-design-backend
```

Iniciar el contenedor:

```bash
sudo docker run -d \
  --name backend \
  -p 8080:8080 \
  --restart unless-stopped \
  backend
```

El backend queda disponible en `http://IP_VM:8080`.

---

## Ejecución local (sin Docker)

Requisitos: Java 21+, Gradle. Perfil por defecto: `local` (BD y core en `application-local.yaml`).

```bash
./gradlew bootRun
```

### Flyway / schema

El schema se aplica al arrancar (`src/main/resources/db/migration/`). Por defecto `FLYWAY_BASELINE_VERSION=1`: Flyway marca V1 como baseline sobre tablas existentes y ejecuta V2 (campañas).

**Solo** usa `FLYWAY_BASELINE_VERSION=2` si la BD ya tiene **todas** las tablas de campaña creadas manualmente con `db.sql` completo.

**Error `missing table [campaign_backbones]`:** Flyway quedó en v2 sin haber creado las tablas (p. ej. arranque previo con `FLYWAY_BASELINE_VERSION=2` en una BD solo V1). Reparación:

```bash
# Verifica que FLYWAY_BASELINE_VERSION no esté en 2
# PowerShell: Remove-Item Env:FLYWAY_BASELINE_VERSION -ErrorAction SilentlyContinue

./gradlew repairFlywaySchema
./gradlew bootRun
```

Alternativa manual: `./gradlew dropFlywayHistory` y reiniciar con baseline por defecto (1).

Con perfil docker (variables de entorno):

```bash
export CORE_URL=http://...
export DB_URL=jdbc:mysql://...
export DB_USERNAME=...
export DB_PASSWORD=...
export CORS_ORIGINS=http://localhost:4200
./gradlew bootRun --args='--spring.profiles.active=docker'
```

---

## API documentation (OpenAPI / Swagger UI)

The backend can expose **OpenAPI 3** docs and **Swagger UI** via [springdoc-openapi](https://springdoc.org/).

- **Enable/disable**: Set `app.api-docs.enabled` (default `true`). In production use profile `prod` or set `API_DOCS_ENABLED=false` so docs are not exposed.
- **URLs** (when enabled):
  - **Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (or `/swagger-ui/index.html`)
  - **OpenAPI JSON**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Security**: The spec documents JWT Bearer auth. Use **Authorize** in Swagger UI and paste the token from `POST /api/auth/login`. The login endpoint is marked as public (no Bearer required).
- **Build & run**:
  ```bash
  ./gradlew build
  ./gradlew bootRun
  ```
  Then open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html).
