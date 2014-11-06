package net.atos.entng.support.services;

import static org.entcore.common.sql.SqlResult.*;

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

		String query = "SELECT c.*, u.username AS owner_name"
				+ " FROM support.comments AS c"
				+ " INNER JOIN support.users AS u ON c.owner = u.id"
				+ " WHERE ticket_id = ?";
		JsonArray values = new JsonArray().add(Sql.parseId(ticketId));

		sql.prepared(query, values, validResultHandler(handler));
	}



}
