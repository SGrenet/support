package net.atos.entng.support.services;

import java.util.concurrent.ConcurrentMap;

import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

/**
 * Terminology used : "ticket" for tickets in ENT, "issue" for tickets in bug tracker
 */
public interface EscalationService {

	/**
	 * Parameters "ticket", "comments" and "attachments" are used to create a ticket in bug tracker
	 *
	 * @param attachmentMap : emptyMap that must be filled by function escalateTicket. key = attachmentId in bug tracker, value = attachmentId in gridfs
	 */
	public void escalateTicket(HttpServerRequest request, JsonObject ticket, JsonArray comments, JsonArray attachments,
			ConcurrentMap<Integer, String> attachmentMap, Handler<Either<String, JsonObject>> handler);

	public void listIssues(Handler<Either<String, JsonObject>> handler);

	public void getIssue(int issueId, Handler<Either<String, JsonObject>> handler);

	public Integer extractIdFromIssue(JsonObject issue);

	public void pullAndSynchronizeTickets();
}
