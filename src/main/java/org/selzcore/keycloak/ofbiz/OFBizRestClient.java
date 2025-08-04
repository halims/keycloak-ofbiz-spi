package org.selzcore.keycloak.ofbiz;

import org.keycloak.component.ComponentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Base64;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST client for OFBiz integration
 * 
 * Handles authentication and user information retrieval through OFBiz REST APIs
 */
public class OFBizRestClient {

    private static final Logger logger = LoggerFactory.getLogger(OFBizRestClient.class);
    
    private final String baseUrl;
    private final String authEndpoint;
    private final String userEndpoint;
    private final int timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String authToken; // Store authentication token
    private int tokenExpiresInSeconds; // Store token expiration from OFBiz

    public OFBizRestClient(ComponentModel model) {
        this.baseUrl = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_BASE_URL);
        this.authEndpoint = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_AUTH_ENDPOINT);
        this.userEndpoint = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_USER_ENDPOINT);
        
        String timeoutStr = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_TIMEOUT);
        this.timeout = timeoutStr != null && !timeoutStr.trim().isEmpty() ? 
                      Integer.parseInt(timeoutStr) : 5000;
        
        // Create HTTP client with SSL support for development (trusts self-signed certificates)
        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout));
                
        // For HTTPS endpoints, configure to accept self-signed certificates in development
        if (baseUrl != null && baseUrl.startsWith("https://")) {
            try {
                SSLContext sslContext = SSLContext.getInstance("TLS");
                sslContext.init(null, new TrustManager[]{new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }}, new java.security.SecureRandom());
                
                clientBuilder.sslContext(sslContext);
                logger.debug("Configured HTTP client for HTTPS with self-signed certificate support");
            } catch (Exception e) {
                logger.warn("Failed to configure SSL context for HTTPS, using default: {}", e.getMessage());
            }
        }
        
        this.httpClient = clientBuilder.build();
        this.objectMapper = new ObjectMapper();
        
        logger.debug("Created OFBizRestClient with baseUrl: {}, timeout: {}ms", 
                    maskUrl(baseUrl), timeout);
    }

    /**
     * Tests connectivity to OFBiz instance
     */
    public boolean testConnection() {
        logger.debug("Testing connection to OFBiz at: {}", maskUrl(baseUrl));
        
        try {
            // Try to connect to the REST endpoint
            String testUrl = baseUrl + "/rest/"; // OFBiz REST endpoint
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(testUrl))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            logger.info("OFBiz connection test - Status: {}, URL: {}", 
                       response.statusCode(), maskUrl(testUrl));
            
            // Accept various success codes (200, 302 redirect, etc.)
            boolean connected = response.statusCode() >= 200 && response.statusCode() < 400;
            
            if (connected) {
                logger.info("✅ OFBiz REST endpoint accessible");
                return true;
            } else {
                logger.error("❌ Failed to connect to OFBiz REST endpoint");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Failed to connect to OFBiz at {}: {}", maskUrl(baseUrl), e.getMessage());
            return false;
        }
    }

    /**
     * Authenticates a user against OFBiz REST API using /rest/auth/token
     */
    public boolean authenticateUser(String username, String password) {
        logger.info("🌐 OFBIZ REST: Starting authentication for user '{}' via OFBiz REST API", username);
        
        // Test connection first
        if (!testConnection()) {
            logger.error("❌ OFBIZ REST: Cannot reach OFBiz instance at {}, authentication failed", maskUrl(baseUrl));
            return false;
        }
        
        try {
            // Use OFBiz standard REST auth endpoint
            String url = baseUrl + "/rest/auth/token";
            
            // Create Basic Authentication header
            String credentials = username + ":" + password;
            String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Basic " + encodedCredentials)
                    .POST(HttpRequest.BodyPublishers.ofString("{}"));
            
            HttpRequest request = requestBuilder.build();
            
            logger.info("🌐 OFBIZ REST: Sending authentication request to: {}", maskUrl(url));
            logger.debug("🌐 OFBIZ REST: Using Basic Authentication for user: {}", username);
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            logger.info("🌐 OFBIZ REST: Received response - Status: {}, Body length: {} chars", 
                        response.statusCode(), response.body().length());
            logger.debug("🌐 OFBIZ REST: Response body: {}", response.body());
            
            if (response.statusCode() == 200) {
                try {
                    JsonNode responseJson = objectMapper.readTree(response.body());
                    
                    // Extract token from data.access_token (OFBiz standard format)
                    JsonNode dataNode = responseJson.path("data");
                    String token = dataNode.path("access_token").asText();
                    String tokenType = dataNode.path("token_type").asText();
                    int expiresIn = dataNode.path("expires_in").asInt();
                    
                    if (token != null && !token.isEmpty()) {
                        logger.info("✅ OFBIZ REST SUCCESS: User '{}' authenticated successfully", username);
                        logger.info("🎟️ Token details - Type: '{}', Length: {} chars, Expires in: {} seconds", 
                                   tokenType, token.length(), expiresIn);
                        
                        // Store token and expiration for subsequent requests
                        this.authToken = token;
                        this.tokenExpiresInSeconds = expiresIn;
                        return true;
                    } else {
                        String errorMsg = responseJson.path("errorMessage").asText(
                            responseJson.path("_ERROR_MESSAGE_").asText("No token in response"));
                        logger.warn("❌ OFBIZ REST FAILED: User '{}' authentication failed - {}", username, errorMsg);
                        logger.debug("Full response JSON: {}", responseJson.toString());
                        return false;
                    }
                } catch (Exception jsonEx) {
                    logger.error("❌ OFBIZ REST ERROR: Invalid JSON response for user '{}': {}", 
                               username, response.body().substring(0, Math.min(500, response.body().length())));
                    return false;
                }
            } else if (response.statusCode() == 401) {
                logger.warn("❌ OFBIZ REST FAILED: Invalid credentials (401) for user '{}'", username);
                return false;
            } else if (response.statusCode() == 404) {
                logger.error("❌ OFBIZ REST ERROR: Authentication endpoint not found (404) - check OFBiz configuration and URL: {}", maskUrl(url));
                return false;
            } else {
                logger.error("❌ OFBIZ REST ERROR: Authentication request failed with status: {} for user '{}', response: {}", 
                           response.statusCode(), username, response.body().substring(0, Math.min(500, response.body().length())));
                return false;
            }
            
        } catch (Exception e) {
            logger.error("💥 OFBIZ REST EXCEPTION: Error during REST authentication for user '{}': {}", 
                        username, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Retrieves user information and tenant data from OFBiz REST API
     * Uses the new getUserInfo service that returns data.email and data.tenantId
     * Requires prior authentication to have a valid auth token
     */
    public OFBizUserInfo getUserInfo(String username) {
        logger.debug("Retrieving user info for '{}' via OFBiz REST API", username);
        
        // Ensure we have an authentication token from prior authentication
        if (this.authToken == null || this.authToken.isEmpty()) {
            logger.warn("No authentication token available for user info lookup - authentication required first");
            return null;
        }
        
        try {
            // Use OFBiz REST service to get user info - POST to /rest/services/getUserInfo
            String url = baseUrl + userEndpoint; // This will be /rest/services/getUserInfo
            
            // Create request body with service parameters
            String requestBody = "{\"userLoginId\": \"" + username + "\"}";
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + this.authToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            
            HttpRequest request = requestBuilder.build();
            
            logger.trace("Sending user info request to: {} with body: {}", maskUrl(url), requestBody);
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            logger.trace("User info response status: {}, body: {}", response.statusCode(), response.body());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                
                // Check if we got data object
                JsonNode dataNode = responseJson.path("data");
                if (!dataNode.isMissingNode() && !dataNode.isNull()) {
                    
                    // Extract email and tenantId from data object
                    String email = dataNode.path("email").asText(null);
                    String tenantId = dataNode.path("tenantId").asText(null);
                    String userLoginId = dataNode.path("userLoginId").asText(username);
                    String partyId = dataNode.path("partyId").asText(null);
                    
                    // Extract firstName and lastName if available in the response
                    String firstName = dataNode.path("firstName").asText(null);
                    String lastName = dataNode.path("lastName").asText(null);
                    
                    // Check for missing mandatory fields and log warnings
                    boolean hasValidationErrors = false;
                    StringBuilder missingFields = new StringBuilder();
                    
                    if (firstName == null || firstName.trim().isEmpty()) {
                        logger.warn("⚠️  MISSING FIELD: firstName is missing for user '{}' - will use default value 'User'", username);
                        missingFields.append("firstName ");
                        firstName = "User"; // Default first name
                        hasValidationErrors = true;
                    }
                    
                    if (lastName == null || lastName.trim().isEmpty()) {
                        logger.warn("⚠️  MISSING FIELD: lastName is missing for user '{}' - will use username as fallback", username);
                        missingFields.append("lastName ");
                        lastName = userLoginId; // Use username as last name fallback
                        hasValidationErrors = true;
                    }
                    
                    if (email == null || email.trim().isEmpty()) {
                        logger.error("❌ MISSING MANDATORY FIELD: email is required for user '{}' but not provided by OFBiz", username);
                        missingFields.append("email ");
                        hasValidationErrors = true;
                    }
                    
                    if (tenantId == null || tenantId.trim().isEmpty()) {
                        logger.warn("⚠️  MISSING FIELD: tenantId is missing for user '{}' - will use default value 'default'", username);
                        missingFields.append("tenantId ");
                        tenantId = "default";
                        hasValidationErrors = true;
                    }
                    
                    if (hasValidationErrors) {
                        logger.error("🚨 USER DATA VALIDATION ISSUES for '{}': Missing fields: [{}]. This may cause authentication failures.", 
                                   username, missingFields.toString().trim());
                        logger.error("💡 SOLUTION: Ensure OFBiz user '{}' has complete profile data (firstName, lastName, email) in the Person and ContactMech tables", username);
                    }
                    
                    // Create user info with the retrieved data
                    OFBizUserInfo userInfo = new OFBizUserInfo(
                        userLoginId,
                        firstName,
                        lastName,  
                        email,
                        true, // enabled - assume enabled if found
                        tenantId != null ? tenantId : "default" // use tenantId from response
                    );
                    
                    // Add custom attributes from the response
                    if (partyId != null) {
                        userInfo.addCustomAttribute("partyId", partyId);
                    }
                    
                    logger.debug("Successfully retrieved user info for '{}' via REST API (email: '{}', tenant: '{}', name: '{} {}')", 
                               username, email, tenantId, firstName, lastName);
                    return userInfo;
                } else {
                    // Check for error in response
                    String errorMessage = responseJson.path("errorMessage").asText(
                        responseJson.path("_ERROR_MESSAGE_").asText("No data in response"));
                    logger.warn("User '{}' info request failed: {}", username, errorMessage);
                    return null;
                }
                
            } else if (response.statusCode() == 401) {
                logger.warn("Authentication token expired, need to re-authenticate");
                this.authToken = null;
                return null;
            } else {
                logger.warn("User info request failed with status: {} for user '{}'", 
                           response.statusCode(), username);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error retrieving user info for '{}' via REST API: {}", username, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Retrieves user information by email from OFBiz REST API
     */
    public OFBizUserInfo getUserInfoByEmail(String email) {
        logger.debug("Retrieving user info by email '{}' via OFBiz REST API", email);
        
        if (this.authToken == null || this.authToken.isEmpty()) {
            logger.debug("No auth token available, authentication required first");
            return null;
        }
        
        try {
            // Find party by email using ContactMech
            String url = baseUrl + "/rest/services/findPartyByEmail";
            String requestBody = "{\"email\": \"" + email + "\"}";
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + this.authToken)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            
            HttpRequest request = requestBuilder.build();
            
            logger.trace("Sending user info by email request to: {} with body: {}", maskUrl(url), requestBody);
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                JsonNode partyNode = responseJson.path("party");
                
                if (!partyNode.isMissingNode()) {
                    String partyId = partyNode.path("partyId").asText();
                    
                    // Now find UserLogin for this partyId
                    String userLoginUrl = baseUrl + "/rest/services/findUserLoginByPartyId";
                    String userLoginRequestBody = "{\"partyId\": \"" + partyId + "\"}";
                    
                    HttpRequest userLoginRequest = HttpRequest.newBuilder()
                            .uri(URI.create(userLoginUrl))
                            .timeout(Duration.ofMillis(timeout))
                            .header("Content-Type", "application/json")
                            .header("Accept", "application/json")
                            .header("Authorization", "Bearer " + this.authToken)
                            .POST(HttpRequest.BodyPublishers.ofString(userLoginRequestBody))
                            .build();
                    
                    HttpResponse<String> userLoginResponse = httpClient.send(userLoginRequest, 
                            HttpResponse.BodyHandlers.ofString());
                    
                    if (userLoginResponse.statusCode() == 200) {
                        JsonNode userLoginJson = objectMapper.readTree(userLoginResponse.body());
                        JsonNode userLoginNode = userLoginJson.path("userLogin");
                        
                        if (!userLoginNode.isMissingNode()) {
                            String username = userLoginNode.path("userLoginId").asText();
                            logger.info("Successfully found user by email '{}' -> username '{}'", email, username);
                            
                            return getUserInfo(username); // Get full user info
                        }
                    }
                }
            }
            
            logger.info("No user found with email '{}' via REST API", email);
            return null;
            
        } catch (Exception e) {
            logger.error("Error retrieving user info by email '{}' via REST API: {}", email, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Closes the HTTP client
     */
    public void close() {
        // HttpClient doesn't need explicit closing in Java 11+
        logger.debug("OFBizRestClient closed");
    }

    /**
     * Gets the current authentication token
     * @return the current OFBiz JWT token, or null if not authenticated
     */
    public String getAuthToken() {
        return this.authToken;
    }

    /**
     * Gets the token expiration time in seconds
     * @return the token expiration time in seconds from OFBiz response
     */
    public int getTokenExpiresInSeconds() {
        return this.tokenExpiresInSeconds;
    }

    /**
     * Masks URL for safe logging
     */
    private String maskUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Remove sensitive information from URLs
        return url.replaceAll("password=[^&]*", "password=***");
    }

    /**
     * Data class for OFBiz user information
     */
    public static class OFBizUserInfo {
        private final String username;
        private final String firstName;
        private final String lastName;
        private final String email;
        private final boolean enabled;
        private final String tenant;
        private final Map<String, String> customAttributes;

        public OFBizUserInfo(String username, String firstName, String lastName, 
                           String email, boolean enabled, String tenant) {
            this.username = username;
            this.firstName = firstName;
            this.lastName = lastName;
            this.email = email;
            this.enabled = enabled;
            this.tenant = tenant;
            this.customAttributes = new HashMap<>();
        }

        public void addCustomAttribute(String key, String value) {
            customAttributes.put(key, value);
        }

        // Getters
        public String getUsername() { return username; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public String getEmail() { return email; }
        public boolean isEnabled() { return enabled; }
        public String getTenant() { return tenant; }
        public Map<String, String> getCustomAttributes() { return new HashMap<>(customAttributes); }
    }
}
