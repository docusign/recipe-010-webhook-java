import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.docusign.esign.api.*;
import com.docusign.esign.api.AuthenticationApi.LoginOptions;
import com.docusign.esign.client.*;
import com.docusign.esign.model.*;

public class DsRecipeLib {

	final static Logger logger = LoggerFactory.getLogger(DsRecipeLib.class);
	private String dsUserEmail;
	private String dsUserPw;
	private String dsIntegrationId;
	private String dsAccountId;
	private String dsBaseUrl;
	private String dsAuthHeader;
	private String dsApiUrl = "https://demo.docusign.net/restapi"; // change for production
	private String myUrl; // url of the overall script
	private String tempEmailServer = "mailinator.com"; // Used for throw-away email addresses
	private int emailCount = 2; // Used to make email addresses unique.
	private String b64PwPrefix = "ZW5jb";
	private String b64PwClearPrefix = "encoded";
	private AuthenticationApi authenticationApi;

	public DsRecipeLib(String dsUserEmail, String dsUserPw, String dsIntegrationId, String dsAccountId) {
		// if dsAccountId is null then the user's default account will be used
		// if dsUserEmail is "***" then environment variables are used

		if ("***".equals(dsUserEmail)) {
			dsUserEmail = this.getEnv("DS_USER_EMAIL");
			dsUserPw = this.getEnv("DS_USER_PW");
			dsIntegrationId = this.getEnv("DS_INTEGRATION_ID");
		}

		if ((dsUserEmail == null) || (dsUserEmail.length() < 4)) {
			logger.error(
					"<h3>No DocuSign login settings! Either set in the script or use environment variables dsUserEmail, dsUserPw, and dsIntegrationId</h3>");
		}
		// Decode the pw if it is in base64
		if (this.b64PwPrefix.equals(dsUserPw.substring(0, this.b64PwPrefix.length()))) {
			// it was encoded
			dsUserPw = new String(Base64.getDecoder().decode(dsUserPw));
			dsUserPw = dsUserPw.substring(0, this.b64PwClearPrefix.length()); // remove
																				// prefix
		}
		this.dsUserPw = dsUserPw;
		this.dsUserEmail = dsUserEmail;
		this.dsIntegrationId = dsIntegrationId;
		this.dsAccountId = dsAccountId;
		// construct the authentication header:
		this.dsAuthHeader = "<DocuSignCredentials><Username>" + dsUserEmail + "</Username><Password>" + dsUserPw
				+ "</Password><IntegratorKey>" + dsIntegrationId + "</IntegratorKey></DocuSignCredentials>";
	}

	public void curlAddCaInfo(String curl) {
		// Add the bundle of trusted CA information to curl
		// In most environments, the list of trusted of CAs is set
		// at the OS level. However, some PAAS services such as
		// MS Azure App Service enable you to trust just the CAs that you
		// choose. So that's what we're doing here.
		// The usual list of trusted CAs is from Mozilla via the Curl
		// people.

		// curl_setopt($curl, CURLOPT_CAINFO, getcwd() .
		// "/assets_master/ca-bundle.crt");
	}

	public String getSignerName(String name) {
		if (name == null || "***".equals(name)) {
			name = this.getFakeName();
		}
		return name;
	}

	public String getSignerEmail(String email) {
		if (email != null && !"***".equals(email)) {
			return email;
		} else {
			return this.makeTempEmail();
		}
	}

	public String getTempEmailAccess(String email) {
		// just create something unique to use with maildrop.cc
		// Read the email at http://maildrop.cc/inbox/<mailbox_name>
		String url = "https://mailinator.com/inbox2.jsp?public_to=";
		String[] parts = email.split("@");
		if (!parts[1].equals(this.tempEmailServer)) {
			return null;
		}
		return url + parts[0];
	}

	public String getTempEmailAccessQrcode(boolean emailAccess) {
		// TODO Auto-generated method stub
		return null;
	}

	public Map<String, String> login() {
		Map<String, String> map = new HashMap<>();
		// Login (to retrieve baseUrl and accountId)
		ApiClient apiClient = new ApiClient();
		apiClient.setBasePath(dsApiUrl);
		apiClient.addDefaultHeader("X-DocuSign-Authentication", dsAuthHeader);
		Configuration.setDefaultApiClient(apiClient);

		try {
			// login call available off the AuthenticationApi
			authenticationApi = new AuthenticationApi();
			// login has some optional parameters we can set
			LoginOptions options = authenticationApi.new LoginOptions();
			LoginInformation loginInformation = authenticationApi.login(options);
			if (loginInformation == null || loginInformation.getLoginAccounts().size() < 1) {
				map.put("ok", "false");
				map.put("errMsg", "Error calling DocuSign login");
				return map;
			}
			// Example response:
			// { "loginAccounts": [
			// { "name": "DocuSign", "accountId": "1374267",
			// "baseUrl":
			// "https://demo.docusign.net/restapi/v2/accounts/1374267",
			// "isDefault": "true", "userName": "Recipe Login",
			// "userId": "d43a4a6a-dbe7-491e-9bad-8f7b4cb7b1b5",
			// "email": "temp2+recipe@kluger.com", "siteDescription": ""
			// }
			// ]}
			//

			boolean found = false;
			String errMsg = "";
			// Get account_id and base_url.
			if (dsAccountId == null) {
				// Get default
				for (LoginAccount account : loginInformation.getLoginAccounts()) {
					if ("true".equals(account.getIsDefault())) {
						this.dsAccountId = account.getAccountId();
						this.dsBaseUrl = account.getBaseUrl();
						found = true;
						break;
					}
				}
				if (!found) {
					errMsg = "Could not find default account for the username.";
				}
			} else {
				// get the account's base_url
				for (LoginAccount account : loginInformation.getLoginAccounts()) {
					if (account.getAccountId().equals(dsAccountId)) {
						this.dsBaseUrl = account.getBaseUrl();
						found = true;
						break;
					}
				}
				if (!found) {
					errMsg = "Could not find baseUrl for account " + this.dsAccountId;
				}
			}
			map.put("ok", String.valueOf(found));
			map.put("errMsg", errMsg);
		} catch (ApiException e) {
			logger.error(e.getLocalizedMessage());
		}
		return map;
	}

