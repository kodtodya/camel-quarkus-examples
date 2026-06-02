package com.kodtodya.insurance.poc.config;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.h2.tools.Server;
import org.jboss.logging.Logger;

/**
 * Starts an H2 TCP server on port 9092 at application startup
 * so the embedded H2 database is accessible from:
 *   - H2 Console in browser: http://localhost:8080/h2-console
 *   - External clients via JDBC: jdbc:h2:tcp://localhost:9092/~/insurancedb
 */
@ApplicationScoped
public class H2ServerConfig {

    private static final Logger LOG = Logger.getLogger(H2ServerConfig.class);

    private Server tcpServer;
    private Server webServer;

    void onStart(@Observes StartupEvent event) {
        try {
            // Start H2 TCP server (allows external JDBC connections)
            tcpServer = Server.createTcpServer(
                "-tcp",
                "-tcpPort", "9092",
                "-tcpAllowOthers",
                "-ifNotExists"
            ).start();
            LOG.infof("H2 TCP Server started on port 9092. JDBC URL: jdbc:h2:tcp://localhost:9092/~/insurancedb");

            // Start H2 Web console server on port 8082 for standalone access
            webServer = Server.createWebServer(
                "-web",
                "-webPort", "8082",
                "-webAllowOthers",
                "-ifNotExists"
            ).start();
            LOG.infof("H2 Web Console started at: http://localhost:8082");
            LOG.infof("H2 Console (via Quarkus) available at: http://localhost:8080/h2-console");
        } catch (Exception e) {
            LOG.errorf(e, "Failed to start H2 server");
        }
    }

    void onStop(@Observes ShutdownEvent event) {
        if (tcpServer != null && tcpServer.isRunning(false)) {
            tcpServer.stop();
            LOG.info("H2 TCP Server stopped");
        }
        if (webServer != null && webServer.isRunning(false)) {
            webServer.stop();
            LOG.info("H2 Web Console stopped");
        }
    }
}
