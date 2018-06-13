package ru.shishmakov;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;
import io.vertx.ext.sql.UpdateResult;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.shishmakov.blog.Whisky;

import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

/**
 * Use <b>vertx-web</b> part of Vert.x
 */
public class WebSqlVerticle extends AbstractVerticle {
    private static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS whisky (id INTEGER IDENTITY, name varchar(100), origin varchar(100))";
    private static final String SELECT_ALL = "SELECT * FROM whisky";
    private static final String SELECT_BY_ID = "SELECT * FROM whisky WHERE id=?";
    private static final String INSERT_ONE = "INSERT INTO whisky (name, origin) VALUES (?, ?)";
    private static final String UPDATE_NAME_AND_ORIGIN_AND_ID = "UPDATE whisky SET name=?, origin=? WHERE id=?";
    private static final String DELETE_BY_ID = "DELETE FROM whisky WHERE id=?";
    private static final String DATABASE = "whisky_store";
    private static final Pattern digits = Pattern.compile("^[0-9]+$");

    private JDBCClient jdbc;

    @Override
    public void start(Future<Void> verticleFuture) {
        System.setProperty("hsqldb.reconfig_logging", "false");

        this.jdbc = JDBCClient.createShared(vertx, ((UnaryOperator<JsonObject>) conf -> {
            conf.getMap().putIfAbsent("url", "jdbc:hsqldb:file:db/" + DATABASE);
            conf.getMap().putIfAbsent("driver_class", "org.hsqldb.jdbcDriver");
            conf.getMap().putIfAbsent("max_pool_size", 10);
            return conf;
        }).apply(config()), "ds-whisky");

        getConnection()
                .compose(this::initDefaultData)
                .compose(v -> startWeb())
                .setHandler(verticleFuture.completer());
    }

    @Override
    public void stop() {
        jdbc.close();
        logger.info("server has stopped");
    }

    /**
     * Retrieves an async SQLConnection
     */
    private Future<SQLConnection> getConnection() {
        Future<SQLConnection> future = Future.future();
        jdbc.getConnection(connection -> {
            if (connection.failed()) future.fail(connection.cause());
            else future.complete(connection.result());
        });
        return future;
    }

    /**
     * Initializes the database with default values
     *
     * @param sqlCon SQLConnection instance
     */
    private Future<Void> initDefaultData(SQLConnection sqlCon) {
        Future<Void> future = Future.future();
        sqlCon.execute(CREATE_TABLE, createResult -> {
            if (createResult.failed()) {
                future.fail(createResult.cause());
                sqlCon.close();
                return;
            }
            sqlCon.query(SELECT_ALL, selectResult -> {
                if (selectResult.failed()) {
                    future.fail(selectResult.cause());
                    sqlCon.close();
                    return;
                }
                if (selectResult.result().getNumRows() == 0) {
                    // add 2 whines
                    insertOne(buildBowmore(), sqlCon, insertBowmoreResult -> {
                        if (insertBowmoreResult.failed()) {
                            future.fail(insertBowmoreResult.cause());
                            sqlCon.close();
                        } else insertOne(buildTalisker(), sqlCon, insertTaliskerResult -> {
                            future.complete();
                            sqlCon.close();
                            logger.info("init default items for whisky store");
                        });
                    });
                } else {
                    future.complete();
                    sqlCon.close();
                }
            });
        });
        return future;
    }

