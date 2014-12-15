package net.atos.entng.support.controllers;

import static net.atos.entng.support.Support.SUPPORT_NAME;
import static net.atos.entng.support.TicketStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.TicketServiceSqlImpl;
import net.atos.entng.support.services.UserService;
import net.atos.entng.support.services.UserServiceDirectoryImpl;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.http.RouteMatcher;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.platform.Container;

import fr.wseduc.rs.ApiDoc;
import fr.wseduc.rs.Get;
import fr.wseduc.rs.Post;
import fr.wseduc.rs.Put;
import fr.wseduc.security.ActionType;
import fr.wseduc.security.SecuredAction;
import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.request.RequestUtils;


public class TicketController extends ControllerHelper {

	private static final String TICKET_CREATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_CREATED";
	private static final String TICKET_UPDATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_UPDATED";
	private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;

	private TicketService ticketService;
	private UserService userService;
	private EscalationService escalationService;

	public TicketController(EventBus eb, EscalationService es) {
		ticketService = new TicketServiceSqlImpl();
		userService = new UserServiceDirectoryImpl(eb);
		escalationService = es;
	}

	@Override
	public void init(Vertx vertx, Container container, RouteMatcher rm, Map<String, fr.wseduc.webutils.security.SecuredAction> securedActions) {
		super.init(vertx, container, rm, securedActions);
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
							ticketService.createTicket(ticket, user, getCreateOrUpdateTicketHandler(request, user, ticket, true));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	private Handler<Either<String, JsonObject>> getCreateOrUpdateTicketHandler(final HttpServerRequest request,
			final UserInfos user, final JsonObject ticket, final boolean isCreate) {

		return new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if (event.isRight()) {
					JsonObject response = event.right().getValue();
					if (response != null && response.size() > 0) {
						if(isCreate) {
							notifyTicketCreated(request, user, response, ticket);
							response.putString("owner_name", user.getUsername());
						}
						else {
							notifyTicketUpdated(request, user, response);
						}
						renderJson(request, response, 200);
					} else {
						notFound(request);
					}
				} else {
					badRequest(request, event.left().getValue());
				}
			}
		};
	}

	/**
	 * Notify local administrators that a ticket has been created
	 */
	private void notifyTicketCreated(final HttpServerRequest request, final UserInfos user,
			final JsonObject response, final JsonObject ticket) {

		final String eventType = TICKET_CREATED_EVENT_TYPE;
		final String template = "notify-ticket-created.html";

		try {
			final long id = response.getLong("id", 0L);
			final String ticketSubject = ticket.getString("subject", null);
			final String structure = ticket.getString("school_id", null);

			if(id == 0L || ticketSubject == null || structure == null) {
				log.error("Could not get parameters id, subject or school_id. Unable to send timeline "+ eventType
								+ " notification.");
				return;
			}
			final String ticketId = Long.toString(id);

			userService.getLocalAdministrators(structure, new Handler<JsonArray>() {
				@Override
				public void handle(JsonArray event) {
					if (event != null) {
						Set<String> recipientSet = new HashSet<>();
						for (Object o : event) {
							if (!(o instanceof JsonObject)) continue;
							JsonObject j = (JsonObject) o;
							String id = j.getString("id");
							if(!user.getUserId().equals(id)) {
								recipientSet.add(id);
							}
						}

						List<String> recipients = new ArrayList<>(recipientSet);
						if(!recipients.isEmpty()) {
							JsonObject params = new JsonObject();
							params.putString("uri", container.config().getString("userbook-host") +
									"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
							params.putString("ticketUri", container.config().getString("host")
									+ "/support#/ticket/" + ticketId)
								.putString("username", user.getUsername())
								.putString("ticketid", ticketId)
								.putString("ticketsubject", shortenSubject(ticketSubject));

							notification.notifyTimeline(request, user, SUPPORT_NAME, eventType,
									recipients, ticketId, template, params);
						}
					}
				}
			});

		} catch (Exception e) {
			log.error("Unable to send timeline "+ eventType + " notification.", e);
		}
	}

	private String shortenSubject(String subject) {
		if(subject.length() > SUBJECT_LENGTH_IN_NOTIFICATION) {
			return subject.substring(0, SUBJECT_LENGTH_IN_NOTIFICATION)
					.concat(" [...]");
		}
		return subject;
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
							ticketService.updateTicket(ticketId, ticket, user,
									getCreateOrUpdateTicketHandler(request, user, null, false));
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	/**
	 * Notify owner and local administrators that the ticket has been updated
	 */
	private void notifyTicketUpdated(final HttpServerRequest request, final UserInfos user,
			final JsonObject response) {

		final String eventType = TICKET_UPDATED_EVENT_TYPE;
		final String template = "notify-ticket-updated.html";

		try {
			final String ticketSubject = response.getString("subject", null);
			final String ticketOwner = response.getString("owner", null);
			final String structure = response.getString("school_id", null);
			final String ticketId = request.params().get("id");

			if(ticketSubject == null || ticketOwner == null || structure == null) {
				log.error("Could not get parameters subject, owner or school_id. Unable to send timeline "+ eventType
								+ " notification.");
				return;
			}

			final Set<String> recipientSet = new HashSet<>();
			if(!ticketOwner.equals(user.getUserId())) {
				recipientSet.add(ticketOwner);
			}

			userService.getLocalAdministrators(structure,  new Handler<JsonArray>() {
				@Override
				public void handle(JsonArray event) {
					if (event != null) {
						for (Object o : event) {
							if (!(o instanceof JsonObject)) continue;
							JsonObject j = (JsonObject) o;
							String id = j.getString("id");
							if(!user.getUserId().equals(id)) {
								recipientSet.add(id);
							}
						}

						List<String> recipients = new ArrayList<>(recipientSet);

						if(!recipients.isEmpty()) {
							JsonObject params = new JsonObject();
							params.putString("uri", container.config().getString("userbook-host") +
									"/userbook/annuaire#" + user.getUserId() + "#" + user.getType());
							params.putString("ticketUri", container.config().getString("host")
									+ "/support#/ticket/" + ticketId)
								.putString("username", user.getUsername())
								.putString("ticketid", ticketId)
								.putString("ticketsubject", shortenSubject(ticketSubject));

							notification.notifyTimeline(request, user, SUPPORT_NAME, eventType,
									recipients, ticketId, template, params);
						}
					}
				}
			});

		} catch (Exception e) {
			log.error("Unable to send timeline "+ eventType + " notification.", e);
		}
	}


	@Get("/tickets")
	@ApiDoc("If current user is local admin, get all tickets. Otherwise, get my tickets")
	@SecuredAction("support.ticket.list")
	public void listTickets(final HttpServerRequest request) {
		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					Map<String, UserInfos.Function> functions = user.getFunctions();
					if (functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
						ticketService.listTickets(user, arrayResponseHandler(request));
					}
					else {
						ticketService.listMyTickets(user, arrayResponseHandler(request));
					}
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});

	}

	// TODO filter "LocalAdmin" : only local administrators can escalate
	@Post("/ticket/:id/escalate")
	@ApiDoc("Escalate ticket : the ticket is forwarded to an external bug tracker, and a copy of the ticket is saved and regularly synchronized")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(OwnerOrLocalAdmin.class)
	public void escalateTicket(final HttpServerRequest request) {

		escalationService.escalateTicket(request, new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject event) {
				// TODO
				renderJson(request, event);
			}
		});
	}

	// TODO : remove this temporary webservice, used to test vertx HttpClient
	@Get("/tickets/redmine")
	@ApiDoc("List tickets")
	@SecuredAction(value = "support.manager", type= ActionType.AUTHENTICATED)
	public void getRedmineTickets(final HttpServerRequest request) {

		escalationService.getTickets(new Handler<JsonObject>() {
			@Override
			public void handle(JsonObject event) {
				// TODO
				renderJson(request, event);
			}
		});
	}

}
