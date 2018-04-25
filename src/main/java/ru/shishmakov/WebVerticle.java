package ru.shishmakov;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServer;
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
import ru.shishmakov.blog.Whisky;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toList;

/**
 * Use <b>vertx-web</b> part of Vert.x
 */
public class WebVerticle extends AbstractVerticle {

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS whisky (id INTEGER IDENTITY, name varchar(100), origin varchar(100))";
    public static final String SELECT_ALL = "SELECT * FROM whisky";
    public static final String SELECT_ONE = "SELECT * FROM whisky WHERE id=?";
    public static final String INSERT_ONE = "INSERT INTO whisky (name, origin) VALUES (?, ?)";
    public static final String UPDATE_ONE = "UPDATE whisky SET name=?, origin=? WHERE id=?";
    public static final String DELETE_ONE = "DELETE FROM whisky WHERE id=?";

    private JDBCClient jdbc;

    @Override
    public void start(Future<Void> verticleFuture) {
        this.jdbc = JDBCClient.createShared(vertx, config(), "ds-whisky");
        startBackend(con -> initDefaultData(
                con,
                initialized -> startWeb(httpServer -> completeStartup(httpServer, verticleFuture)),
                verticleFuture
        ), verticleFuture);
    }

    @Override
    public void stop() {
        jdbc.close();
    }

    /**
     * Retrieves a SQLConnection and then calls the next step
     *
     * @param next           next step in the chain handlers
     * @param verticleFuture main verticle future
     */
    private void startBackend(Handler<AsyncResult<SQLConnection>> next, Future<Void> verticleFuture) {
        jdbc.getConnection(connection -> {
            if (connection.failed()) verticleFuture.fail(connection.cause());
            else next.handle(Future.succeededFuture(connection.result()));
        });
    }

    /**
     * Initializes the database with default values and then calls the next step
     *
     * @param connResult     SQLConnection instance
     * @param next           next step in the chain handlers
     * @param verticleFuture main verticle future
     */
    private void initDefaultData(AsyncResult<SQLConnection> connResult, Handler<AsyncResult<Void>> next, Future<Void> verticleFuture) {
        if (connResult.failed()) verticleFuture.fail(connResult.cause());
        else {
            SQLConnection sqlCon = connResult.result();
            sqlCon.execute(CREATE_TABLE, createResult -> {
                if (createResult.failed()) {
                    verticleFuture.fail(createResult.cause());
                    sqlCon.close();
                    return;
                }
                sqlCon.query(SELECT_ALL, selectResult -> {
                    if (selectResult.failed()) {
                        verticleFuture.fail(selectResult.cause());
                        sqlCon.close();
                        return;
                    }
                    if (selectResult.result().getNumRows() == 0) {
                        // add 2 whines
                        insertOne(buildBowmore(), sqlCon, insertResult1 ->
                                insertOne(buildTalisker(), sqlCon, insertResult2 -> {
                                    next.handle(Future.succeededFuture());
                                    sqlCon.close();
                                }));
                    } else {
                        next.handle(Future.succeededFuture());
                        sqlCon.close();
                    }
                });
            });
        }
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
        router.route("/api/whiskies*").handler(BodyHandler.create()); //resource not found
        router.get("/api/whiskies").handler(this::getAllHandler);
        router.get("/api/whiskies/:id").handler(this::getOneHandler);
        router.post("/api/whiskies").handler(this::addOneHandler);
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
        Integer id = Optional.of(context.request()).map(r -> r.getParam("id")).map(Integer::valueOf).orElse(null);
        JsonObject src = context.getBodyAsJson();
        if (isNull(id) || isNull(src)) context.response().setStatusCode(400).end();
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            updateOne(id, src, sqlCon, updateResult -> {
                if (updateResult.failed()) context.response().setStatusCode(404).end();
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
        Integer id = Optional.of(context.request()).map(r -> r.getParam("id")).map(Integer::valueOf).orElse(null);
        if (isNull(id)) context.response().setStatusCode(400).end();
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            selectOne(id, sqlCon, selectResult -> {
                if (selectResult.failed()) context.response().setStatusCode(404).end();
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
        Integer id = Optional.of(context.request()).map(r -> r.getParam("id")).map(Integer::valueOf).orElse(null);
        if (isNull(id)) context.response().setStatusCode(400).end();
        else jdbc.getConnection(conResult -> {
            SQLConnection sqlCon = conResult.result();
            sqlCon.updateWithParams(DELETE_ONE, new JsonArray().add(id), deleteResult -> {
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
                if (insertResult.failed()) context.response().setStatusCode(400).end();
                else context.response()
                        .setStatusCode(201)
                        .putHeader("content-type", "application/json; charset=utf-8")
                        .end(Json.encodePrettily(whisky));
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
                context.response()
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
        sqlCon.updateWithParams(UPDATE_ONE, new JsonArray().add(src.getString("name")).add(src.getString("origin")).add(id),
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
        sqlCon.queryWithParams(SELECT_ONE, new JsonArray().add(id), selectResult -> {
            if (selectResult.failed()) {
                next.handle(Future.failedFuture(selectResult.cause()));
                return;
            }
            if (selectResult.result().getNumRows() == 0) {
                next.handle(Future.failedFuture("not found whisky with id: " + id));
            } else if (selectResult.result().getNumRows() == 1) {
                next.handle(Future.succeededFuture(new Whisky(selectResult.result().getRows().get(0))));
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
                next.handle(Future.succeededFuture(selectResult.result().getRows().stream().map(Whisky::new).collect(toList())));
            }
        });
    }
}
