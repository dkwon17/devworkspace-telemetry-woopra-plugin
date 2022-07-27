/*
 * Copyright (c) 2022 Red Hat, Inc.
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Red Hat, Inc. - initial API and implementation
 */
package com.redhat.devworkspace.services.telemetry.woopra;

import com.redhat.devworkspace.services.telemetry.woopra.exception.WoopraCredentialException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.slf4j.LoggerFactory.getLogger;

public class AnalyticsManagerStartupTest {

    private static TestHttpServer server;

    @BeforeAll
    public static void setup() throws Exception {
        server = new TestHttpServer();
        server.start();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        server.stop();
    }

    @Test
    public void willNotStartWithoutWriteKey() {
        MainConfiguration config = new MainConfigurationBuilder()
                .woopraDomain("woopra.domain")
                .build();

        assertThrows(WoopraCredentialException.class, () -> {
            AnalyticsManager am = new TestAnalyticsManager(config);
        }, "Requires a Segment write key or the URL of an endpoint that will return the Segment write key. " +
                "Set either SEGMENT_WRITE_KEY or SEGMENT_WRITE_KEY_ENDPOINT");
    }

    @Test
    public void willNotStartWithoutWoopraDomain() {
        MainConfiguration config = new MainConfigurationBuilder()
                .segmentWriteKey("segment.write.key")
                .build();

        assertThrows(WoopraCredentialException.class, () -> {
            AnalyticsManager am = new TestAnalyticsManager(config);
        }, "Requires a Woopra domain or the URL of an endpoint that will return the Woopra domain. " +
                "Set either WOOPRA_DOMAIN or WOOPRA_DOMAIN_ENDPOINT");
    }

    @Test
    public void readWoopraDomainFromEndpoint() {
        MainConfiguration config = new MainConfigurationBuilder()
                .segmentWriteKey("segment.write.key")
                .woopraDomainEndpoint(TestHttpServer.ROUTE_URL + "?body=woopra.domain")
                .build();
        AnalyticsManager am = new TestAnalyticsManager(config);
        assertEquals("woopra.domain", am.woopraDomain);
    }

    @Test
    public void readSegmentWriteKeyFromEndpoint() {
        MainConfiguration config = new MainConfigurationBuilder()
                .woopraDomain("woopra.domain")
                .segmentWriteKeyEndpoint(TestHttpServer.ROUTE_URL + "?body=segment.write.key")
                .build();
        AnalyticsManager am = new TestAnalyticsManager(config);
        assertEquals("segment.write.key", am.segmentWriteKey);
    }

    @Test
    public void readWoopraDomainAndSegmentWriteKeyFromEndpoint() {
        MainConfiguration config = new MainConfigurationBuilder()
                .woopraDomainEndpoint(TestHttpServer.ROUTE_URL + "?body=woopra.domain")
                .segmentWriteKeyEndpoint(TestHttpServer.ROUTE_URL + "?body=segment.write.key")
                .build();
        AnalyticsManager am = new TestAnalyticsManager(config);
        assertEquals("woopra.domain", am.woopraDomain);
        assertEquals("segment.write.key", am.segmentWriteKey);
    }

    @Test
    public void readWoopraDomainAndSegmentWriteKey() {
        MainConfiguration config = new MainConfigurationBuilder()
                .woopraDomain("woopra.domain")
                .segmentWriteKey("segment.write.key")
                .build();
        AnalyticsManager am = new TestAnalyticsManager(config);
        assertEquals("woopra.domain", am.woopraDomain);
        assertEquals("segment.write.key", am.segmentWriteKey);
    }

    @Test
    public void doNotReadWoopraDomainAndSegmentWriteKeyFromEndpoints() {
        MainConfiguration config = new MainConfigurationBuilder()
                .woopraDomain("woopra.domain")
                .segmentWriteKey("segment.write.key")
                .woopraDomainEndpoint(TestHttpServer.ROUTE_URL + "?body=woopra.domain.from.endpoint")
                .segmentWriteKeyEndpoint(TestHttpServer.ROUTE_URL + "?body=segment.write.key.from.endpoint")
                .build();
        AnalyticsManager am = new TestAnalyticsManager(config);
        assertEquals("woopra.domain", am.woopraDomain);
        assertEquals("segment.write.key", am.segmentWriteKey);
    }
}

class TestAnalyticsManager extends AnalyticsManager {
    public TestAnalyticsManager(MainConfiguration config) {
        super(config,
                new MockDevworkspaceFinder(),
                new MockUsernameFinder(),
                null,
                new HttpUrlConnectionProvider());
    }

    @Override
    public boolean isEnabled() {
        return false;
    }
}

/**
 * Simple HTTP server.
 *
 * GET request to '{ROUTE_URL}?body=test' results in a response
 * with a Content-type of 'text/plain' and a response body of 'test'
 */
class TestHttpServer {
    public static final int PORT = 8888;
    public static final String HOSTNAME = "localhost";
    public static final String PATH = "/";
    public static final String ROUTE_URL = "http://" + HOSTNAME + ":" + PORT + PATH;

    private static final Logger LOG = getLogger(TestHttpServer.class);

    private HttpServer server = null;

    public void start() throws Exception {
        LOG.info("Creating socket address with hostname: {} and port: {}", HOSTNAME, PORT);
        server = HttpServer.create(new InetSocketAddress(HOSTNAME, PORT), 0);
        server.createContext(PATH, new HttpHandler() {
            @Override
            public void handle(HttpExchange httpExchange) throws IOException {
                String response = httpExchange.getRequestURI().toString().split("\\?body")[1].split("=")[1];
                OutputStream outputStream = httpExchange.getResponseBody();
                httpExchange.getResponseHeaders().put("Content-Type", Collections.singletonList("text/plain"));
                httpExchange.sendResponseHeaders(200, response.length());
                outputStream.write(response.getBytes());
                outputStream.flush();
                outputStream.close();
            }
        });
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
