package net.atos.entng.support.services;

import org.entcore.common.service.CrudService;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public interface AttachmentService extends CrudService {

	public void listTicketAttachments(String ticketId, Handler<Either<String, JsonArray>> handler);

}
