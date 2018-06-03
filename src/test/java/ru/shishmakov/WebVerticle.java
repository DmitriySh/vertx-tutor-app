package ru.shishmakov;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import org.junit.Test;
import ru.shishmakov.blog.Whisky;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

public abstract class WebVerticle {

    protected abstract Vertx getVertx();

    protected abstract int getPort();

    @Test
    public void getWelcomePageShouldGetResponse(TestContext context) {
        Async async = context.async();
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/", response -> {
            context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
            response.handler(body -> {
                context.assertNotNull(body.toString(), "body is empty");
                context.assertTrue(body.toString().contains("Hello"), "not welcome page");
                async.complete();
            });
        });
    }

    @Test
    public void getAssetsIndexShouldBeAvailable(TestContext context) {
        Async async = context.async();
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/assets/index.html", response -> {
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
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/unavailablepage.html", response -> {
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
        getVertx().createHttpClient().post(getPort(), "localhost", "/api/whiskies/")
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
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/api/whiskies/", response -> {
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
    public void getApiShouldReturnWhisky(TestContext context) {
        final int id = 1;
        Async async = context.async();
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/api/whiskies/" + id, response -> {
            context.assertEquals(200, response.statusCode(), "status code isn't 'ok'");
            response.bodyHandler(body -> {
                context.assertNotNull(body.toString(), "body is empty");
                context.assertEquals(id, Json.decodeValue(body, Whisky.class).getId(), "whisky id is incorrect");
                async.complete();
            });
        });
    }

    @Test
    public void getApiShouldNotReturnWhiskyIfIdNotExists(TestContext context) {
        final int id = 50;
        Async async = context.async();
        getVertx().createHttpClient().getNow(getPort(), "localhost", "/api/whiskies/" + id, response -> {
            context.assertEquals(404, response.statusCode(), "status code isn't 'not found'");
            context.assertTrue(response.statusMessage().contains("not found whisky with id"), "message is incorrect");
            response.bodyHandler(body -> {
                context.assertEquals(0, body.length(), "body isn't empty");
                async.complete();
            });
        });
    }

    @Test
    public void putApiShouldChangeWhisky(TestContext context) {
        final int id = 1;
        String src = Json.encodePrettily(new Whisky("The new Whisky", "The new Origin"));
        Async async = context.async();
        getVertx().createHttpClient().put(getPort(), "localhost", "/api/whiskies/" + id)
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
        final int id = 50;
        String src = Json.encodePrettily(new Whisky("The new Whisky", "The new Origin"));
        Async async = context.async();
        getVertx().createHttpClient().put(getPort(), "localhost", "/api/whiskies/" + id)
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

    @Test
    public void deleteApiShouldRemoveWhisky(TestContext context) {
        final int id = 1;
        Async async = context.async();
        HttpClient client = getVertx().createHttpClient();
        client.getNow(getPort(), "localhost", "/api/whiskies/" + id, getResponse -> {
            context.assertEquals(200, getResponse.statusCode(), "status code isn't 'ok'");

            client.delete(getPort(), "localhost", "/api/whiskies/" + id, deleteResponse -> {
                context.assertEquals(204, deleteResponse.statusCode(), "status code isn't 'no content'");
                deleteResponse.bodyHandler(body -> {
                    context.assertEquals(0, body.length(), "body isn't empty");

                    client.getNow(getPort(), "localhost", "/api/whiskies/" + id, getResponse2 -> {
                        context.assertEquals(404, getResponse2.statusCode(), "status code isn't 'not found'");
                        async.complete();
                    });
                });
            }).end();
        });
    }

    protected static int getFreeLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
