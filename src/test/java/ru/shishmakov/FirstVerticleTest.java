package ru.shishmakov;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static java.util.Objects.nonNull;

/**
 * Unit test for simple App.
 */
@RunWith(VertxUnitRunner.class)
public class FirstVerticleTest {

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();
        vertx.deployVerticle(FirstVerticle.class.getName(), context.asyncAssertSuccess());
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void verticleShouldResponseSuccess(TestContext context) {
        Async async = context.async();
        vertx.createHttpClient()
                .getNow(8080, "localhost", "/", response -> response.handler(body -> {
                    String text = body.toString();
                    context.assertTrue(nonNull(text), "body is empty");
                    context.assertTrue(text.contains("Hello"), "not success answer");
                    async.complete();
                }));
    }
}
