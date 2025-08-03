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
    private final String apiKey;
    private final int timeout;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OFBizRestClient(ComponentModel model) {
        this.baseUrl = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_BASE_URL);
        this.authEndpoint = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_AUTH_ENDPOINT);
        this.userEndpoint = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_USER_ENDPOINT);
        this.apiKey = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_API_KEY);
        
        String timeoutStr = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_OFBIZ_TIMEOUT);
        this.timeout = timeoutStr != null && !timeoutStr.trim().isEmpty() ? 
                      Integer.parseInt(timeoutStr) : 5000;
        
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .build();
        
        this.objectMapper = new ObjectMapper();
        
        logger.debug("Created OFBizRestClient with baseUrl: {}, timeout: {}ms", 
                    maskUrl(baseUrl), timeout);
    }

    /**
     * Authenticates a user against OFBiz REST API
     */
    public boolean authenticateUser(String username, String password) {
        logger.debug("Authenticating user '{}' via OFBiz REST API", username);
        
        try {
            Map<String, Object> authRequest = new HashMap<>();
            authRequest.put("username", username);
            authRequest.put("password", password);
            
            String requestBody = objectMapper.writeValueAsString(authRequest);
            String url = baseUrl + authEndpoint;
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody));
            
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            
            HttpRequest request = requestBuilder.build();
            
            logger.trace("Sending authentication request to: {}", maskUrl(url));
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            logger.trace("Authentication response status: {}", response.statusCode());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                boolean success = responseJson.path("success").asBoolean(false);
                
                if (success) {
                    logger.info("✅ REST AUTH SUCCESS: User '{}' authenticated via OFBiz REST API", username);
                } else {
                    String errorMsg = responseJson.path("errorMessage").asText("Authentication failed");
                    logger.warn("❌ REST AUTH FAILED: User '{}' authentication failed: {}", username, errorMsg);
                }
                
                return success;
            } else {
                logger.warn("❌ REST AUTH ERROR: Authentication request failed with status: {} for user '{}'", 
                           response.statusCode(), username);
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error during REST authentication for user '{}': {}", username, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Retrieves user information and tenant data from OFBiz REST API
     */
    public OFBizUserInfo getUserInfo(String username) {
        logger.debug("Retrieving user info for '{}' via OFBiz REST API", username);
        
        try {
            String url = baseUrl + userEndpoint + "?username=" + username;
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .GET();
            
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            
            HttpRequest request = requestBuilder.build();
            
            logger.trace("Sending user info request to: {}", maskUrl(url));
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            logger.trace("User info response status: {}", response.statusCode());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                
                if (responseJson.path("success").asBoolean(false)) {
                    JsonNode userNode = responseJson.path("user");
                    
                    OFBizUserInfo userInfo = new OFBizUserInfo(
                        userNode.path("username").asText(username),
                        userNode.path("firstName").asText(null),
                        userNode.path("lastName").asText(null),
                        userNode.path("email").asText(null),
                        userNode.path("enabled").asBoolean(true),
                        userNode.path("tenant").asText(null)
                    );
                    
                    // Parse custom attributes
                    JsonNode attributesNode = userNode.path("attributes");
                    if (attributesNode.isObject()) {
                        attributesNode.fields().forEachRemaining(entry -> {
                            userInfo.addCustomAttribute(entry.getKey(), entry.getValue().asText());
                        });
                    }
                    
                    logger.info("Successfully retrieved user info for '{}' via REST API (tenant: '{}', attributes: {})", 
                               username, userInfo.getTenant(), userInfo.getCustomAttributes().size());
                    
                    return userInfo;
                } else {
                    String errorMsg = responseJson.path("errorMessage").asText("User not found");
                    logger.warn("User info request failed for '{}': {}", username, errorMsg);
                    return null;
                }
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
        
        try {
            String url = baseUrl + userEndpoint + "?email=" + email;
            
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofMillis(timeout))
                    .header("Content-Type", "application/json")
                    .GET();
            
            if (apiKey != null && !apiKey.trim().isEmpty()) {
                requestBuilder.header("Authorization", "Bearer " + apiKey);
            }
            
            HttpRequest request = requestBuilder.build();
            
            logger.trace("Sending user info by email request to: {}", maskUrl(url));
            
            HttpResponse<String> response = httpClient.send(request, 
                    HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode responseJson = objectMapper.readTree(response.body());
                
                if (responseJson.path("success").asBoolean(false)) {
                    JsonNode userNode = responseJson.path("user");
                    String username = userNode.path("username").asText();
                    
                    logger.info("Successfully found user by email '{}' -> username '{}'", email, username);
                    
                    return getUserInfo(username); // Get full user info
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
     * Masks URL for safe logging
     */
    private String maskUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Remove sensitive information from URLs
        return url.replaceAll("password=[^&]*", "password=***")
                  .replaceAll("apikey=[^&]*", "apikey=***");
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
