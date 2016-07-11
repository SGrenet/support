package net.atos.entng.support.services;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import java.util.Set;

public interface TicketServiceNeo4j {
    public void getUsersFromList(JsonArray listUserIds, Handler<Either<String, JsonArray>> handler);
}
