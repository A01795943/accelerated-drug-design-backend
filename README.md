# Backend — Drug Accelerator

API del proyecto **Drug Accelerator** (diseño acelerado de fármacos). Expone los endpoints que usa el frontend para proyectos, backbones (RFdiffusion) y trabajos de generación de secuencias.

## Qué hace este backend

- **Proyectos**: CRUD de proyectos (nombre, descripción, proteína diana y complejo, opción de descarga desde PDB).
- **Backbones**: Crear, listar y eliminar backbones por proyecto; consultar estado por `runId`; ejecución con contigs, hotspots, cadenas a eliminar, etc.
- **Trabajos de generación**: Crear, listar y eliminar jobs por proyecto; consultar estado por `runId`; descarga de resultados (CSV, FASTA, mejor PDB).

El frontend se conecta a esta API (puerto 8080). CORS se configura con `CORS_ORIGINS` (orígenes permitidos separados por coma).

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

Crea la base de datos y las tablas con el script `db.sql` (ejecutarlo contra esa instancia MySQL).

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

Con perfil docker (variables de entorno):

```bash
export CORE_URL=http://...
export DB_URL=jdbc:mysql://...
export DB_USERNAME=...
export DB_PASSWORD=...
export CORS_ORIGINS=http://localhost:4200
./gradlew bootRun --args='--spring.profiles.active=docker'
```
