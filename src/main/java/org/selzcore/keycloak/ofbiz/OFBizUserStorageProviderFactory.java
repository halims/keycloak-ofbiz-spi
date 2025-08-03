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

    @Override
    public OFBizUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.debug("Creating OFBiz User Storage Provider for model: {} in realm: {}", 
                    model.getName(), model.getParentId());
        
        // Get the realm for this component
        RealmModel realm = session.realms().getRealm(model.getParentId());
        if (realm == null) {
            logger.warn("❌ Cannot create OFBiz provider: realm not found for ID: {}", model.getParentId());
            return null;
        }
        
        logger.debug("Provider requested for realm: '{}' (ID: {})", realm.getName(), realm.getId());
        
        // CRITICAL: Never create provider for master realm unless explicitly configured
        if ("master".equals(realm.getName())) {
            String enabledRealms = model.get(CONFIG_KEY_ENABLED_REALMS);
            if (enabledRealms == null || !enabledRealms.contains("master")) {
                logger.warn("🚫 SECURITY: Refusing to create OFBiz User Storage Provider for master realm. " +
                           "This is a security protection. Configure 'enabledRealms' property to explicitly enable.");
                return null; // Don't create provider for master realm
            } else {
                logger.warn("⚠️  WARNING: OFBiz provider explicitly enabled for master realm - ensure this is intentional!");
            }
        }
        
        // Additional check: if enabledRealms is specified, ensure current realm is in the list
        String enabledRealms = model.get(CONFIG_KEY_ENABLED_REALMS);
        if (enabledRealms != null && !enabledRealms.trim().isEmpty()) {
            String[] realms = enabledRealms.split(",");
            boolean isEnabled = false;
            for (String enabledRealm : realms) {
                if (enabledRealm.trim().equals(realm.getName())) {
                    isEnabled = true;
                    break;
                }
            }
            if (!isEnabled) {
                logger.debug("❌ OFBiz provider not enabled for realm: '{}', configured realms: {}", 
                           realm.getName(), enabledRealms);
                return null; // Don't create provider for non-enabled realms
            }
        }
        
        logger.info("✅ Creating OFBiz User Storage Provider for realm: '{}' (model: {})", 
                   realm.getName(), model.getName());
        return new OFBizUserStorageProvider(session, model);
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
                    .name(CONFIG_KEY_JDBC_DRIVER)
                    .label("JDBC Driver Class")
                    .helpText("JDBC driver class name (e.g., com.mysql.cj.jdbc.Driver, org.postgresql.Driver)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("com.mysql.cj.jdbc.Driver")
                    .add()
                .property()
                    .name(CONFIG_KEY_JDBC_URL)
                    .label("JDBC URL")
                    .helpText("JDBC connection URL to OFBiz database")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("jdbc:mysql://localhost:3306/ofbiz")
                    .add()
                .property()
                    .name(CONFIG_KEY_DB_USERNAME)
                    .label("Database Username")
                    .helpText("Database username for OFBiz database")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("ofbiz")
                    .add()
                .property()
                    .name(CONFIG_KEY_DB_PASSWORD)
                    .label("Database Password")
                    .helpText("Database password for OFBiz database")
                    .type(ProviderConfigProperty.PASSWORD)
                    .secret(true)
                    .add()
                .property()
                    .name(CONFIG_KEY_VALIDATION_QUERY)
                    .label("Validation Query")
                    .helpText("SQL query to validate database connections")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("SELECT 1")
                    .add()
                .property()
                    .name(CONFIG_KEY_POOL_SIZE)
                    .label("Connection Pool Size")
                    .helpText("Maximum number of database connections in the pool")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .defaultValue("10")
                    .add()
                .property()
                    .name(CONFIG_KEY_ENABLED_REALMS)
                    .label("Enabled Realms")
                    .helpText("Comma-separated list of realm names where this provider should be active (optional, leave empty to allow all)")
                    .type(ProviderConfigProperty.STRING_TYPE)
                    .add()
                .build();
    }

    @Override
    public void validateConfiguration(KeycloakSession session, RealmModel realm, ComponentModel config) 
            throws ComponentValidationException {
        
        String jdbcDriver = config.get(CONFIG_KEY_JDBC_DRIVER);
        String jdbcUrl = config.get(CONFIG_KEY_JDBC_URL);
        String username = config.get(CONFIG_KEY_DB_USERNAME);
        String password = config.get(CONFIG_KEY_DB_PASSWORD);
        
        if (jdbcDriver == null || jdbcDriver.trim().isEmpty()) {
            throw new ComponentValidationException("JDBC Driver is required");
        }
        
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new ComponentValidationException("JDBC URL is required");
        }
        
        if (username == null || username.trim().isEmpty()) {
            throw new ComponentValidationException("Database Username is required");
        }
        
        if (password == null || password.trim().isEmpty()) {
            throw new ComponentValidationException("Database Password is required");
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
        
        logger.info("OFBiz User Storage Provider configuration validated successfully");
    }
}
