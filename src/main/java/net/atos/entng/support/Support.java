package net.atos.entng.support;

import net.atos.entng.support.controllers.CommentController;
import net.atos.entng.support.controllers.DisplayController;
import net.atos.entng.support.controllers.TicketController;

import org.entcore.common.http.BaseServer;

public class Support extends BaseServer {

	@Override
	public void start() {
		super.start();

		addController(new DisplayController());

		TicketController ticketController = new TicketController();
		addController(ticketController);

		CommentController commentController = new CommentController();
		addController(commentController);
	}

}
