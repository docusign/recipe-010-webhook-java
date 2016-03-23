import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.docusign.esign.model.*;
import com.docusign.esign.api.*;
import com.docusign.esign.client.ApiException;
import com.docusign.esign.client.JSON;

public class WebhookLib {
	// Settings
	//
	public String dsUserEmail = "***";
	public String dsUserPw = "***";
	public String dsIntegrationId = "***";
	public String dsSigner1Name = "***"; // Set signer info here or leave as is
											// to use example signers
	public String dsSigner1Email = "***";
	public String dsCC1Name = "***"; // Set a cc recipient here or leave as is
										// to use example recipients
	public String dsCC1Email = "***";
	public String dsAccountId; // Set during login process or explicitly by
								// configuration here.
	// Note that many customers have more than one account!
	// A username/pw can access multiple accounts!
	public String dsBaseUrl; // The base url associated with the account_id.
	public String dsAuthHeader;
	public String myUrl; // The url for this script. Must be accessible from the
							// internet!
	// Can be set here or determined dynamically
	public String webhookUrl;
	public String docFilename = "sample_documents_master/NDA.pdf";
	public String docDocumentName = "NDA.pdf";
	public String docFiletype = "application/pdf";

	private DsRecipeLib dsRecipeLib;
	private String webhookSuffix = "?op=webhook";

	private String xmlFileDir = "files/";
	private String docPrefix = "doc_";
	final static Logger logger = LoggerFactory.getLogger(WebhookLib.class);

	////////////////////////////////////////////////////////////////////////
	////////////////////////////////////////////////////////////////////////
	public WebhookLib() {
		dsRecipeLib = new DsRecipeLib(dsUserEmail, dsUserPw, dsIntegrationId, dsAccountId);
		myUrl = dsRecipeLib.getMyUrl(myUrl);
	}

	////////////////////////////////////////////////////////////////////////

	public boolean send1(String url) {
		// Prepares for sending the envelope
		Map<String, String> result = login();
		if ("false".equals(result.get("ok"))) {
			return false;
		}
		myUrl = url;

		webhookUrl = (myUrl != null && !myUrl.isEmpty()) ? myUrl + webhookSuffix
				: "http://localhost:5000/" + webhookSuffix;
		webhookUrl = "https://ds-webhook-java.herokuapp.com/" + webhookSuffix;
		dsSigner1Name = dsRecipeLib.getSignerName(dsSigner1Name);
		dsSigner1Email = dsRecipeLib.getSignerEmail(dsSigner1Email);
		dsCC1Name = dsRecipeLib.getSignerName(dsCC1Name);
		dsCC1Email = dsRecipeLib.getSignerEmail(dsCC1Email);
		return true;
	}

	private Map<String, String> login() {
		Map<String, String> map = new HashMap<>();

		// Logs into DocuSign
		Map<String, String> result = dsRecipeLib.login();
		if ("true".equals(result.get("ok"))) {
			dsAccountId = dsRecipeLib.getDsAccountId();
			dsBaseUrl = dsRecipeLib.getDsBaseUrl();
			dsAuthHeader = dsRecipeLib.getDsAuthHeader();
			map.put("ok", "true");
		} else {
			map.put("ok", "false");
			map.put("errMsg", result.get("errMsg"));
		}

		return map;
	}

	public String getDsAccountId() {
		return dsAccountId;
	}

	public void setDsAccountId(String dsAccountId) {
		this.dsAccountId = dsAccountId;
	}

	public String getDsSigner1Name() {
		return dsSigner1Name;
	}

	public String getWebhookUrl() {
		return webhookUrl;
	}

