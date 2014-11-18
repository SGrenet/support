package net.atos.entng.support.services;

import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

public interface UserService {

	public void getLocalAdministrators(String structure, Handler<JsonArray> handler);

}
