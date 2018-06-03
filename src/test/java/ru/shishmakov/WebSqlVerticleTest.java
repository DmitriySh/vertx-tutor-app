package ru.shishmakov;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Unit tests for vert.x web app
 */
@RunWith(VertxUnitRunner.class)
public class WebSqlVerticleTest extends WebVerticle {

    private int port;
    private Vertx vertx;

    @Before
    public void setUp(TestContext context) throws IOException {
        port = buildLocalPort();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject()
                .put("http.port", port)
                .put("url", "jdbc:hsqldb:mem:whisky_store;shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver"));
        vertx = Vertx.vertx();
        vertx.deployVerticle(WebSqlVerticle.class, options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Override
    protected Vertx getVertx() {
        return vertx;
    }

    @Override
    protected int getPort() {
        return port;
    }
}
