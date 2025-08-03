package org.selzcore.keycloak.ofbiz;

import org.keycloak.component.ComponentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

/**
 * Connection provider for OFBiz database
 */
public class OFBizConnectionProvider {

    private static final Logger logger = LoggerFactory.getLogger(OFBizConnectionProvider.class);
    
    private static final ConcurrentHashMap<String, HikariDataSource> dataSources = new ConcurrentHashMap<>();
    
    private final ComponentModel model;
    private final String connectionKey;

    public OFBizConnectionProvider(ComponentModel model) {
        this.model = model;
        this.connectionKey = generateConnectionKey(model);
        logger.debug("Created OFBiz connection provider with key: {}", maskConnectionKey(connectionKey));
    }

    public Connection getConnection() throws SQLException {
        logger.trace("Requesting database connection");
        HikariDataSource dataSource = getOrCreateDataSource();
        Connection connection = dataSource.getConnection();
        logger.trace("Database connection acquired successfully");
        return connection;
    }

    public void close() {
        // Connection pool will be closed when Keycloak shuts down
        // Individual connections are managed by HikariCP
    }

    private HikariDataSource getOrCreateDataSource() {
        return dataSources.computeIfAbsent(connectionKey, key -> createDataSource());
    }

    private HikariDataSource createDataSource() {
        logger.info("Creating new HikariCP DataSource for OFBiz connection");
        
        String jdbcDriver = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_JDBC_DRIVER);
        String jdbcUrl = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_JDBC_URL);
        String username = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_DB_USERNAME);
        String password = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_DB_PASSWORD);
        String validationQuery = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_VALIDATION_QUERY);
        String poolSizeStr = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_POOL_SIZE);
        
        logger.debug("Database connection config - Driver: {}, URL: {}, Username: {}", 
                    jdbcDriver, maskJdbcUrl(jdbcUrl), username);
        
        int poolSize = 10;
        if (poolSizeStr != null && !poolSizeStr.trim().isEmpty()) {
            try {
                poolSize = Integer.parseInt(poolSizeStr);
                logger.debug("Using configured pool size: {}", poolSize);
            } catch (NumberFormatException e) {
                logger.warn("Invalid pool size value: {}, using default: 10", poolSizeStr);
            }
        } else {
            logger.debug("Using default pool size: {}", poolSize);
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(jdbcDriver);
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(Math.min(2, poolSize));
        config.setConnectionTimeout(30000); // 30 seconds
        config.setIdleTimeout(600000); // 10 minutes
        config.setMaxLifetime(1800000); // 30 minutes
        config.setLeakDetectionThreshold(60000); // 1 minute
        
        logger.debug("HikariCP pool configuration - MaxPool: {}, MinIdle: {}, ConnTimeout: {}ms", 
                    config.getMaximumPoolSize(), config.getMinimumIdle(), config.getConnectionTimeout());
        
        if (validationQuery != null && !validationQuery.trim().isEmpty()) {
            config.setConnectionTestQuery(validationQuery);
            logger.debug("Using validation query: {}", validationQuery);
        }
        
        // Additional HikariCP optimizations
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        config.addDataSourceProperty("useLocalSessionState", "true");
        config.addDataSourceProperty("rewriteBatchedStatements", "true");
        config.addDataSourceProperty("cacheResultSetMetadata", "true");
        config.addDataSourceProperty("cacheServerConfiguration", "true");
        config.addDataSourceProperty("elideSetAutoCommits", "true");
        config.addDataSourceProperty("maintainTimeStats", "false");
        
        try {
            HikariDataSource dataSource = new HikariDataSource(config);
            logger.info("Successfully created HikariCP DataSource for OFBiz connection");
            return dataSource;
        } catch (Exception e) {
            logger.error("Failed to create HikariCP DataSource", e);
            throw new RuntimeException("Failed to create database connection pool", e);
        }
    }

    private String generateConnectionKey(ComponentModel model) {
        // Generate a unique key based on connection parameters
        String jdbcUrl = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_JDBC_URL);
        String username = model.get(OFBizUserStorageProviderFactory.CONFIG_KEY_DB_USERNAME);
        return jdbcUrl + "_" + username + "_" + model.getId();
    }

    public static void closeAllDataSources() {
        logger.info("Closing all HikariCP DataSources");
        dataSources.values().forEach(dataSource -> {
            try {
                dataSource.close();
            } catch (Exception e) {
                logger.error("Error closing DataSource", e);
            }
        });
        dataSources.clear();
    }

    /**
     * Masks connection key for safe logging
     */
    private String maskConnectionKey(String key) {
        if (key == null || key.length() < 10) {
            return "***";
        }
        return key.substring(0, 5) + "***" + key.substring(key.length() - 5);
    }

    /**
     * Masks JDBC URL for safe logging
     */
    private String maskJdbcUrl(String url) {
        if (url == null) {
            return "null";
        }
        // Remove password from URL if present
        return url.replaceAll("password=[^&;]*", "password=***");
    }
}
