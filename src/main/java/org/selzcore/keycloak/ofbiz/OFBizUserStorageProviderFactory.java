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

    @Override
    public OFBizUserStorageProvider create(KeycloakSession session, ComponentModel model) {
        logger.debug("Creating OFBiz User Storage Provider for model: {}", model.getName());
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
