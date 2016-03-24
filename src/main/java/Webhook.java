import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import static spark.Spark.*;

public class Webhook {

	public static void main(String[] args) {

		port(Integer.valueOf(System.getenv("PORT")));
		staticFileLocation("/tmp/files");
		staticFileLocation("/public");

		get("/", (request, response) -> {
			String op = request.queryParams("op");
			if (op != null && op != "") {
				switch (op) {
				case "send1":
					return doSend1(request.host());
				case "status":
					String envelopeId = request.queryParams("envelope_id");
					return doStatus(envelopeId);
				case "status_items":
					return doAjax(op, request.body());
				}
			} else {
				return showOptions();
			}
			return response;
		});

		post("/", (request, response) -> {
			String op = request.queryParams("op");
			if (op != null && op != "") {
				switch (op) {
				case "send2":
					response.header("Content-type", "application/json");
					return doSend2(request.body());
				case "status_items":
					return doAjax(op, request.body());
				case "status_info":
					return doAjax(op, request.body());
				case "webhook":
					webhook(request.body());
					break;
				}
			}
			return "";
		});

	}

	private static String showOptions() {
		StringBuilder sb = new StringBuilder();

		sb.append(showHeader());
		sb.append(showWelcome());
		sb.append(showFooter("{\"navbar\": \"li_home\"}"));

		return sb.toString();
	}

	private static String showFooter(String params) {
		StringBuilder sb = new StringBuilder();

		sb.append("\n<script>ds_params = " + params + ";</script>\n");
		sb.append(
				"</div><!-- /.container -->\r\n\r\n<!-- Mustache template for toc entries -->\r\n<!-- See https://github.com/janl/mustache.js -->\r\n<script id=\"toc_item_template\" type=\"x-tmpl-mustache\">\r\n<li class=\"toc_item\">\r\n\t<h4 class=\"{{envelope_status_class}}\">Envelope: {{envelope_status}}</h4>\r\n\t{{#recipients}}\r\n\t\t<p>{{type}}: {{user_name}}<br/>\r\n\t\t\t<span class=\"{{status_class}}\">Status: {{status}}</span>\r\n\t\t</p>\r\n\t{{/recipients}}\r\n</li>\r\n</script>\r\n\r\n<!-- Mustache template for displaying xml file -->\r\n<!-- XML in Ace editor, see http://stackoverflow.com/a/16147926/64904 -->\r\n<script id=\"xml_file_template\" type=\"x-tmpl-mustache\">\r\n\t<h3>XML Notification Content</h3>\r\n\t<h5 class=\"{{envelope_status_class}}\">Envelope status: {{envelope_status}}</h5>\r\n\t<p class=\"margintop\">Recipients</p>\r\n\t<ul>\r\n\t{{#recipients}}\r\n\t\t<li>{{type}}: {{user_name}} <span class=\"{{status_class}}\">Status: {{status}}</span></li>\r\n\t{{/recipients}}\r\n\t</ul>\r\n\t<h5><a href='{{xml_url}}' target='_blank'>Download the XML file</a></h5>\r\n\t{{#documents.length}}\r\n\t\t<p>Documents<ul>\r\n\t\t\t{{#documents}}\r\n\t\t\t\t<li>Document ID {{document_ID}}: {{name}} <a href='{{url}}' target='_blank'>Download</a></li>\r\n\t\t\t{{/documents}}\r\n\t\t</ul></p>\r\n\t{{/documents.length}}\r\n</script>\r\n\r\n<!-- Bootstrap core JavaScript -->\r\n<script src=\"https://ajax.googleapis.com/ajax/libs/jquery/1.11.3/jquery.min.js\"></script>\r\n<!-- Latest compiled and minified JavaScript -->\r\n<script src=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/js/bootstrap.min.js\" integrity=\"sha384-0mSbJDEHialfmuBBQP6A4Qrprq5OVfW37PRR3j5ELqxss1yVqOtnepnHVP9aJ7xS\" crossorigin=\"anonymous\"></script><!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->\r\n<script src=\"assets_master/ie10-viewport-bug-workaround.js\"></script>\r\n<script src=\"bower_components/mustache.js/mustache.min.js\"></script>\r\n<script src=\"https://cdn.jsdelivr.net/g/ace@1.2.2(noconflict/ace.js+noconflict/mode-xml.js+noconflict/theme-chrome.js)\"></script>\r\n\r\n<script src=\"010.webhook.js\"></script> <!-- nb. different assets directory -->\r\n</body>\r\n</html>");

		return sb.toString();
	}

