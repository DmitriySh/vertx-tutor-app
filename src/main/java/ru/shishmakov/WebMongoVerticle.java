package ru.shishmakov;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.mongo.FindOptions;
import io.vertx.ext.mongo.MongoClient;
import io.vertx.ext.mongo.UpdateOptions;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import ru.shishmakov.blog.Whisky;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * Use <b>vertx-web</b> part of Vert.x
 */
public class WebMongoVerticle extends AbstractVerticle {

    private static final String COLLECTION = "whiskies";
    private static final String COLLECTION_SEQ = "whiskies_seq";
    private static final String DATABASE = "whisky_store";
    private static final Pattern digits = Pattern.compile("^[0-9]+$");

    private MongoClient mongoClient;

    @Override
    public void start(Future<Void> verticleFuture) {
        this.mongoClient = MongoClient.createShared(vertx, ((UnaryOperator<JsonObject>) conf -> {
            conf.getMap().putIfAbsent("db_name", DATABASE);
            conf.getMap().putIfAbsent("connection_string", "mongodb://localhost:27017");
            return conf;
        }).apply(config()), "ds-whisky");
        initDefaultData(
                initialized -> startWeb(httpServer -> completeStartup(httpServer, verticleFuture)),
                verticleFuture);
    }

    @Override
    public void stop() {
        mongoClient.close();
    }

    /**
     * Initializes the database with default values and then calls the next step
     *
     * @param next           next step in the chain handlers
     * @param verticleFuture main verticle future
     */
    private void initDefaultData(Handler<AsyncResult<Void>> next, Future<Void> verticleFuture) {
        mongoClient.count(COLLECTION, new JsonObject(), countResult -> {
            if (countResult.failed()) {
                verticleFuture.fail(countResult.cause());
                return;
            }
            if (countResult.result() == 0L) {
                // add 2 whines
                insertOne(buildBowmore(), insertBowmoreResult -> {
                    if (insertBowmoreResult.failed()) verticleFuture.fail(insertBowmoreResult.cause());
                    else insertOne(buildTalisker(), insertTaliskerResult -> {
                        if (insertTaliskerResult.failed()) verticleFuture.fail(insertTaliskerResult.cause());
                        else next.handle(Future.succeededFuture());
                    });
                });
            } else next.handle(Future.succeededFuture());
        });
    }

