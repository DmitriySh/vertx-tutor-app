package ru.shishmakov;

import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.ServerSocket;

public class WebMongoVerticleTest {

    private static int port;
    private static MongodProcess mongo;

    @BeforeClass
    public static void setUp() throws IOException {
        port = buildLocalPort();
        mongo = MongodStarter.getDefaultInstance()
                .prepare(new MongodConfigBuilder()
                        .version(Version.Main.PRODUCTION)
                        .net(new Net(port, Network.localhostIsIPv6()))
                        .build())
                .start();
    }

    @AfterClass
    public static void tearDown() {
        mongo.stop();
    }

    @Test
    public void name() {
        int a = 1;
    }

    private static int buildLocalPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
