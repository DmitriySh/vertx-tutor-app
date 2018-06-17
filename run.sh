#!/usr/bin/env bash

export main_class="io.vertx.core.Launcher"
export main_verticle="ru.shishmakov.WebMongoVerticle"
#export main_verticle="ru.shishmakov.WebSqlVerticle"
#export main_verticle="ru.shishmakov.SimpleVerticle"


if [[ -n "$1" && "$1" = "build" ]]; then
	./mvnw clean verify
fi

java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar

