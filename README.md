# Keycloak OFBiz SPI

A Keycloak Service Provider Interface (SPI) that integrates Keycloak v26.3.2 with Apache OFBiz v24.09.01 via REST API. This allows other systems to use Keycloak as an OIDC provider while user data remains in the OFBiz system.

## Features

- **REST API Integration**: Secure integration with OFBiz via REST endpoints
- **User Authentication**: Authenticate users against OFBiz via REST API
- **Password Verification**: Support for OFBiz password hashing and validation
- **User Lookup**: Find users by username or ID through REST API
- **User Creation**: Create new users in OFBiz when they don't exist (optional)
- **Secure Password Generation**: Automatic cryptographically secure random passwords for new users
- **Tenant Support**: Multi-tenant user attributes and custom data mapping
- **JWT Token Management**: Secure token-based authentication with OFBiz
- **Comprehensive Logging**: Detailed authentication and operational logging
- **Configurable**: Easy configuration through Keycloak admin console

## Architecture

```
┌─────────────────┐    OIDC/SAML    ┌─────────────────┐
│   Client Apps   │ ◄──────────────► │    Keycloak     │
└─────────────────┘                 └─────────────────┘
                                             │
                                    Keycloak SPI
                                             │
                                             ▼
                                 ┌─────────────────┐
                                 │ OFBiz REST API  │
                                 │   (Web Service) │
                                 └─────────────────┘
                                             │
                                             ▼
                                 ┌─────────────────┐
                                 │   Apache OFBiz  │
                                 │   (User Store)  │
                                 └─────────────────┘
```

## Prerequisites

- Java 11 or higher
- Maven 3.6 or higher
- Keycloak 26.3.2
- Apache OFBiz 24.09.01
- Database (MySQL, PostgreSQL, etc.)

## Building

```bash
mvn clean package
```

This will create `target/keycloak-ofbiz-spi-0.0.7.jar`

## Installation

1. **Build the JAR**: Run `mvn clean package` to build the SPI JAR file.

2. **Deploy to Keycloak**: Copy the JAR file to your Keycloak providers directory:
   ```bash
   cp target/keycloak-ofbiz-spi-0.0.7.jar $KEYCLOAK_HOME/providers/
   ```

3. **Restart Keycloak**: Restart your Keycloak server to load the new provider.

4. **Configure the Provider**: 
   - Log into Keycloak Admin Console
   - Navigate to your realm
   - Go to "User Federation"
   - Click "Add provider" and select "ofbiz-user-storage"

## Configuration

The OFBiz User Storage Provider integrates with OFBiz via REST API endpoints for secure, distributed deployments.

For detailed configuration instructions, see:
- **[Configuration Guide](readme/CONFIGURATION_GUIDE.md)** - Basic configuration instructions
- **[Runtime Realm Configuration](readme/RUNTIME_REALM_CONFIGURATION.md)** - Advanced realm-specific settings

### Quick Start Configuration

Configure the following properties in Keycloak Admin Console:

```
OFBiz Base URL: http://ofbiz.local:8080
Auth Endpoint: /rest/auth/token
User Endpoint: /rest/services/getUserInfo
Timeout: 5000
Enabled Realms: ofbiz
Enable User Creation: false
Enable Tenant Creation: false
```

### Configuration Properties

| Property | Description | Default |
|----------|-------------|---------|
| OFBiz Base URL | Base URL of OFBiz instance | `http://ofbiz.local:8080` |
| OFBiz Auth Endpoint | Authentication endpoint | `/rest/auth/token` |
| OFBiz User Endpoint | User info endpoint | `/rest/services/getUserInfo` |
| OFBiz Timeout | Request timeout in milliseconds | `5000` |
| Enabled Realms | Comma-separated list of enabled realms | `ofbiz` |
| Enable User Creation | Allow creating new users | `false` |
| Enable Tenant Creation | Allow creating new tenants | `false` |
| Service Account Username | OFBiz service account for user lookups | `admin` |
| Service Account Password | OFBiz service account password | `ofbiz` |

## OFBiz Database Schema

The SPI expects the following OFBiz tables:

### user_login
- `user_login_id` (VARCHAR): Primary key, username
- `current_password` (VARCHAR): Hashed password
- `enabled` (CHAR): 'Y' for enabled, 'N' for disabled
- `party_id` (VARCHAR): Link to person table

### person
- `party_id` (VARCHAR): Primary key, links to user_login
- `first_name` (VARCHAR): User's first name
- `last_name` (VARCHAR): User's last name
- `personal_title` (VARCHAR): Title (Mr., Ms., etc.)

### contact_mech
- `party_id` (VARCHAR): Links to person
- `contact_mech_type_id` (VARCHAR): Type of contact ('EMAIL_ADDRESS')
- `info_string` (VARCHAR): The actual contact information
- `thru_date` (TIMESTAMP): NULL for active contacts

## Password Format

OFBiz passwords are typically stored in the format:
```
{hashType}salt$hash
```

Examples:
- `{SHA}mysalt$dGVzdGhhc2g=` (SHA-1 with salt)
- `{SHA256}anothersalt$aGFzaGVkdmFsdWU=` (SHA-256 with salt)

The SPI automatically handles password verification using the `OFBizPasswordUtil` class.

## Testing

Run unit tests:
```bash
mvn test
```

Run integration tests (requires test database):
```bash
mvn integration-test
```

## Usage Example

Once configured, users can authenticate through Keycloak using their OFBiz credentials:

1. **Direct Login**: Users log into Keycloak using OFBiz username/password
2. **OIDC Integration**: Client applications use Keycloak as OIDC provider
3. **User Management**: User data remains in OFBiz, Keycloak acts as authentication proxy

## Troubleshooting

### Common Issues

1. **Connection Errors**
   - Verify database connectivity
   - Check JDBC URL format
   - Ensure database user has proper permissions

2. **Authentication Failures**
   - Verify password format in OFBiz database
   - Check user_login.enabled = 'Y'
   - Review Keycloak logs for detailed errors

3. **Performance Issues**
   - Adjust connection pool size
   - Optimize database queries
   - Check database indexes

### Logging

Enable debug logging in Keycloak:
```xml
<logger category="org.selzcore.keycloak.ofbiz" use-parent-handlers="false">
    <level name="DEBUG"/>
    <handlers>
        <handler name="CONSOLE"/>
    </handlers>
</logger>
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests for new functionality
5. Ensure all tests pass
6. Submit a pull request

## Development Branches

This project maintains two main development branches to support different integration approaches:

### Main Branch (`main`)
- **REST API Only**: Pure REST API integration with OFBiz
- **Recommended for production**: More secure and scalable
- **Stateless**: No direct database connections
- **Version**: 0.0.7+
- **Features**: User authentication, creation, and management via OFBiz REST endpoints

### DB and REST Integration Branch (`feature/db-and-rest-integration`)
- **Dual Mode**: Supports both direct database and REST API integration
- **Legacy compatibility**: Maintains backward compatibility with database mode
- **Version**: 0.0.7
- **Features**: All REST features plus direct database connectivity option

**Migration Path**: Start with the dual-mode branch for existing installations, then migrate to REST-only main branch for new deployments.

## License

This project is licensed under the Apache License 2.0 - see the LICENSE file for details.

## Support

For issues and questions:
- Check the troubleshooting section
- Review Keycloak and OFBiz documentation
- Open an issue on GitHub

## Compatibility

| Component | Version |
|-----------|---------|
| Keycloak | 26.3.2+ |
| OFBiz | 24.09.01+ |
| Java | 11+ |
| MySQL | 8.0+ |
| PostgreSQL | 12+ |
