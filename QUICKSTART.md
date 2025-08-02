# Quick Setup Guide

## Overview
This project provides a Keycloak SPI that integrates with Apache OFBiz for user authentication. Follow these steps to get started quickly.

## Prerequisites
- Java 11 or higher
- Maven 3.6+
- Docker and Docker Compose (for testing)
- Keycloak 26.3.2
- OFBiz 24.09.01 with MySQL/PostgreSQL database

## Quick Start

### 1. Build the Project
```bash
mvn clean package
```

### 2. Testing with Docker
```bash
# Start test environment
docker-compose up -d

# Check logs
docker-compose logs -f keycloak

# Access Keycloak Admin Console
# URL: http://localhost:8080
# Username: admin
# Password: admin
```

### 3. Production Deployment
```bash
# Copy JAR to Keycloak
cp target/keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/

# Restart Keycloak
$KEYCLOAK_HOME/bin/kc.sh stop
$KEYCLOAK_HOME/bin/kc.sh start
```

### 4. Configure in Keycloak Admin Console
1. Log into Keycloak Admin Console
2. Select your realm
3. Go to "User Federation"
4. Click "Add provider" → "ofbiz-user-storage"
5. Configure database settings:
   - **JDBC Driver**: `com.mysql.cj.jdbc.Driver`
   - **JDBC URL**: `jdbc:mysql://localhost:3306/ofbiz`
   - **Username**: `ofbiz`
   - **Password**: `your_password`
6. Save and test

## Test Users (Docker Environment)
- **Username**: `admin`, **Password**: `password`
- **Username**: `john.doe`, **Password**: `password`
- **Username**: `jane.smith`, **Password**: `password`

## OFBiz Database Schema
Required tables:
- `user_login`: User authentication data
- `person`: User personal information
- `contact_mech`: Contact information (email)
- `party_contact_mech`: Links users to contact info

## Troubleshooting

### Connection Issues
- Verify database connectivity
- Check JDBC URL format
- Ensure database user has proper permissions

### Authentication Failures
- Check OFBiz password format: `{SHA}salt$hash`
- Verify user_login.enabled = 'Y'
- Review Keycloak logs

### Performance Issues
- Adjust connection pool size
- Check database indexes
- Monitor connection usage

## VS Code Tasks
- **Build**: `Ctrl+Shift+P` → "Tasks: Run Task" → "Build Keycloak OFBiz SPI"
- **Test**: `Ctrl+Shift+P` → "Tasks: Run Task" → "Run Tests"
- **Deploy**: `Ctrl+Shift+P` → "Tasks: Run Task" → "Deploy to Keycloak"

## File Structure
```
├── src/main/java/org/selzcore/keycloak/ofbiz/
│   ├── OFBizUserStorageProvider.java      # Main SPI implementation
│   ├── OFBizUserStorageProviderFactory.java # Factory and configuration
│   ├── OFBizUserAdapter.java              # User model adapter
│   ├── OFBizConnectionProvider.java       # Database connections
│   └── OFBizPasswordUtil.java             # Password utilities
├── src/main/resources/
│   ├── META-INF/services/                  # SPI service registration
│   └── application.properties             # Default configuration
├── scripts/deploy.sh                      # Deployment script
├── docker-compose.yml                     # Test environment
└── README.md                              # Full documentation
```

## Next Steps
1. Customize SQL queries for your OFBiz schema
2. Add user attribute mapping if needed
3. Implement group synchronization
4. Set up monitoring and logging
5. Configure backup and recovery procedures
