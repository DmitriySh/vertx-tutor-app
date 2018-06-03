package ru.shishmakov;

import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(VertxUnitRunner.class)
public class WebMongoVerticleTest extends WebVerticle {

    private static final String DATABASE = "whiskies_test";
    private static int mongoPort;
    private static MongodProcess mongo;

    private Vertx vertx;
    private int vertxPort;

    @BeforeClass
    public static void initialized() {
        // TODO: 03.06.2018 start mongod
    }

    @AfterClass
    public static void shutdown() {
        // TODO: 03.06.2018 stop mongod
    }

    @Before
    public void setUp(TestContext context) throws IOException {
        mongoPort = buildLocalPort();
        mongo = MongodStarter.getDefaultInstance()
                .prepare(new MongodConfigBuilder()
                        .version(Version.Main.PRODUCTION)
                        .net(new Net(mongoPort, Network.localhostIsIPv6()))
                        .build())
                .start();

        vertxPort = buildLocalPort();
        DeploymentOptions options = new DeploymentOptions().setConfig(new JsonObject()
                .put("http.port", vertxPort)
                .put("db_name", DATABASE)
                .put("connection_string", "mongodb://localhost:" + mongoPort));
        vertx = Vertx.vertx();
        vertx.deployVerticle(WebMongoVerticle.class, options, context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
        // drop collections instead of stop mongod
        mongo.stop();
    }

    @Override
    protected Vertx getVertx() {
        return vertx;
    }

    @Override
    protected int getPort() {
        return vertxPort;
    }
}
