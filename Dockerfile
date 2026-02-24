# Backend Drug Accelerator: Java 21, perfil docker. La BD se crea y administra aparte.
# Uso: docker build --build-arg CORE_URL=... --build-arg DB_URL=... --build-arg DB_USERNAME=... --build-arg DB_PASSWORD=... --build-arg CORS_ORIGINS=http://IP:4200 -t backend .

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /build

COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon || true
COPY src src
RUN ./gradlew bootJar -x test --no-daemon

# Imagen final: solo Java 21 JRE (sin MySQL)
FROM eclipse-temurin:21-jre-jammy

ARG CORE_URL
ARG DB_URL
ARG DB_USERNAME
ARG DB_PASSWORD
ARG CORS_ORIGINS

COPY --from=builder /build/build/libs/accelerated-drug-design-backend-*.jar /app/accelerated-drug-design-backend.jar

# Variables de entorno (nombres como en application-docker.yaml)
ENV SPRING_PROFILES_ACTIVE=docker \
    CORE_URL="${CORE_URL}" \
    DB_URL="${DB_URL}" \
    DB_USERNAME="${DB_USERNAME}" \
    DB_PASSWORD="${DB_PASSWORD}" \
    CORS_ORIGINS="${CORS_ORIGINS}"

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/accelerated-drug-design-backend.jar"]
