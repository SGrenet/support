package net.atos.entng.support;

import net.atos.entng.support.controllers.AttachmentController;
import net.atos.entng.support.controllers.CommentController;
import net.atos.entng.support.controllers.DisplayController;
import net.atos.entng.support.controllers.TicketController;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.EscalationServiceRedmineImpl;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.TicketServiceSqlImpl;

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

		TicketService ticketService = new TicketServiceSqlImpl();

		EscalationService escalationService = new EscalationServiceRedmineImpl(vertx, container, log, eb, ticketService);
		TicketController ticketController = new TicketController(eb, escalationService, ticketService);
		addController(ticketController);

		SqlConf commentSqlConf = SqlConfs.createConf(CommentController.class.getName());
		commentSqlConf.setTable("comments");
		commentSqlConf.setSchema("support");
		CommentController commentController = new CommentController();
		addController(commentController);

		AttachmentController attachmentController = new AttachmentController();
		addController(attachmentController);
	}

}