	public void webhookListener(String data) {
		// Process the incoming webhook data. See the DocuSign Connect guide
		// for more information
		//
		// Strategy: examine the data to pull out the envelopeId and
		// time_generated fields.
		// Then store the entire xml on our local file system using those
		// fields.
		//
		// If the envelope status=="Completed" then store the files as doc1.pdf,
		// doc2.pdf, etc
		//
		// This function could also enter the data into a dbms, add it to a
		// queue, etc.
		// Note that the total processing time of this function must be less
		// than
		// 100 seconds to ensure that DocuSign's request to your app doesn't
		// time out.
		// Tip: aim for no more than a couple of seconds! Use a separate queuing
		// service
		// if need be.

		logger.info("Data received from DS Connect: " + data);
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();

			org.w3c.dom.Document xml = builder.parse(data);
			Element root = xml.getDocumentElement();
			Element envelopeStatus = (Element) root.getElementsByTagName("EnvelopeStatus").item(0);
			String envelopeId = envelopeStatus.getElementsByTagName("EnvelopeID").item(0).getNodeValue();
			String timeGenerated = envelopeStatus.getElementsByTagName("TimeGenerated").item(0).getNodeValue();

			// Store the file. Create directories as needed
			// Some systems might still not like files or directories to start
			// with numbers.
			// So we prefix the envelope ids with E and the timestamps with T
			File filesDir = new File(System.getProperty("user.dir") + "/" + xmlFileDir);
			if (!filesDir.isDirectory()) {
				filesDir.mkdir();
				// mkdir(filesDir, 0755);
			}
			File envelopeDir = new File(filesDir + "E" + envelopeId);
			if (!envelopeDir.isDirectory()) {
				envelopeDir.mkdir();
			}
			String filename = envelopeDir + "/T" + timeGenerated.replace(':', '_') + ".xml";
			try {
				File xmlFile = new File(filename);
				FileWriter fw = new FileWriter(xmlFile);
				try {
					fw.write(data);
				} finally {
					fw.close();
				}
			} catch (Exception ex) {
				// Couldn't write the file! Alert the humans!
				logger.error("!!!!!! PROBLEM DocuSign Webhook: Couldn't store " + filename + " !");
				return;
			}

			// log the event
			logger.info("DocuSign Webhook: created " + filename);

			if ("Completed".equals(envelopeStatus.getElementsByTagName("Status").item(0).getNodeValue())) {
				// Loop through the DocumentPDFs element, storing each document.
				NodeList nodeList = root.getElementsByTagName("DocumentPDFs").item(0).getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					Element pdf = (Element) nodeList.item(i);
					filename = docPrefix + pdf.getElementsByTagName("DocumentID").item(0).getNodeValue() + ".pdf";
					String fullFilename = envelopeDir + "/" + filename;
					try {
						File pdfFile = new File(fullFilename);
						byte[] pdfBytes = Base64.getDecoder()
								.decode(pdf.getElementsByTagName("PDFBytes").item(0).getNodeValue());
						FileOutputStream fos = new FileOutputStream(pdfFile);
						try {
							fos.write(pdfBytes);
						} finally {
							fos.close();
						}
					} catch (Exception ex) {
						// Couldn't write the file! Alert the humans!
						System.err.println("!!!!!! PROBLEM DocuSign Webhook: Couldn't store " + filename + " !");
						return;
					}
				}
			}
		} catch (Exception e) {
			logger.error("!!!!!! PROBLEM DocuSign Webhook: Couldn't pase the XML sent by DocuSign Connect!");
		}
	}

	public String send2(Map<String, String> params) {
		// Send the envelope
		// params --
		// "ds_signer1_name"
		// "ds_signer1_email"
		// "ds_cc1_name"
		// "ds_cc1_email"
		// "webhook_url"
		// "baseurl"
		Map<String, String> result = login();
		if ("false".equals(result.get("ok"))) {
			return "{\"ok\": false, \"html\": \"<h3>Problem</h3><p>Couldn't login to DocuSign: " + result.get("errMsg")
					+ "</p>\"}";
		}
		webhookUrl = params.get("webhook_url");
		dsSigner1Name = params.get("ds_signer1_name");
		dsSigner1Email = params.get("ds_signer1_email");
		dsCC1Name = params.get("ds_cc1_name");
		dsCC1Email = params.get("ds_cc1_email");
		// The envelope request includes a signer-recipient and their tabs
		// object,
		// and an eventNotification object which sets the parameters for
		// webhook notifications to us from the DocuSign platform
		EnvelopeEvent envelopeEvent = new EnvelopeEvent();
		envelopeEvent.setEnvelopeEventStatusCode("sent");
		envelopeEvent.setEnvelopeEventStatusCode("delivered");
		envelopeEvent.setEnvelopeEventStatusCode("completed");
		envelopeEvent.setEnvelopeEventStatusCode("declined");
		envelopeEvent.setEnvelopeEventStatusCode("voided");
		envelopeEvent.setEnvelopeEventStatusCode("sent");
		envelopeEvent.setEnvelopeEventStatusCode("sent");
		List<EnvelopeEvent> envelopeEvents = new ArrayList<>();
		envelopeEvents.add(envelopeEvent);

		RecipientEvent recipientEvent = new RecipientEvent();
		recipientEvent.setRecipientEventStatusCode("Sent");
		recipientEvent.setRecipientEventStatusCode("Delivered");
		recipientEvent.setRecipientEventStatusCode("Completed");
		recipientEvent.setRecipientEventStatusCode("Declined");
		recipientEvent.setRecipientEventStatusCode("AuthenticationFailed");
		recipientEvent.setRecipientEventStatusCode("AutoResponded");
		List<RecipientEvent> recipientEvents = new ArrayList<>();
		recipientEvents.add(recipientEvent);

		EventNotification eventNotification = new EventNotification();
		eventNotification.setUrl(webhookUrl);
		eventNotification.setLoggingEnabled("true");
		eventNotification.setRequireAcknowledgment("true");
		eventNotification.setUseSoapInterface("false");
		eventNotification.setIncludeCertificateWithSoap("false");
		eventNotification.setSignMessageWithX509Cert("false");
		eventNotification.setIncludeDocuments("true");
		eventNotification.setIncludeEnvelopeVoidReason("true");
		eventNotification.setIncludeTimeZone("true");
		eventNotification.setIncludeSenderAccountAsCustomField("true");
		eventNotification.setIncludeDocumentFields("true");
		eventNotification.setIncludeCertificateOfCompletion("true");
		eventNotification.setEnvelopeEvents(envelopeEvents);
		eventNotification.setRecipientEvents(recipientEvents);

		byte[] fileBytes = null;
		try {
			Path path = Paths.get("./src/main/resources/public/" + docFilename);
			fileBytes = Files.readAllBytes(path);
		} catch (Exception ex) {
			System.err.println(ex);
		}
		Document document = new Document();
		String base64Doc = Base64.getEncoder().encodeToString(fileBytes);
		document.setDocumentId("1");
		document.setName(docDocumentName);
		document.setDocumentBase64(base64Doc);
		List<Document> documents = new ArrayList<>();
		documents.add(document);
		Signer signer = new Signer();
		signer.setEmail(dsSigner1Email);
		signer.setName(dsSigner1Name);
		signer.setRecipientId("1");
		signer.setRoutingOrder("1");
		signer.setTabs(getNdaFields());
		List<Signer> signers = new ArrayList<>();
		signers.add(signer);
		CarbonCopy carbonCopy = new CarbonCopy();
		carbonCopy.setEmail(dsCC1Email);
		carbonCopy.setName(dsCC1Name);
		carbonCopy.setRecipientId("2");
		carbonCopy.setRoutingOrder("2");
		List<CarbonCopy> carbonCopies = new ArrayList<>();
		carbonCopies.add(carbonCopy);
		Recipients recipients = new Recipients();
		recipients.setSigners(signers);
		recipients.setCarbonCopies(carbonCopies);
		EnvelopeDefinition envelopeDefinition = new EnvelopeDefinition();
		// We want to use the most friendly email subject line.
		// The regexp below removes the suffix from the file name.
		envelopeDefinition
				.setEmailSubject("Please sign the " + docDocumentName.replaceAll("\\.[^.\\s]{3,4}", "") + " document");
		envelopeDefinition.setDocuments(documents);
		envelopeDefinition.setRecipients(recipients);
		envelopeDefinition.setEventNotification(eventNotification);
		envelopeDefinition.setStatus("sent");
		// Send the envelope:
		EnvelopesApi envelopesApi = new EnvelopesApi();
		EnvelopeSummary envelopeSummary;
		try {
			envelopeSummary = envelopesApi.createEnvelope(dsAccountId, envelopeDefinition);
			if (envelopeSummary == null || envelopeSummary.getEnvelopeId() == null) {
				return "{\"ok\": false, \"html\": \"<h3>Problem</h3> \r\n  <p>Error calling DocuSign</p>\"}";
			}
			String envelopeId = envelopeSummary.getEnvelopeId();
			// Create instructions for reading the email
			StringBuilder html = new StringBuilder();
			html.append("<h2>Signature request sent!</h2><p>Envelope ID: " + envelopeId + "</p>");
			html.append("<h2>Next steps</h2>" + "<h3>1. Open the Webhook Event Viewer</h3>");
			html.append("<p><a href='" + (params.get("baseurl") != null ? params.get("baseurl") : "/")
					+ "?op=status&envelope_id=" + URLEncoder.encode(envelopeId, "UTF-8") + "'");
			html.append("  class='btn btn-primary' role='button' target='_blank' style='margin-right:1.5em;'>");
			html.append("View Events</a> (A new tab/window will be used.)</p>");
			html.append("<h3>2. Respond to the Signature Request</h3>");
			String emailAccess = dsRecipeLib.getTempEmailAccess(dsSigner1Email);
			if (emailAccess != null) {
				// A temp account was used for the email
				html.append("<p>Respond to the request via your mobile phone by using the QR code: </p>");
				html.append("<p>" + dsRecipeLib.getTempEmailAccessQrcode(emailAccess) + "</p>");
				html.append("<p> or via <a target='_blank' href='" + emailAccess + "'>your web browser.</a></p>");
			} else {
				// A regular email account was used
				html.append("<p>Respond to the request via your mobile phone or other mail tool.</p>");
				html.append("<p>The email was sent to " + dsSigner1Name + " &lt;" + dsSigner1Email + "&gt;</p>");
			}
			return "{ \"ok\": true, \r\n \"envelope_id\": \"" + envelopeId + "\", \r\n \"html\": \"" + html.toString()
					+ "\", \r\n \"js\": [{\"disable_button\": \"sendbtn\"}]}";
		} catch (Exception e) {
			if (e instanceof ApiException) {
				System.out.println(((ApiException) e).getCode());
			}
			e.printStackTrace();
		}
		return "{  \"ok\": false, \r\n \"html\": \"Error while sending the envelope\" }";
	}

	private Tabs getNdaFields() {
		// The fields for the sample document "NDA"
		// Create 4 fields, using anchors
		// * signer1sig
		// * signer1name
		// * signer1company
		// * signer1date

		// This method uses the SDK to create the fields data structure

		SignHere signHereTab = new SignHere();
		signHereTab.setAnchorString("signer1sig");
		signHereTab.setAnchorXOffset("0");
		signHereTab.setAnchorYOffset("0");
		signHereTab.setAnchorUnits("mms");
		signHereTab.setRecipientId("1");
		signHereTab.setName("Please sign here");
		signHereTab.setOptional("false");
		signHereTab.setScaleValue(1);
		signHereTab.setTabLabel("signer1sig");
		List<SignHere> signHereTabs = new ArrayList<>();
		signHereTabs.add(signHereTab);
		FullName fullNameTab = new FullName();
		fullNameTab.setAnchorString("signer1name");
		fullNameTab.setAnchorYOffset("-6");
		fullNameTab.setFontSize("Size12");
		fullNameTab.setRecipientId("1");
		fullNameTab.setTabLabel("Full Name");
		fullNameTab.setName("Full Name");
		List<FullName> fullNameTabs = new ArrayList<>();
		fullNameTabs.add(fullNameTab);
		Text textTab = new Text();
		textTab.setAnchorString("signer1company");
		textTab.setAnchorYOffset("-8");
		textTab.setFontSize("Size12");
		textTab.setRecipientId("1");
		textTab.setTabLabel("Company");
		textTab.setName("Company");
		textTab.setRequired("false");
		List<Text> textTabs = new ArrayList<>();
		textTabs.add(textTab);
		DateSigned dateSignedTab = new DateSigned();
		dateSignedTab.setAnchorString("signer1date");
		dateSignedTab.setAnchorYOffset("-6");
		dateSignedTab.setFontSize("Size12");
		dateSignedTab.setRecipientId("1");
		dateSignedTab.setName("Date Signed");
		dateSignedTab.setTabLabel("Company");
		List<DateSigned> dateSignedTabs = new ArrayList<>();
		dateSignedTabs.add(dateSignedTab);
		Tabs fields = new Tabs();
		fields.setSignHereTabs(signHereTabs);
		fields.setFullNameTabs(fullNameTabs);
		fields.setTextTabs(textTabs);
		fields.setDateSignedTabs(dateSignedTabs);
		return fields;
	}

	public String getDsSigner1Email() {
		return dsSigner1Email;
	}

	public String getDsCC1Email() {
		return dsCC1Email;
	}

	public String getDsCC1Name() {
		return dsCC1Name;
	}

	public String statusItems(Map<String, String> params) {
		// List of info about the envelope's event items received
		String filesDirUrl = ((myUrl == null || myUrl.isEmpty()) ? "/" : myUrl.substring(0, myUrl.indexOf('/') + 1))
				+ xmlFileDir;
		// remove http or https
		filesDirUrl = filesDirUrl.replace("http:", "").replace("https:", "");
		logger.debug("filesDirUrl=" + filesDirUrl);
		File filesDir = new File(System.getProperty("user.dir") + "/" + xmlFileDir + "E" + params.get("envelope_id"));
		logger.debug("filesDir=" + filesDir);

		String results = "";
		if (!filesDir.isDirectory()) {
			logger.debug("results=" + results);
			return results; // no results!
		}

		for (File file : filesDir.listFiles(new FileFilter() {

			@Override
			public boolean accept(File path) {
				return path.isFile() && path.getName().toLowerCase().endsWith(".xml");
			}
		})) {
			results = statusItem(file, file.getName(), filesDirUrl);
			break;
		}
		logger.debug("results=" + results);
		return results;
	}

	private String statusItem(File file, String filename, String filesDirUrl) {
		// summary info about the notification
		String result = "";
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();

			InputStream fis = new FileInputStream(file);
			org.w3c.dom.Document xml = builder.parse(fis);
			Element root = xml.getDocumentElement();
			Element envelopeStatus = (Element) root.getElementsByTagName("EnvelopeStatus").item(0);
			Element recipientStatuses = (Element) envelopeStatus.getElementsByTagName("RecipientStatuses").item(0);

			// iterate through the recipients
			String recipients = "";
			NodeList nodeList = recipientStatuses.getChildNodes();
			for (int i = 0; i < nodeList.getLength(); i++) {
				Element recipient = (Element) nodeList.item(i);
				recipients = "{" + "\"type\":\"" + recipient.getElementsByTagName("Type").item(0).getNodeValue() + "\","
						+ "\"email\":\"" + recipient.getElementsByTagName("Email").item(0).getNodeValue() + "\","
						+ "\"user_name\":\"" + recipient.getElementsByTagName("UserName").item(0).getNodeValue() + "\","
						+ "\"routing_order\":\"" + recipient.getElementsByTagName("RoutingOrder").item(0).getNodeValue()
						+ "\"," + "\"sent_timestamp\":\""
						+ recipient.getElementsByTagName("Sent").item(0).getNodeValue() + "\","
						+ "\"delivered_timestamp\":\""
						+ recipient.getElementsByTagName("Delivered").item(0).getNodeValue() + "\","
						+ "\"signed_timestamp\":\"" + recipient.getElementsByTagName("Signed").item(0).getNodeValue()
						+ "\"," + "\"status\":\"" + recipient.getElementsByTagName("Status").item(0).getNodeValue()
						+ "\"" + "}";
			}

			String documents = "";
			String envelopeId = envelopeStatus.getElementsByTagName("EnvelopeID").item(0).getNodeValue();
			// iterate through the documents if the envelope is Completed
			if ("Completed".equals(envelopeStatus.getElementsByTagName("Status").item(0).getNodeValue())) {
				// Loop through the DocumentPDFs element, noting each document.
				nodeList = root.getElementsByTagName("DocumentPDFs").item(0).getChildNodes();
				for (int i = 0; i < nodeList.getLength(); i++) {
					Element pdf = (Element) nodeList.item(i);
					String docFilename = docPrefix + pdf.getElementsByTagName("DocumentID").item(0).getNodeValue()
							+ ".pdf";
					documents = "{" + "\"document_ID\":\""
							+ pdf.getElementsByTagName("DocumentID").item(0).getNodeValue() + "\","
							+ "\"document_type\":\"" + pdf.getElementsByTagName("DocumentType").item(0).getNodeValue()
							+ "\"," + "\"name\":\"" + pdf.getElementsByTagName("Name").item(0).getNodeValue() + "\","
							+ "\"url\":\"" + filesDirUrl + "E" + envelopeId + "/" + docFilename + "\"" + "}";
				}
			}

			result = "{" + "\"envelopeId\":\"" + envelopeId + "\"," + "\"xml_url\":\"" + filesDirUrl + "E" + envelopeId
					+ "/" + filename + "\"," + "\"time_generated\":\""
					+ envelopeStatus.getElementsByTagName("TimeGenerated").item(0).getNodeValue() + "\","
					+ "\"subject\":\"" + envelopeStatus.getElementsByTagName("Subject").item(0).getNodeValue() + "\","
					+ "\"sender_user_name\":\"" + envelopeStatus.getElementsByTagName("UserName").item(0).getNodeValue()
					+ "\"," + "\"sender_email\":\""
					+ envelopeStatus.getElementsByTagName("Email").item(0).getNodeValue() + "\","
					+ "\"envelope_status\":\"" + envelopeStatus.getElementsByTagName("Status").item(0).getNodeValue()
					+ "\"," + "\"envelope_sent_timestamp\":\""
					+ envelopeStatus.getElementsByTagName("Sent").item(0).getNodeValue() + "\","
					+ "\"envelope_created_timestamp\":\""
					+ envelopeStatus.getElementsByTagName("Cretaed").item(0).getNodeValue() + "\","
					+ "\"envelope_delivered_timestamp\":\""
					+ envelopeStatus.getElementsByTagName("Delivered").item(0).getNodeValue() + "\","
					+ "\"envelope_signed_timestamp\":\""
					+ envelopeStatus.getElementsByTagName("Signed").item(0).getNodeValue() + "\","
					+ "\"envelope_completed_timestamp\":\""
					+ envelopeStatus.getElementsByTagName("Completed").item(0).getNodeValue() + "\","
					+ "\"timezone\":\"" + root.getElementsByTagName("TimeZone").item(0).getNodeValue() + "\","
					+ "\"timezone_offset\":\"" + root.getElementsByTagName("TimeZoneOffset").item(0).getNodeValue()
					+ "\"," + "\"recipients\":\"" + recipients + "\"," + "\"documents\":\"" + documents + "\"" + "}";
		} catch (Exception e) {
			System.err.println("!!!!!! PROBLEM DocuSign Webhook: Couldn't pase the XML sent by DocuSign Connect!");
		}
		logger.debug("result=" + result);
		return result;
	}

	public String statusInfo(Map<String, String> map) {
		// Info about the envelope
		// Calls /accounts/{accountId}/envelopes/{envelopeId}
		Map<String, String> result = login();
		if ("false".equals(result.get("ok"))) {
			return "{\"ok\": false, \"html\": \"<h3>Problem</h3><p>Couldn't login to DocuSign: " + result.get("errMsg")
					+ "</p>\"}";
		}
		EnvelopesApi envelopesApi = new EnvelopesApi();
		Envelope envelope;
		try {
			envelope = envelopesApi.getEnvelope(dsAccountId, map.get("envelope_id"));
			if (envelope == null || envelope.getEnvelopeId() == null) {
				return "{\"ok\": false, \"html\": \"<h3>Problem</h3><p>Error calling DocuSign</p>\"}";
			}
			return new JSON().serialize(envelope);
		} catch (ApiException e) {
			e.printStackTrace();
		}
		return "{\"ok\": false, \"html\": \"<h3>Problem</h3><p>Couldn't get envelope.</p>\"}";
	}

}
