package ru.shishmakov;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.StaticHandler;
import ru.shishmakov.blog.Whisky;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Use <b>vertx-web</b> part of Vert.x
 */
public class WebVerticle extends AbstractVerticle {

    private static final int DEFAULT_PORT = 8080;
    private Map<Integer, Whisky> products;

    @Override
    public void start(Future<Void> startFuture) {
        products = buildProducts();
        Router router = Router.router(vertx);
        router.route("/").handler(this::rootHandler);
        router.route("/assets/*").handler(StaticHandler.create("assets"));
        router.route("/api/whiskies").handler(this::whiskiesHandler);
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", DEFAULT_PORT), result -> {
                    if (result.succeeded()) startFuture.complete();
                    else startFuture.fail(result.cause());
                });
    }

    private Map<Integer, Whisky> buildProducts() {
        Whisky bowmore = new Whisky("Bowmore 15 Years Laimrig", "Scotland, Islay");
        Whisky talisker = new Whisky("Talisker 57° North", "Scotland, Island");
        Map<Integer, Whisky> products = new LinkedHashMap<>();
        products.put(bowmore.getId(), bowmore);
        products.put(talisker.getId(), talisker);
        return products;
    }

    private void whiskiesHandler(RoutingContext context) {
        context.response()
                .putHeader("content-type", "application/json; charset=utf-8")
                .end(Json.encodePrettily(products.values()));
    }

    private void rootHandler(RoutingContext context) {
        context.response()
                .putHeader("content-type", "text/html")
                .end("<h1>Hello from my first Vert.x 3 application!</h1>");
    }
}
