# =============================================================
#  Image de l'API E-commerce
#  Render n'a pas de runtime Java natif : on passe par Docker.
# =============================================================

# ---------- Etape 1 : compilation ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Les dependances changent moins souvent que le code : en copiant d'abord
# le pom, cette couche reste en cache et les builds suivants sont rapides.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Etape 2 : execution ----------
# JRE seul : image bien plus petite qu'avec le JDK et la chaine Maven.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Ne jamais tourner en root dans un conteneur exposé.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

# Render fournit PORT ; 8080 sert de valeur de repli en local.
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage : sans cela la JVM calcule son tas sur la RAM de l'hote
# et non sur la limite du conteneur, ce qui provoque des OOM sur les
# petites instances (512 Mo sur le plan gratuit de Render).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
