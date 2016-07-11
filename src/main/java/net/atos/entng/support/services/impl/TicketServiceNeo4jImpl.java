package net.atos.entng.support.services.impl;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

import org.entcore.common.neo4j.Neo4j;
import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.json.impl.Json;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;


public class TicketServiceNeo4jImpl {

    private Neo4j neo4j = Neo4j.getInstance();

    public void getUsersFromList(JsonArray listUserIds, Handler<Either<String, JsonArray>> handler) {
        String query = "match(n:User) where n.id in {ids} return n.id, n.profiles;";

        JsonObject params = new JsonObject().putArray("ids", listUserIds);

        neo4j.execute(query, params, validResultHandler(handler));
    }
}