	public String getDsAuthHeader() {
		return dsAuthHeader;
	}

	public String getDsAccountId() {
		return dsAccountId;
	}

	public String getDsBaseUrl() {
		return dsBaseUrl;
	}

	public String getDsApiUrl() {
		return dsApiUrl;
	}

	public String makeTempEmail() {
		// just create something unique to use with maildrop.cc
		// Read the email at http://maildrop.cc/inbox/<mailbox_name>
		String ip = "100";
		this.emailCount = (int) Math.pow(this.emailCount, 2);

		String email = this.emailCount + new java.util.Date().getTime() + ip;
		email = Base64.getEncoder().encodeToString(email.getBytes());
		email = email.replaceAll("[^a-zA-Z0-9]", "");
		email = email.substring(0, Math.min(25, email.length()));

		return email + "@" + tempEmailServer;
	}

	public String getTempEmailAccessQrcode(String address) {
		// String url = "http://open.visualead.com/?size=130&type=png&data=";
		String url = "https://chart.googleapis.com/chart?cht=qr&chs=150x150&chl=";
		try {
			url += URLEncoder.encode(address, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		int size = 150;
		String html = "<img height='" + size + "' width='" + size + "' src='" + url
				+ "' alt='QR Code' style='margin:10px 0 10px;' />";
		return html;
	}

	public String getMyUrl(String url) {
		// Dynamically determine the script's url
		// For production use, this is not a great idea. Instead, set it
		// explicitly. Remember that for production, webhook urls must start with https!
		if (url != null) {
			// already set
			this.myUrl = url;
		} else {
			this.myUrl = this.getEnv("URL");
			this.myUrl = (this.myUrl != null) ? this.myUrl: "/";
		}
		return this.myUrl;
	}

	// See http://stackoverflow.com/a/8891890/64904
	private String urlOrigin(String s, boolean useForwardedHost) {
		String ssl = null;
		String sp = "http";
		String protocol = "http";
		String port = System.getenv("PORT");
		String host = System.getenv("IP");
		return protocol + "://" + host;
	}

	private String fullUrl(String s, boolean useForwardedHost) {
		return this.urlOrigin(s, useForwardedHost); // + $s['REQUEST_URI'];
	}

	private String rmQueryParameters(String in) {
		String[] parts = in.split("?");
		return parts[0];
	}

	public String getFakeName() {
		String[] firstNames = { "Verna", "Walter", "Blanche", "Gilbert", "Cody", "Kathy", "Judith", "Victoria", "Jason",
				"Meghan", "Flora", "Joseph", "Rafael", "Tamara", "Eddie", "Logan", "Otto", "Jamie", "Mark", "Brian",
				"Dolores", "Fred", "Oscar", "Jeremy", "Margart", "Jennie", "Raymond", "Pamela", "David", "Colleen",
				"Marjorie", "Darlene", "Ronald", "Glenda", "Morris", "Myrtis", "Amanda", "Gregory", "Ariana", "Lucinda",
				"Stella", "James", "Nathaniel", "Maria", "Cynthia", "Amy", "Sylvia", "Dorothy", "Kenneth", "Jackie" };
		String[] lastNames = { "Francisco", "Deal", "Hyde", "Benson", "Williamson", "Bingham", "Alderman", "Wyman",
				"McElroy", "Vanmeter", "Wright", "Whitaker", "Kerr", "Shaver", "Carmona", "Gremillion", "O'Neill",
				"Markert", "Bell", "King", "Cooper", "Allard", "Vigil", "Thomas", "Luna", "Williams", "Fleming", "Byrd",
				"Chaisson", "McLeod", "Singleton", "Alexander", "Harrington", "McClain", "Keels", "Jackson", "Milne",
				"Diaz", "Mayfield", "Burnham", "Gardner", "Crawford", "Delgado", "Pape", "Bunyard", "Swain", "Conaway",
				"Hetrick", "Lynn", "Petersen" };
		String first = firstNames[(int) (Math.random() * firstNames.length)];
		String last = lastNames[(int) (Math.random() * lastNames.length)];
		return first + " " + last;
	}

	/*
	 * private String varDumpRet(String mixed) { //obStart(); varDump(mixed);
	 * String content = obGetContents(); obEndClean(); return content; }
	 */

	private String getEnv(String name) {
		// Turns out that sometimes the environment variables are
		// passed by $_SERVER for Apache. ?!
		String result;
		if (System.getenv(name) != null) {
			result = System.getenv(name);
		} else {
			result = null;
		}
		return result;
	}

}
