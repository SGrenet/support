package net.atos.entng.support.services;

import static org.entcore.common.neo4j.Neo4jResult.*;

import org.entcore.common.neo4j.Neo4j;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class UserServiceNeoImpl implements UserService {

	private final Neo4j neo = Neo4j.getInstance();

	@Override
	public void getLocalAdministrators(final UserInfos user, final String structure,
			final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("MATCH (u:User)-[hf:HAS_FUNCTION]->(:Function{externalId : \"")
			.append(DefaultFunctions.ADMIN_LOCAL)
			.append("\"}) WHERE {structure} in hf.scope")
			.append(" AND u.id <> {currentUserId}")
			.append(" return DISTINCT u.id as userId");

		JsonObject parameters = new JsonObject();
		parameters.putString("structure", structure)
			.putString("currentUserId", user.getUserId());

		neo.execute(query.toString(), parameters, validResultHandler(handler));
	}

}
