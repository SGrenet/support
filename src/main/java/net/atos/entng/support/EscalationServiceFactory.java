package net.atos.entng.support;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.impl.EscalationServiceRedmineImpl;


public class EscalationServiceFactory {

	public static EscalationService makeEscalationService(final BugTracker bugTracker, final Vertx vertx, final Container container,
			final Logger logger, final EventBus eb, final TicketService ts, final UserService us) {

		switch (bugTracker) {
			case REDMINE:
				return new EscalationServiceRedmineImpl(vertx, container, logger, eb, ts, us);

			default:
				throw new IllegalArgumentException("Invalid parameter bugTracker");
		}
	}

}
