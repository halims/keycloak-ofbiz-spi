package org.selzcore.keycloak.ofbiz;

import org.keycloak.component.ComponentModel;
import org.keycloak.component.ComponentValidationException;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;
import org.keycloak.storage.UserStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Factory for creating OFBiz User Storage Provider instances
 */
public class OFBizUserStorageProviderFactory implements UserStorageProviderFactory<OFBizUserStorageProvider> {

    private static final Logger logger = LoggerFactory.getLogger(OFBizUserStorageProviderFactory.class);
    
    public static final String PROVIDER_NAME = "ofbiz-spi";
    
    // Configuration properties
    public static final String CONFIG_KEY_INTEGRATION_MODE = "integrationMode";
    public static final String CONFIG_KEY_ENABLED_REALMS = "enabledRealms";
    public static final String CONFIG_KEY_TENANT_ATTRIBUTE = "tenantAttribute";
    public static final String CONFIG_KEY_CUSTOM_ATTRIBUTES = "customAttributes";
    public static final String CONFIG_KEY_OFBIZ_BASE_URL = "ofbizBaseUrl";
    public static final String CONFIG_KEY_OFBIZ_AUTH_ENDPOINT = "ofbizAuthEndpoint";
    public static final String CONFIG_KEY_OFBIZ_USER_ENDPOINT = "ofbizUserEndpoint";
    public static final String CONFIG_KEY_OFBIZ_TIMEOUT = "ofbizTimeout";
    public static final String CONFIG_KEY_SERVICE_ACCOUNT_USERNAME = "serviceAccountUsername";
    public static final String CONFIG_KEY_SERVICE_ACCOUNT_PASSWORD = "serviceAccountPassword";
    public static final String CONFIG_KEY_OFBIZ_CREATE_USER_ENDPOINT = "ofbizCreateUserEndpoint";
    public static final String CONFIG_KEY_OFBIZ_CREATE_TENANT_ENDPOINT = "ofbizCreateTenantEndpoint";
    public static final String CONFIG_KEY_ENABLE_USER_CREATION = "enableUserCreation";
    public static final String CONFIG_KEY_ENABLE_TENANT_CREATION = "enableTenantCreation";

    @Override
    public OFBizUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.debug("Creating OFBiz User Storage Provider for model: {} in realm: {}", 
                    model.getName(), model.getParentId());
        
        // Get the realm for this component
        RealmModel realm = session.realms().getRealm(model.getParentId());
        if (realm == null) {
            logger.warn("❌ Cannot create OFBiz provider: realm not found for ID: {}", model.getParentId());
            // Never return null - create a disabled provider instead
            return new OFBizUserStorageProvider(session, model);
        }
        
        logger.debug("Provider requested for realm: '{}' (ID: {})", realm.getName(), realm.getId());
        
        // Check if provider should be enabled for this realm
        boolean shouldEnable = shouldEnableForRealm(realm, model);
        
        if (!shouldEnable) {
            logger.debug("OFBiz provider disabled for realm: '{}'", realm.getName());
        } else {
            // Only log at INFO level the first time or when it's actually significant
            // Reduce noise from repeated provider creation calls
            logger.debug("✅ Creating OFBiz User Storage Provider for realm: '{}' (model: {})", 
                       realm.getName(), model.getName());
        }
        
