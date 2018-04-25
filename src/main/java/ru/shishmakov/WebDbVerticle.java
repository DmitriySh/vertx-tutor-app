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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static java.util.Objects.isNull;

/**
 * Use <b>vertx-web</b> part of Vert.x
 */
public class WebDbVerticle extends AbstractVerticle {

    public static final String CREATE_TABLE = "CREATE TABLE IF NOT EXISTS whisky (id INTEGER IDENTITY, name varchar(100), origin varchar(100))";
    public static final String SELECT_ALL = "SELECT * FROM whisky";
    public static final String INSERT_ONE = "INSERT INTO whisky (name, origin) VALUES (?, ?)";

    private final Map<Integer, Whisky> products;
    private final JDBCClient jdbc;

    public WebDbVerticle() {
        this.products = buildDefaultProducts();
        this.jdbc = JDBCClient.createShared(vertx, config(), "ds-whisky");
    }

    @Override
    public void start(Future<Void> verticleFuture) {
        startBackend(connection -> initDefaultData(
                connection,
                initialized -> startWebApp(httpServer -> completeStartup(httpServer, verticleFuture)),
                verticleFuture
        ), verticleFuture);
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
            SQLConnection connection = connResult.result();
            connection.execute(CREATE_TABLE, createResult -> {
                if (createResult.failed()) {
                    verticleFuture.fail(createResult.cause());
                    connection.close();
                    return;
                }
                connection.query(SELECT_ALL, selectResult -> {
                    if (selectResult.failed()) {
                        verticleFuture.fail(selectResult.cause());
                        connection.close();
                        return;
                    }
                    if (selectResult.result().getNumRows() == 0) {
                        // add 2 whines
                        insert(buildBowmore(), connection, insertResult1 ->
                                insert(buildTalisker(), connection, insertResult2 -> {
                                    next.handle(Future.succeededFuture());
                                    connection.close();
                                }));
                    } else {
                        next.handle(Future.succeededFuture());
                        connection.close();
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
    private void startWebApp(Handler<AsyncResult<HttpServer>> next) {
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
     * Last step in start sequence: complete or fail verticle future
     *
     * @param httpServer     instance of http server
     * @param verticleFuture main verticle future
     */
    private void completeStartup(AsyncResult<HttpServer> httpServer, Future<Void> verticleFuture) {
        if (httpServer.succeeded()) verticleFuture.complete();
        else verticleFuture.fail(httpServer.cause());
    }

    private void insert(Whisky whisky, SQLConnection connection, Handler<AsyncResult<Whisky>> next) {
        connection.updateWithParams(INSERT_ONE,
                new JsonArray().add(whisky.getName()).add(whisky.getOrigin()),
                updateResult -> {
                    if (updateResult.failed()) {
                        next.handle(Future.failedFuture(updateResult.cause()));
                        connection.close();
                        return;
                    }
                    UpdateResult result = updateResult.result();
                    // Build a new whisky instance with the generated id.
                    Whisky w = new Whisky(result.getKeys().getInteger(0), whisky.getName(), whisky.getOrigin());
                    next.handle(Future.succeededFuture(w));
                });
    }

    private Map<Integer, Whisky> buildDefaultProducts() {
        Whisky bowmore = buildBowmore();
        Whisky talisker = buildTalisker();
        Map<Integer, Whisky> products = new LinkedHashMap<>();
        products.put(bowmore.getId(), bowmore);
        products.put(talisker.getId(), talisker);
        return products;
    }

    private Whisky buildTalisker() {
        return new Whisky("Talisker 57° North", "Scotland, Island");
    }

    private Whisky buildBowmore() {
        return new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
    }

    /**
     * curl -H "Content-Type: application/json" -X PUT -d '{"name":"Jameson","origin":"Ireland"}' http://localhost:8080/api/whiskies/1
     */
    private void updateOneHandler(RoutingContext context) {
        Integer id = Optional.of(context.request()).map(r -> r.getParam("id")).map(Integer::valueOf).orElse(null);
        JsonObject json = context.getBodyAsJson();
        if (isNull(id) || isNull(json)) {
            context.response().setStatusCode(400).end();
        } else if (!products.containsKey(id)) {
            context.response().setStatusCode(404).end();
        } else {
            Whisky whisky = products.get(id);
            whisky.setName(json.getString("name"));
            whisky.setOrigin(json.getString("origin"));
            context.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(whisky));
        }
    }

    /**
     * curl -X GET http://localhost:8080/api/whiskies/1
     */
    private void getOneHandler(RoutingContext context) {
        Integer id = Optional.of(context.request()).map(r -> r.getParam("id")).map(Integer::valueOf).orElse(null);
        if (isNull(id)) {
            context.response().setStatusCode(400).end();
        } else if (!products.containsKey(id)) {
            context.response().setStatusCode(404).end();
        } else {
            context.response()
                    .putHeader("content-type", "application/json; charset=utf-8")
                    .end(Json.encodePrettily(products.get(id)));
        }
    }

    /**
     * curl -X DELETE http://localhost:8080/api/whiskies/2
     */
    private void deleteOneHandler(RoutingContext context) {
        String id = context.request().getParam("id");
        if (isNull(id)) context.response().setStatusCode(400).end();
        else products.remove(Integer.valueOf(id));
        context.response().setStatusCode(204).end();
    }

    /**
     * curl -H "Content-Type: application/json" -X POST -d '{"id":2,"name":"WhiskyName","origin":"WhiskyOrigin"}' http://localhost:8080/api/whiskies
     */
    private void addOneHandler(RoutingContext context) {
        Whisky whisky = Json.decodeValue(context.getBodyAsString(), Whisky.class);
        products.put(whisky.getId(), whisky);
        context.response()
                .setStatusCode(201)
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(whisky));
    }

    /**
     * curl -X GET http://localhost:8080/api/whiskies
     */
    private void getAllHandler(RoutingContext context) {
        context.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(products.values()));
    }

    private void welcomeRootHandler(RoutingContext context) {
        context.response()
                .putHeader("content-type", "text/html; charset=utf-8")
                .end("<h1>Hello from my first Vert.x 3 application!</h1>");
    }
}
