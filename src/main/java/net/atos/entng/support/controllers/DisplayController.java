package net.atos.entng.support.controllers;

import org.vertx.java.core.http.HttpServerRequest;

import fr.wseduc.rs.Get;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.http.BaseController;

public class DisplayController extends BaseController {

	@Get("")
	@SecuredAction("support.view")
	public void view(final HttpServerRequest request) {
		renderView(request);
	}

}
