/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

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