	private static String showWelcome() {
		return "<div class=\"intro\">\r\n\t<h1>DocuSign Webhook Recipe</h1>\r\n\t<h2>No More Polling!</h2>\r\n\t<p class=\"lead\">Please use the navigation bar, above, to first send the signature request, then view the signature events via a webhook.</p>\r\n</div>";
	}

	private static String showHeader() {
		return "<!DOCTYPE html>\r\n<html lang=\"en\">\r\n  <head>\r\n    <meta charset=\"utf-8\">\r\n    <meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\">\r\n    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\r\n    <!-- The above 3 meta tags *must* come first in the head; any other head content must come *after* these tags -->\r\n    <link rel=\"icon\" href=\"https://www.docusign.com/sites/all/themes/custom/docusign/favicons/favicon.ico\">\r\n    <title>DocuSign Webhook recipe</title>\r\n\t<!-- Latest compiled and minified CSS -->\r\n\t<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap.min.css\" \tintegrity=\"sha384-1q8mTJOASx8j1Au+a5WDVnPi2lkFfwwEAa8hDDdjZlpLegxhjVME1fgjWPGmkzs7\" crossorigin=\"anonymous\">\r\n\r\n\t<!-- Optional theme -->\r\n\t<link rel=\"stylesheet\" href=\"https://maxcdn.bootstrapcdn.com/bootstrap/3.3.6/css/bootstrap-theme.min.css\" \tintegrity=\"sha384-fLW2N01lMqjakBkx3l/M9EahuwpSfeNvV63J5ezn3uZzapT0u7EYsXMjQV+0En5r\" crossorigin=\"anonymous\">\r\n\r\n    <!-- IE10 viewport hack for Surface/desktop Windows 8 bug -->\r\n    <link href=\"assets_master/ie10-viewport-bug-workaround.css\" rel=\"stylesheet\">\r\n    <link href=\"assets_master/recipes.css\" rel=\"stylesheet\">  <!-- Some custom styles -->\r\n    <!-- HTML5 shim and Respond.js for IE8 support of HTML5 elements and media queries -->\r\n    <!--[if lt IE 9]>\r\n      <script src=\"https://oss.maxcdn.com/html5shiv/3.7.2/html5shiv.min.js\"></script>\r\n      <script src=\"https://oss.maxcdn.com/respond/1.4.2/respond.min.js\"></script>\r\n    <![endif]-->\r\n  </head>\r\n  <body class=\"recipes\">\r\n    <nav class=\"navbar navbar-inverse navbar-fixed-top\">\r\n      <div class=\"container\">\r\n        <div class=\"navbar-header\">\r\n          <button type=\"button\" class=\"navbar-toggle collapsed\" data-toggle=\"collapse\" data-target=\"#navbar\" aria-expanded=\"false\" aria-controls=\"navbar\">\r\n            <span class=\"sr-only\">Toggle navigation</span>\r\n            <span class=\"icon-bar\"></span>\r\n            <span class=\"icon-bar\"></span>\r\n            <span class=\"icon-bar\"></span>\r\n          </button>\r\n          <a class=\"navbar-brand\" href=\"#\">DocuSign Webhook Recipe</a>\r\n        </div>\r\n        <div id=\"navbar\" class=\"collapse navbar-collapse\">\r\n          <ul class=\"nav navbar-nav\">\r\n            <li id=\"li_home\"><a href=\"/\">Home</a></li>\r\n            <li id=\"li_send\"><a href=\"/?op=send1\">Send Signature Request</a></li>\r\n          </ul>\r\n        </div><!--/.nav-collapse -->\r\n      </div>\r\n    </nav>\r\n\r\n    <div class=\"container\">\r\n      ";
	}

	private static String showStatus() {
		return "<h1>DocuSign Webhook Recipe: Envelope Status</h1>\r\n<div id=\"env_info\"></div>\r\n<div class=\"row fill\">\r\n\t<div class=\"wrapper\">\r\n  \t  <div id=\"status_left\" class=\"col-md-3\">\r\n\t\t  <p id=\"working\">Working</p>\r\n\t\t  <ul id=\"toc\" class=\"list-unstyled\"></ul>\r\n\t\t  <ul class=\"margintop\"><li>Click on an entry to view it</li><li>Italics indicate a change</li></ul>\r\n\t  </div>\r\n  \t  <div id=\"right_column\" class=\"col-md-9\">\r\n\t\t  <div id=\"xml_info\">\r\n  \t\t  \t<div id=\"countdown\"><h2>Waiting for first results\u2026 <span id=\"counter\"></span></h2></div>\r\n\t\t  </div>\r\n\t\t  <div id=\"editor\"></div>\r\n\t\t  <p class=\"margintop\">&nbsp;</p>\r\n\t  </div>\r\n\t<div>\r\n</div>";
	}

