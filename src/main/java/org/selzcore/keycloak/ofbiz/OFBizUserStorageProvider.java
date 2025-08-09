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
        this.restClient = new OFBizRestClient(model);
        logger.debug("Initialized OFBiz User Storage Provider in REST mode for OAuth 2.0/OIDC compliance");
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
        if (restClient != null) {
            restClient.close();
        }
    }

    /**
     * LOGOUT FIX: Clears user session data on logout
     * This method should be called when a user logs out to clear cached tokens and user data
     */
    public static void clearUserSession(String username, String realmId) {
        logger.info("🚪 LOGOUT CLEANUP: Clearing session data for user '{}' in realm '{}'", username, realmId);
        
        // Clear cached OFBiz token
        clearCachedOFBizToken(username, realmId);
        
        // Clear cached user data
        String userCacheKey = realmId + ":" + username;
        CachedUserData removedUserData = cachedUserData.remove(userCacheKey);
        if (removedUserData != null) {
            logger.debug("🗑️  LOGOUT CLEANUP: Removed cached user data for '{}'", username);
        }
        
        logger.info("✅ LOGOUT CLEANUP: Session data cleared for user '{}' - next login will require fresh authentication", username);
    }

    /**
     * LOGOUT FIX: Clear all session data for a realm
     * Useful for testing or admin operations
     */
    public static void clearAllSessionsForRealm(String realmId) {
        logger.info("🚪 LOGOUT CLEANUP: Clearing all session data for realm '{}'", realmId);
        
        // Clear all tokens for this realm
        tokenCache.entrySet().removeIf(entry -> entry.getKey().startsWith(realmId + ":"));
        
        // Clear all user data for this realm
        cachedUserData.entrySet().removeIf(entry -> entry.getKey().startsWith(realmId + ":"));
        
        logger.info("✅ LOGOUT CLEANUP: All session data cleared for realm '{}'", realmId);
    }

    @Override
    public UserModel getUserById(RealmModel realm, String id) {
        logger.debug("Getting user by ID: {}", id);
        String externalId = StorageId.externalId(id);
        return getUserByUsername(realm, externalId);
    }

    @Override
    public UserModel getUserByUsername(RealmModel realm, String username) {
        logger.debug("Looking up user '{}' in realm '{}' using REST mode", 
                    username, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping user lookup for '{}'", 
                        realm.getName(), username);
            return null;
        }
        
        return getUserByUsernameViaRest(realm, username);
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
                    logger.debug("❌ REST USER NOT FOUND: User '{}' not found or disabled via REST API (even with cached token)", username);
                    // Token might be invalid, clear it and fall through to temporary user creation
                    clearCachedOFBizToken(username, realm.getId());
                }
            }
            
            // NO CACHED TOKEN OR TOKEN INVALID: Create temporary user for authentication flow
            logger.debug("🔍 REST USER LOOKUP: No valid cached token for user '{}'. Creating temporary user for authentication flow", username);
            
            // LOGOUT FIX: Always provide a temporary user when no cached token is available
            // This ensures that after logout, users can still be looked up and authenticated
            logger.info("🏗️ Creating temporary user profile for '{}' - details will be updated after authentication", username);
            logger.warn("⚠️  TEMPORARY DATA: Using placeholder values for user '{}' because authentication is required first", username);
            logger.warn("   • firstName: Will use capitalized username as placeholder");
            logger.warn("   • lastName: Will use 'User' as placeholder");
            logger.warn("   • email: Will use '{}@example.com' as placeholder", username);
            logger.warn("   • tenantId: Will use 'default' as placeholder");
            logger.warn("💡 AFTER AUTHENTICATION: Profile will be updated with real data from OFBiz");
            
            // Provide reasonable defaults that will be updated after authentication
            String tempEmail = username.contains("@") ? username : username + "@example.com";
            
            // Create temporary user info and cache it briefly (shorter TTL for temporary users)
            OFBizRestClient.OFBizUserInfo tempUserInfo = new OFBizRestClient.OFBizUserInfo(
                username, 
                username.substring(0, 1).toUpperCase() + username.substring(1), 
                "User", 
                tempEmail, 
                true, 
                "default"
            );
            
            // Cache temporarily - this will be replaced with real data after authentication
            cachedUserData.put(cacheKey, new CachedUserData(tempUserInfo)); 
            logger.debug("💾 CACHE STORE: Cached temporary user data for '{}' in realm '{}' (will be replaced after auth)", username, realm.getName());
            
            return new OFBizUserAdapter(session, realm, model, 
                username,                           // username
                username.substring(0, 1).toUpperCase() + username.substring(1), // firstName - capitalized username
                "User",                            // lastName - generic default
                tempEmail,                         // email - provide a temporary email to prevent null
                true,                              // enabled
                "default",                         // tenant
                new HashMap<>()                    // customAttributes
            );
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

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        logger.debug("Looking up user by email '{}' in realm '{}' using REST mode", 
                    email, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping email lookup for '{}'", 
                        realm.getName(), email);
            return null;
        }
        
        return getUserByEmailViaRest(realm, email);
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

    @Override
    public int getUsersCount(RealmModel realm) {
        logger.debug("Getting users count for realm: {} using REST mode", realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm: {}, returning 0 count", realm.getName());
            return 0;
        }
        
        // For REST mode, we return a positive number to indicate users exist
        // This tells Keycloak admin console that this provider has users
        // The actual count is not critical for federated storage providers
        logger.debug("✅ REST USER COUNT: Returning estimated count for security (actual count via search)");
        return Integer.MAX_VALUE; // Indicates "many users exist, use search to find them"
    }

    @Override
    public Stream<UserModel> searchForUserStream(RealmModel realm, Map<String, String> params, 
            Integer firstResult, Integer maxResults) {
        logger.debug("Searching for users with params: {} in realm: {} using REST mode", 
                    params, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm: {}, returning empty search results", realm.getName());
            return Stream.empty();
        }
        
        try {
            String searchTerm = params.get(UserModel.SEARCH);
            String usernameParam = params.get(UserModel.USERNAME);
            String emailParam = params.get(UserModel.EMAIL);
            String firstNameParam = params.get(UserModel.FIRST_NAME);
            String lastNameParam = params.get(UserModel.LAST_NAME);
            
            // Determine what to search for
            String actualSearchTerm = null;
            if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                actualSearchTerm = searchTerm.trim();
            } else if (usernameParam != null && !usernameParam.trim().isEmpty()) {
                actualSearchTerm = usernameParam.trim();
            } else if (emailParam != null && !emailParam.trim().isEmpty()) {
                actualSearchTerm = emailParam.trim();
            } else if (firstNameParam != null && !firstNameParam.trim().isEmpty()) {
                actualSearchTerm = firstNameParam.trim();
            } else if (lastNameParam != null && !lastNameParam.trim().isEmpty()) {
                actualSearchTerm = lastNameParam.trim();
            }
            
            List<UserModel> users = new ArrayList<>();
            
            if (actualSearchTerm != null) {
                logger.debug("🔍 REST USER SEARCH: Searching for users with term '{}' in OFBiz", actualSearchTerm);
                
                // Search by username first
                try {
                    UserModel user = getUserByUsername(realm, actualSearchTerm);
                    if (user != null) {
                        // Check for duplicates: don't add if email == username
                        if (!isDuplicateUser(users, user)) {
                            users.add(user);
                            logger.debug("✅ Found user by username: {}", actualSearchTerm);
                        }
                    }
                } catch (Exception e) {
                    logger.debug("No user found by username '{}': {}", actualSearchTerm, e.getMessage());
                }
                
                // Search by email if not found by username and looks like email
                if (users.isEmpty() && actualSearchTerm.contains("@")) {
                    try {
                        UserModel user = getUserByEmail(realm, actualSearchTerm);
                        if (user != null) {
                            // Check for duplicates: don't add if this user is already in results
                            if (!isDuplicateUser(users, user)) {
                                users.add(user);
                                logger.debug("✅ Found user by email: {}", actualSearchTerm);
                            }
                        }
                    } catch (Exception e) {
                        logger.debug("No user found by email '{}': {}", actualSearchTerm, e.getMessage());
                    }
                }
                
                // Use REST client search for broader results
                try {
                    int maxResults_safe = maxResults != null ? Math.min(maxResults, 100) : 20;
                    java.util.List<OFBizRestClient.OFBizUserInfo> searchResults = restClient.searchUsers(actualSearchTerm, maxResults_safe);
                    
                    for (OFBizRestClient.OFBizUserInfo userInfo : searchResults) {
                        try {
                            UserModel user = new OFBizUserAdapter(session, realm, model, 
                                userInfo.getUsername(),
                                userInfo.getFirstName(),
                                userInfo.getLastName(), 
                                userInfo.getEmail(),
                                userInfo.isEnabled(),
                                userInfo.getTenant(),
                                userInfo.getCustomAttributes());
                            
                            // Check for duplicates before adding
                            if (!isDuplicateUser(users, user)) {
                                users.add(user);
                                logger.debug("✅ Added user from search: {}", userInfo.getUsername());
                            } else {
                                logger.debug("⚠️ Skipped duplicate user: {}", userInfo.getUsername());
                            }
                        } catch (Exception e) {
                            logger.warn("Error creating user adapter for '{}': {}", userInfo.getUsername(), e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    logger.debug("REST search failed: {}", e.getMessage());
                }
                
                // If we have search results, apply pagination
                if (!users.isEmpty()) {
                    int start = firstResult != null ? firstResult : 0;
                    int max = maxResults != null ? maxResults : users.size();
                    int end = Math.min(start + max, users.size());
                    
                    if (start < users.size()) {
                        List<UserModel> paginatedUsers = users.subList(start, end);
                        logger.debug("✅ Returning {} paginated users for search term '{}'", paginatedUsers.size(), actualSearchTerm);
                        return paginatedUsers.stream();
                    }
                }
                
                logger.debug("❌ REST USER SEARCH: No users found for search term '{}'", actualSearchTerm);
            } else {
                logger.debug("🔍 REST USER SEARCH: No search term provided - this is likely 'View all users' request");
                
                // For "View all users", we need to return some users
                // Since we can't fetch all users from OFBiz via REST, we'll return cached users
                List<UserModel> cachedUsers = getCachedUsers(realm, firstResult, maxResults);
                if (!cachedUsers.isEmpty()) {
                    logger.debug("✅ Returning {} cached users for 'View all users' request", cachedUsers.size());
                    return cachedUsers.stream();
                } else {
                    logger.debug("⚠️  No cached users available for 'View all users' - users will appear after they authenticate");
                    // Return empty stream with informative logging
                    logger.info("💡 ADMIN CONSOLE TIP: Users will appear in the list after they authenticate at least once");
                    logger.info("   Or use the search function to find specific users by username or email");
                }
            }
            
            return Stream.empty();
            
        } catch (Exception e) {
            logger.error("💥 REST USER SEARCH ERROR: Error searching for users: {}", e.getMessage(), e);
            return Stream.empty();
        }
    }

    /**
     * Check if a user is already in the list to prevent duplicates
     * This handles cases where email is used as username
     */
    private boolean isDuplicateUser(List<UserModel> users, UserModel newUser) {
        for (UserModel existingUser : users) {
            // Check for exact username match
            if (existingUser.getUsername().equals(newUser.getUsername())) {
                return true;
            }
            
            // Check for email-username conflicts (when email is used as username)
            if (existingUser.getEmail() != null && newUser.getEmail() != null) {
                // If existing user's username is the same as new user's email, it's a duplicate
                if (existingUser.getUsername().equals(newUser.getEmail())) {
                    return true;
                }
                // If existing user's email is the same as new user's username, it's a duplicate
                if (existingUser.getEmail().equals(newUser.getUsername())) {
                    return true;
                }
                // If both emails are the same, it's a duplicate
                if (existingUser.getEmail().equals(newUser.getEmail())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Gets cached users for "View all users" functionality
     * Returns users that have been cached from previous authentications
     * Only returns users that have valid authentication tokens (no temporary/placeholder users)
     */
    private List<UserModel> getCachedUsers(RealmModel realm, Integer firstResult, Integer maxResults) {
        List<UserModel> users = new ArrayList<>();
        String realmId = realm.getId();
        
        try {
            // Get users from the cached user data, but only include users that have authentication tokens
            for (Map.Entry<String, CachedUserData> entry : cachedUserData.entrySet()) {
                String cacheKey = entry.getKey();
                CachedUserData cachedData = entry.getValue();
                
                // Check if this cache entry belongs to the current realm and is not expired
                if (cacheKey.startsWith(realmId + ":") && !cachedData.isExpired()) {
                    OFBizRestClient.OFBizUserInfo userInfo = cachedData.getUserInfo();
                    String username = userInfo.getUsername();
                    
                    // CRITICAL FIX: Only include users that have valid authentication tokens
                    // This prevents temporary/placeholder users from appearing in listings
                    String cachedToken = getCachedOFBizToken(username, realmId);
                    if (cachedToken != null) {
                        logger.debug("✅ Including authenticated user '{}' in user listing", username);
                        
                        UserModel user = new OFBizUserAdapter(session, realm, model, 
                            userInfo.getUsername(),
                            userInfo.getFirstName(),
                            userInfo.getLastName(), 
                            userInfo.getEmail(),
                            userInfo.isEnabled(),
                            userInfo.getTenant(),
                            userInfo.getCustomAttributes());
                        
                        // Check for duplicates before adding
                        if (!isDuplicateUser(users, user)) {
                            users.add(user);
                        }
                    } else {
                        logger.debug("🚫 Skipping user '{}' - no valid authentication token (temporary/placeholder user)", username);
                    }
                }
            }
            
            // Sort by username for consistent ordering
            users.sort((u1, u2) -> u1.getUsername().compareToIgnoreCase(u2.getUsername()));
            
            // Apply pagination
            int start = firstResult != null ? firstResult : 0;
            int max = maxResults != null ? maxResults : 20; // Default to 20 users if no limit specified
            int end = Math.min(start + max, users.size());
            
            if (start < users.size()) {
                return users.subList(start, end);
            } else {
                return new ArrayList<>();
            }
            
        } catch (Exception e) {
            logger.error("Error getting cached users: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
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
        
        logger.info("🔑 CREDENTIAL CHECK: Starting password validation for user '{}' in realm '{}' using REST mode", 
                   username, realm.getName());
        boolean isValid = validatePassword(username, password);
        
        if (isValid) {
            logger.info("✅ LOGIN SUCCESS: User '{}' successfully authenticated in realm '{}' using REST mode", 
                       username, realm.getName());
            
            // CRITICAL FIX: Set basic user profile fields to prevent Account Console popup
            try {
                logger.info("🔧 POPUP FIX: Setting basic profile fields for user '{}' to prevent Account Console popup", username);
                
                // Set firstName if not already set
                String currentFirstName = user.getFirstName();
                if (currentFirstName == null || currentFirstName.trim().isEmpty() || "User".equals(currentFirstName)) {
                    user.setFirstName(username); // Use username as firstName
                    logger.info("✅ FIRSTNAME SET: Set firstName to '{}'", username);
                }
                
                // Set lastName if not already set  
                String currentLastName = user.getLastName();
                if (currentLastName == null || currentLastName.trim().isEmpty()) {
                    user.setLastName("User"); // Set default lastName
                    logger.info("✅ LASTNAME SET: Set lastName to 'User'");
                }
                
                // Set email if it's a placeholder
                String currentEmail = user.getEmail();
                if (currentEmail == null || currentEmail.endsWith("@placeholder.local")) {
                    user.setEmail(username + "@example.com"); // Set a basic email
                    logger.info("✅ EMAIL SET: Set email to '{}'", username + "@example.com");
                }
                
                // Mark email as verified and remove any required actions
                user.setEmailVerified(true);
                user.removeRequiredAction(UserModel.RequiredAction.UPDATE_PROFILE);
                user.removeRequiredAction(UserModel.RequiredAction.VERIFY_EMAIL);
                
                logger.info("✅ POPUP FIX COMPLETE: User profile completed for '{}'", username);
                
            } catch (Exception e) {
                logger.error("💥 POPUP FIX FAILED: Could not set profile fields for '{}': {}", username, e.getMessage());
            }
        } else {
            logger.error("❌ LOGIN FAILED: Authentication failed for user '{}' in realm '{}' using REST mode", 
                       username, realm.getName());
            logger.error("🚨 AUTHENTICATION FAILURE ANALYSIS for user '{}':", username);
            logger.error("   Possible causes:");
            logger.error("   1. Invalid credentials (username: '{}', password: [HIDDEN])", username);
            logger.error("   2. Missing mandatory profile data (firstName, lastName, email)");
            logger.error("   3. User account disabled in OFBiz");
            logger.error("   4. Network connectivity issues with OFBiz");
            logger.error("   5. OFBiz service errors or configuration problems");
            logger.error("💡 TROUBLESHOOTING STEPS:");
            logger.error("   1. Verify user credentials: Test login directly against OFBiz");
            logger.error("   2. Check user profile: Ensure '{}' has complete firstName, lastName, email", username);
            logger.error("   3. Verify OFBiz connectivity: Check OFBiz logs and network access");
            logger.error("   4. Test OFBiz REST API: curl -u '{}:[password]' http://ofbiz.local:8080/rest/auth/token", username);
            logger.error("⚠️  NOTE: Keycloak shows 'invalid_grant' error, but root cause may be incomplete user profile data");
        }
        
        return isValid;
    }

    /**
     * Validates user password using REST API only
     */
    private boolean validatePassword(String username, String password) {
        return validatePasswordViaRest(username, password);
    }

    /**
     * Validates user password via REST API
     * LOGOUT FIX: Always validates credentials, never relies on cached tokens during authentication
     */
    private boolean validatePasswordViaRest(String username, String password) {
        logger.info("🔐 REST AUTH: Starting password validation for user '{}' via OFBiz REST API", username);
        
        // LOGOUT FIX: Clear any existing cached tokens ONLY during credential validation
        // This ensures that logout properly invalidates cached credentials for authentication
        // but allows user lookup operations to still use cached data
        String realmId = session.getContext().getRealm().getId();
        String tokenCacheKey = realmId + ":" + username;
        OFBizTokenInfo existingToken = tokenCache.get(tokenCacheKey);
        
        if (existingToken != null) {
            logger.debug("🧹 LOGOUT FIX: Found existing cached token for user '{}' - clearing before fresh authentication", username);
            clearCachedOFBizToken(username, realmId);
        } else {
            logger.debug("🔍 CREDENTIAL VALIDATION: No existing cached token for user '{}' - proceeding with fresh authentication", username);
        }
        
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
                logger.error("❌ REST AUTH FAILED: User '{}' authentication failed via OFBiz REST API", username);
                logger.error("🔍 POSSIBLE CAUSES for authentication failure:");
                logger.error("   1. Invalid credentials (wrong username/password)");
                logger.error("   2. Missing mandatory user profile data (firstName, lastName, email)");
                logger.error("   3. User account disabled in OFBiz");
                logger.error("   4. OFBiz getUserInfo service not returning complete user data");
                logger.error("💡 TROUBLESHOOT: Check OFBiz logs and verify user '{}' profile completeness", username);
                
                // LOGOUT FIX: Ensure no cached data remains for failed authentication
                clearUserSession(username, realmId);
            }
            
            return isValid;
        } catch (Exception e) {
            logger.error("💥 REST AUTH ERROR: Exception during password validation for user '{}' via REST API: {}", 
                        username, e.getMessage(), e);
            
            // LOGOUT FIX: Clear any cached data on authentication error
            clearUserSession(username, realmId);
            
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
                // Validate user data before updating federated storage
                boolean hasValidationErrors = false;
                StringBuilder missingFields = new StringBuilder();
                
                if (userInfo.getFirstName() == null || userInfo.getFirstName().trim().isEmpty() || "User".equals(userInfo.getFirstName())) {
                    logger.error("❌ MISSING MANDATORY FIELD: firstName is missing or using default for user '{}'", username);
                    missingFields.append("firstName ");
                    hasValidationErrors = true;
                }
                
                if (userInfo.getLastName() == null || userInfo.getLastName().trim().isEmpty() || userInfo.getLastName().equals(username)) {
                    logger.error("❌ MISSING MANDATORY FIELD: lastName is missing or using username fallback for user '{}'", username);
                    missingFields.append("lastName ");
                    hasValidationErrors = true;
                }
                
                if (userInfo.getEmail() == null || userInfo.getEmail().trim().isEmpty()) {
                    logger.error("❌ MISSING MANDATORY FIELD: email is required for user '{}' but not provided by OFBiz", username);
                    missingFields.append("email ");
                    hasValidationErrors = true;
                }
                
                if (userInfo.getTenant() == null || userInfo.getTenant().trim().isEmpty() || "default".equals(userInfo.getTenant())) {
                    logger.warn("⚠️  MISSING FIELD: tenantId is missing or using default for user '{}'", username);
                    missingFields.append("tenantId ");
                    hasValidationErrors = true;
                }
                
                if (hasValidationErrors) {
                    logger.error("🚨 AUTHENTICATION MAY FAIL: User '{}' has incomplete profile data. Missing/default fields: [{}]", 
                               username, missingFields.toString().trim());
                    logger.error("💡 TO FIX: Update OFBiz user '{}' profile with complete firstName, lastName, email in Person and ContactMech tables", username);
                    logger.error("🔧 ALTERNATIVE: Check OFBiz getUserInfo service to ensure it returns all required fields");
                }
                
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
     * Attempts to create a new user in OFBiz when user is not found
     * @param realm the Keycloak realm
     * @param username the username to create
     * @return UserModel if creation succeeded, null otherwise
     */
    private UserModel attemptUserCreation(RealmModel realm, String username) {
        logger.info("🔨 USER CREATION: Attempting to create missing user '{}' in OFBiz", username);
        
        try {
            // Extract basic user information from username (if email format)
            String firstName = username;
            String lastName = "User";
            String email = username;
            String tenantId = "default";
            
            // If username looks like an email, extract name parts
            if (username.contains("@")) {
                String localPart = username.substring(0, username.indexOf("@"));
                String[] nameParts = localPart.split("\\.");
                if (nameParts.length >= 2) {
                    firstName = nameParts[0].substring(0, 1).toUpperCase() + nameParts[0].substring(1);
                    lastName = nameParts[1].substring(0, 1).toUpperCase() + nameParts[1].substring(1);
                } else {
                    firstName = localPart.substring(0, 1).toUpperCase() + localPart.substring(1);
                }
                email = username; // Use full email
            } else {
                // For non-email usernames, generate an email
                email = username + "@example.com";
                firstName = username.substring(0, 1).toUpperCase() + username.substring(1);
            }
            
            // Attempt to create tenant first if tenant creation is enabled
            if (restClient.isTenantCreationEnabled() && !"default".equals(tenantId)) {
                logger.info("🏢 Creating tenant '{}' for new user '{}'", tenantId, username);
                restClient.createTenant(tenantId, tenantId + " Organization");
            }
            
            // Create the user in OFBiz
            String userResult = restClient.createUser(username, firstName, lastName, email, tenantId);
            
            if (userResult != null && !userResult.isEmpty()) {
                logger.info("✅ USER CREATED: Successfully created user '{}' in OFBiz", username);
                
                // Return a user adapter with the created user data
                Map<String, String> customAttributes = new HashMap<>();
                customAttributes.put("createdByKeycloak", "true");
                customAttributes.put("createdAt", String.valueOf(System.currentTimeMillis()));
                
                return new OFBizUserAdapter(session, realm, model, 
                    username, firstName, lastName, email, true, tenantId, customAttributes);
            } else {
                logger.error("❌ USER CREATION FAILED: Could not create user '{}' in OFBiz", username);
                return null;
            }
            
        } catch (Exception e) {
            logger.error("💥 USER CREATION EXCEPTION: Error creating user '{}': {}", username, e.getMessage(), e);
            return null;
        }
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