    /**
     * Start http server and then calls the next step
     *
     * @param next next step in the chain handlers
     */
    private void startWeb(Handler<AsyncResult<HttpServer>> next) {
        Router router = Router.router(vertx);
        router.route("/").handler(this::welcomeRootHandler);
        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.get("/api/whiskies").handler(this::getAllHandler);
        router.route("/api/whiskies*").handler(BodyHandler.create()); //resource not found

        router.post("/api/whiskies").handler(this::addOneHandler);
        router.get("/api/whiskies/:id").handler(this::getOneHandler);
        router.put("/api/whiskies/:id").handler(this::updateOneHandler);
        router.delete("/api/whiskies/:id").handler(this::deleteOneHandler);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080), next);
    }

    /**
     * Http server has bound on the port and verticle starting has completed
     *
     * @param httpServer     instance of http server
     * @param verticleFuture main verticle future
     */
    private void completeStartup(AsyncResult<HttpServer> httpServer, Future<Void> verticleFuture) {
        if (httpServer.succeeded()) verticleFuture.complete();
        else verticleFuture.fail(httpServer.cause());
    }

    private Whisky buildTalisker() {
        return new Whisky("Talisker 57° North", "Scotland, Island");
    }

    private Whisky buildBowmore() {
        return new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
    }

    /**
     * curl -H "Content-Type: application/json" -X PUT -d '{"name":"Jameson","origin":"Ireland"}' localhost:8080/api/whiskies/1
     */
    private void updateOneHandler(RoutingContext context) {
        Integer id = Optional.of(context.request())
                .map(r -> r.getParam("id"))
                .filter(n -> digits.matcher(n).find())
                .map(Integer::valueOf)
                .orElse(null);
        JsonObject src = context.getBodyAsJson();
        if (isNull(id) || isNull(src)) context.response().setStatusCode(400).end();
        else updateOne(id, src, updateResult -> {
            if (updateResult.failed()) context.response()
                    .setStatusCode(404)
                    .setStatusMessage(updateResult.cause().getMessage())
                    .end();
            else context.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(updateResult.result()));
        });
    }

    /**
     * curl -X GET localhost:8080/api/whiskies/1
     */
    private void getOneHandler(RoutingContext context) {
        Integer id = Optional.of(context.request())
                .map(r -> r.getParam("id"))
                .filter(n -> digits.matcher(n).find())
                .map(Integer::valueOf)
                .orElse(null);
        if (isNull(id)) context.response().setStatusCode(400).end();
        else selectOne(id, selectResult -> {
            if (selectResult.failed()) context.response()
                    .setStatusCode(404)
                    .setStatusMessage(selectResult.cause().getMessage())
                    .end();
            else context.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(selectResult.result()));
        });
    }

    /**
     * curl -X DELETE localhost:8080/api/whiskies/2
     */
    private void deleteOneHandler(RoutingContext context) {
        Integer id = Optional.of(context.request())
                .map(r -> r.getParam("id"))
                .filter(n -> digits.matcher(n).find())
                .map(Integer::valueOf)
                .orElse(null);
        if (isNull(id)) context.response().setStatusCode(400).end();
        else delete(id, deleteResult -> context.response().setStatusCode(204).end());
    }

    /**
     * curl -H "Content-Type: application/json" -X POST -d '{"id":2,"name":"WhiskyName","origin":"WhiskyOrigin"}' localhost:8080/api/whiskies
     */
    private void addOneHandler(RoutingContext context) {
        Whisky whisky = Json.decodeValue(context.getBodyAsString(), Whisky.class);
        insertOne(whisky, insertResult -> {
            if (insertResult.failed()) context.response()
                    .setStatusCode(400)
                    .setStatusMessage(insertResult.cause().getMessage())
                    .end();
            else context.response()
                    .setStatusCode(201)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(insertResult.result()));
        });
    }

    /**
     * curl -X GET localhost:8080/api/whiskies
     */
    private void getAllHandler(RoutingContext context) {
        selectAll(selectResult -> {
            if (selectResult.failed()) context.response()
                    .setStatusCode(400)
                    .setStatusMessage(selectResult.cause().getMessage())
                    .end();
            context.response()
                    .setStatusCode(200)
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(selectResult.result()));
        });
    }

    /**
     * curl -X GET localhost:8080
     */
    private void welcomeRootHandler(RoutingContext context) {
        context.response()
                .putHeader("content-type", "text/html; charset=utf-8")
                .end("<h1>Hello from my first Vert.x 3 application!</h1>");
    }

    private void delete(Integer id, Handler<AsyncResult<Void>> next) {
        mongoClient.removeDocument(COLLECTION, new JsonObject().put("_id", id), removeResult -> {
            if (removeResult.failed()) next.handle(Future.failedFuture(removeResult.cause()));
            else next.handle(Future.succeededFuture());
        });
    }

    /**
     * First of all we need to generate a sequence number and then save the new document
     */
    private void insertOne(Whisky src, Handler<AsyncResult<Whisky>> next) {
        mongoClient.findOneAndUpdateWithOptions(COLLECTION_SEQ,
                new JsonObject(),
                new JsonObject().put("$inc", new JsonObject().put("number", 1)),
                new FindOptions(),
                new UpdateOptions().setUpsert(true),
                findResult -> {
                    if (findResult.failed()) next.handle(Future.failedFuture(findResult.cause()));
                    else {
                        Integer nextId = ofNullable(findResult.result()).map(j -> j.getInteger("number")).orElse(0);
                        mongoClient.insert(COLLECTION, src.toJson(true).put("_id", nextId), insertResult -> {
                            if (insertResult.failed()) next.handle(Future.failedFuture(insertResult.cause()));
                            else {
                                next.handle(Future.succeededFuture(new Whisky(nextId, src.getName(), src.getOrigin())));
                            }
                        });
                    }
                });
    }

    private void updateOne(Integer id, JsonObject src, Handler<AsyncResult<Whisky>> next) {
        mongoClient.findOneAndUpdateWithOptions(COLLECTION,
                new JsonObject().put("_id", id),
                new JsonObject().put("$set", src.put("NAME", src.getString("name")).put("ORIGIN", src.getString("origin"))),
                new FindOptions(),
                new UpdateOptions().setReturningNewDocument(true),
                updateResult -> {
                    if (updateResult.failed()) {
                        next.handle(Future.failedFuture(updateResult.cause()));
                    } else if (updateResult.result() == null) {
                        next.handle(Future.failedFuture("not found whisky: " + id));
                    } else {
                        next.handle(Future.succeededFuture(Whisky.fromJson(updateResult.result())));
                    }
                });
    }

    private void selectOne(Integer id, Handler<AsyncResult<Whisky>> next) {
        mongoClient.findOne(COLLECTION, new JsonObject().put("_id", id), null, findResult -> {
            if (findResult.failed()) {
                next.handle(Future.failedFuture(findResult.cause()));
            } else {
                if (findResult.result() == null) {
                    next.handle(Future.failedFuture("not found whisky with id: " + id));
                } else {
                    next.handle(Future.succeededFuture(Whisky.fromJson(findResult.result())));
                }
            }
        });
    }

    private void selectAll(Handler<AsyncResult<List<Whisky>>> next) {
        mongoClient.find(COLLECTION, new JsonObject(), findResult -> {
            if (findResult.failed()) {
                next.handle(Future.failedFuture(findResult.cause()));
            } else if (findResult.result().isEmpty()) {
                next.handle(Future.failedFuture("whiskies not found"));
            } else {
                next.handle(Future.succeededFuture(findResult.result().stream().map(Whisky::fromJson).collect(toList())));
            }
        });
    }
}
