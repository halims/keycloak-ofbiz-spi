package org.selzcore.keycloak.ofbiz;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User adapter for OFBiz users in Keycloak
 */
public class OFBizUserAdapter extends AbstractUserAdapterFederatedStorage {

    private static final Logger logger = LoggerFactory.getLogger(OFBizUserAdapter.class);

    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final boolean enabled;
    private final String tenant;
    private final Map<String, String> customAttributes;

    public OFBizUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model,
                           String username, String firstName, String lastName, String email, boolean enabled) {
        this(session, realm, model, username, firstName, lastName, email, enabled, null, new HashMap<>());
    }

    public OFBizUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model,
                           String username, String firstName, String lastName, String email, boolean enabled,
                           String tenant, Map<String, String> customAttributes) {
        super(session, realm, model);
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.enabled = enabled;
        this.tenant = tenant;
        this.customAttributes = customAttributes != null ? customAttributes : new HashMap<>();
        
        logger.debug("Created OFBizUserAdapter for user '{}' in realm '{}' (email: '{}', enabled: {}, tenant: '{}', customAttrs: {})", 
                    username, realm.getName(), email != null ? email : "none", enabled, 
                    tenant != null ? tenant : "none", this.customAttributes.size());
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        // OFBiz usernames are typically not editable through Keycloak
        // This could be implemented to update the OFBiz database if needed
        logger.debug("Username change attempted for user '{}' - not implemented for OFBiz integration", this.username);
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        // This could be implemented to update the OFBiz database if needed
        logger.debug("First name change attempted for user '{}': '{}' -> '{}' - using federated storage", 
                    this.username, this.firstName, firstName);
        setSingleAttribute("firstName", firstName);
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        // This could be implemented to update the OFBiz database if needed
        logger.debug("Last name change attempted for user '{}': '{}' -> '{}' - using federated storage", 
                    this.username, this.lastName, lastName);
        setSingleAttribute("lastName", lastName);
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        // This could be implemented to update the OFBiz database if needed
        logger.debug("Email change attempted for user '{}': '{}' -> '{}' - using federated storage", 
                    this.username, this.email, email);
        setSingleAttribute("email", email);
    }

    @Override
    public boolean isEmailVerified() {
        // For OFBiz integration, assume email is verified if present to avoid "Update Account" prompt
        // OFBiz doesn't have built-in email verification workflow like Keycloak
        return email != null && !email.trim().isEmpty();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        setSingleAttribute("emailVerified", Boolean.toString(verified));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // This could be implemented to update the OFBiz database if needed
        logger.debug("Enabled status change attempted for user '{}': {} -> {} - using federated storage", 
                    this.username, this.enabled, enabled);
        setSingleAttribute("enabled", Boolean.toString(enabled));
    }

    @Override
    public Long getCreatedTimestamp() {
        // For federated users, return a reasonable creation timestamp
        // Use current time if not available from federated storage
        List<String> createdTimestamps = getAttributeStream("createdTimestamp").toList();
        if (createdTimestamps != null && !createdTimestamps.isEmpty()) {
            try {
                return Long.parseLong(createdTimestamps.get(0));
            } catch (NumberFormatException e) {
                logger.debug("Could not parse stored createdTimestamp for user '{}': {}", username, createdTimestamps.get(0));
            }
        }
        
        // Use a default timestamp (current time when user was first accessed)
        // This prevents invalid date displays in the admin console
        long defaultTimestamp = System.currentTimeMillis();
        setSingleAttribute("createdTimestamp", String.valueOf(defaultTimestamp));
        logger.debug("Set default createdTimestamp for user '{}': {}", username, defaultTimestamp);
        return defaultTimestamp;
    }

    @Override
    public void setCreatedTimestamp(Long timestamp) {
        if (timestamp != null) {
            setSingleAttribute("createdTimestamp", timestamp.toString());
            logger.debug("Set createdTimestamp for user '{}': {}", username, timestamp);
        }
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, username);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> attributes = new MultivaluedHashMap<>();
        attributes.add("username", getUsername());
        if (getFirstName() != null) {
            attributes.add("firstName", getFirstName());
        }
        if (getLastName() != null) {
            attributes.add("lastName", getLastName());
        }
        if (getEmail() != null) {
            attributes.add("email", getEmail());
        }
        attributes.add("enabled", Boolean.toString(isEnabled()));
        attributes.add("emailVerified", Boolean.toString(isEmailVerified()));
        
        // Add tenant information
        if (tenant != null && !tenant.trim().isEmpty()) {
            String tenantAttributeName = storageProviderModel.get("tenantAttribute");
            if (tenantAttributeName == null || tenantAttributeName.trim().isEmpty()) {
                tenantAttributeName = "tenant"; // default
            }
            attributes.add(tenantAttributeName, tenant);
            logger.trace("Added tenant attribute '{}' = '{}' for user '{}'", tenantAttributeName, tenant, username);
        }
        
        // Add custom attributes
        for (Map.Entry<String, String> entry : customAttributes.entrySet()) {
            attributes.add(entry.getKey(), entry.getValue());
            logger.trace("Added custom attribute '{}' = '{}' for user '{}'", entry.getKey(), entry.getValue(), username);
        }
        
        // Add any additional attributes from federated storage
        Map<String, List<String>> federatedAttributes = super.getAttributes();
        if (federatedAttributes != null) {
            attributes.putAll(federatedAttributes);
        }
        
        logger.info("📋 USER ATTRIBUTES for '{}': firstName='{}', lastName='{}', email='{}', emailVerified={}, enabled={}, tenant='{}', totalAttributes={}", 
                   username, getFirstName(), getLastName(), getEmail(), isEmailVerified(), isEnabled(), tenant, attributes.size());
        
        return attributes;
    }

    /**
     * Gets the tenant for this user
     */
    public String getTenant() {
        return tenant;
    }

    /**
     * Gets all custom attributes for this user
     */
    public Map<String, String> getCustomAttributes() {
        return new HashMap<>(customAttributes);
    }
}
