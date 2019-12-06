# Multi-stage build
# === Bild image: 1st phase ===
FROM maven:3.6.2-jdk-8-slim  as maven

# copy the project files
COPY ./ ./

ARG main_class="io.vertx.core.Launcher"
ARG main_verticle="ru.shishmakov.WebSqlVerticle"
#ARG main_verticle="ru.shishmakov.WebMongoVerticle"
#ARG main_verticle="ru.shishmakov.SimpleVerticle"

# build all dependencies for offline usage
RUN mvn dependency:go-offline --no-transfer-progress --batch-mode
RUN mvn clean package


#  === Artifact image: 2nd phase ===
FROM  azul/zulu-openjdk-alpine:8u232
LABEL maintainer="DmitriySh"
EXPOSE 8080

# set deployment directory
WORKDIR /project

# copy built artifact from the maven image
COPY --from=maven target/vertx-tutor*-fat.jar ./vertx-tutor.jar
# run app
ENTRYPOINT exec java $JAVA_OPTS -jar ./vertx-tutor.jar

#docker image build -f ./docker/Dockerfile -t test-vertx-tutor:latest ./ --pull --no-cache
#docker run -p 8080:8080 --name test-vertx-app test-vertx-tutor
