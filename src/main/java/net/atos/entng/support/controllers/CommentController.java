package net.atos.entng.support.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.CommentService;
import net.atos.entng.support.services.CommentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.http.filter.sql.OwnerOnly;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;


public class CommentController extends ControllerHelper {

	private final CommentService commentService;

	public CommentController() {
		commentService = new CommentServiceSqlImpl();
		crudService = commentService;
	}

	@Put("comment/:id")
	@ApiDoc("Update a comment")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOnly.class)
	public void updateComment(final HttpServerRequest request) {
		final String commentId = request.params().get("id");

		RequestUtils.bodyToJson(request, pathPrefix + "updateComment", new Handler<JsonObject>(){
			@Override
			public void handle(JsonObject comment) {
				commentService.update(commentId, comment, notEmptyResponseHandler(request));
			}
		});
	}

	@Get("/ticket/:id/comments")
	@ApiDoc("Get all comments of a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void listTicketComments(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		commentService.listTicketComments(ticketId, arrayResponseHandler(request));
	}


}
