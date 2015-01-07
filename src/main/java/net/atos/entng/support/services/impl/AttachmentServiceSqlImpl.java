package net.atos.entng.support.services.impl;

import static org.entcore.common.sql.SqlResult.validResultHandler;
import net.atos.entng.support.services.AttachmentService;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public class AttachmentServiceSqlImpl extends SqlCrudService implements AttachmentService  {

	public AttachmentServiceSqlImpl() {
		super("support", "comments");
	}

	@Override
	public void listTicketAttachments(String ticketId,
			Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("SELECT a.*, u.username AS owner_name")
			.append(" FROM support.attachments AS a")
			.append(" INNER JOIN support.users AS u ON a.owner = u.id")
			.append(" WHERE a.ticket_id = ?")
			.append(" ORDER BY a.created");

		JsonArray values = new JsonArray().add(Sql.parseId(ticketId));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

}
