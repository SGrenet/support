package net.atos.entng.support.services;

import org.entcore.common.service.CrudService;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public interface TicketService extends CrudService {

	public void createTicket(JsonObject ticket, JsonArray attachments, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void updateTicket(String id, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler);

	public void listTickets(UserInfos user, Handler<Either<String, JsonArray>> handler);

	public void listMyTickets(UserInfos user, Handler<Either<String, JsonArray>> handler);

	public void getTicketForEscalation(String ticketId, Handler<Either<String, JsonObject>> handler);

	public void endSuccessfulEscalation(String ticketId, JsonObject issue, Integer issueId,
			UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void endFailedEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler);

	public void updateIssue(int issueId, String content, Handler<Either<String, JsonObject>> handler);
}
