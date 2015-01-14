package net.atos.entng.support.controllers;

import static net.atos.entng.support.Support.SUPPORT_NAME;
import static net.atos.entng.support.enums.TicketStatus.*;
import static org.entcore.common.http.response.DefaultResponseHandler.arrayResponseHandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.atos.entng.support.filters.LocalAdmin;
import net.atos.entng.support.filters.OwnerOrLocalAdmin;
import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;
import net.atos.entng.support.services.UserService;

import org.entcore.common.controller.ControllerHelper;
import org.entcore.common.http.filter.ResourceFilter;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserUtils;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
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
import fr.wseduc.webutils.FileUtils;
import fr.wseduc.webutils.I18n;
import fr.wseduc.webutils.request.RequestUtils;


public class TicketController extends ControllerHelper {

	private static final String TICKET_CREATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_CREATED";
	private static final String TICKET_UPDATED_EVENT_TYPE = SUPPORT_NAME + "_TICKET_UPDATED";
	private static final int SUBJECT_LENGTH_IN_NOTIFICATION = 50;

	private final TicketService ticketService;
	private final UserService userService;
	private final EscalationService escalationService;
	private final String gridfsAddress;

	public TicketController(TicketService ts, EscalationService es, UserService us, String gridfsAddress) {
		ticketService = ts;
		userService = us;
		escalationService = es;
		this.gridfsAddress = gridfsAddress;
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

							JsonArray attachments = ticket.getArray("attachments", null);
							ticket.removeField("attachments");
							ticketService.createTicket(ticket, attachments, user, getCreateOrUpdateTicketHandler(request, user, ticket, true));
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

	@Post("/ticket/:id/escalate")
	@ApiDoc("Escalate ticket : the ticket is forwarded to an external bug tracker, a copy of the ticket is saved and will be regularly synchronized")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(LocalAdmin.class)
	public void escalateTicket(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					ticketService.getTicketForEscalation(ticketId, getTicketForEscalationHandler(request, ticketId, user));
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});
	}

	private Handler<Either<String,JsonObject>> getTicketForEscalationHandler(final HttpServerRequest request,
			final String ticketId, final UserInfos user) {

		return new Handler<Either<String,JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> getTicketResponse) {
				if(getTicketResponse.isRight()) {
					JsonObject ticket = getTicketResponse.right().getValue();
					if(ticket == null || ticket.size() == 0) {
						log.error("Ticket " + ticketId + " cannot be escalated : its status should be new or opened, and its escalation status should be not_done or in_progress");
						badRequest(request, "support.error.escalation.conflict");
						return;
					}

					JsonArray comments = new JsonArray(ticket.getString("comments"));
					JsonArray attachments = new JsonArray(ticket.getString("attachments"));
					final ConcurrentMap<Integer, String> attachmentMap = new ConcurrentHashMap<Integer, String>();

					escalationService.escalateTicket(request, ticket, comments, attachments, attachmentMap,
							getEscalateTicketHandler(request, ticketId, user, attachmentMap));

				}
				else {
					log.error("Error when calling service getTicketForEscalation. " + getTicketResponse.left().getValue());
					renderError(request, new JsonObject().putString("error",
							"support.escalation.error.data.cannot.be.retrieved.from.database"));
				}
			}
		};
	}

	private Handler<Either<String,JsonObject>> getEscalateTicketHandler(final HttpServerRequest request,
			final String ticketId, final UserInfos user, final ConcurrentMap<Integer, String> attachmentMap) {

		return new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(final Either<String, JsonObject> escalationResponse) {
				if(escalationResponse.isRight()) {
					final JsonObject issue = escalationResponse.right().getValue();
					final Number issueId = escalationService.getBugTrackerType().extractIdFromIssue(issue);

					// get the whole issue (i.e. with attachments' metadata and comments) to save it in database
					escalationService.getIssue(issueId, new Handler<Either<String, JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> getWholeIssueResponse) {
							if(getWholeIssueResponse.isRight()) {
								final JsonObject wholeIssue = getWholeIssueResponse.right().getValue();
								ticketService.endSuccessfulEscalation(ticketId, wholeIssue, issueId, attachmentMap, user, new Handler<Either<String,JsonObject>>() {

									@Override
									public void handle(Either<String, JsonObject> event) {
										if(event.isRight()) {
											renderJson(request, wholeIssue);
										}
										else {
											log.error("Error when trying to update escalation status to successful and to save bug tracker issue");
											renderError(request, new JsonObject().putString("error", event.left().getValue()));
										}
									}

								});
							}
							else {
								log.error("Error when trying to get bug tracker issue");

								// Update escalation status to successful (escalation succeeded, but data could not be saved in postgresql)
								ticketService.endSuccessfulEscalation(ticketId, new JsonObject(), issueId, attachmentMap, user, new Handler<Either<String,JsonObject>>() {
									@Override
									public void handle(Either<String, JsonObject> event) {
										if(event.isLeft()) {
											log.error("Error when trying to update escalation status to successful");
										}
									}
								});
								renderError(request, new JsonObject().putString("error", getWholeIssueResponse.left().getValue()));
							}
						}
					});

				}
				else {
					ticketService.endFailedEscalation(ticketId, user, new Handler<Either<String,JsonObject>>() {
						@Override
						public void handle(Either<String, JsonObject> event) {
							if(event.isLeft()) {
								log.error("Error when updating escalation status to failed");
							}
						}
					});
					renderError(request, new JsonObject().putString("error", escalationResponse.left().getValue()));
				}
			}
		};
	}

	@Get("/ticket/:id/bugtrackerissue")
	@ApiDoc("Get bug tracker issue saved in postgresql")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(LocalAdmin.class)
	public void getBugTrackerIssue(final HttpServerRequest request) {
		final String ticketId = request.params().get("id");
		ticketService.getIssue(ticketId, arrayResponseHandler(request));
	}

	@Get("/gridfs/:id")
	@ApiDoc("Get bug tracker attachment saved in gridfs")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(LocalAdmin.class)
	public void getBugTrackerAttachment(final HttpServerRequest request) {
		final String attachmentId = request.params().get("id");

		ticketService.getIssueAttachmentName(attachmentId, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if(event.isRight() && event.right().getValue() != null) {
					String name = event.right().getValue().getString("name", null);
					final String filename = (name!=null && name.trim().length()>0) ? name : "filename";

					FileUtils.gridfsReadFile(attachmentId, eb, gridfsAddress, new Handler<Buffer>() {
						@Override
						public void handle(Buffer data) {
							request.response()
								.putHeader("Content-Disposition",
										"attachment; filename="+filename)
								.setChunked(true)
								.write(data).end();
						}
					});
				}
				else {
					renderError(request, new JsonObject().putString("error",
							"support.get.attachment.metadata.error.data.cannot.be.retrieved.from.database"));
				}
			}
		});
	}

	@Post("/issue/:id/comment")
	@ApiDoc("Add comment to bug tracker issue")
	@SecuredAction(value = "support.manager", type= ActionType.RESOURCE)
	@ResourceFilter(LocalAdmin.class)
	public void commentIssue(final HttpServerRequest request) {
		final String id = request.params().get("id");
		final Integer issueId = Integer.parseInt(id);

		UserUtils.getUserInfos(eb, request, new Handler<UserInfos>() {
			@Override
			public void handle(final UserInfos user) {
				if (user != null) {
					RequestUtils.bodyToJson(request, pathPrefix + "commentIssue", new Handler<JsonObject>(){
						@Override
						public void handle(JsonObject comment) {

							// add author name to comment
							StringBuilder content = new StringBuilder();
							content.append(I18n.getInstance().translate("support.escalated.ticket.author", I18n.acceptLanguage(request)))
								.append(" : ")
								.append(user.getUsername())
								.append("\n")
								.append(comment.getString("content"));
							comment.putString("content", content.toString());

							escalationService.commentIssue(issueId, comment, new Handler<Either<String,JsonObject>>() {
								@Override
								public void handle(Either<String,JsonObject> event) {
									if(event.isRight()) {
										// get the whole issue (i.e. with attachments' metadata and comments) and save it in postgresql
										escalationService.getIssue(issueId, new Handler<Either<String, JsonObject>>() {
											@Override
											public void handle(Either<String, JsonObject> response) {
												if(response.isRight()) {
													final JsonObject issue = response.right().getValue();
													ticketService.updateIssue(issueId, issue.toString(), new Handler<Either<String,JsonObject>>() {

														@Override
														public void handle(Either<String, JsonObject> updateIssueResponse) {
															if(updateIssueResponse.isRight()) {
																renderJson(request, issue);
															}
															else {
																renderError(request, new JsonObject().putString("error",
																		"support.error.comment.added.to.escalated.ticket.but.synchronization.failed"));
																log.error("Error when trying to update bug tracker issue: "+updateIssueResponse.toString());
															}
														}

													});
												}
												else {
													renderError(request, new JsonObject().putString("error", response.left().getValue()));
												}
											}
										});
									}
									else {
										renderError(request, new JsonObject().putString("error", event.left().getValue()));
									}
								}
							});
						}
					});
				} else {
					log.debug("User not found in session.");
					unauthorized(request);
				}
			}
		});

	}

}
