package net.atos.entng.support.services.impl;

import static org.entcore.common.sql.SqlResult.*;
import net.atos.entng.support.services.CommentService;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public class CommentServiceSqlImpl extends SqlCrudService implements CommentService {

	public CommentServiceSqlImpl() {
		super("support", "comments");
	}

	@Override
	public void listTicketComments(String ticketId, Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("SELECT c.*, u.username AS owner_name")
			.append(" FROM support.comments AS c")
			.append(" INNER JOIN support.users AS u ON c.owner = u.id")
			.append(" WHERE c.ticket_id = ?")
			.append(" ORDER BY c.modified");

		JsonArray values = new JsonArray().add(Sql.parseId(ticketId));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}



}
