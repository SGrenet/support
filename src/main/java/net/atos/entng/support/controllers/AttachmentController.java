package net.atos.entng.support.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.AttachmentService;
import net.atos.entng.support.services.impl.AttachmentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.vertx.java.core.http.HttpServerRequest;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;

public class AttachmentController extends ControllerHelper {

	private AttachmentService attachmentService;

	public AttachmentController() {
		attachmentService = new AttachmentServiceSqlImpl();
		crudService = attachmentService;
	}

	@Get("/ticket/:id/attachments")
	@ApiDoc("Get all attachments of a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void listTicketAttachments(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		attachmentService.listTicketAttachments(ticketId, arrayResponseHandler(request));
	}

}
