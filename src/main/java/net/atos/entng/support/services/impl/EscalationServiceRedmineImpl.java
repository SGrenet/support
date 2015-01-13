package net.atos.entng.support.services.impl;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import net.atos.entng.support.services.EscalationService;
import net.atos.entng.support.services.TicketService;

import org.entcore.common.bus.WorkspaceHelper;
import org.entcore.common.bus.WorkspaceHelper.Document;
import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.FileUtils;
import fr.wseduc.webutils.I18n;

public class EscalationServiceRedmineImpl implements EscalationService {

	private final Logger log;

	private final HttpClient httpClient;
	private final String redmineHost;
	private final int redminePort;
	private boolean proxyIsDefined;
	private final int redmineProjectId;

	private final WorkspaceHelper wksHelper;
	private final TicketService ticketService;
	private EventBus eb;

	/*
	 * According to http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication :
	 * API key is a handy way to avoid putting a password in a script.
	 * You can find your API key on your account page ( /my/account ) when logged in, on the right-hand pane of the default layout.
	 */
	private static final String HEADER_REDMINE_API_KEY = "X-Redmine-API-Key";
	private String redmineApiKey;

	private static final String REDMINE_ISSUES_PATH = "/issues.json";
	private static final String REDMINE_UPLOAD_ATTACHMENT_PATH = "/uploads.json";


	public EscalationServiceRedmineImpl(final Vertx vertx, Container container, Logger logger, EventBus eb, TicketService ts) {
		JsonObject config = container.config();
		log = logger;
		httpClient = vertx.createHttpClient();
		wksHelper = new WorkspaceHelper(config.getString("gridfs-address", "wse.gridfs.persistor"), eb);
		ticketService = ts;
		this.eb = eb;

		String proxyHost = System.getProperty("http.proxyHost", null);
		int proxyPort = 80;
		try {
			proxyPort = Integer.valueOf(System.getProperty("http.proxyPort", "80"));
		} catch (NumberFormatException e) {
			log.error("JVM property 'http.proxyPort' must be an integer", e);
		}

		redmineHost = config.getString("bug-tracker-host", null);
		if (redmineHost == null || redmineHost.trim().isEmpty()) {
			log.error("Module property 'bug-tracker-host' must be defined");
		}
		redminePort = config.getInteger("bug-tracker-port", 80);

		if (proxyHost != null && !proxyHost.trim().isEmpty()) {
			proxyIsDefined = true;
			httpClient.setHost(proxyHost)
				.setPort(proxyPort);
		} else {
			httpClient.setHost(redmineHost)
				.setPort(redminePort);
		}
		log.info("[Support] proxyHost: "+proxyHost);
		log.info("[Support] proxyPort: "+proxyPort);

		redmineApiKey = config.getString("bug-tracker-api-key", null);
		if (redmineApiKey == null || redmineApiKey.trim().isEmpty()) {
			log.error("Module property 'bug-tracker-api-key' must be defined");
		}

		redmineProjectId = config.getInteger("bug-tracker-projectid", -1);
		if(redmineProjectId == -1) {
			log.error("Module property 'bug-tracker-projectid' must be defined");
		}

		httpClient.setMaxPoolSize(config.getInteger("escalation-httpclient-maxpoolsize",  16))
			.setKeepAlive(config.getBoolean("escalation-httpclient-keepalive", false))
			.setTryUseCompression(config.getBoolean("escalation-httpclient-tryusecompression", true))
			.exceptionHandler(new Handler<Throwable>() {
				@Override
				public void handle(Throwable t) {
					log.error("Error in redmine escalation httpClient", t);
				}
			});

		Long delayInMinutes = config.getLong("refresh-period", 30);
		log.info("[Support] Data will be pulled from Redmine every "+delayInMinutes+" minutes");
		final Long delay = TimeUnit.MILLISECONDS.convert(delayInMinutes, TimeUnit.MINUTES);

		ticketService.getLastIssuesUpdate(new Handler<Either<String, JsonArray>>() {
			@Override
			public void handle(Either<String, JsonArray> event) {
				final String lastUpdate;
				if(event.isRight() && event.right().getValue() != null) {
					JsonObject jo = (JsonObject) event.right().getValue().get(0);

					String date = jo.getString("last_update", null);
					log.info("[Support] Last pull from Redmine : "+date);
					lastUpdate = date;
				}
				else {
					lastUpdate = null;
				}

				vertx.setPeriodic(delay, new Handler<Long>() {
					// initialize last update with value from database
					String lastUpdateTime = lastUpdate;

					@Override
					public void handle(Long timerId) {
						Date currentDate = new Date();
						log.debug("Current date : " + currentDate.toString());
						DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

						EscalationServiceRedmineImpl.this.pullDataAndUpdateIssues(lastUpdateTime);
						lastUpdateTime = df.format(currentDate);
						log.debug("New value of lastUpdateTime : "+lastUpdateTime);
					}
				});
			}
		});



	}



