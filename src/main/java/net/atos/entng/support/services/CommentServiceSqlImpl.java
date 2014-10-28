package net.atos.entng.support.services;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class CommentServiceSqlImpl extends SqlCrudService implements CommentService {

	public CommentServiceSqlImpl() {
		super("support", "comments");
	}

	@Override
	public void addComment(String ticketId, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {
		// TODO Auto-generated method stub
	}

	@Override
	public void updateComment(String ticketId, String commentId,
			JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {
		// TODO Auto-generated method stub

	}

	@Override
	public void listTicketComments(String ticketId, UserInfos user,
			Handler<Either<String, JsonArray>> handler) {
		// TODO Auto-generated method stub

	}



}
