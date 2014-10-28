package net.atos.entng.support.services;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface CommentService extends CrudService {

	public void addComment(String ticketId, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler);

	public void updateComment(String ticketId, String commentId, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler);

	public void listTicketComments(String ticketId, UserInfos user,
			Handler<Either<String, JsonArray>> handler);

}