	/**
	 * 	@inheritDoc
	 */
	@Override
	public void escalateTicket(final HttpServerRequest request, final JsonObject ticket,
			final JsonArray comments, final JsonArray attachmentsIds,
			final ConcurrentMap<Integer, String> attachmentMap,
			final Handler<Either<String, JsonObject>> handler) {

		/*
		 * Escalation steps
		 * 1) if there are attachments, upload each attachement. Redmine returns a token for each attachement
		 * 2) create the issue with all its attachments
		 * 3) update the issue with all comments
		 */
		final JsonArray attachments = new JsonArray();

		if(attachmentsIds != null && attachmentsIds.size() > 0) {
			final AtomicInteger successfulUploads = new AtomicInteger(0);
			final AtomicInteger failedUploads = new AtomicInteger(0);

			for (Object o : attachmentsIds) {
				if(!(o instanceof String)) continue;
				final String attachmentId = (String) o;

				// read attachment from workspace, and upload it to redmine
				wksHelper.readDocument(attachmentId, new Handler<WorkspaceHelper.Document>() {
					@Override
					public void handle(final Document file) {
						try {
							final String filename = file.getDocument().getString("name");
							final String contentType = file.getDocument().getObject("metadata").getString("content-type");

							EscalationServiceRedmineImpl.this.uploadAttachment(file.getData(), new Handler<HttpClientResponse>() {
								@Override
								public void handle(final HttpClientResponse resp) {

									resp.bodyHandler(new Handler<Buffer>() {
										@Override
										public void handle(final Buffer event) {
											if(resp.statusCode() == 201) {
												// Response from redmine is for instance {"upload":{"token":"781.687411f12da55bbd5a3d991675ac2135"}}
												JsonObject response = new JsonObject(event.toString());
												String token = response.getObject("upload").getString("token");
												String attachmentIdInRedmine = token.substring(0, token.indexOf('.'));
												attachmentMap.put(Integer.valueOf(attachmentIdInRedmine),
														file.getDocument().getString("_id"));

												JsonObject attachment = new JsonObject().putString("token", token)
														.putString("filename", filename)
														.putString("content_type", contentType);
												attachments.addObject(attachment);

												// Create redmine issue only if all attachments have been uploaded successfully
												if (successfulUploads.incrementAndGet() == attachmentsIds.size()) {
													EscalationServiceRedmineImpl.this.createIssue(request, ticket,
															getCreateIssueHandler(request, comments, handler), attachments);
												}
											}
											else {
												log.error("Error during escalation. Could not upload attachment to Redmine. Response status is "
															+ resp.statusCode() + " instead of 201.");
												log.error(event.toString());

												// Return error message as soon as one upload failed
												if(failedUploads.incrementAndGet() == 1) {
													handler.handle(new Either.Left<String, JsonObject>("support.escalation.error.upload.attachment.failed"));
												}
											}
										}
									});
								}
							});
						} catch (Exception e) {
							log.error("Error when processing response from readDocument", e);
						}

					}
				});

			}
		}
		else {
			this.createIssue(request, ticket, getCreateIssueHandler(request, comments, handler), attachments);
		}

	}

