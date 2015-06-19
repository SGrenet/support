package net.atos.entng.support.controllers;

import java.util.Map;

import net.atos.entng.support.Support;

import org.entcore.common.events.EventStore;
import org.entcore.common.events.EventStoreFactory;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.platform.Container;

import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;

public class DisplayController extends BaseController {

	private EventStore eventStore;
	private enum SupportEvent { ACCESS }

	@Override
	public void init(Vertx vertx, Container container, RouteMatcher rm,
			Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, container, rm, securedActions);
		eventStore = EventStoreFactory.getFactory().getEventStore(Support.class.getSimpleName());
	}

	@Get("")
	@SecuredAction("support.view")
	public void view(final HttpServerRequest request) {
		renderView(request);

		// Create event "access to application Support" and store it, for module "statistics"
		eventStore.createAndStoreEvent(SupportEvent.ACCESS.name(), request);
	}

}
