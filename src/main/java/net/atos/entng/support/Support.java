package net.atos.entng.support;

import net.atos.entng.support.controllers.AttachmentController;
import net.atos.entng.support.controllers.CommentController;
import net.atos.entng.support.controllers.DisplayController;
import net.atos.entng.support.controllers.TicketController;
import net.atos.entng.support.enums.BugTracker;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.impl.TicketServiceSqlImpl;
import net.atos.entng.support.services.impl.UserServiceDirectoryImpl;

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

		final BugTracker bugTrackerType = BugTracker.REDMINE;
		TicketService ticketService = new TicketServiceSqlImpl(bugTrackerType);
		UserService userService = new UserServiceDirectoryImpl(eb);

		EscalationService escalationService =
				EscalationServiceFactory.makeEscalationService(bugTrackerType, vertx, container, log, eb, ticketService, userService);
		TicketController ticketController = new TicketController(ticketService, escalationService, userService,
				container.config().getString("gridfs-address", "wse.gridfs.persistor"));
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
