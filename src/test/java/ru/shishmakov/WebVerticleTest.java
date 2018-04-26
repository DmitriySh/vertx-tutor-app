package ru.shishmakov;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import ru.shishmakov.blog.Whisky;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * Unit tests for vert.x web app
 */
@RunWith(VertxUnitRunner.class)
public class WebVerticleTest {

    private int port;
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) throws IOException {
        port = buildLocalPort();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject()
                .put("http.port", port)
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver"));
        vertx = Vertx.vertx();
        vertx.deployVerticle(WebVerticle.class.getName(), options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void checkGetAssetsIndexShouldBeAvailable(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/assets/index.html", rh -> {
            context.assertEquals(200, rh.statusCode(), "status code is not equal");
            context.assertEquals("text/html;charset=UTF-8", rh.headers().get("content-type"), "content-type is not equal");
            rh.bodyHandler(body -> {
                context.assertTrue(body.toString().contains("<title>My Whisky Collection</title>"), "body has not correct answer");
                async.complete();
            });
        });
    }

    @Test
    public void checkPostShouldAddOneWhisky(TestContext context) {
        Async async = context.async();
        String src = Json.encodePrettily(new Whisky("Jameson", "Ireland"));
        vertx.createHttpClient().post(port, "localhost", "/api/whiskies/")
                .putHeader("content-type", "application/json")
                .putHeader("content-length", String.valueOf(src.length()))
                .handler(h -> {
                    context.assertEquals(201, h.statusCode(), "status code is not equal");
                    context.assertEquals("application/json; charset=utf-8", h.headers().get("content-type"), "content-type is not equal");
                    h.bodyHandler(body -> {
                        Whisky whisky = Json.decodeValue(body, Whisky.class);
                        context.assertEquals("Jameson", whisky.getName(), "whisky name is not equal");
                        context.assertEquals("Ireland", whisky.getOrigin(), "whisky origin is not equal");
                        context.assertNotNull(whisky.getId(), "whisky is not defined");
                        async.complete();
                    });
                })
                .write(src)
                .end();
    }

    private int buildLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
