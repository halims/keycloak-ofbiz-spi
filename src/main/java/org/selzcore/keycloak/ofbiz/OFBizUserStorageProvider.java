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
        logger.debug("Getting user by username: {}", username);
        
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT ul.user_login_id, ul.current_password, ul.enabled, " +
                        "p.first_name, p.last_name, p.personal_title, " +
                        "cm.info_string as email " +
                        "FROM user_login ul " +
                        "LEFT JOIN person p ON ul.party_id = p.party_id " +
                        "LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL " +
                        "LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id " +
                        "AND cm.contact_mech_type_id = 'EMAIL_ADDRESS' " +
                        "WHERE ul.user_login_id = ? AND ul.enabled = 'Y'";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapUserFromResultSet(realm, rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user by username: {}", username, e);
        }
        
        return null;
    }

    @Override
    public UserModel getUserByEmail(RealmModel realm, String email) {
        logger.debug("Getting user by email: {}", email);
        
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT ul.user_login_id, ul.current_password, ul.enabled, " +
                        "p.first_name, p.last_name, p.personal_title, " +
                        "cm.info_string as email " +
                        "FROM user_login ul " +
                        "JOIN person p ON ul.party_id = p.party_id " +
                        "JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL " +
                        "JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id " +
                        "WHERE cm.contact_mech_type_id = 'EMAIL_ADDRESS' " +
                        "AND cm.info_string = ? " +
                        "AND ul.enabled = 'Y'";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, email);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return mapUserFromResultSet(realm, rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error getting user by email: {}", email, e);
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
        logger.debug("Searching for users with params: {}", params);
        
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
        logger.debug("Validating credentials for user: {}", user.getUsername());
        
        if (!supportsCredentialType(credentialInput.getType()) || 
            !(credentialInput instanceof PasswordCredentialModel)) {
            return false;
        }

        PasswordCredentialModel passwordCredential = (PasswordCredentialModel) credentialInput;
        String password = passwordCredential.getPasswordSecretData().getValue();
        
        return validatePassword(user.getUsername(), password);
    }

    /**
     * Validates user password against OFBiz database
     */
    private boolean validatePassword(String username, String password) {
        try (Connection connection = connectionProvider.getConnection()) {
            String sql = "SELECT ul.current_password, ul.password_hint " +
                        "FROM user_login ul " +
                        "WHERE ul.user_login_id = ? AND ul.enabled = 'Y'";
            
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setString(1, username);
                
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedPassword = rs.getString("current_password");
                        
                        // OFBiz typically uses SHA-1 hashing with salt
                        // You may need to adjust this based on your OFBiz password configuration
                        return OFBizPasswordUtil.verifyPassword(password, storedPassword);
                    }
                }
            }
        } catch (SQLException e) {
            logger.error("Error validating password for user: {}", username, e);
        }
        
        return false;
    }

    /**
     * Maps database result set to UserModel
     */
    private UserModel mapUserFromResultSet(RealmModel realm, ResultSet rs) throws SQLException {
        String username = rs.getString("user_login_id");
        String firstName = rs.getString("first_name");
        String lastName = rs.getString("last_name");
        String email = rs.getString("email");
        boolean enabled = "Y".equals(rs.getString("enabled"));
        
        return new OFBizUserAdapter(session, realm, model, username, firstName, lastName, email, enabled);
    }
}
