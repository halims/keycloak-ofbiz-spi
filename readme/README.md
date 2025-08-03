# Keycloak OFBiz SPI

A Keycloak Service Provider Interface (SPI) that integrates Keycloak v26.3.2 with Apache OFBiz v24.09.01 as the backing user store. This allows other systems to use Keycloak as an OIDC provider while user data remains in the OFBiz system.

## Features

- **User Authentication**: Authenticate users against OFBiz database
- **Password Verification**: Support for OFBiz password hashing schemes (SHA-1 with salt)
- **User Lookup**: Find users by username, email, or ID
- **User Search**: Search and list users with pagination
- **Connection Pooling**: Efficient database connection management with HikariCP
- **Multi-Database Support**: Works with MySQL, PostgreSQL, and other JDBC databases
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
                                    │  OFBiz Database │
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

This will create `target/keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar`

## Installation

1. **Build the JAR**: Run `mvn clean package` to build the SPI JAR file.

2. **Deploy to Keycloak**: Copy the JAR file to your Keycloak providers directory:
   ```bash
   cp target/keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar $KEYCLOAK_HOME/providers/
   ```

3. **Restart Keycloak**: Restart your Keycloak server to load the new provider.

4. **Configure the Provider**: 
   - Log into Keycloak Admin Console
   - Navigate to your realm
   - Go to "User Federation"
   - Click "Add provider" and select "ofbiz-user-storage"

## Configuration

Configure the following properties in Keycloak Admin Console:

| Property | Description | Default |
|----------|-------------|---------|
| JDBC Driver Class | Database driver class name | `com.mysql.cj.jdbc.Driver` |
| JDBC URL | Database connection URL | `jdbc:mysql://localhost:3306/ofbiz` |
| Database Username | Database username | `ofbiz` |
| Database Password | Database password | - |
| Validation Query | SQL query to validate connections | `SELECT 1` |
| Connection Pool Size | Maximum connections in pool | `10` |

### Example Configuration

**MySQL:**
```
JDBC Driver: com.mysql.cj.jdbc.Driver
JDBC URL: jdbc:mysql://localhost:3306/ofbiz?useSSL=false&serverTimezone=UTC
Username: ofbiz
Password: your_password
```

**PostgreSQL:**
```
JDBC Driver: org.postgresql.Driver
JDBC URL: jdbc:postgresql://localhost:5432/ofbiz
Username: ofbiz
Password: your_password
```

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
