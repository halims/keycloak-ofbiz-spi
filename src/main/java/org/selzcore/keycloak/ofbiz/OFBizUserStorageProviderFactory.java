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
    
    public static final String PROVIDER_NAME = "ofbiz-user-storage";
    
    // Configuration properties
    public static final String CONFIG_KEY_JDBC_DRIVER = "jdbcDriver";
    public static final String CONFIG_KEY_JDBC_URL = "jdbcUrl";
    public static final String CONFIG_KEY_DB_USERNAME = "username";
    public static final String CONFIG_KEY_DB_PASSWORD = "password";
    public static final String CONFIG_KEY_VALIDATION_QUERY = "validationQuery";
    public static final String CONFIG_KEY_POOL_SIZE = "poolSize";
    public static final String CONFIG_KEY_ENABLED_REALMS = "enabledRealms";
    public static final String CONFIG_KEY_TENANT_ATTRIBUTE = "tenantAttribute";
    public static final String CONFIG_KEY_CUSTOM_ATTRIBUTES = "customAttributes";
    public static final String CONFIG_KEY_INTEGRATION_MODE = "integrationMode";
    public static final String CONFIG_KEY_OFBIZ_BASE_URL = "ofbizBaseUrl";
    public static final String CONFIG_KEY_OFBIZ_AUTH_ENDPOINT = "ofbizAuthEndpoint";
    public static final String CONFIG_KEY_OFBIZ_USER_ENDPOINT = "ofbizUserEndpoint";
    public static final String CONFIG_KEY_OFBIZ_TIMEOUT = "ofbizTimeout";
    
    // Integration mode constants
    public static final String INTEGRATION_MODE_DATABASE = "database";
    public static final String INTEGRATION_MODE_REST = "rest";

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
                    .name(CONFIG_KEY_INTEGRATION_MODE)
                    .label("Integration Mode")
                    .helpText("Choose between direct database access or OFBiz REST API integration")
                    .type(ProviderConfigProperty.LIST_TYPE)
                    .defaultValue(INTEGRATION_MODE_REST)
                    .options(INTEGRATION_MODE_DATABASE, INTEGRATION_MODE_REST)
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_BASE_URL)
                    .label("OFBiz Base URL")
                    .helpText("Base URL of OFBiz instance (mandatory for REST mode), make sure SSL is valid")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("http://ofbiz.local:8080")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_AUTH_ENDPOINT)
                    .label("OFBiz Authentication Endpoint")
                    .helpText("REST endpoint for user authentication (REST mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/rest/auth/token")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_USER_ENDPOINT)
                    .label("OFBiz User Info Endpoint")
                    .helpText("REST endpoint for user information and tenant data (REST mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("/rest/services/getUserInfo")
                    .add()
                .property()
                    .name(CONFIG_KEY_OFBIZ_TIMEOUT)
                    .label("OFBiz Request Timeout")
                    .helpText("Timeout for OFBiz REST API calls in milliseconds (REST mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("5000")
                    .add()
                .property()
                    .name(CONFIG_KEY_JDBC_DRIVER)
                    .label("JDBC Driver Class")
                    .helpText("JDBC driver class name (required for database mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("org.postgresql.Driver")
                    .add()
                .property()
                    .name(CONFIG_KEY_JDBC_URL)
                    .label("JDBC URL")
                    .helpText("JDBC connection URL to OFBiz database (required for database mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("jdbc:postgresql://localhost:5432/ofbiz")
                    .add()
                .property()
                    .name(CONFIG_KEY_DB_USERNAME)
                    .label("Database Username")
                    .helpText("Database username for OFBiz database (required for database mode)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("keycloak")
                    .add()
                .property()
                    .name(CONFIG_KEY_DB_PASSWORD)
                    .label("Database Password")
                    .helpText("Database password for OFBiz database (required for database mode)")
                    .type(ProviderConfigProperty.PASSWORD)
                    .secret(true)
                    .add()
                .property()
                    .name(CONFIG_KEY_VALIDATION_QUERY)
                    .label("Validation Query")
                    .helpText("SQL query to validate database connections (database mode only)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("SELECT 1")
                    .add()
                .property()
                    .name(CONFIG_KEY_POOL_SIZE)
                    .label("Connection Pool Size")
                    .helpText("Maximum number of database connections in the pool (database mode only)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("8")
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
                .build();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config) 
            throws ComponentValidationException {
        
        String integrationMode = config.get(CONFIG_KEY_INTEGRATION_MODE);
        if (integrationMode == null || integrationMode.trim().isEmpty()) {
            integrationMode = INTEGRATION_MODE_DATABASE; // default
        }
        
        logger.debug("Validating OFBiz User Storage Provider configuration for mode: {}", integrationMode);
        
        if (INTEGRATION_MODE_DATABASE.equals(integrationMode)) {
            validateDatabaseConfiguration(config);
        } else if (INTEGRATION_MODE_REST.equals(integrationMode)) {
            validateRestConfiguration(config);
        } else {
            throw new ComponentValidationException("Invalid integration mode: " + integrationMode + 
                ". Must be '" + INTEGRATION_MODE_DATABASE + "' or '" + INTEGRATION_MODE_REST + "'");
        }
        
        // Common validations
        validateCommonConfiguration(config);
        
        logger.info("OFBiz User Storage Provider configuration validated successfully for mode: {}", integrationMode);
    }
    
    private void validateDatabaseConfiguration(ComponentModel config) throws ComponentValidationException {
        String jdbcDriver = config.get(CONFIG_KEY_JDBC_DRIVER);
        String jdbcUrl = config.get(CONFIG_KEY_JDBC_URL);
        String username = config.get(CONFIG_KEY_DB_USERNAME);
        String password = config.get(CONFIG_KEY_DB_PASSWORD);
        
        if (jdbcDriver == null || jdbcDriver.trim().isEmpty()) {
            throw new ComponentValidationException("JDBC Driver is required for database mode");
        }
        
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new ComponentValidationException("JDBC URL is required for database mode");
        }
        
        if (username == null || username.trim().isEmpty()) {
            throw new ComponentValidationException("Database Username is required for database mode");
        }
        
        if (password == null || password.trim().isEmpty()) {
            throw new ComponentValidationException("Database Password is required for database mode");
        }
        
        // Validate JDBC driver class exists
        try {
            Class.forName(jdbcDriver);
        } catch (ClassNotFoundException e) {
            throw new ComponentValidationException("JDBC Driver class not found: " + jdbcDriver);
        }
        
        // Validate pool size is a valid number
        String poolSizeStr = config.get(CONFIG_KEY_POOL_SIZE);
        if (poolSizeStr != null && !poolSizeStr.trim().isEmpty()) {
            try {
                int poolSize = Integer.parseInt(poolSizeStr);
                if (poolSize <= 0) {
                    throw new ComponentValidationException("Pool size must be a positive number");
                }
            } catch (NumberFormatException e) {
                throw new ComponentValidationException("Pool size must be a valid number");
            }
        }
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