	private Handler<HttpClientResponse> getCreateIssueHandler(final HttpServerRequest request,
			final JsonArray comments, final Handler<Either<String, JsonObject>> handler) {

		return new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(final Buffer data) {
						if(resp.statusCode() == 201) { // Issue creation was successful
							try {
								final JsonObject response = new JsonObject(data.toString());
								if(comments == null || comments.size() == 0) {
									handler.handle(new Either.Right<String, JsonObject>(response));
									return;
								}

								// Add all comments to the redmine issue
								Number issueId = EscalationServiceRedmineImpl.this.extractIdFromIssue(response);
								EscalationServiceRedmineImpl.this.updateIssue(issueId, aggregateComments(request, comments),
										getUpdateIssueHandler(response, handler));

							} catch (Exception e) {
								log.error("Redmine issue was created. Error when trying to update it, i.e. when adding comment", e);
								handler.handle(new Either.Left<String, JsonObject>("support.escalation.error"));
							}
						}
						else {
							log.error("Error during escalation. Could not create redmine issue. Response status is " + resp.statusCode() + " instead of 201.");
							log.error(data.toString());
							handler.handle(new Either.Left<String, JsonObject>("support.escalation.error"));
						}
					}
				});
			}
		};

	}

	private Handler<HttpClientResponse> getUpdateIssueHandler(final JsonObject response,
			final Handler<Either<String, JsonObject>> handler) {

		return new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse event) {
				event.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						if(event.statusCode() == 200) {
							handler.handle(new Either.Right<String, JsonObject>(response));
						}
						else {
							log.error("Error during escalation. Could not update redmine issue to add comment. Response status is "
									+ event.statusCode() + " instead of 200.");
							log.error(buffer.toString());
							handler.handle(new Either.Left<String, JsonObject>("support.error.escalation.incomplete"));
						}
					}
				});
			}
		};
	}


	/*
	 * Return a JsonObject containing all comments
	 */
	private JsonObject aggregateComments(final HttpServerRequest request, JsonArray comments) {
		JsonObject result = new JsonObject();
		if(comments != null && comments.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (Object o : comments) {
				if(!(o instanceof JsonObject)) continue;
				JsonObject c = (JsonObject) o;

				sb.append(c.getString("owner_name")).append(", ");

				String onDate = I18n.getInstance().translate("support.on", I18n.acceptLanguage(request));
				sb.append(onDate).append(" ").append(c.getString("created"));

				sb.append("\n\n").append(c.getString("content")).append("\n\n");
			}

			result.putString("content", sb.toString());
		}

		return result;
	}

	private void uploadAttachment(final Buffer data, final Handler<HttpClientResponse> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_UPLOAD_ATTACHMENT_PATH) : REDMINE_UPLOAD_ATTACHMENT_PATH;

		httpClient.post(url, handler)
				.putHeader(HttpHeaders.HOST, redmineHost)
				.putHeader(HttpHeaders.CONTENT_TYPE, "application/octet-stream")
				.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(data.length()))
				.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
				.write(data)
				.end();
	}


	private void createIssue(final HttpServerRequest request, final JsonObject ticket, final Handler<HttpClientResponse> handler, JsonArray attachments) {

		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		JsonObject data = new JsonObject()
			.putNumber("project_id", redmineProjectId)
			.putString("subject", ticket.getString("subject"));

		// add ticket id, application name, school id to description
		String applicationLabel = I18n.getInstance().translate("support.escalated.ticket.application", I18n.acceptLanguage(request));
		String ticketOwnerLabel = I18n.getInstance().translate("support.escalated.ticket.ticket.owner", I18n.acceptLanguage(request));
		String ticketIdLabel = I18n.getInstance().translate("support.escalated.ticket.ticket.id", I18n.acceptLanguage(request));
		String schoolIdLabel = I18n.getInstance().translate("support.escalated.ticket.school.id", I18n.acceptLanguage(request));

		StringBuilder description = new StringBuilder();
		this.appendDataToDescription(description, applicationLabel, ticket.getString("category").substring(1));
		this.appendDataToDescription(description, ticketOwnerLabel, ticket.getString("owner_name"));
		this.appendDataToDescription(description, ticketIdLabel, ticket.getNumber("id").toString());
		this.appendDataToDescription(description, schoolIdLabel, ticket.getString("school_id"));
		description.append("\n").append(ticket.getString("description"));

		data.putString("description", description.toString());


		if(attachments != null && attachments.size() > 0) {
			data.putArray("uploads", attachments);
		}

		JsonObject issue = new JsonObject().putObject("issue", data);

		Buffer buffer = new Buffer();
		buffer.appendString(issue.toString());

		httpClient.post(url, handler)
				.putHeader(HttpHeaders.HOST, redmineHost)
				.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
				.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
				.write(buffer).end();
	}

	private void appendDataToDescription(final StringBuilder description, final String label, final String value) {
		description.append(label).append(": ")
			.append(value).append("\n");
	}


	private void updateIssue(final Number issueId, final JsonObject comment, final Handler<HttpClientResponse> handler) {
		String path = "/issues/" + issueId + ".json";
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + path) : path;

		JsonObject data = new JsonObject()
			.putString("notes", comment.getString("content"));
		JsonObject ticket = new JsonObject().putObject("issue", data);

		Buffer buffer = new Buffer().appendString(ticket.toString());

		httpClient.put(url, handler)
			.putHeader(HttpHeaders.HOST, redmineHost)
			.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
			.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
			.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
			.write(buffer)
			.end();
	}


	private void listIssues(final String since, final int offset, final int limit, final Handler<Either<String, JsonObject>> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		StringBuilder query = new StringBuilder("?status_id=*"); // return open and closed issues
		if(since != null) {
			// TODO : remove following line when switching to redmine 2.5.0 (feature http://www.redmine.org/issues/8842 enables to fetch created/updated issues after a specific timestamp.)
			String date = since.substring(0, 10); // keep only "yyyy-MM-dd"

			// updated_on : fetch issues updated after a certain date
			query.append("&updated_on=%3E%3D").append(date);
			/* "%3E%3D" is ">=" with hex-encoding.
			 * According to http://www.redmine.org/projects/redmine/wiki/Rest_Issues : operators containing ">", "<" or "=" should be hex-encoded
			 */
		}
		if(offset > -1) {
			// offset: skip this number of issues in response
			query.append("&offset=").append(offset);
		}
		if(limit > 0) {
			// limit: number of issues per page
			query.append("&limit=").append(limit);
		}
		url += query.toString();
		log.info("Url used to list redmine issues : "+url);

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						JsonObject response = new JsonObject(data.toString());
						if(resp.statusCode() == 200) {
							handler.handle(new Either.Right<String, JsonObject>(response));
						}
						else {
							log.error("Error when listing redmine tickets. Response status is "
									+ resp.statusCode() + " instead of 200.");
							handler.handle(new Either.Left<String, JsonObject>(response.toString()));
						}
					}
				});
			}
		})
		.putHeader(HttpHeaders.HOST, redmineHost)
		.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
		.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.end();
	}


	private void pullDataAndUpdateIssues(final String since) {
		this.pullDataAndUpdateIssues(since, -1, -1, true);
	}

	private void pullDataAndUpdateIssues(final String lastUpdateTime, final int offset, final int limit, final boolean allowRecursiveCall) {
		/*
		 * Steps :
		 * 1) list Redmine issues that have been created/updated since last time
		 *
		 * 2) for each issue,
		 * i/ get the "whole" issue (i.e. with its attachments' metadata and with its comments).
		 * ii/ update the issue in Postgresql, so that local administrators can see the last changes
		 * iii/ If there are "new" attachments, download them, store them in gridfs and store their metadata in postgresql
		 *
		 */
		log.debug("Value of since : "+lastUpdateTime);

		this.listIssues(lastUpdateTime, offset, limit, new Handler<Either<String, JsonObject>>() {
			@Override
			public void handle(Either<String, JsonObject> event) {
				if(event.isRight()) {
					try {
						JsonArray issues = event.right().getValue().getArray("issues", null);
						if (issues != null && issues.size() > 0) {
							final AtomicInteger remaining = new AtomicInteger(issues.size());

							if(allowRecursiveCall) {
								int aTotalCount = event.right().getValue().getInteger("total_count", -1);
								int aOffset = event.right().getValue().getInteger("offset", -1);
								int aLimit = event.right().getValue().getInteger("limit", -1);
								if(aTotalCount!=-1 && aOffset!=-1 && aLimit!=-1 && (aTotalCount > aLimit)) {
									// Second call to get remaining issues
									EscalationServiceRedmineImpl.this.pullDataAndUpdateIssues(lastUpdateTime, aLimit, aTotalCount - aLimit, false);
								}
							}

							for (Object o : issues) {
								if(!(o instanceof JsonObject)) continue;
								JsonObject jo = (JsonObject) o;
								final Number issueId = jo.getNumber("id");

								EscalationServiceRedmineImpl.this.getIssue(issueId, new Handler<Either<String, JsonObject>>() {
									@Override
									public void handle(Either<String, JsonObject> event) {
										if(event.isRight()) {
											JsonObject issue = event.right().getValue();

											// update issue in postgresql
											ticketService.updateIssue(issueId, issue.toString(), new Handler<Either<String, JsonObject>>() {
												@Override
												public void handle(Either<String, JsonObject> event) {
													if(event.isRight()) {
														log.info("pullAndUpdate OK for issue n°"+issueId);
														if(remaining.decrementAndGet() < 1) {
															log.info("pullAndUpdate OK for all issues !");
														}
													}
													else {
														log.error("pullAndSynchronize FAILED. Error when updating issue n°"+issueId);
													}
												}
											});


											// If "new" attachments have been added in Redmine, download them
											final JsonArray redmineAttachments = issue.getObject("issue").getArray("attachments", null);
											if(redmineAttachments != null && redmineAttachments.size() > 0) {
												// get ids of attachments already stored in ENT
												ticketService.getIssueAttachmentsIds(issueId, new Handler<Either<String, JsonArray>>() {
													@Override
													public void handle(Either<String, JsonArray> event) {
														if(event.isRight()) {
															JsonArray response = event.right().getValue();
															JsonObject jo = response.get(0);
															String ids = jo.getString("ids", null);
															JsonArray existingAttachmentsIds = (ids!=null) ? new JsonArray(ids) : null;

															if(existingAttachmentsIds != null && existingAttachmentsIds.size() > 0) {
																log.debug("Attachments already stored in ENT : "+existingAttachmentsIds.toString());
																for (Object o : redmineAttachments) {
																	if(!(o instanceof JsonObject)) continue;
																	final JsonObject attachment = (JsonObject) o;
																	final Number attachmentIdInRedmine = attachment.getNumber("id");

																	if(!existingAttachmentsIds.contains(attachmentIdInRedmine)) {
																		final String attachmentUrl = attachment.getString("content_url");
																		EscalationServiceRedmineImpl.this.doDownloadAttachment(attachmentUrl, attachment, issueId);
																	}
																}
															}
															else {
																for (Object o : redmineAttachments) {
																	if(!(o instanceof JsonObject)) continue;
																	final JsonObject attachment = (JsonObject) o;
																	final String attachmentUrl = attachment.getString("content_url");

																	EscalationServiceRedmineImpl.this.doDownloadAttachment(attachmentUrl, attachment, issueId);
																}
															}

														}
														else {
															log.error("Error when calling service getIssueAttachmentsIds. New attachments have not been downloaded for issue n°"+issueId);
														}
													}
												});
											}



										}
										else {
											log.error(event.left().getValue());
										}
									}
								});
							}
						}

					} catch (Exception e) {
						log.error("Service pullAndSynchronizeTickets : error after listing issues", e);
					}
				}
				else {
					log.error("Error when listing issues. " + event.left().getValue());
				}
			}

		});
	}

	private void doDownloadAttachment(final String attachmentUrl, final JsonObject attachment, final Number issueId) {
		final Number attachmentIdInRedmine = attachment.getNumber("id");

		EscalationServiceRedmineImpl.this.downloadAttachment(attachmentUrl, new Handler<Buffer>() {
			@Override
			public void handle(Buffer data) {
				// store attachment in gridfs
				FileUtils.gridfsWriteBuffer(data, attachment.getString("content_type", ""), attachment.getString("filename"), eb, new Handler<JsonObject>() {
					@Override
					public void handle(JsonObject attachmentMetaData) {
						/* Response example from gridfsWriteBuffer :
						 * {"_id":"f62f5dac-b32b-4cb8-b70a-1016885f37ec","status":"ok","metadata":{"content-type":"image/png","filename":"test_pj.png","size":118639}}
						 */
						log.info("Metadata of attachment written in gridfs: "+attachmentMetaData.encodePrettily());
						attachmentMetaData.putNumber("id_in_bugtracker", attachmentIdInRedmine);

						// store attachment's metadata in postgresql
						ticketService.insertIssueAttachment(issueId, attachmentMetaData, new Handler<Either<String, JsonArray>>() {
							@Override
							public void handle(Either<String, JsonArray> event) {
								if(event.isRight()) {
									log.info("download attachment "+ attachmentIdInRedmine + " OK for issue n°"+issueId);
								}
								else {
									log.error("download attachment "+ attachmentIdInRedmine + " FAILED for"+issueId+". Error when trying to insert metadata in postgresql");
								}
							}
						});
					}
				});
			}
		});
	}


	@Override
	public void getIssue(final Number issueId, final Handler<Either<String, JsonObject>> handler) {
		String path = "/issues/" + issueId + ".json?include=journals,attachments";
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + path) : path;

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						JsonObject response = new JsonObject(data.toString());
						if(resp.statusCode() == 200) {
							handler.handle(new Either.Right<String, JsonObject>(response));
						}
						else {
							log.error("Error when getting a redmine ticket. Response status is "
									+ resp.statusCode() + " instead of 200.");
							log.error(response.toString());
							handler.handle(new Either.Left<String, JsonObject>("support.error.comment.added.to.escalated.ticket.but.synchronization.failed"));
						}
					}
				});
			}
		})
		.putHeader(HttpHeaders.HOST, redmineHost)
		.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
		.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.end();
	}

	/**
	 * @param attachmentUrl : attachment URL given by Redmine, e.g. "http://support.web-education.net/attachments/download/784/test_pj.png"
	 */
	private void downloadAttachment(final String attachmentUrl, final Handler<Buffer> handler) {
		String url = proxyIsDefined ? attachmentUrl : attachmentUrl.substring(attachmentUrl.indexOf(redmineHost) + redmineHost.length());

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						handler.handle(data);
					}
				});
			}
		})
		.putHeader(HttpHeaders.HOST, redmineHost)
		.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
		.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.end();
	}

	@Override
	public void commentIssue(Number issueId, JsonObject comment, final Handler<Either<String,JsonObject>> handler) {

		this.updateIssue(issueId, comment, new Handler<HttpClientResponse>() {
			@Override
			public void handle(final HttpClientResponse event) {
				event.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer buffer) {
						if(event.statusCode() == 200) {
							handler.handle(new Either.Right<String, JsonObject>(new JsonObject()));
						}
						else {
							log.error("Error : could not update redmine issue to add comment. Response status is "
									+ event.statusCode() + " instead of 200.");
							log.error(buffer.toString());
							handler.handle(new Either.Left<String, JsonObject>("support.error.comment.has.not.been.added.to.escalated.ticket"));
						}
					}
				});
			}
		});

	}

	@Override
	public Number extractIdFromIssue(JsonObject issue) {
		return issue.getObject("issue").getNumber("id");
	}


}
