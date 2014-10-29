package net.atos.entng.support.services;

import org.entcore.common.service.CrudService;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public interface CommentService extends CrudService {

	public void listTicketComments(String ticketId, Handler<Either<String, JsonArray>> handler);

}
