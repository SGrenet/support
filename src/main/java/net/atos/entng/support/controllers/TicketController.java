package net.atos.entng.support.controllers;

import static net.atos.entng.support.TicketStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;
import static org.entcore.common.http.response.DefaultResponseHandler.notEmptyResponseHandler;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.service.CrudService;
import org.entcore.common.service.VisibilityFilter;
import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.request.RequestUtils;


public class TicketController extends ControllerHelper {

	private CrudService ticketService;

	public TicketController() {
		ticketService = new SqlCrudService("support", "tickets");
	}

	@Post("/ticket")
	@ApiDoc("Create a ticket")
	@SecuredAction("support.ticket.create")
	public void createTicket(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "createTicket", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject ticket) {
							ticket.putNumber("status", NEW.status());
							ticketService.create(ticket, user, notEmptyResponseHandler(request));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Put("/ticket/:id")
	@ApiDoc("Update a ticket")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void updateTicket(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "updateTicket", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject ticket) {

							// TODO : do not authorize description update if there is a comment
							ticketService.update(ticketId, ticket, notEmptyResponseHandler(request));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	@Get("/tickets")
	@ApiDoc("Get all tickets")
	@SecuredAction("support.ticket.manage")
	public void listTickets(final HttpServerRequest request) {
		ticketService.list(arrayResponseHandler(request));
	}

	@Get("/tickets/mine")
	@ApiDoc("Get all tickets created by current user")
	@SecuredAction("support.ticket.list")
	public void listUserTickets(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					ticketService.list(VisibilityFilter.OWNER, user, arrayResponseHandler(request));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});

	}

}
