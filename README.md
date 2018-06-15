vertx-tutor-app
=======

My version of applications from [Introduction to Vert.x](https://vertx.io/blog/posts/introduction-to-vertx.html)
The examples demonstrate how to use [Vert.x](https://vertx.io) including [Vert.x-core](https://github.com/eclipse/vert.x), [Vert.x-web](https://github.com/vert-x3/vertx-web), [Vert.x-unit](https://github.com/vert-x3/vertx-unit) with HSQLDB and MongoDB.
[Vert.x](https://vertx.io) is a toolkit for building reactive applications on the JVM: modern, scalable network apps.

## Requirements:
  * Java SE Development Kit 8
  * Maven 3.X (or you could use Maven wrapper)
  * Git 1.7.x (or newer)


## Examples:
 * Simple Vert.x instance<br/>
   change the value of element `Main-Verticle` in pom.xml to ...
   ```xml
   <Main-Verticle>ru.shishmakov.SimpleVerticle</Main-Verticle>
   ```

 * Vert.x instance with HSQLDB 2.x (*embedded HSQLDB*)<br/>
   change the value of element `Main-Verticle` in pom.xml to ...
   ```xml
   <Main-Verticle>ru.shishmakov.WebSqlVerticle</Main-Verticle>
   ```

 * Vert.x instance with MongoDB 3.x (*need external mongod process*)<br/>
   change the value of element `Main-Verticle` in pom.xml to ...
   ```xml
   <Main-Verticle>ru.shishmakov.WebMongoVerticle</Main-Verticle>
   ```


## REST API
 * get all items
    - `curl -X GET localhost:8080/api/whiskies`
 * get one item by id
    - `curl -X GET localhost:8080/api/whiskies/1`
 * delete item by id
    - `curl -X DELETE localhost:8080/api/whiskies/2`
 * change item by id
    - `curl -H "Content-Type: application/json" -X PUT -d '{"name":"Jameson","origin":"Ireland"}' localhost:8080/api/whiskies/1`
 * add new item
    - `curl -H "Content-Type: application/json" -X POST -d '{"name":"WhiskyName","origin":"WhiskyOrigin"}' localhost:8080/api/whiskies`


## Run:
 * build fat jar and run unit/integration tests
```bash
$ ./mvnw clean verify
[INFO] Scanning for projects...
[INFO]
[INFO] -------------------< ru.shishmakov:vertx-tutor-app >--------------------
[INFO] Building vertx-tutor-app 1.0-SNAPSHOT
[INFO] --------------------------------[ jar ]---------------------------------
[INFO]
[INFO] --- maven-clean-plugin:2.5:clean (default-clean) @ vertx-tutor-app ---
[INFO] Deleting /Users/dima/programming/git/vertx/vertx-tutor-app/target
[INFO]

...

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 27.809 s
[INFO] Finished at: 2018-06-03T22:08:17+01:00
[INFO] ------------------------------------------------------------------------
```

 * start app
```bash
$ java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar
INFO: Succeeded in deploying verticle
```

 * start app with custom configs
```bash
java -jar ./target/vertx-tutor-app-1.0-SNAPSHOT-fat.jar -conf ./target/classes/application-conf.json
INFO: Succeeded in deploying verticle
```

 * open pages in a web browser
     - 'http://localhost:8080'
     - 'http://localhost:8080/api/whiskies'
     - 'http://localhost:8080/assets/index.html'

## Stop
 * need interruption by the user, such as typing `^C` (Ctrl + C)
 * kill the process `kill <PID>`
