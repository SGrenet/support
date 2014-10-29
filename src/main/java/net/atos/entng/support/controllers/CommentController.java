package net.atos.entng.support.controllers;

import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import net.atos.entng.support.services.CommentService;
import net.atos.entng.support.services.CommentServiceSqlImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.webutils.request.RequestUtils;


public class CommentController extends ControllerHelper {

	private CommentService commentService;

	public CommentController() {
		commentService = new CommentServiceSqlImpl();
	}

	@Post("/ticket/:id/comment")
	@ApiDoc("Add a comment to a ticket")
	public void createComment(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createOrUpdateComment", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject comment) {
							comment.putNumber("ticket_id", Long.parseLong(ticketId));
							commentService.create(comment, user, notEmptyResponseHandler(request));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Put("comment/:id")
	@ApiDoc("Update a comment")
	public void updateComment(final HttpServerRequest request) {
		final String commentId = request.params().get("id");

		RequestUtils.bodyToJson(request, pathPrefix + "createOrUpdateComment", new Handler<JsonObject>(){
			@Override
			public void handle(JsonObject comment) {
				commentService.update(commentId, comment, notEmptyResponseHandler(request));
			}
		});
	}

	@Get("/ticket/:id/comments")
	@ApiDoc("Get all comments of a ticket")
	public void listTicketComments(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		commentService.listTicketComments(ticketId, arrayResponseHandler(request));
	}


}
