package net.atos.entng.support.services;


import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;

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

public class EscalationServiceRedmineImpl implements EscalationService {

	private final Logger log;

	private final HttpClient httpClient;
	private final String redmineHost;
	private final int redminePort;
	private boolean proxyIsDefined;
	private final int redmineProjectId;

	private final WorkspaceHelper wksHelper;

	/*
	 * According to http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication :
	 * API key is a handy way to avoid putting a password in a script.
	 * You can find your API key on your account page ( /my/account ) when logged in, on the right-hand pane of the default layout.
	 */
	private static final String HEADER_REDMINE_API_KEY = "X-Redmine-API-Key";
	private String redmineApiKey;

	private static final String REDMINE_ISSUES_PATH = "/issues.json";
	private static final String REDMINE_UPLOAD_ATTACHMENT_PATH = "/uploads.json";


	public EscalationServiceRedmineImpl(Vertx vertx, Container container, Logger logger, EventBus eb) {
		JsonObject config = container.config();
		log = logger;
		httpClient = vertx.createHttpClient();
		wksHelper = new WorkspaceHelper(config.getString("gridfs-address", "wse.gridfs.persistor"), eb);

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
	}

	@Override
	public void escalateTicket(final HttpServerRequest request, final JsonObject ticket,
			final JsonArray comments, final JsonArray attachments, final Handler<Either<String, JsonObject>> handler) {

		/*
		 * Escalation steps
		 * 1) if there are attachments, upload each attachement. Redmine returns a token for each attachement
		 * 2) create the issue with all its attachments
		 * 3) update the issue with all comments
		 */

		final ConcurrentMap<String, JsonObject> attachmentMap = new ConcurrentHashMap<>();

		if(attachments != null && attachments.size() > 0) {
			final AtomicInteger remaining = new AtomicInteger(attachments.size());

			for (Object o : attachments) {
				if(!(o instanceof String)) continue;
				final String attachmentId = (String) o;

				// read attachment from workspace, and upload it to redmine
				wksHelper.readDocument(attachmentId, new Handler<WorkspaceHelper.Document>() {
					@Override
					public void handle(final Document file) {
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

											JsonObject attachment = new JsonObject().putString("token", token)
													.putString("filename", filename)
													.putString("content_type", contentType);

											attachmentMap.put(attachmentId, attachment);

											if (remaining.decrementAndGet() < 1 && !attachmentMap.isEmpty()) {
												EscalationServiceRedmineImpl.this.createIssue(ticket,
														getCreateIssueHandler(comments, handler), attachmentMap);
											}
										}
										else {
											log.error("Error during escalation. Could not upload attachment to Redmine. Response status is "
														+ resp.statusCode() + " instead of 201.");
											log.info(event.toString());

											// TODO : i18n for error message
											handler.handle(new Either.Left<String, JsonObject>("Error during escalation. Could not upload attachment to Redmine"));
										}
									}
								});
							}
						});
					}
				});

			}
		}
		else {
			this.createIssue(ticket, getCreateIssueHandler(comments, handler), attachmentMap);
		}

	}

	private Handler<HttpClientResponse> getCreateIssueHandler(final JsonArray comments,
			final Handler<Either<String, JsonObject>> handler) {

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
								Integer issueId = (Integer) response.getObject("issue").getNumber("id");
								EscalationServiceRedmineImpl.this.updateIssue(issueId, aggregateComments(comments),
										getUpdateIssueHandler(response, handler));

							} catch (Exception e) {
								log.error("Redmine issue was created. Error when trying to update it, i.e. when adding comment", e);

								// TODO : i18n for error message
								handler.handle(new Either.Left<String, JsonObject>("Error during escalation. Could not create redmine issue"));
							}
						}
						else {
							log.error("Error during escalation. Could not create redmine issue. Response status is " + resp.statusCode() + " instead of 201.");
							log.info(data.toString());

							// TODO : i18n for error message
							handler.handle(new Either.Left<String, JsonObject>("Error during escalation. Could not create redmine issue"));
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
							// TODO : add comments to "response"
							handler.handle(new Either.Right<String, JsonObject>(response));
						}
						else {
							log.error("Error during escalation. Could not update redmine issue to add comment. Response status is "
									+ event.statusCode() + " instead of 200.");
							log.info(buffer.toString());

							// TODO : i18n for error message
							handler.handle(new Either.Left<String, JsonObject>("Error during escalation. Could not update redmine issue"));
						}
					}
				});
			}
		};
	}


	/*
	 * Return a JsonObject containing all comments
	 */
	private JsonObject aggregateComments(JsonArray comments) {
		JsonObject result = new JsonObject();
		if(comments != null && comments.size() > 0) {
			StringBuilder sb = new StringBuilder();
			for (Object o : comments) {
				if(!(o instanceof JsonObject)) continue;
				JsonObject c = (JsonObject) o;
				sb.append("Le ").append(c.getString("created"))
					.append("\n")
					.append(c.getString("content"))
					.append("\n\n");
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


	private void createIssue(final JsonObject ticket, final Handler<HttpClientResponse> handler,
			 ConcurrentMap<String, JsonObject> attachmentMap) {

		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		JsonObject data = new JsonObject()
			.putNumber("project_id", redmineProjectId)
			.putString("subject", ticket.getString("subject"))
			.putString("description", ticket.getString("description"));
		// TODO : add application name and school name to description

		JsonArray uploads = new JsonArray();
		for (JsonObject attachment : attachmentMap.values()) {
			uploads.add(attachment);
		}
		if(uploads.size() > 0) {
			data.putArray("uploads", uploads);
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

	private void updateIssue(final int issueId, final JsonObject comment, final Handler<HttpClientResponse> handler) {
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


	@Override
	public void listTickets(final Handler<JsonObject> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						JsonObject response = new JsonObject(data.toString());
						handler.handle(response);
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
	public void getTicket(final int issueId, final Handler<JsonObject> handler) {
		String path = "/issues/" + issueId + ".json?include=journals,attachments";
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + path) : path;

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						JsonObject response = new JsonObject(data.toString());
						// TODO : download attachments
						// temporary code
						EscalationServiceRedmineImpl.this.downloadAttachment("http://support.web-education.net/attachments/download/784/test_pj.png", handler);
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
	private void downloadAttachment(final String attachmentUrl, final Handler<JsonObject> handler) {
		String url = proxyIsDefined ? attachmentUrl : attachmentUrl.substring(attachmentUrl.indexOf(redmineHost) + redmineHost.length());

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						// TODO : store attachment in gridfs, and store attachment's id in postgresql
						handler.handle(new JsonObject());
					}
				});
			}
		})
		.putHeader(HttpHeaders.HOST, redmineHost)
		.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
		.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
		.end();
	}

}
