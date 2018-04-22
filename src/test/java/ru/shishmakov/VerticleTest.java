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
 * Unit test for simple App.
 */
@RunWith(VertxUnitRunner.class)
public class VerticleTest {

    private int port;
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) throws IOException {
        port = builLocalPort();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject().put("http.port", port));
        vertx = Vertx.vertx();
        vertx.deployVerticle(WebVerticle.class.getName(), options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown() {
        vertx.close();
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

    private int builLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
