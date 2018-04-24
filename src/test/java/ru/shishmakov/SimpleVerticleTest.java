package ru.shishmakov;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.net.ServerSocket;

import static java.util.Objects.nonNull;

/**
 * Unit test for vert.x core app
 */
@RunWith(VertxUnitRunner.class)
public class SimpleVerticleTest {

    private int port;
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) throws IOException {
        port = buildLocalPort();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
        vertx = Vertx.vertx();
        vertx.deployVerticle(SimpleVerticle.class.getName(), options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void verticleShouldResponseSuccess(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient()
                .getNow(port, "localhost", "/", response -> response.handler(body -> {
                    String text = body.toString();
                    context.assertTrue(nonNull(text), "body is empty");
                    context.assertTrue(text.contains("Hello"), "not success answer");
                    async.complete();
                }));
    }

    private int buildLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
