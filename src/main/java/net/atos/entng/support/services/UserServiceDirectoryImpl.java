package net.atos.entng.support.services;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

public class UserServiceDirectoryImpl implements UserService {

	private final EventBus eb;
	private static final String DIRECTORY_ADDRESS = "directory";

	public UserServiceDirectoryImpl(EventBus eb) {
		this.eb = eb;
	}

	@Override
	public void getLocalAdministrators(String structure, final Handler<JsonArray> handler) {

		JsonObject action = new JsonObject()
			.putString("action", "list-adml")
			.putString("structureId", structure);
		eb.send(DIRECTORY_ADDRESS, action, new Handler<Message<JsonArray>>() {
			@Override
			public void handle(Message<JsonArray> res) {
				handler.handle(res.body());
			}
		});
	}



}
