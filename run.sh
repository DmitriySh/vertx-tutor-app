#!/usr/bin/env bash

export main_class="io.vertx.core.Launcher"
export main_verticle="ru.shishmakov.WebMongoVerticle"
#export main_verticle="ru.shishmakov.WebSqlVerticle"
#export main_verticle="ru.shishmakov.SimpleVerticle"

conf=""
for key in "$@"
do
	echo $key
	if [[ -n "$key" && "$key" = "build" ]]; then
		./mvnw clean verify
	fi
	if [[ -n "$key" && -f "$key" ]]; then
		conf=$key
	fi
done


if [[ -n "$conf" ]]
then
	java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar -conf $conf
else
	java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar
fi