        // Always return a provider instance, but it may be inactive for certain realms
        return new OFBizUserStorageProvider(session, model);
    }

    /**
     * Determines if the provider should be enabled for the given realm
     */
    private boolean shouldEnableForRealm(RealmModel realm, ComponentModel model) {
        try {
            String enabledRealms = model.get(CONFIG_KEY_ENABLED_REALMS);

            // CRITICAL: Never enable for master realm unless explicitly configured
            if ("master".equals(realm.getName())) {
                if (enabledRealms == null || !enabledRealms.contains("master")) {
                    logger.debug("OFBiz provider not enabled for master realm (security protection)");
                    return false;
                } else {
                    logger.warn("⚠️  WARNING: OFBiz provider explicitly enabled for master realm - ensure this is intentional!");
                    return true;
                }
            }
            
            // Check if enabledRealms is specified and current realm is in the list
            if (enabledRealms != null && !enabledRealms.trim().isEmpty()) {
                String[] realms = enabledRealms.split(",");
                for (String enabledRealm : realms) {
                    if (enabledRealm.trim().equals(realm.getName())) {
                        return true;
                    }
                }
                logger.debug("OFBiz provider not enabled for realm: '{}'", realm.getName());
                return false;
            }
            
            // If no specific realms are configured, enable for all non-master realms
            return true;
            
        } catch (Exception e) {
            logger.warn("Error checking realm configuration for realm '{}': {}", realm.getName(), e.getMessage());
            // Default to disabled, do not try to enable for realms with errors
            return false;
        }
    }

    @Override
    public String getId() {
        return PROVIDER_NAME;
    }

    @Override
    public String getHelpText() {
        return "User Storage Provider that integrates with Apache OFBiz user management system";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                    .name(CONFIG_KEY_ENABLED_REALMS)
                    .label("Enabled Realms")
                    .helpText("Comma-separated list of realm names where this provider should be active (optional, leave empty to allow all)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("ofbiz")
                    .add()
                .property()
                    .name(CONFIG_KEY_ENABLE_USER_CREATION)
                    .label("Enable User Creation")
                    .helpText("Allow creating new users in OFBiz via REST API when they don't exist")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                .property()
                    .name(CONFIG_KEY_ENABLE_TENANT_CREATION)
                    .label("Enable Tenant Creation")
                    .helpText("Allow creating new tenants/organizations in OFBiz via REST API")
                    .type(ProviderConfigProperty.BOOLEAN_TYPE)
                    .defaultValue("false")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_BASE_URL)
                    .label("OFBiz Base URL")
                    .helpText("Base URL of OFBiz instance, make sure SSL is valid")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("http://ofbiz.local:8080/rest")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_AUTH_ENDPOINT)
                    .label("OFBiz Authentication Endpoint")
                    .helpText("REST endpoint for user authentication")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/auth/token")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_USER_ENDPOINT)
                    .label("OFBiz User Info Endpoint")
                    .helpText("REST endpoint for user information and tenant data")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/services/getUserInfo")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_CREATE_USER_ENDPOINT)
                    .label("OFBiz Create User Endpoint")
                    .helpText("REST endpoint for creating new users in OFBiz")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/services/createUser")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_CREATE_TENANT_ENDPOINT)
                    .label("OFBiz Create Tenant Endpoint")
                    .helpText("REST endpoint for creating new tenants/organizations in OFBiz")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/services/createPartyGroup")
                    .add()
                .property()
                    .name(CONFIG_KEY_TENANT_ATTRIBUTE)
                    .label("Tenant Attribute Name")
                    .helpText("Name of the user attribute to store tenant information (default: 'tenant')")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("tenant")
                    .add()
                .property()
                    .name(CONFIG_KEY_CUSTOM_ATTRIBUTES)
                    .label("Custom Attribute Mappings")
                    .helpText("Comma-separated list of attribute mappings in format 'attributeName:ofbizField' (e.g., 'department:dept_id,location:location_code')")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_TIMEOUT)
                    .label("OFBiz Request Timeout")
                    .helpText("Timeout for OFBiz REST API calls in milliseconds")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("5000")
                    .add()
                .property()
                    .name(CONFIG_KEY_SERVICE_ACCOUNT_USERNAME)
                    .label("Service Account Username")
                    .helpText("OFBiz service account username for user lookups (optional, required for user search)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .add()
                .property()
                    .name(CONFIG_KEY_SERVICE_ACCOUNT_PASSWORD)
                    .label("Service Account Password")
                    .helpText("OFBiz service account password for user lookups (optional, required for user search)")
                    .type(ProviderConfigProperty.PASSWORD)
                    .secret(true)
                    .add()
                .build();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config) 
            throws ComponentValidationException {
        
        logger.debug("Validating OFBiz User Storage Provider configuration for REST mode");
        
        validateRestConfiguration(config);
        validateCommonConfiguration(config);
        
        logger.info("OFBiz User Storage Provider configuration validated successfully for REST mode");
    }
    
    private void validateRestConfiguration(ComponentModel config) throws ComponentValidationException {
        String baseUrl = config.get(CONFIG_KEY_OFBIZ_BASE_URL);
        String authEndpoint = config.get(CONFIG_KEY_OFBIZ_AUTH_ENDPOINT);
        String userEndpoint = config.get(CONFIG_KEY_OFBIZ_USER_ENDPOINT);
        
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new ComponentValidationException("OFBiz Base URL is required for REST mode");
        }
        
        if (authEndpoint == null || authEndpoint.trim().isEmpty()) {
            throw new ComponentValidationException("OFBiz Authentication Endpoint is required for REST mode");
        }
        
        if (userEndpoint == null || userEndpoint.trim().isEmpty()) {
            throw new ComponentValidationException("OFBiz User Info Endpoint is required for REST mode");
        }
        
        // Validate URL format
        try {
            new java.net.URL(baseUrl);
        } catch (java.net.MalformedURLException e) {
            throw new ComponentValidationException("Invalid OFBiz Base URL format: " + baseUrl);
        }
        
        // Validate timeout is a valid number
        String timeoutStr = config.get(CONFIG_KEY_OFBIZ_TIMEOUT);
        if (timeoutStr != null && !timeoutStr.trim().isEmpty()) {
            try {
                int timeout = Integer.parseInt(timeoutStr);
                if (timeout <= 0) {
                    throw new ComponentValidationException("OFBiz timeout must be a positive number");
                }
            } catch (NumberFormatException e) {
                throw new ComponentValidationException("OFBiz timeout must be a valid number");
            }
        }
    }
    
    private void validateCommonConfiguration(ComponentModel config) throws ComponentValidationException {
        // No additional common validations needed at this time
        // This method is reserved for future common validation logic
    }
}
