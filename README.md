# Backend — Drug Accelerator

API del proyecto **Drug Accelerator** (diseño acelerado de fármacos). Expone los endpoints que usa el frontend para proyectos, backbones (RFdiffusion) y trabajos de generación de secuencias.

## Qué hace este backend

- **Proyectos**: CRUD de proyectos (nombre, descripción, proteína diana y complejo, opción de descarga desde PDB).
- **Backbones**: Crear, listar y eliminar backbones por proyecto; consultar estado por `runId`; ejecución con contigs, hotspots, cadenas a eliminar, etc.
- **Trabajos de generación**: Crear, listar y eliminar jobs por proyecto; consultar estado por `runId`; descarga de resultados (CSV, FASTA, mejor PDB).
- **Generación**: Endpoint para lanzar o orquestar la generación (según implementación).

El frontend espera la API en **http://localhost:8080** (por ejemplo `GET/POST /api/projects`, `GET/POST/DELETE /api/projects/:id/backbones`, `GET/POST/DELETE /api/projects/:id/generation-jobs`, etc.).

## Cómo ejecutarlo

### Requisitos

Dependen del stack del backend (por ejemplo: Java 17+, Maven/Gradle para Spring Boot; o Python 3.x y dependencias si es FastAPI/Flask).

### Instalación y arranque

Ejemplos según tecnología:

**Si es Spring Boot (Java):**

```bash
./mvnw spring-boot:run
```

o, con Gradle:

```bash
./gradlew bootRun
```

**Si es Python (FastAPI/Flask, etc.):**

```bash
pip install -r requirements.txt
python main.py
# u otro comando que levante el servidor en el puerto 8080
```

Asegúrate de que el servidor quede escuchando en el **puerto 8080** (o cambia la URL en el frontend en `src/environments/environment.ts`).

### Variables de entorno

Si el backend usa base de datos o claves externas, configura las variables que requiera (por ejemplo `DATABASE_URL`, API keys, etc.) según la documentación o los archivos de ejemplo (`.env.example`) del repositorio.

## Proyecto Drug Accelerator

Este backend forma parte del proyecto **Drug Accelerator**. El frontend Angular se conecta a esta API para gestionar proyectos, backbones y trabajos de generación de secuencias.
