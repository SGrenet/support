package net.atos.entng.support.services;

import org.vertx.java.core.Handler;
import org.vertx.java.core.Vertx;
import org.vertx.java.core.buffer.Buffer;
import org.vertx.java.core.http.HttpClient;
import org.vertx.java.core.http.HttpClientRequest;
import org.vertx.java.core.http.HttpClientResponse;
import org.vertx.java.core.http.HttpHeaders;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.platform.Container;

public class EscalationServiceRedmineImpl implements EscalationService {

	private HttpClient httpClient;
	private Logger log;
	private boolean proxyIsDefined;
	private String redmineHost;
	private int redminePort;

	/*
	 * According to http://www.redmine.org/projects/redmine/wiki/Rest_api#Authentication :
	 * API key is a handy way to avoid putting a password in a script.
	 * You can find your API key on your account page ( /my/account ) when logged in, on the right-hand pane of the default layout.
	 */
	private static final String HEADER_REDMINE_API_KEY = "X-Redmine-API-Key";
	private String redmineApiKey;

	private static final String REDMINE_ISSUES_PATH = "/issues.json";

	public EscalationServiceRedmineImpl(Vertx vertx, Container container, Logger logger) {
		log = logger;
		httpClient = vertx.createHttpClient();

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

		httpClient.setMaxPoolSize(16)
			.setKeepAlive(false)
		// TODO : .exceptionHandler(handler)
		;
	}

	@Override
	public void escalateTicket(final HttpServerRequest request,
			final Handler<JsonObject> handler) {

		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		HttpClientRequest escalateRequest = httpClient.post(url,
			new Handler<HttpClientResponse>() {
				@Override
				public void handle(HttpClientResponse resp) {
					log.info("Got a response: " + resp.statusCode());
					// TODO : check status. Should be 201

					resp.bodyHandler(new Handler<Buffer>() {
						@Override
						public void handle(Buffer data) {
							JsonObject response = new JsonObject(data.toString());
							handler.handle(response);
						}
					});
				}
			});

		// TODO : remove temporary code used for test
		JsonObject data = new JsonObject()
			.putNumber("project_id", 39)
			.putString("subject", "Création d'une demande avec org.vertx.java.core.http.HttpClient")
			.putNumber("priority_id", 10)
			.putString("description", "Description de la demande créée avec org.vertx.java.core.http.HttpClient");
		JsonObject ticket = new JsonObject().putObject("issue", data);

		Buffer buffer = new Buffer();
		buffer.appendString(ticket.toString());

		escalateRequest
				.putHeader(HttpHeaders.HOST, redmineHost)
				.putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
				.putHeader(HttpHeaders.CONTENT_LENGTH, String.valueOf(buffer.length()))
				.putHeader(HEADER_REDMINE_API_KEY, redmineApiKey)
				.write(buffer).end();
	}

	@Override
	public void getTickets(final Handler<JsonObject> handler) {
		String url = proxyIsDefined ? ("http://" + redmineHost + ":" + redminePort + REDMINE_ISSUES_PATH) : REDMINE_ISSUES_PATH;

		httpClient.get(url, new Handler<HttpClientResponse>() {
			@Override
			public void handle(HttpClientResponse resp) {
				log.info("Got a response: " + resp.statusCode());
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

}
