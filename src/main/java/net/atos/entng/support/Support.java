package net.atos.entng.support;

import net.atos.entng.support.controllers.CommentController;
import net.atos.entng.support.controllers.DisplayController;
import net.atos.entng.support.controllers.TicketController;

import org.entcore.common.http.BaseServer;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.vertx.java.core.eventbus.EventBus;


public class Support extends BaseServer {

	public final static String SUPPORT_NAME = "SUPPORT";

	@Override
	public void start() {
		super.start();
		final EventBus eb = getEventBus(vertx);

		addController(new DisplayController());

		TicketController ticketController = new TicketController(eb);
		addController(ticketController);

		SqlConf commentSqlConf = SqlConfs.createConf(CommentController.class.getName());
		commentSqlConf.setTable("comments");
		commentSqlConf.setSchema("support");
		CommentController commentController = new CommentController();
		addController(commentController);

	}

}
