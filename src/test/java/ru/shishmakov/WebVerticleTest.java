package ru.shishmakov;

import com.fasterxml.jackson.core.type.TypeReference;
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
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

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
                .put("url", "jdbc:hsqldb:mem:test;shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver"));
        vertx = Vertx.vertx();
        vertx.deployVerticle(WebSqlVerticle.class, options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void getAssetsIndexShouldBeAvailable(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/assets/index.html", response -> {
            context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
            context.assertEquals("text/html;charset=UTF-8", response.headers().get("content-type"), "content-type isn't equal");
            response.bodyHandler(body -> {
                context.assertTrue(body.toString().contains("<title>My Whisky Collection</title>"), "body has not correct answer");
                async.complete();
            });
        });
    }

    @Test
    public void getUnavailablePageShouldGetResponse(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/unavailablepage.html", response -> {
            context.assertEquals(404, response.statusCode(), "status code isn't 'bad request'");
            response.bodyHandler(body -> {
                String text = body.toString();
                context.assertNotNull(text, "body is empty");
                context.assertTrue(text.contains("Resource not found"), "not 404 page");
                async.complete();
            });
        });
    }

    @Test
    public void postApiShouldAddOneWhisky(TestContext context) {
        String src = Json.encodePrettily(new Whisky("Jameson", "Ireland"));
        Async async = context.async();
        vertx.createHttpClient().post(port, "localhost", "/api/whiskies/")
                .putHeader("content-type", "application/json")
                .putHeader("content-length", String.valueOf(src.length()))
                .handler(response -> {
                    context.assertEquals(201, response.statusCode(), "status code isn't 'created'");
                    context.assertEquals("application/json; charset=utf-8", response.headers().get("content-type"), "content-type isn't equal");
                    response.bodyHandler(body -> {
                        Whisky whisky = Json.decodeValue(body, Whisky.class);
                        context.assertEquals(2, whisky.getId(), "whisky id doesn't next in consequence");
                        context.assertEquals("Jameson", whisky.getName(), "whisky name isn't equal");
                        context.assertEquals("Ireland", whisky.getOrigin(), "whisky origin isn't equal");
                        async.complete();
                    });
                })
                .write(src)
                .end();
    }

    @Test
    public void getApiShouldReturnAllWhiskies(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/api/whiskies/", response -> {
            context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
            response.bodyHandler(body -> {
                context.assertNotNull(body.toString(), "body is empty");
                context.assertEquals(asList(0, 1), Json.decodeValue(body, new TypeReference<List<Whisky>>() {
                }).stream().map(Whisky::getId).collect(toList()), "whiskies ids aren't default values");
                async.complete();
            });
        });
    }

    @Test
    public void getApiShouldReturnFirstWhiskyIfIdExists(TestContext context) {
        final int id = 1;
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/api/whiskies/" + id, response -> {
            context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
            response.bodyHandler(body -> {
                context.assertNotNull(body.toString(), "body is empty");
                context.assertEquals(id, Json.decodeValue(body, Whisky.class).getId(), "whisky id is incorrect");
                async.complete();
            });
        });
    }

    @Test
    public void getApiShouldNotReturnFirstWhiskyIfIdNotExists(TestContext context) {
        final int id = 2;
        Async async = context.async();
        vertx.createHttpClient().getNow(port, "localhost", "/api/whiskies/" + id, response -> {
            context.assertEquals(404, response.statusCode(), "status code isn't 'not found'");
            context.assertTrue(response.statusMessage().contains("not found whisky with id"), "message is incorrect");
            response.bodyHandler(body -> {
                context.assertEquals(0, body.length(), "body isn't empty");
                async.complete();
            });
        });
    }

    @Test
    public void putApiShouldChangeWhiskyIfIdExists(TestContext context) {
        final int id = 1;
        String src = Json.encodePrettily(new Whisky("The new Whisky", "The new Origin"));
        Async async = context.async();
        vertx.createHttpClient().put(port, "localhost", "/api/whiskies/" + id)
                .putHeader("content-type", "application/json")
                .putHeader("content-length", String.valueOf(src.length()))
                .handler(response -> {
                    context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
                    response.bodyHandler(body -> {
                        Whisky whisky = Json.decodeValue(body.toString(), Whisky.class);
                        context.assertEquals(id, whisky.getId(), "whisky id isn't equal");
                        context.assertEquals("The new Whisky", whisky.getName(), "whisky name isn't equal");
                        context.assertEquals("The new Origin", whisky.getOrigin(), "whisky origin isn't equal");
                        async.complete();
                    });
                })
                .write(src)
                .end();
    }

    @Test
    public void putApiShouldFailWhiskyChangesIfIdNotExists(TestContext context) {
        final int id = 5;
        String src = Json.encodePrettily(new Whisky("The new Whisky", "The new Origin"));
        Async async = context.async();
        vertx.createHttpClient().put(port, "localhost", "/api/whiskies/" + id)
                .putHeader("content-type", "application/json")
                .putHeader("content-length", String.valueOf(src.length()))
                .handler(response -> {
                    context.assertEquals(404, response.statusCode(), "status code isn't 'not found'");
                    response.bodyHandler(body -> {
                        context.assertEquals(0, body.length(), "body isn't empty");
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
