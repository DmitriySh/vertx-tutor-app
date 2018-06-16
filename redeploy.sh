#!/usr/bin/env bash

export main_class="io.vertx.core.Launcher"
export main_verticle="ru.shishmakov.WebMongoVerticle"
#export main_verticle="ru.shishmakov.WebSqlVerticle"
#export main_verticle="ru.shishmakov.SimpleVerticle"

export compile_verify="compile verify"
export compile_package="compile package"

./mvnw $compile_verify

java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar
