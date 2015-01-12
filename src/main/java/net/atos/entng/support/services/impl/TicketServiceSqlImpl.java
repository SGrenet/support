package net.atos.entng.support.services.impl;

import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validRowsResultHandler;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

import net.atos.entng.support.EscalationStatus;
import net.atos.entng.support.TicketStatus;
import net.atos.entng.support.services.TicketService;

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
	public void createTicket(JsonObject ticket, JsonArray attachments, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		String upsertUserQuery = "SELECT support.merge_users(?,?)";
		s.prepared(upsertUserQuery, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		ticket.putString("owner", user.getUserId());
		String returnedFields = "id, school_id, status, created, modified, escalation_status, escalation_date";
		s.insert(resourceTable, ticket, returnedFields);

		this.insertAttachments(attachments, user, s, null);

		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
	}

	@Override
	public void updateTicket(String ticketId, JsonObject data, UserInfos user,
			Handler<Either<String, JsonObject>> handler) {

		SqlStatementsBuilder s = new SqlStatementsBuilder();

		// 1. Upsert user
		String upsertUserQuery = "SELECT support.merge_users(?,?)";
		s.prepared(upsertUserQuery, new JsonArray().add(user.getUserId()).add(user.getUsername()));

		// 2. Update ticket
		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.getFieldNames()) {
			if( !"newComment".equals(attr) && !"attachments".equals(attr) ) {
				sb.append(attr).append(" = ?, ");
				values.add(data.getValue(attr));
			}
		}
		values.add(parseId(ticketId));

		String updateTicketQuery = "UPDATE support.tickets" +
				" SET " + sb.toString() + "modified = NOW() " +
				"WHERE id = ? RETURNING modified, subject, owner, school_id";
		s.prepared(updateTicketQuery, values);

		// 3. Insert comment
		String comment = data.getString("newComment", null);
		if(comment != null && !comment.trim().isEmpty()) {
			String insertCommentQuery =
					"INSERT INTO support.comments (ticket_id, owner, content) VALUES(?, ?, ?)";
			JsonArray commentValues = new JsonArray();
			commentValues.add(parseId(ticketId))
				.add(user.getUserId())
				.add(comment);
			s.prepared(insertCommentQuery, commentValues);
		}

		// 4. Insert attachments
		JsonArray attachments = data.getArray("attachments", null);
		this.insertAttachments(attachments, user, s, ticketId);

		// Send queries to event bus
		sql.transaction(s.build(), validUniqueResultHandler(1, handler));
	}


	/**
	 * Append query "insert attachments" to SqlStatementsBuilder s
	 *
	 * @param ticketId : must be null when creating a ticket, and supplied when updating a ticket
	 */
	private void insertAttachments(final JsonArray attachments,
			final UserInfos user, final SqlStatementsBuilder s, final String ticketId) {

		if(attachments != null && attachments.size() > 0) {
			StringBuilder query = new StringBuilder();
			query.append("INSERT INTO support.attachments(gridfs_id, name, size, owner, ticket_id)")
				.append(" VALUES");

			JsonArray values = new JsonArray();
			for (Object a : attachments) {
				if(!(a instanceof JsonObject)) continue;
				JsonObject jo = (JsonObject) a;
				query.append("(?, ?, ?, ?, ");
				values.add(jo.getString("id"))
					.add(jo.getString("name"))
					.add(jo.getInteger("size"))
					.add(user.getUserId());

				if(ticketId == null){
					query.append("(SELECT currval('support.tickets_id_seq'))),");
				}
				else {
					query.append("?),");
					values.add(ticketId);
				}
			}
			// remove trailing comma
			query.deleteCharAt(query.length() - 1);

			s.prepared(query.toString(), values);
		}

	}


	@Override
	public void listTickets(UserInfos user, Handler<Either<String, JsonArray>> handler) {
		StringBuilder query = new StringBuilder();
		query.append("SELECT t.*, u.username AS owner_name,")
			.append(" i.content->'issue'->>'updated_on' as last_issue_update") // TODO : à revoir. Code spécifique redmine
			.append(" FROM support.tickets AS t")
			.append(" INNER JOIN support.users AS u ON t.owner = u.id")
			.append(" LEFT JOIN support.bug_tracker_issues AS i ON t.id=i.ticket_id");

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

	/**
	 * If escalation status is "not_done" or "failed", and ticket status is new or opened,
	 * update escalation status to "in_progress" and return the ticket with its attachments' ids and its comments.
	 *
	 * Else (escalation is not allowed) return null.
	 */
	@Override
	public void getTicketForEscalation(String ticketId, Handler<Either<String, JsonObject>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		// 1) WITH query to update status
		query.append("WITH updated_ticket AS (")
			.append(" UPDATE support.tickets")
			.append(" SET escalation_status = ?, escalation_date = NOW()")
			.append(" WHERE id = ?");
		values.add(EscalationStatus.IN_PROGRESS.status())
			.add(ticketId);

		query.append(" AND escalation_status NOT IN (?, ?)")
			.append(" AND status NOT IN (?, ?)")
			.append(" RETURNING * )");
		values.add(EscalationStatus.IN_PROGRESS.status())
			.add(EscalationStatus.SUCCESSFUL.status())
			.add(TicketStatus.RESOLVED.status())
			.add(TicketStatus.CLOSED.status());

		// 2) query to select ticket, attachments' ids and comments
		query.append("SELECT t.id, t.subject, t.description, t.category, t.school_id, u.username AS owner_name,")
			/*  When no rows are selected, json_agg returns a JSON array whose objects' fields have null values.
			 * We use CASE to return an empty array instead. */
			.append(" CASE WHEN COUNT(a.gridfs_id) = 0 THEN '[]' ELSE json_agg(DISTINCT a.gridfs_id) END AS attachments,")

			.append(" CASE WHEN COUNT(c.id) = 0 THEN '[]' ")
				.append(" ELSE json_agg(DISTINCT(date_trunc('second', c.created), c.id, c.content, v.username)::support.comment_tuple")
				.append(" ORDER BY (date_trunc('second', c.created), c.id, c.content, v.username)::support.comment_tuple)")
				.append(" END AS comments")

			.append(" FROM updated_ticket AS t")
			.append(" INNER JOIN support.users AS u ON t.owner = u.id")
			.append(" LEFT JOIN support.attachments AS a ON t.id = a.ticket_id")
			.append(" LEFT JOIN support.comments AS c ON t.id = c.ticket_id")
			.append(" LEFT JOIN support.users AS v ON c.owner = v.id")
			.append(" GROUP BY t.id, t.subject, t.description, t.category, t.school_id, u.username");

		sql.prepared(query.toString(), values, validUniqueResultHandler(handler));
	}


	private void updateTicketAfterEscalation(String ticketId, EscalationStatus targetStatus,
			JsonObject issue, Number issueId, ConcurrentMap<Integer, String> attachmentMap,
			UserInfos user, Handler<Either<String, JsonObject>> handler) {

		// 1. Update escalation status
		String query = "UPDATE support.tickets"
				+ " SET escalation_status = ?, escalation_date = NOW()"
				+ " WHERE id = ?";

		JsonArray values = new JsonArray()
			.add(targetStatus.status())
			.add(ticketId);

		if(!EscalationStatus.SUCCESSFUL.equals(targetStatus)) {
			sql.prepared(query, values, validUniqueResultHandler(handler));
		}
		else {
			SqlStatementsBuilder statements = new SqlStatementsBuilder();
			statements.prepared(query, values);

			// 2. Insert bug tracker issue in ENT, so that local administrators can see it
			String insertQuery = "INSERT INTO support.bug_tracker_issues(id, ticket_id, content, owner)"
					+ " VALUES(?, ?, ?, ?)";

			JsonArray insertValues = new JsonArray().add(issueId)
					.add(ticketId)
					.add(issue.toString())
					.add(user.getUserId());

			statements.prepared(insertQuery, insertValues);

			// 3. Insert attachment (document from workspace) metadata
			JsonArray attachments = issue.getObject("issue").getArray("attachments", null); // TODO : à revoir. Code spécifique redmine
			if(attachments != null && attachments.size() > 0
					&& attachmentMap != null && !attachmentMap.isEmpty()) {
				/*
				 * Example of "attachments" array with one attachment :
				 *
					"attachments":[
						{
						    "id": 784,
						    "filename": "test_pj.png",
						    "filesize": 118639,
						    "content_type": "image/png",
						    "description": "descriptionpj",
						    "content_url": "http: //support.web-education.net/attachments/download/784/test_pj.png"
						}
					]
				 */
				StringBuilder attachmentsQuery = new StringBuilder();
				attachmentsQuery.append("INSERT INTO support.bug_tracker_attachments(id, issue_id, document_id, name, size)")
					.append(" VALUES");

				JsonArray attachmentsValues = new JsonArray();

				for (Object o : attachments) {
					if(!(o instanceof JsonObject)) continue;
					JsonObject attachment = (JsonObject) o;
					attachmentsQuery.append("(?, ?, ?, ?, ?),");

					Number attachmentIdInBugTracker = attachment.getNumber("id");
					attachmentsValues.addNumber(attachmentIdInBugTracker)
						.addNumber(issueId)
						.addString(attachmentMap.get(attachmentIdInBugTracker))
						.addString(attachment.getString("filename"))
						.addNumber(attachment.getNumber("filesize"));
				}
				// remove trailing comma
				attachmentsQuery.deleteCharAt(attachmentsQuery.length() - 1);

				statements.prepared(attachmentsQuery.toString(), attachmentsValues);
			}

			sql.transaction(statements.build(), validUniqueResultHandler(1, handler));
		}

	}

	/**
	 * 	@inheritDoc
	 */
	@Override
	public void endSuccessfulEscalation(String ticketId, JsonObject issue, Number issueId,
			ConcurrentMap<Integer, String> attachmentMap,
			UserInfos user, Handler<Either<String, JsonObject>> handler) {

		this.updateTicketAfterEscalation(ticketId, EscalationStatus.SUCCESSFUL, issue, issueId, attachmentMap, user, handler);
	}

	@Override
	public void endFailedEscalation(String ticketId, UserInfos user, Handler<Either<String, JsonObject>> handler) {
		this.updateTicketAfterEscalation(ticketId, EscalationStatus.FAILED, null, null, null, user, handler);
	}

	@Override
	public void updateIssue(Number issueId, String content, Handler<Either<String, JsonObject>> handler) {
		String query = "UPDATE support.bug_tracker_issues"
				+ " SET content = ?, modified = now() "
				+ " WHERE id = ?";

		JsonArray values = new JsonArray()
			.addString(content)
			.addNumber(issueId);

		sql.prepared(query, values, validRowsResultHandler(handler));
	}

	@Override
	public void getLastIssuesUpdate(Handler<Either<String, JsonArray>> handler) {
		// TODO : faire une passe pour enlever le code spécifique redmine dans les classes autres que EscalationServiceRedmineImpl.
		// La clause select ci-dessous est par exemple specifique à redmine
		String query = "select max(content->'issue'->>'updated_on') as last_update from support.bug_tracker_issues";
		sql.raw(query, validResultHandler(handler));
	}

	@Override
	public void getIssueAttachmentsIds(Number issueId, Handler<Either<String, JsonArray>> handler) {
		// Return for instance [{"ids":"[886, 887, 888]"}]
		String query = "SELECT json_agg(id) AS ids FROM support.bug_tracker_attachments"
				+ " WHERE issue_id = ?";
		JsonArray values = new JsonArray().addNumber(issueId);

		sql.prepared(query, values, validResultHandler(handler));
	}

	@Override
	public void getIssue(String ticketId, Handler<Either<String, JsonArray>> handler) {
		// TODO : also SELECT issue's attachments
		String query = "SELECT * FROM support.bug_tracker_issues"
				+ " WHERE ticket_id = ?";
		JsonArray values = new JsonArray().add(ticketId);

		sql.prepared(query, values, validResultHandler(handler));
	};

	@Override
	public void insertIssueAttachment(Number issueId, JsonObject attachment, Handler<Either<String, JsonArray>> handler) {
		/* NB : Attachments downloaded from bug tracker are saved in gridfs, but not in application "workspace".
		 * => we save a gridfs_id, not a document_id
		 */
		String query = "INSERT INTO support.bug_tracker_attachments(id, issue_id, gridfs_id, name, size) VALUES(?, ?, ?, ?, ?)";

		JsonArray values = new JsonArray();
		JsonObject metadata = attachment.getObject("metadata");

		values.addNumber(attachment.getNumber("id_in_bugtracker"))
			.addNumber(issueId)
			.addString(attachment.getString("_id"))
			.addString(metadata.getString("filename"))
			.addNumber(metadata.getNumber("size"));

		sql.prepared(query.toString(), values, validResultHandler(handler));
	}

}