	private static void webhook(String data) {
		// An incoming call from the DocuSign platform
		// See the Connect guide:
		// https://www.docusign.com/sites/default/files/connect-guide_0.pdf
		WebhookLib webhookLib = new WebhookLib();
		webhookLib.webhookListener(data);
	}

	@SuppressWarnings("unchecked")
	private static String doAjax(String op, String params) {
		String result = "";
		if (params != null) {
			WebhookLib webhookLib = new WebhookLib();
			// The result includes the html for showing the View status button,
			// and more
			Map<String, String> map;
			try {
				map = new ObjectMapper().readValue(params, HashMap.class);
				switch (op) {
				case "status_items":
					return webhookLib.statusItems(map);
				case "status_info":
					return webhookLib.statusInfo(map);
				}
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			result = "{\"ok\": false, \"html\": \"<h2>Bad JSON input!</h2>\"}";
		}
		return result;

	}

	private static String doStatus(String envelopeId) {
		// Shows the empty status screen.
		// All the hard work is done in the 010.webhook.js file
		// Required query parameter: envelope_id
		StringBuilder sb = new StringBuilder();
		sb.append(showHeader());
		sb.append(showStatus());
		String envId = "";
		if (envelopeId != null && envelopeId != "") {
			envId = envelopeId;
		} else {
			envId = "false";
			sb.append("<h3>Missing envelope_id query parameter!</h3>");
		}
		sb.append(showFooter("{ \r\n \"status_envelope_id\": \"" + envId + "\", \r\n \"url\": \"/\" \r\n	}"));

		return sb.toString();
	}

	@SuppressWarnings("unchecked")
	private static String doSend2(String params) {
		String result = "";
		if (params != null) {
			WebhookLib webhookLib = new WebhookLib();
			// The result includes the html for showing the View status button,
			// and more
			Map<String, String> map;
			try {
				map = new ObjectMapper().readValue(params, HashMap.class);
				result = webhookLib.send2(map);
			} catch (JsonParseException e) {
				e.printStackTrace();
			} catch (JsonMappingException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			result = "{\"ok\": false, \"html\": \"<h2>Bad JSON input!</h2>\"}";
		}
		return result;
	}

	private static String doSend1(String url) {
		// Show a button that sends the signature request
		// When pressed, we're called with op=send2
		WebhookLib webhookLib = new WebhookLib();
		boolean ok = webhookLib.send1(url);
		StringBuilder sb = new StringBuilder();
		sb.append(showHeader());
		if (ok) {
			sb.append("<h5>DocuSign Account id: " + webhookLib.getDsAccountId() + "</h5>");
			sb.append("<h5>Signer: " + webhookLib.getDsSigner1Name() + " &lt;" + webhookLib.getDsSigner1Email()
					+ "&gt;</h5>");
			sb.append("<h5>Webhook url: " + webhookLib.getWebhookUrl()
					+ "</h5>\r\n	    <p class=\"margintop\"><button id=\"sendbtn\" type=\"button\" class=\"btn btn-primary\">Send the signature request!</button>\r\n	<span style=\"margin-left:3em;\"><a href=\"/?op=send1\">Reset</a></span></p>\r\n		<div class=\"margintop\" id=\"target\"></div>");
		} else {
			// not ok
			// sb.append("<h3>" + result.get("errMsg") + "</h3>");
			sb.append("<h5 class='margintop'>Please solve the problem and retry.</h5>");
		}

		sb.append(showFooter("{\"navbar\": \"li_send\", \r\n	\"send_param\": {\"ds_signer1_name\": \""
				+ webhookLib.getDsSigner1Name() + "\", \r\n \"ds_signer1_email\": \"" + webhookLib.getDsSigner1Email()
				+ "\", \r\n \"ds_cc1_name\": \"" + webhookLib.getDsCC1Name() + "\", \r\n \"ds_cc1_email\": \""
				+ webhookLib.getDsCC1Email() + "\", \r\n \"webhook_url\": \"" + webhookLib.getWebhookUrl()
				+ "\", \r\n \"button\": \"sendbtn\", \r\n \"url\": \"/?op=send2\", \r\n \"target\": \"target\"}}"));

		return sb.toString();
	}

}
