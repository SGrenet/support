package net.atos.entng.support.services;

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

public class EscalationServiceRedmineImpl implements EscalationService {

	private Logger log;

	private HttpClient httpClient;
	private String redmineHost;
	private int redminePort;
	private boolean proxyIsDefined;

	private WorkspaceHelper wksHelper;

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
		log = logger;
		httpClient = vertx.createHttpClient();
		wksHelper = new WorkspaceHelper(container.config().getString("gridfs-address", "wse.gridfs.persistor"), eb);

		String proxyHost = System.getProperty("http.proxyHost", null);
		int proxyPort = 80;
		try {
			proxyPort = Integer.valueOf(System.getProperty("http.proxyPort", "80"));
		} catch (NumberFormatException e) {
			log.error("JVM property 'http.proxyPort' must be an integer", e);
		}

		redmineHost = container.config().getString("bug-tracker-host", null);
		if (redmineHost == null || redmineHost.trim().isEmpty()) {
			log.error("Module property 'bug-tracker-host' must be defined");
		}
		redminePort = container.config().getInteger("bug-tracker-port", 80);

		if (proxyHost != null && !proxyHost.trim().isEmpty()) {
			proxyIsDefined = true;
			httpClient.setHost(proxyHost)
				.setPort(proxyPort);
		} else {
			httpClient.setHost(redmineHost)
				.setPort(redminePort);
		}

		redmineApiKey = container.config().getString("bug-tracker-api-key", null);
		if (redmineApiKey == null || redmineApiKey.trim().isEmpty()) {
			log.error("Module property 'bug-tracker-api-key' must be defined");
		}

		// TODO : maxPoolSize and keepAlive should be configurable
		httpClient.setMaxPoolSize(16)
			.setKeepAlive(false)
		// TODO : .exceptionHandler(handler)
		;
	}

	@Override
	public void escalateTicket(final HttpServerRequest request, final JsonObject ticket,
			final JsonArray comments, final JsonArray attachments, final Handler<JsonObject> handler) {

		/*
		 * TODO
		 * 1) if there are attachments, upload each attachement. Redmine returns a token for each attachement
		 * 2) create the issue with all its attachments
		 * 3) update the issue with each comment
		 */


		// 1) if there are attachments, upload each attachement. Redmine returns a token for each attachement

		// temporary code - try to upload file to redmine
//		wksHelper.readDocument("ee40b029-2ef5-45e7-af29-76f8f6cf095d", new Handler<WorkspaceHelper.Document>() {
//			@Override
//			public void handle(Document event) {
//				JsonObject doc = event.getDocument();
//				Buffer data = event.getData();
//
//				EscalationServiceRedmineImpl.this.uploadAttachment(data, new Handler<HttpClientResponse>() {
//					@Override
//					public void handle(HttpClientResponse resp) {
//						resp.bodyHandler(new Handler<Buffer>() {
//							@Override
//							public void handle(Buffer event) {
//								log.info(event.toString());
//								JsonObject response = new JsonObject(event.toString());
//								String token = response.getObject("upload").getString("token");
//								handler.handle(response);
//							}
//						});
//					}
//				});
//			}
//		});

		this.createIssue(ticket, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				// TODO : check status. Should be 201

				resp.bodyHandler(new Handler<Buffer>() {
					@Override
					public void handle(Buffer data) {
						// TODO : if status 201, then update the issue with each comment

						JsonObject response = new JsonObject(data.toString());
						handler.handle(response);
					}
				});
			}
		});

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


	private void createIssue(final JsonObject ticket, final Handler<HttpClientResponse> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		JsonObject data = new JsonObject()
			.putNumber("project_id", 39) // TODO : put project_id and priority_if in module conf
			.putNumber("priority_id", 10)
			.putString("subject", ticket.getString("subject"))
			.putString("description", ticket.getString("description"));
		// TODO : add application name and school name to description

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

		httpClient.get(attachmentUrl, new Handler<HttpClientResponse>() {
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
