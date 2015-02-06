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
import org.entcore.common.storage.Storage;
import org.entcore.common.storage.StorageFactory;
import org.vertx.java.core.eventbus.EventBus;


public class Support extends BaseServer {

	public final static String SUPPORT_NAME = "SUPPORT";

	@Override
	public void start() {
		super.start();
		final EventBus eb = getEventBus(vertx);

		addController(new DisplayController());

		final BugTracker bugTrackerType = BugTracker.REDMINE; // TODO : read bugTracker from module configuration
		final Storage storage = new StorageFactory(vertx, config).getStorage();

		TicketService ticketService = new TicketServiceSqlImpl(bugTrackerType);
		UserService userService = new UserServiceDirectoryImpl(eb);

		EscalationService escalationService = EscalationServiceFactory.makeEscalationService(
						bugTrackerType, vertx, container, ticketService, userService, storage);
		TicketController ticketController = new TicketController(
				ticketService, escalationService, userService, storage
				);
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
