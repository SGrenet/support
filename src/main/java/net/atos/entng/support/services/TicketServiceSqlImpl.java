package net.atos.entng.support.services;

import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;
import static org.entcore.common.sql.SqlResult.validResultHandler;

import java.util.List;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class TicketServiceSqlImpl extends SqlCrudService implements TicketService {

	public TicketServiceSqlImpl() {
		super("support", "tickets");
	}

	@Override
	public void createTicket(JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		String upsertUserQuery = "SELECT support.merge_users(?,?)";
		s.prepared(upsertUserQuery, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		data.putString("owner", user.getUserId());
		String returnedFields = "id, school_id, status, created, modified";
		s.insert(resourceTable, data, returnedFields);

		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
	}

	@Override
	public void updateTicket(String id, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		// Upsert user
		String upsertUserQuery = "SELECT support.merge_users(?,?)";
		s.prepared(upsertUserQuery, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// Update ticket
		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.getFieldNames()) {
			if(!"newComment".equals(attr)) {
				sb.append(attr).append(" = ?, ");
				values.add(data.getValue(attr));
			}
		}
		values.add(parseId(id));

		String updateTicketQuery = "UPDATE support.tickets" +
				" SET " + sb.toString() + "modified = NOW() " +
				"WHERE id = ? RETURNING modified, subject, owner, school_id";
		s.prepared(updateTicketQuery, values);

		// Add comment
		String comment = data.getString("newComment", null);
		if(comment != null && !comment.trim().isEmpty()) {
			String insertCommentQuery =
					"INSERT INTO support.comments (ticket_id, owner, content) VALUES(?, ?, ?)";
			JsonArray commentValues = new JsonArray();
			commentValues.add(parseId(id))
				.add(user.getUserId())
				.add(comment);
			s.prepared(insertCommentQuery, commentValues);
		}

		// Send queries to event bus
		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
	}

	@Override
	public void listTickets(UserInfos user, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name FROM support.tickets AS t")
			.append(" INNER JOIN support.users AS u ON t.owner = u.id");

		JsonArray values = new JsonArray();
		Function adminLocal = user.getFunctions().get(DefaultFunctions.ADMIN_LOCAL);

		if (adminLocal != null) {
			List<String> scopesList = adminLocal.getScope();
			if(scopesList != null && !scopesList.isEmpty()) {
				query.append(" WHERE t.school_id IN (");
				for (String scope : scopesList) {
					query.append("?,");
					values.addString(scope);
				}
				query.deleteCharAt(query.length() - 1);
				query.append(")");
			}
		}

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

	@Override
	public void listMyTickets(UserInfos user, Handler<Either<String, JsonArray>> handler) {
		String query = "SELECT t.*, u.username AS owner_name FROM support.tickets AS t"
			+ " INNER JOIN support.users AS u ON t.owner = u.id"
			+ " WHERE t.owner = ?";
		JsonArray values = new JsonArray().add(user.getUserId());

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

}