    /**
     * Start http server
     */
    private Future<Void> startWeb() {
        Future<Void> future = Future.future();
        Router router = Router.router(vertx);
        router.route("/").handler(this::welcomeRootHandler);
        router.route("/assets/*").handler(StaticHandler.create("assets"));

        router.get("/api/whiskies").handler(this::getAllHandler);
        router.route("/api/whiskies*").handler(BodyHandler.create());

        router.post("/api/whiskies").handler(this::addOneHandler);
        router.get("/api/whiskies/:id").handler(this::getOneHandler);
        router.put("/api/whiskies/:id").handler(this::updateOneHandler);
        router.delete("/api/whiskies/:id").handler(this::deleteOneHandler);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", 8080), serverResult -> {
                    if (serverResult.failed()) {
                        future.fail(serverResult.cause());
                        logger.info("server has failed on start");
                    } else {
                        future.complete();
                        logger.info("server has started successfully");
                    }
                });
        return future;
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
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            updateOne(id, src, sqlCon, updateResult -> {
                if (updateResult.failed()) context.response()
                        .setStatusCode(404)
                        .setStatusMessage(updateResult.cause().getMessage())
                        .end();
                else context.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(updateResult.result()));
                sqlCon.close();
            });
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
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            selectOne(id, sqlCon, selectResult -> {
                if (selectResult.failed()) context.response()
                        .setStatusCode(404)
                        .setStatusMessage(selectResult.cause().getMessage())
                        .end();
                else context.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(selectResult.result()));
                sqlCon.close();
            });
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
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            sqlCon.updateWithParams(DELETE_BY_ID, new JsonArray().add(id), deleteResult -> {
                context.response().setStatusCode(204).end();
                sqlCon.close();
            });
        });
    }

    /**
     * curl -H "Content-Type: application/json" -X POST -d '{"id":2,"name":"WhiskyName","origin":"WhiskyOrigin"}' localhost:8080/api/whiskies
     */
    private void addOneHandler(RoutingContext context) {
        Whisky whisky = Json.decodeValue(context.getBodyAsString(), Whisky.class);
        jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            insertOne(whisky, sqlCon, insertResult -> {
                if (insertResult.failed()) context.response()
                        .setStatusCode(400)
                        .setStatusMessage(insertResult.cause().getMessage())
                        .end();
                else context.response()
                        .setStatusCode(201)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(insertResult.result()));
                sqlCon.close();
            });
        });
    }

    /**
     * curl -X GET localhost:8080/api/whiskies
     */
    private void getAllHandler(RoutingContext context) {
        jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            selectAll(sqlCon, selectResult -> {
                if (selectResult.failed()) context.response()
                        .setStatusCode(400)
                        .setStatusMessage(selectResult.cause().getMessage())
                        .end();
                else context.response()
                        .setStatusCode(200)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(selectResult.result()));
                sqlCon.close();
            });
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

    private void insertOne(Whisky src, SQLConnection sqlCon, Handler<AsyncResult<Whisky>> next) {
        sqlCon.updateWithParams(INSERT_ONE, new JsonArray().add(src.getName()).add(src.getOrigin()), updateResult -> {
            if (updateResult.failed()) {
                next.handle(Future.failedFuture(updateResult.cause()));
                return;
            }
            UpdateResult result = updateResult.result();
            next.handle(Future.succeededFuture(new Whisky(result.getKeys().getInteger(0), src.getName(), src.getOrigin())));
        });
    }

    private void updateOne(Integer id, JsonObject src, SQLConnection sqlCon, Handler<AsyncResult<Whisky>> next) {
        sqlCon.updateWithParams(UPDATE_NAME_AND_ORIGIN_AND_ID,
                new JsonArray().add(src.getString("name")).add(src.getString("origin")).add(id),
                updateResult -> {
                    if (updateResult.failed()) {
                        next.handle(Future.failedFuture(updateResult.cause()));
                        return;
                    }
                    if (updateResult.result().getUpdated() == 0) {
                        next.handle(Future.failedFuture("not found whisky: " + id));
                    } else {
                        next.handle(Future.succeededFuture(new Whisky(id, src.getString("name"), src.getString("origin"))));
                    }
                });
    }

    private void selectOne(Integer id, SQLConnection sqlCon, Handler<AsyncResult<Whisky>> next) {
        sqlCon.queryWithParams(SELECT_BY_ID, new JsonArray().add(id), selectResult -> {
            if (selectResult.failed()) {
                next.handle(Future.failedFuture(selectResult.cause()));
                return;
            }
            if (selectResult.result().getNumRows() == 0) {
                next.handle(Future.failedFuture("not found whisky with id: " + id));
            } else if (selectResult.result().getNumRows() == 1) {
                next.handle(Future.succeededFuture(Whisky.fromJson(selectResult.result().getRows().get(0))));
            } else {
                next.handle(Future.failedFuture("several whiskies with id: " + id));
            }
        });

    }

    private void selectAll(SQLConnection sqlCon, Handler<AsyncResult<List<Whisky>>> next) {
        sqlCon.query(SELECT_ALL, selectResult -> {
            if (selectResult.failed()) {
                next.handle(Future.failedFuture(selectResult.cause()));
                return;
            }
            if (selectResult.result().getNumRows() == 0) {
                next.handle(Future.failedFuture("whiskies not found"));
            } else {
                next.handle(Future.succeededFuture(selectResult.result().getRows().stream().map(Whisky::fromJson).collect(toList())));
            }
        });
    }
}
