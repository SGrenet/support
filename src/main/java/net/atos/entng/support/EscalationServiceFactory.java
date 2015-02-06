package net.atos.entng.support;
import org.entcore.common.storage.Storage;
import org.vertx.java.core.Vertx;
import org.vertx.java.platform.Container;

import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.impl.EscalationServiceRedmineImpl;


public class EscalationServiceFactory {

	public static EscalationService makeEscalationService(final BugTracker bugTracker,
			final Vertx vertx, final Container container, final TicketService ts, final UserService us,
			Storage storage) {

		switch (bugTracker) {
			case REDMINE:
				return new EscalationServiceRedmineImpl(vertx, container, ts, us, storage);

			default:
				throw new IllegalArgumentException("Invalid parameter bugTracker");
		}
	}

}
