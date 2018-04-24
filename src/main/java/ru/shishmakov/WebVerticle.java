package ru.shishmakov;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
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
public class WebVerticle extends AbstractVerticle {

    private static final int DEFAULT_PORT = 8080;
    private Map<Integer, Whisky> products;

    @Override
    public void start(Future<Void> startFuture) {
        products = buildDefaultProducts();
        Router router = buildRouter();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", DEFAULT_PORT), result -> {
                    if (result.succeeded()) startFuture.complete();
                    else startFuture.fail(result.cause());
                });
    }

    private Router buildRouter() {
        Router router = Router.router(vertx);
        router.route("/").handler(this::welcomeRootHandler);
        router.route("/assets/*").handler(StaticHandler.create("assets"));
        router.route("/api/whiskies*").handler(BodyHandler.create()); //resource not found
        router.get("/api/whiskies").handler(this::getAllHandler);
        router.get("/api/whiskies/:id").handler(this::getOneHandler);
        router.post("/api/whiskies").handler(this::addOneHandler);
        router.put("/api/whiskies/:id").handler(this::updateOneHandler);
        router.delete("/api/whiskies/:id").handler(this::deleteOneHandler);
        return router;
    }

    private Map<Integer, Whisky> buildDefaultProducts() {
        Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
        Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
        Map<Integer, Whisky> products = new LinkedHashMap<>();
        products.put(bowmore.getId(), bowmore);
        products.put(talisker.getId(), talisker);
        return products;
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
                .putHeader("content-type", "text/html")
                .end("<h1>Hello from my first Vert.x 3 application!</h1>");
    }
}
