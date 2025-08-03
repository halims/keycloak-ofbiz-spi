# OFBiz User Storage Provider Configuration Guide

## Overview

The OFBiz User Storage Provider allows Keycloak to authenticate users against an Apache OFBiz database. This SPI is designed to be **realm-specific** and should NOT be used on the master realm for security reasons.

## Important Security Considerations

⚠️ **WARNING**: Never configure this SPI on the master realm. The master realm should always use Keycloak's built-in authentication mechanisms.

✅ **RECOMMENDED**: Create dedicated realms for your applications and configure the OFBiz SPI only on those realms.

## Configuration Steps

### 1. Create a New Realm

1. Login to Keycloak Admin Console
2. Click on the realm dropdown (top-left)
3. Click "Create Realm"
4. Enter a name (e.g., "myapp-realm")
5. Click "Create"

### 2. Configure User Federation

1. In your new realm, go to **User Federation**
2. Click **Add provider** → **ofbiz-user-storage**
3. Configure the following settings:

#### Required Settings:

| Setting | Value | Description |
|---------|-------|-------------|
| **Console Display Name** | OFBiz Users | Display name in admin console |
| **JDBC Driver Class** | `com.mysql.cj.jdbc.Driver` | For MySQL, or `org.postgresql.Driver` for PostgreSQL |
| **JDBC URL** | `jdbc:mysql://localhost:3306/ofbiz` | Your OFBiz database URL |
| **Database Username** | `ofbiz` | Database username |
| **Database Password** | `ofbiz` | Database password |

#### Optional Settings:

| Setting | Default | Description |
|---------|---------|-------------|
| **Validation Query** | `SELECT 1` | Query to test connections |
| **Connection Pool Size** | `10` | Max database connections |
| **Enabled Realms** | *(empty)* | Comma-separated list of allowed realms |

### 3. Realm-Specific Configuration

#### Option A: Allow All Realms (Default)
Leave **Enabled Realms** empty. The provider will work on any realm but log warnings for the master realm.

#### Option B: Restrict to Specific Realms (Recommended)
Set **Enabled Realms** to: `myapp-realm,another-realm`

This ensures the provider only works on explicitly listed realms.

## Testing the Configuration

### 1. Test Database Connection
1. Save the configuration
2. Check Keycloak logs for connection errors
3. Look for: `OFBiz User Storage Provider configuration validated successfully`

### 2. Test User Lookup
1. Go to **Users** in your realm
2. Click **Add user**
3. Try to find an existing OFBiz user
4. The user should appear if found in the OFBiz database

### 3. Test Authentication
1. Create a test client application
2. Try to login with OFBiz credentials
3. Check logs for authentication attempts

## Database Requirements

### OFBiz Tables Required:
- `user_login` - Main user authentication table
- `person` - User personal information
- `party_contact_mech` - Contact mechanism relationships
- `contact_mech` - Contact information (email, etc.)

### Required OFBiz Schema:
```sql
-- Minimal required schema for the SPI
CREATE TABLE user_login (
    user_login_id VARCHAR(255) PRIMARY KEY,
    party_id VARCHAR(255),
    current_password VARCHAR(255),
    enabled CHAR(1) DEFAULT 'Y'
);

CREATE TABLE person (
    party_id VARCHAR(255) PRIMARY KEY,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    personal_title VARCHAR(255)
);

CREATE TABLE contact_mech (
    contact_mech_id VARCHAR(255) PRIMARY KEY,
    contact_mech_type_id VARCHAR(255),
    info_string VARCHAR(255)
);

CREATE TABLE party_contact_mech (
    party_id VARCHAR(255),
    contact_mech_id VARCHAR(255),
    from_date TIMESTAMP,
    thru_date TIMESTAMP
);
```

## Troubleshooting

### Common Issues:

#### 1. "Factory not found" errors
- **Cause**: SPI JAR not properly loaded
- **Solution**: Check `/opt/keycloak/providers/` directory contains the JAR file

#### 2. Database connection errors
- **Cause**: Wrong JDBC URL or credentials
- **Solution**: Verify database connectivity and credentials

#### 3. Users not found
- **Cause**: Realm configuration or database schema issues
- **Solution**: Check enabled realms setting and verify OFBiz data exists

#### 4. Master realm warnings
- **Cause**: SPI configured on master realm
- **Solution**: Use dedicated realms and set "Enabled Realms" appropriately

### Log Messages to Look For:

#### Success Messages:
```
OFBiz User Storage Provider configuration validated successfully
OFBiz User Storage Provider for model: MyProvider in realm: myapp-realm
Getting user by username: testuser in realm: myapp-realm
```

#### Warning Messages:
```
OFBiz User Storage Provider active on master realm - this is not recommended
OFBiz provider not active for realm: master, skipping user lookup
```

#### Error Messages:
```
JDBC Driver class not found
Unable to connect to database
Error getting user by username
```

## Security Best Practices

1. **Never use on master realm**
2. **Use dedicated application realms**
3. **Set strong database passwords**
4. **Limit database user permissions**
5. **Use SSL for database connections in production**
6. **Configure connection pooling appropriately**
7. **Monitor authentication logs**

## Example Production Configuration

### Realm: `production-app`
```yaml
Console Display Name: Production OFBiz Users
JDBC Driver Class: org.postgresql.Driver
JDBC URL: jdbc:postgresql://db-server:5432/ofbiz_prod
Database Username: keycloak_readonly
Database Password: ******************
Validation Query: SELECT 1
Connection Pool Size: 20
Enabled Realms: production-app
```

### Realm: `staging-app` 
```yaml
Console Display Name: Staging OFBiz Users
JDBC Driver Class: org.postgresql.Driver
JDBC URL: jdbc:postgresql://staging-db:5432/ofbiz_staging
Database Username: keycloak_readonly
Database Password: ******************
Validation Query: SELECT 1
Connection Pool Size: 5
Enabled Realms: staging-app
```

This ensures each environment is isolated and the master realm remains protected.
