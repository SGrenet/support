package net.atos.entng.support.enums;

import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public enum BugTracker {

	REDMINE {
		@Override
		public String getLastIssueUpdateFromPostgresqlJson() {
			return "->'issue'->>'updated_on'";
		}

		@Override
		public String getStatusIdFromPostgresqlJson() {
			return "#>'{issue,status,id}'";
		}

		@Override
		public Number extractIdFromIssue(JsonObject issue) {
			return issue.getObject("issue").getNumber("id");
		}

		@Override
		public JsonArray extractAttachmentsFromIssue(JsonObject issue) {
			return issue.getObject("issue").getArray("attachments", null);
		}
	};


	/**
	 * @return SQL expression to extract last update time of bug tracker issue from JSON field stored in postgresql
	 */
	public abstract String getLastIssueUpdateFromPostgresqlJson();

	/**
	 * @return SQL expression to extract statusId of bug tracker issue from JSON field stored in postgresql
	 */
	public abstract String getStatusIdFromPostgresqlJson();

	/**
	 * Extract "id" from JSON object sent by the bug tracker REST API
	 */
	public abstract Number extractIdFromIssue(JsonObject issue);

	/**
	 * Extract "attachments" from JSON object sent by the bug tracker REST API
	 */
	public abstract JsonArray extractAttachmentsFromIssue(JsonObject issue);

}
