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
    
    private final KeycloakSession session;
    private final ComponentModel model;
    private final OFBizConnectionProvider connectionProvider;

    public OFBizUserStorageProvider(KeycloakSession session, ComponentModel model) {
        this.session = session;
        this.model = model;
        this.connectionProvider = new OFBizConnectionProvider(model);
    }

    /**
     * Check if this provider should be active for the given realm
     */
    private boolean isActiveForRealm(RealmModel realm) {
        String enabledRealms = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_ENABLED_REALMS);
        
        // If no specific realms configured, allow all (but warn for master)
        if (enabledRealms == null || enabledRealms.trim().isEmpty()) {
            if ("master".equals(realm.getName())) {
                logger.warn("OFBiz User Storage Provider active on master realm - this is not recommended for production");
            }
            return true;
        }
        
        // Check if current realm is in the enabled list
        String[] realms = enabledRealms.split(",");
        for (String enabledRealm : realms) {
            if (enabledRealm.trim().equals(realm.getName())) {
                return true;
            }
        }
        
        logger.debug("OFBiz User Storage Provider not active for realm: {}", realm.getName());
        return false;
    }

    @Override
    public void close() {
        if (connectionProvider != null) {
            connectionProvider.close();
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
        logger.debug("Looking up user '{}' in realm '{}'", username, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping user lookup for '{}'", 
                        realm.getName(), username);
            return null;
        }
        
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
        logger.debug("Looking up user by email '{}' in realm '{}'", email, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping email lookup for '{}'", 
                        realm.getName(), email);
            return null;
        }
        
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
        logger.debug("Getting users count for realm: {}", realm.getName());
        
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
        logger.debug("Searching for users with params: {} in realm: {}", params, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm: {}, returning empty search results", realm.getName());
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
        return PasswordCredentialModel.TYPE.equals(credentialType);
    }

    @Override
    public boolean isConfiguredFor(RealmModel realm, UserModel user, String credentialType) {
        return supportsCredentialType(credentialType);
    }

    @Override
    public boolean isValid(RealmModel realm, UserModel user, CredentialInput credentialInput) {
        String username = user.getUsername();
        logger.debug("Validating credentials for user '{}' in realm '{}'", username, realm.getName());
        
        // Check if this provider should be active for this realm
        if (!isActiveForRealm(realm)) {
            logger.debug("OFBiz provider not active for realm '{}', skipping credential validation for user '{}'", 
                        realm.getName(), username);
            return false;
        }
        
        if (!supportsCredentialType(credentialInput.getType()) || 
            !(credentialInput instanceof PasswordCredentialModel)) {
            logger.debug("Unsupported credential type '{}' for user '{}' in realm '{}'", 
                        credentialInput.getType(), username, realm.getName());
            return false;
        }

        PasswordCredentialModel passwordCredential = (PasswordCredentialModel) credentialInput;
        String password = passwordCredential.getPasswordSecretData().getValue();
        
        logger.trace("Attempting password validation for user '{}' in realm '{}'", username, realm.getName());
        boolean isValid = validatePassword(username, password);
        
        if (isValid) {
            logger.info("✅ LOGIN SUCCESS: User '{}' successfully authenticated in realm '{}'", username, realm.getName());
        } else {
            logger.warn("❌ LOGIN FAILED: Invalid credentials for user '{}' in realm '{}'", username, realm.getName());
        }
        
        return isValid;
    }

    /**
     * Validates user password against OFBiz database
     */
    private boolean validatePassword(String username, String password) {
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
}
