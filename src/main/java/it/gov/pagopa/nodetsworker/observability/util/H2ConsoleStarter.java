package it.gov.pagopa.nodetsworker.observability.util;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.sql.SQLException;
import org.h2.tools.Server;
import org.jboss.logging.Logger;

/**
 * Starts the H2 web console (dev profile only) inside the running JVM so the in-memory
 * {@code observability} datasource ({@code jdbc:h2:mem:observability}) can be inspected.
 *
 * <p>Open http://localhost:8082 and connect with:
 *
 * <ul>
 *   <li>JDBC URL: {@code jdbc:h2:mem:observability;DB_CLOSE_DELAY=-1}
 *   <li>User: {@code sa} — Password: (empty)
 * </ul>
 *
 * Never runs outside the {@code dev} profile.
 */
@IfBuildProfile("dev")
@ApplicationScoped
public class H2ConsoleStarter {

  private static final Logger LOG = Logger.getLogger(H2ConsoleStarter.class);
  private static final String WEB_PORT = "8082";

  private Server webServer;

  void onStart(@Observes StartupEvent event) {
    try {
      webServer = Server.createWebServer("-web", "-webPort", WEB_PORT).start();
      LOG.infof("H2 web console started at http://localhost:%s", WEB_PORT);
    } catch (SQLException e) {
      LOG.warnf(e, "Unable to start H2 web console on port %s", WEB_PORT);
    }
  }

  void onStop(@Observes ShutdownEvent event) {
    if (webServer != null) {
      webServer.stop();
      LOG.info("H2 web console stopped");
    }
  }
}
