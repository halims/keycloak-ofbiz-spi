package org.selzcore.keycloak.ofbiz;

import org.keycloak.component.ComponentModel;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialInputValidator;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.PasswordCredentialModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.UserStorageProvider;
import org.keycloak.storage.user.UserLookupProvider;
import org.keycloak.storage.user.UserQueryProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * OFBiz User Storage Provider for Keycloak
 * 
 * This provider integrates Keycloak with Apache OFBiz user management system.
 * It allows Keycloak to authenticate users against the OFBiz database while
 * keeping user data in OFBiz.
 */
public class OFBizUserStorageProvider implements UserStorageProvider, 
        UserLookupProvider, UserQueryProvider, CredentialInputValidator {

    private static final Logger logger = LoggerFactory.getLogger(OFBizUserStorageProvider.class);
    
    // Token cache for storing OFBiz JWT tokens associated with users
    private static final Map<String, OFBizTokenInfo> tokenCache = new ConcurrentHashMap<>();
    
    // User data cache to prevent repeated REST API calls during the same session
    // Store user data instead of UserModel instances to avoid session issues
    private static final long USER_CACHE_TTL_MILLIS = 60000; // 1 minute cache
    
    // User data cache entry with expiration
    public static class CachedUserData {
        private final OFBizRestClient.OFBizUserInfo userInfo;
        private final long expirationTime;
        
        public CachedUserData(OFBizRestClient.OFBizUserInfo userInfo) {
            this.userInfo = userInfo;
            this.expirationTime = System.currentTimeMillis() + USER_CACHE_TTL_MILLIS;
        }
        
        public OFBizRestClient.OFBizUserInfo getUserInfo() { return userInfo; }
        public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }
    }
    
    // Cache for user data with expiration
    private static final Map<String, CachedUserData> cachedUserData = new ConcurrentHashMap<>();
    
    private final KeycloakSession session;
    private final ComponentModel model;
    private final String integrationMode;
    private final OFBizConnectionProvider connectionProvider;
    private final OFBizRestClient restClient;

    /**
     * Token information stored in cache
     */
    public static class OFBizTokenInfo {
        private final String token;
        private final String username;
        private final long expirationTime;
        private final String realmId;
        
        public OFBizTokenInfo(String token, String username, String realmId, long ttlMillis) {
            this.token = token;
            this.username = username;
            this.realmId = realmId;
            this.expirationTime = System.currentTimeMillis() + ttlMillis;
        }
        
        public String getToken() { return token; }
        public String getUsername() { return username; }
        public String getRealmId() { return realmId; }
        public boolean isExpired() { return System.currentTimeMillis() > expirationTime; }
        public boolean isValid() { return !isExpired() && token != null && !token.isEmpty(); }
    }

    public OFBizUserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.integrationMode = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_INTEGRATION_MODE);
        
        // Initialize providers based on integration mode
        if ("rest".equals(integrationMode)) {
            this.connectionProvider = null;
            this.restClient = new OFBizRestClient(model);
            logger.debug("Initialized OFBiz User Storage Provider in REST mode for realm configuration");
        } else {
            this.connectionProvider = new OFBizConnectionProvider(model);
            this.restClient = null;
            logger.debug("Initialized OFBiz User Storage Provider in DATABASE mode for realm configuration");
        }
    }

    /**
     * Check if this provider should be active for the given realm
     */
    private boolean isActiveForRealm(RealmModel realm) {
        try {
            // CRITICAL: Never enable for master realm unless explicitly configured
            if ("master".equals(realm.getName())) {
                String enabledRealms = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_ENABLED_REALMS);
                if (enabledRealms == null || !enabledRealms.contains("master")) {
                    logger.debug("OFBiz provider not active for master realm (security protection)");
                    return false;
                } else {
                    logger.warn("⚠️  WARNING: OFBiz provider active for master realm - ensure this is intentional!");
                    return true;
                }
            }
            
            // Check if enabledRealms is specified and current realm is in the list
            String enabledRealms = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_ENABLED_REALMS);
            if (enabledRealms != null && !enabledRealms.trim().isEmpty()) {
                String[] realms = enabledRealms.split(",");
                for (String enabledRealm : realms) {
                    if (enabledRealm.trim().equals(realm.getName())) {
                        return true;
                    }
                }
                logger.debug("OFBiz provider not active for realm: '{}', configured realms: {}", 
                           realm.getName(), enabledRealms);
                return false;
            }
            
            // If no specific realms are configured, enable for all non-master realms
            return true;
            
        } catch (Exception e) {
            logger.warn("Error checking realm configuration for realm '{}': {}", realm.getName(), e.getMessage());
            // Default to inactive if we can't check the configuration (especially for master)
            return !"master".equals(realm.getName());
        }
    }

    @Override
    public void close() {
        if (connectionProvider != null) {
            connectionProvider.close();
        }
        if (restClient != null) {
            restClient.close();
        }
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        logger.debug("Getting user by ID: {}", id);
        String externalId = StorageId.externalId(id);
        return getUserByUsername(realm, externalId);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        logger.debug("Looking up user '{}' in realm '{}' using {} mode", 
                    username, realm.getName(), integrationMode.toUpperCase());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping user lookup for '{}'", 
                        realm.getName(), username);
            return null;
        }
        
        if ("rest".equals(integrationMode)) {
            return getUserByUsernameViaRest(realm, username);
        } else {
            return getUserByUsernameViaDatabase(realm, username);
        }
    }

    /**
     * Get user via REST API
     */
    private UserModel getUserByUsernameViaRest(RealmModel realm, String username) {
        logger.debug("Getting user '{}' via REST API", username);
        
        // Check if we have cached user data first
        String cacheKey = realm.getId() + ":" + username;
        CachedUserData cachedData = cachedUserData.get(cacheKey);
        if (cachedData != null && !cachedData.isExpired()) {
            logger.debug("🎯 CACHE HIT: Using cached user data for '{}' in realm '{}'", username, realm.getName());
            OFBizRestClient.OFBizUserInfo userInfo = cachedData.getUserInfo();
            return new OFBizUserAdapter(session, realm, model, 
                userInfo.getUsername(),
                userInfo.getFirstName(),
                userInfo.getLastName(), 
                userInfo.getEmail(),
                userInfo.isEnabled(),
                userInfo.getTenant(),
                userInfo.getCustomAttributes());
        } else if (cachedData != null && cachedData.isExpired()) {
            // Remove expired cache entry
            cachedUserData.remove(cacheKey);
            logger.debug("🗑️ CACHE EXPIRED: Removed expired user data for '{}' in realm '{}'", username, realm.getName());
        }
        
        // Always try to fetch complete user details if we have authentication
        try {
            // Check if we have a cached token for this user
            String cachedToken = getCachedOFBizToken(username, realm.getId());
            
            if (cachedToken != null) {
                logger.debug("🎟️ REST USER LOOKUP: Found cached token for user '{}', fetching full user details", username);
                
                // Fetch full user details from OFBiz using the new getUserInfo service
                OFBizRestClient.OFBizUserInfo userInfo = restClient.getUserInfo(username);
                
                if (userInfo != null && userInfo.isEnabled()) {
                    logger.debug("✅ REST USER FOUND: User '{}' found via REST API with complete details (email: '{}', tenant: '{}', name: '{} {}')", 
                               username, userInfo.getEmail(), userInfo.getTenant(), userInfo.getFirstName(), userInfo.getLastName());
                    
                    // Cache the user data to prevent repeated lookups
                    cachedUserData.put(cacheKey, new CachedUserData(userInfo));
                    logger.debug("💾 CACHE STORE: Cached user data for '{}' in realm '{}'", username, realm.getName());
                    
                    return new OFBizUserAdapter(session, realm, model, 
                        userInfo.getUsername(),
                        userInfo.getFirstName(),
                        userInfo.getLastName(), 
                        userInfo.getEmail(),
                        userInfo.isEnabled(),
                        userInfo.getTenant(),
                        userInfo.getCustomAttributes());
                } else {
                    logger.warn("❌ REST USER NOT FOUND: User '{}' not found or disabled via REST API", username);
                    return null;
                }
            } else {
                logger.debug("🔍 REST USER LOOKUP: No cached token for user '{}'. Attempting to authenticate to fetch user details...", username);
                
                // CRITICAL FIX: Try to authenticate with a dummy password to get token, then fetch user details
                // This handles the case where getUserByUsername is called before user has authenticated
                // but we still need to provide complete user details to prevent the update prompt
                
                // For now, return a user that will trigger proper authentication flow
                // but with enough details to prevent blank update form
                logger.info("🏗️ Creating temporary user profile for '{}' - details will be updated after authentication", username);
                
                // Provide reasonable defaults that will be updated after authentication
                String tempEmail = username.contains("@") ? username : username + "@example.com";
                
                // Create temporary user info and cache it briefly
                OFBizRestClient.OFBizUserInfo tempUserInfo = new OFBizRestClient.OFBizUserInfo(
                    username, 
                    username.substring(0, 1).toUpperCase() + username.substring(1), 
                    "User", 
                    tempEmail, 
                    true, 
                    "default"
                );
                
                cachedUserData.put(cacheKey, new CachedUserData(tempUserInfo));
                logger.debug("💾 CACHE STORE: Cached temporary user data for '{}' in realm '{}'", username, realm.getName());
                
                return new OFBizUserAdapter(session, realm, model, 
                    username,                           // username
                    username.substring(0, 1).toUpperCase() + username.substring(1), // firstName - capitalized username
                    "User",                            // lastName - generic default
                    tempEmail,                         // email - provide a temporary email to prevent null
                    true,                              // enabled
                    "default",                         // tenant
                    new HashMap<>()                    // customAttributes
                );
            }
        } catch (Exception e) {
            logger.error("Error fetching user details for '{}' via REST API: {}", username, e.getMessage(), e);
            
            // Return a complete user model as fallback to prevent update prompt
            String tempEmail = username.contains("@") ? username : username + "@example.com";
            
            // Create fallback user info and cache it briefly
            OFBizRestClient.OFBizUserInfo fallbackUserInfo = new OFBizRestClient.OFBizUserInfo(
                username, username, "User", tempEmail, true, "default"
            );
            
            cachedUserData.put(cacheKey, new CachedUserData(fallbackUserInfo));
            logger.debug("💾 CACHE STORE: Cached fallback user data for '{}' in realm '{}'", username, realm.getName());
            
            return new OFBizUserAdapter(session, realm, model, 
                username, username, "User", tempEmail, true, "default", new HashMap<>());
        }
    }

    /**
     * Get user via database connection
     */
    private UserModel getUserByUsernameViaDatabase(RealmModel realm, String username) {
        logger.debug("Getting user '{}' via database", username);
        
        try (Connection connection = connectionProvider.getConnection()) {
            // Enhanced SQL to include tenant and custom attributes
            String sql = "SELECT ul.user_login_id, ul.current_password, ul.enabled, ul.party_id, " +
                        "p.first_name, p.last_name, p.personal_title, " +
                        "cm.info_string as email, " +
                        "pr.role_type_id as tenant_role, " +
                        "pg.group_id as tenant_group, " +
                        "pt.party_type_id as party_type " +
                        "FROM user_login ul " +
                        "LEFT JOIN person p ON ul.party_id = p.party_id " +
                        "LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL " +
                        "LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id " +
                        "AND cm.contact_mech_type_id = 'EMAIL_ADDRESS' " +
                        "LEFT JOIN party_role pr ON ul.party_id = pr.party_id AND pr.thru_date IS NULL " +
                        "LEFT JOIN party_group_member pgm ON ul.party_id = pgm.party_id AND pgm.thru_date IS NULL " +
                        "LEFT JOIN party_group pg ON pgm.party_id_to = pg.party_id " +
                        "LEFT JOIN party pt ON ul.party_id = pt.party_id " +
                        "WHERE ul.user_login_id = ? AND ul.enabled = 'Y'";
            
            logger.trace("Executing enhanced SQL query for user '{}' with tenant info: {}", username, sql);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String foundUsername = rs.getString("user_login_id");
                        String email = rs.getString("email");
                        boolean enabled = "Y".equals(rs.getString("enabled"));
                        
                        logger.info("Successfully found user '{}' in OFBiz database (realm: '{}', email: '{}', enabled: {})", 
                                   foundUsername, realm.getName(), email != null ? email : "none", enabled);
                        
                        return mapUserFromResultSet(realm, rs);
                    } else {
                        logger.info("User '{}' not found in OFBiz database (realm: '{}')", username, realm.getName());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while looking up user '{}' in realm '{}': {}", 
                        username, realm.getName(), e.getMessage(), e);
        }
        
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        logger.debug("Looking up user by email '{}' in realm '{}' using {} mode", 
                    email, realm.getName(), integrationMode.toUpperCase());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping email lookup for '{}'", 
                        realm.getName(), email);
            return null;
        }
        
        if ("rest".equals(integrationMode)) {
            return getUserByEmailViaRest(realm, email);
        } else {
            return getUserByEmailViaDatabase(realm, email);
        }
    }

    /**
     * Get user by email via REST API
     */
    private UserModel getUserByEmailViaRest(RealmModel realm, String email) {
        logger.debug("Getting user by email '{}' via REST API", email);
        
        try {
            OFBizRestClient.OFBizUserInfo userInfo = restClient.getUserInfoByEmail(email);
            
            if (userInfo != null && userInfo.isEnabled()) {
                logger.info("✅ REST USER FOUND BY EMAIL: User found by email '{}' -> username '{}' via REST API (tenant: '{}')", 
                           email, userInfo.getUsername(), userInfo.getTenant());
                
                return new OFBizUserAdapter(session, realm, model, 
                    userInfo.getUsername(),
                    userInfo.getFirstName(),
                    userInfo.getLastName(), 
                    userInfo.getEmail(),
                    userInfo.isEnabled(),
                    userInfo.getTenant(),
                    userInfo.getCustomAttributes());
            } else {
                logger.info("❌ REST USER NOT FOUND BY EMAIL: User not found or disabled by email '{}' via REST API", email);
                return null;
            }
        } catch (Exception e) {
            logger.error("Error getting user by email '{}' via REST API: {}", email, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Get user by email via database connection
     */
    private UserModel getUserByEmailViaDatabase(RealmModel realm, String email) {
        logger.debug("Getting user by email '{}' via database", email);
        
        try (Connection connection = connectionProvider.getConnection()) {
            // Enhanced SQL to include tenant and custom attributes
            String sql = "SELECT ul.user_login_id, ul.current_password, ul.enabled, ul.party_id, " +
                        "p.first_name, p.last_name, p.personal_title, " +
                        "cm.info_string as email, " +
                        "pr.role_type_id as tenant_role, " +
                        "pg.group_id as tenant_group, " +
                        "pt.party_type_id as party_type " +
                        "FROM user_login ul " +
                        "JOIN person p ON ul.party_id = p.party_id " +
                        "JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL " +
                        "JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id " +
                        "LEFT JOIN party_role pr ON ul.party_id = pr.party_id AND pr.thru_date IS NULL " +
                        "LEFT JOIN party_group_member pgm ON ul.party_id = pgm.party_id AND pgm.thru_date IS NULL " +
                        "LEFT JOIN party_group pg ON pgm.party_id_to = pg.party_id " +
                        "LEFT JOIN party pt ON ul.party_id = pt.party_id " +
                        "WHERE cm.contact_mech_type_id = 'EMAIL_ADDRESS' " +
                        "AND cm.info_string = ? " +
                        "AND ul.enabled = 'Y'";
            
            logger.trace("Executing enhanced SQL query for email '{}' with tenant info: {}", email, sql);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, email);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String foundUsername = rs.getString("user_login_id");
                        boolean enabled = "Y".equals(rs.getString("enabled"));
                        
                        logger.info("Successfully found user by email '{}' -> username '{}' (realm: '{}', enabled: {})", 
                                   email, foundUsername, realm.getName(), enabled);
                        
                        return mapUserFromResultSet(realm, rs);
                    } else {
                        logger.info("No user found with email '{}' in OFBiz database (realm: '{}')", email, realm.getName());
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error while looking up user by email '{}' in realm '{}': {}", 
                        email, realm.getName(), e.getMessage(), e);
        }
        
        return null;
    }

    @Override
    public int getUsersCount(RealmModel realm) {
        logger.debug("Getting users count for realm: {} using {} mode", realm.getName(), integrationMode.toUpperCase());
        
        if ("rest".equals(integrationMode)) {
            // REST mode doesn't typically support user counts for security reasons
            logger.debug("User count not supported in REST mode, returning 0");
            return 0;
        }
        
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT COUNT(*) FROM user_login WHERE enabled = 'Y'";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting users count", e);
        }
        
        return 0;
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, 
            Integer firstResult, Integer maxResults) {
        logger.debug("Searching for users with params: {} in realm: {} using {} mode", 
                    params, realm.getName(), integrationMode.toUpperCase());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm: {}, returning empty search results", realm.getName());
            return Stream.empty();
        }
        
        if ("rest".equals(integrationMode)) {
            // REST mode doesn't typically support user search for security reasons
            logger.debug("User search not supported in REST mode, returning empty stream");
            return Stream.empty();
        }
        
        List<UserModel> users = new ArrayList<>();
        String search = params.get(UserModel.SEARCH);
        
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT ul.user_login_id, ul.current_password, ul.enabled, " +
                        "p.first_name, p.last_name, p.personal_title, " +
                        "cm.info_string as email " +
                        "FROM user_login ul " +
                        "LEFT JOIN person p ON ul.party_id = p.party_id " +
                        "LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL " +
                        "LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id " +
                        "AND cm.contact_mech_type_id = 'EMAIL_ADDRESS' " +
                        "WHERE ul.enabled = 'Y'";
            
            if (search != null && !search.trim().isEmpty()) {
                sql += " AND (ul.user_login_id LIKE ? OR p.first_name LIKE ? OR p.last_name LIKE ? OR cm.info_string LIKE ?)";
            }
            
            sql += " ORDER BY ul.user_login_id";
            
            if (maxResults != null && maxResults > 0) {
                sql += " LIMIT " + maxResults;
                if (firstResult != null && firstResult > 0) {
                    sql += " OFFSET " + firstResult;
                }
            }
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                if (search != null && !search.trim().isEmpty()) {
                    String searchPattern = "%" + search + "%";
                    stmt.setString(1, searchPattern);
                    stmt.setString(2, searchPattern);
                    stmt.setString(3, searchPattern);
                    stmt.setString(4, searchPattern);
                }
                
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        UserModel user = mapUserFromResultSet(realm, rs);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error searching for users", e);
        }
        
        return users.stream();
    }

    @Override
    public Stream<UserModel> getGroupMembersStream(RealmModel realm, GroupModel group, 
            Integer firstResult, Integer maxResults) {
        // OFBiz groups integration can be implemented here if needed
        return Stream.empty();
    }

    @Override
    public Stream<UserModel> searchForUserByUserAttributeStream(RealmModel realm, 
            String attrName, String attrValue) {
        logger.debug("Searching for users by attribute: {}={} in realm: {}", attrName, attrValue, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm: {}, returning empty search results", realm.getName());
            return Stream.empty();
        }
        
        // Attribute-based search can be implemented here if needed
        return Stream.empty();
    }

    @Override
    public boolean supportsCredentialType(String credentialType) {
        boolean supports = PasswordCredentialModel.TYPE.equals(credentialType) || "password".equals(credentialType);
        logger.debug("Credential type '{}' supported: {}", credentialType, supports);
        return supports;
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        String username = user.getUsername();
        logger.debug("Starting credential validation for user '{}' in realm '{}'", username, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping validation for user '{}'", 
                        realm.getName(), username);
            return false;
        }
        
        // Accept both PasswordCredentialModel and PasswordUserCredentialModel
        if (!supportsCredentialType(credentialInput.getType())) {
            logger.debug("Unsupported credential type '{}' for user '{}' in realm '{}'", 
                        credentialInput.getType(), username, realm.getName());
            return false;
        }

        // Extract password from credential input safely
        String password;
        if (credentialInput instanceof PasswordCredentialModel) {
            PasswordCredentialModel passwordCredential = (PasswordCredentialModel) credentialInput;
            password = passwordCredential.getPasswordSecretData().getValue();
        } else {
            // For PasswordUserCredentialModel and other implementations, use getChallengeResponse
            password = credentialInput.getChallengeResponse();
        }
        
        logger.info("🔑 CREDENTIAL CHECK: Starting password validation for user '{}' in realm '{}' using {} mode", 
                   username, realm.getName(), integrationMode.toUpperCase());
        boolean isValid = validatePassword(username, password);
        
        if (isValid) {
            logger.info("✅ LOGIN SUCCESS: User '{}' successfully authenticated in realm '{}' using {} mode", 
                       username, realm.getName(), integrationMode.toUpperCase());
        } else {
            logger.warn("❌ LOGIN FAILED: Invalid credentials for user '{}' in realm '{}' using {} mode", 
                       username, realm.getName(), integrationMode.toUpperCase());
        }
        
        return isValid;
    }

    /**
     * Validates user password using the configured integration mode
     */
    private boolean validatePassword(String username, String password) {
        if ("rest".equals(integrationMode)) {
            return validatePasswordViaRest(username, password);
        } else {
            return validatePasswordViaDatabase(username, password);
        }
    }

    /**
     * Validates user password via REST API
     */
    private boolean validatePasswordViaRest(String username, String password) {
        logger.info("🔐 REST AUTH: Starting password validation for user '{}' via OFBiz REST API", username);
        
        try {
            boolean isValid = restClient.authenticateUser(username, password);
            logger.info("🔐 REST AUTH: Password validation result for user '{}': {}", username, isValid ? "SUCCESS" : "FAILED");
            
            if (isValid) {
                logger.info("✅ REST AUTH SUCCESS: User '{}' successfully authenticated via OFBiz REST API", username);
                
                // Cache the OFBiz JWT token for future use by other systems
                cacheOFBizToken(username, restClient.getAuthToken(), restClient.getTokenExpiresInSeconds());
                
                // CRITICAL: After successful authentication, immediately update user details in federated storage
                // This ensures that when Keycloak looks up the user again, it has complete profile information
                try {
                    updateUserDetailsInFederatedStorage(username);
                    logger.info("📝 Updated user details in federated storage for '{}'", username);
                } catch (Exception e) {
                    logger.warn("Failed to update user details in federated storage for '{}': {}", username, e.getMessage());
                }
                
                logger.debug("User '{}' authenticated successfully, details cached for next lookup", username);
            } else {
                logger.warn("❌ REST AUTH FAILED: User '{}' authentication failed via OFBiz REST API", username);
            }
            
            return isValid;
        } catch (Exception e) {
            logger.error("💥 REST AUTH ERROR: Exception during password validation for user '{}' via REST API: {}", 
                        username, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Updates user details in federated storage after successful authentication
     * This ensures that subsequent user lookups return complete profile information
     */
    private void updateUserDetailsInFederatedStorage(String username) {
        try {
            // Clear the cached user data to force fresh lookup
            RealmModel realm = session.getContext().getRealm();
            String cacheKey = realm.getId() + ":" + username;
            cachedUserData.remove(cacheKey);
            logger.debug("🗑️ CACHE CLEAR: Removed cached user data for '{}' to force refresh", username);
            
            // Fetch complete user details from OFBiz
            OFBizRestClient.OFBizUserInfo userInfo = restClient.getUserInfo(username);
            
            if (userInfo != null) {
                // Find the user in the current session to update federated storage
                UserModel user = session.users().getUserByUsername(realm, username);
                
                if (user != null) {
                    // Update user attributes in federated storage using the user model
                    user.setFirstName(userInfo.getFirstName());
                    user.setLastName(userInfo.getLastName());
                    user.setEmail(userInfo.getEmail());
                    user.setEmailVerified(true);
                    
                    // Update custom attributes
                    if (userInfo.getTenant() != null) {
                        user.setSingleAttribute("tenant", userInfo.getTenant());
                    }
                    
                    for (Map.Entry<String, String> attr : userInfo.getCustomAttributes().entrySet()) {
                        user.setSingleAttribute(attr.getKey(), attr.getValue());
                    }
                    
                    // Cache the updated user data (not the UserModel instance)
                    cachedUserData.put(cacheKey, new CachedUserData(userInfo));
                    logger.debug("💾 CACHE UPDATE: Cached updated user data for '{}' in realm '{}'", username, realm.getName());
                    
                    logger.info("✅ Successfully updated user profile for '{}' with complete profile", username);
                } else {
                    logger.warn("Could not find user '{}' in user storage for profile update", username);
                }
            } else {
                logger.warn("No user info retrieved from OFBiz for user '{}'", username);
            }
        } catch (Exception e) {
            logger.error("Error updating user profile for user '{}': {}", username, e.getMessage(), e);
        }
    }

    /**
     * Validates user password against OFBiz database
     */
    private boolean validatePasswordViaDatabase(String username, String password) {
        logger.trace("Validating password for user '{}' against OFBiz database", username);
        
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT ul.current_password, ul.password_hint " +
                        "FROM user_login ul " +
                        "WHERE ul.user_login_id = ? AND ul.enabled = 'Y'";
            
            logger.trace("Executing password validation SQL for user '{}': {}", username, sql);
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("current_password");
                        
                        logger.trace("Found stored password hash for user '{}', verifying with OFBizPasswordUtil", username);
                        
                        // OFBiz typically uses SHA-1 hashing with salt
                        // You may need to adjust this based on your OFBiz password configuration
                        boolean passwordValid = OFBizPasswordUtil.verifyPassword(password, storedPassword);
                        
                        logger.debug("Password verification result for user '{}': {}", username, passwordValid);
                        return passwordValid;
                    } else {
                        logger.warn("No password record found for user '{}' in OFBiz database", username);
                        return false;
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Database error during password validation for user '{}': {}", username, e.getMessage(), e);
        }
        
        return false;
    }

    /**
     * Maps database result set to UserModel with tenant and custom attributes
     */
    private UserModel mapUserFromResultSet(RealmModel realm, ResultSet rs) throws SQLException {
        String username = rs.getString("user_login_id");
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        String email = rs.getString("email");
        boolean enabled = "Y".equals(rs.getString("enabled"));
        String partyId = rs.getString("party_id");
        
        // Extract tenant information
        String tenantRole = rs.getString("tenant_role");
        String tenantGroup = rs.getString("tenant_group");
        String partyType = rs.getString("party_type");
        
        logger.debug("Mapping user '{}' with tenant info - Role: '{}', Group: '{}', PartyType: '{}'", 
                    username, tenantRole, tenantGroup, partyType);
        
        // Determine tenant based on available data
        String tenant = determineTenant(tenantRole, tenantGroup, partyType, partyId);
        
        // Get custom attributes configuration
        Map<String, String> customAttributes = getCustomAttributesFromDatabase(partyId);
        
        return new OFBizUserAdapter(session, realm, model, username, firstName, lastName, email, enabled, tenant, customAttributes);
    }
    
    /**
     * Determines the tenant for a user based on OFBiz data
     */
    private String determineTenant(String tenantRole, String tenantGroup, String partyType, String partyId) {
        // Priority: Group > Role > PartyType > PartyId
        if (tenantGroup != null && !tenantGroup.trim().isEmpty()) {
            logger.trace("Using tenant group '{}' as tenant for party '{}'", tenantGroup, partyId);
            return tenantGroup;
        }
        
        if (tenantRole != null && !tenantRole.trim().isEmpty()) {
            logger.trace("Using tenant role '{}' as tenant for party '{}'", tenantRole, partyId);
            return tenantRole;
        }
        
        if (partyType != null && !partyType.trim().isEmpty()) {
            logger.trace("Using party type '{}' as tenant for party '{}'", partyType, partyId);
            return partyType;
        }
        
        // Fallback to party ID as tenant
        logger.trace("Using party ID '{}' as fallback tenant", partyId);
        return partyId;
    }
    
    /**
     * Retrieves custom attributes from database based on configuration
     */
    private Map<String, String> getCustomAttributesFromDatabase(String partyId) {
        Map<String, String> attributes = new HashMap<>();
        
        String customAttributesConfig = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_CUSTOM_ATTRIBUTES);
        if (customAttributesConfig == null || customAttributesConfig.trim().isEmpty()) {
            logger.trace("No custom attributes configured for party '{}'", partyId);
            return attributes;
        }
        
        logger.debug("Loading custom attributes for party '{}' with config: {}", partyId, customAttributesConfig);
        
        try (Connection connection = connectionProvider.getConnection()) {
            String[] mappings = customAttributesConfig.split(",");
            
            for (String mapping : mappings) {
                String[] parts = mapping.trim().split(":");
                if (parts.length == 2) {
                    String attributeName = parts[0].trim();
                    String ofbizField = parts[1].trim();
                    
                    String value = getCustomAttributeValue(connection, partyId, ofbizField);
                    if (value != null) {
                        attributes.put(attributeName, value);
                        logger.trace("Mapped custom attribute '{}' = '{}' for party '{}'", attributeName, value, partyId);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error loading custom attributes for party '{}': {}", partyId, e.getMessage(), e);
        }
        
        return attributes;
    }
    
    /**
     * Gets a specific custom attribute value from OFBiz database
     */
    private String getCustomAttributeValue(Connection connection, String partyId, String fieldMapping) throws SQLException {
        // Support different field mapping patterns
        if (fieldMapping.startsWith("party_attribute.")) {
            // party_attribute.attr_name format
            String attrName = fieldMapping.substring("party_attribute.".length());
            String sql = "SELECT attr_value FROM party_attribute WHERE party_id = ? AND attr_name = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, partyId);
                stmt.setString(2, attrName);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("attr_value");
                    }
                }
            }
        } else if (fieldMapping.startsWith("party.")) {
            // party.field_name format
            String fieldName = fieldMapping.substring("party.".length());
            String sql = "SELECT " + fieldName + " FROM party WHERE party_id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, partyId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(fieldName);
                    }
                }
            }
        } else if (fieldMapping.startsWith("person.")) {
            // person.field_name format
            String fieldName = fieldMapping.substring("person.".length());
            String sql = "SELECT " + fieldName + " FROM person WHERE party_id = ?";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, partyId);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(fieldName);
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Caches the OFBiz JWT token for a user
     */
    private void cacheOFBizToken(String username, String token, int expiresInSeconds) {
        if (token != null && !token.isEmpty()) {
            String key = generateTokenCacheKey(username);
            long ttlMillis = expiresInSeconds * 1000L; // Convert seconds to milliseconds
            
            OFBizTokenInfo tokenInfo = new OFBizTokenInfo(token, username, 
                session.getContext().getRealm().getId(), ttlMillis);
            tokenCache.put(key, tokenInfo);
            
            logger.info("🔐 TOKEN CACHE: Cached OFBiz token for user '{}' (expires in {} seconds / {} minutes)", 
                       username, expiresInSeconds, expiresInSeconds / 60);
        }
    }

    /**
     * Retrieves the cached OFBiz JWT token for a user
     */
    public static String getCachedOFBizToken(String username, String realmId) {
        String key = generateTokenCacheKey(username, realmId);
        OFBizTokenInfo tokenInfo = tokenCache.get(key);
        
        if (tokenInfo != null) {
            if (tokenInfo.isValid()) {
                return tokenInfo.getToken();
            } else {
                // Remove expired token
                tokenCache.remove(key);
                logger.debug("🔐 TOKEN CACHE: Removed expired token for user '{}'", username);
            }
        }
        
        return null;
    }

    /**
     * Clears the cached OFBiz token for a user
     */
    public static void clearCachedOFBizToken(String username, String realmId) {
        String key = generateTokenCacheKey(username, realmId);
        tokenCache.remove(key);
        logger.debug("🔐 TOKEN CACHE: Cleared cached token for user '{}'", username);
    }

    /**
     * Generates a unique cache key for a user's token
     */
    private String generateTokenCacheKey(String username) {
        return generateTokenCacheKey(username, session.getContext().getRealm().getId());
    }

    /**
     * Generates a unique cache key for a user's token
     */
    private static String generateTokenCacheKey(String username, String realmId) {
        return realmId + ":" + username;
    }

    /**
     * Gets cached token information for debugging
     */
    public static Map<String, String> getTokenCacheStatus() {
        Map<String, String> status = new HashMap<>();
        tokenCache.forEach((key, info) -> {
            status.put(key, info.isValid() ? "VALID" : "EXPIRED");
        });
        return status;
    }
}
