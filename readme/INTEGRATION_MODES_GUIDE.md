# OFBiz User Storage Provider Integration Modes Guide

The Keycloak OFBiz SPI now supports two integration modes:

1. **Database Mode**: Direct database connection to OFBiz database
2. **REST Mode**: Integration via OFBiz REST API endpoints

## Integration Mode Selection

When configuring the OFBiz User Storage Provider in Keycloak Admin Console, you can choose between these two modes:

### Configuration Properties

| Property | Description | Required | Default |
|----------|-------------|----------|---------|
| `integrationMode` | Integration mode: `database` or `rest` | Yes | `database` |
| `enabledRealms` | Comma-separated list of realms where this provider is active | No | All realms |

## Database Mode Configuration

Direct connection to the OFBiz database using JDBC drivers.

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `integrationMode` | Set to `database` | `database` |
| `dbUrl` | JDBC URL to OFBiz database | `jdbc:postgresql://localhost:5432/ofbiz` |
| `dbUsername` | Database username | `ofbiz` |
| `dbPassword` | Database password | `ofbiz123` |
| `connectionPoolSize` | Connection pool size | `5` |

### Example Database Configuration

```
Integration Mode: database
Database URL: jdbc:postgresql://localhost:5432/ofbiz
Database Username: ofbiz
Database Password: ofbiz123
Connection Pool Size: 5
Enabled Realms: mycompany,customers
```

### Database Mode Features

- ✅ User authentication against OFBiz password hashes
- ✅ User lookup by username and email
- ✅ Tenant information from party groups/roles
- ✅ Custom attributes from OFBiz user data
- ✅ User search and count operations
- ✅ Full SQL query optimization

## REST Mode Configuration

Integration via OFBiz REST API endpoints for authentication and user data.

### Required Properties

| Property | Description | Example |
|----------|-------------|---------|
| `integrationMode` | Set to `rest` | `rest` |
| `ofbizBaseUrl` | Base URL of OFBiz REST API | `https://ofbiz.mycompany.com` |
| `ofbizAuthEndpoint` | Authentication endpoint path | `/rest/auth/login` |
| `ofbizUserEndpoint` | User information endpoint path | `/rest/user/info` |
| `ofbizApiKey` | API key for authentication (optional) | `your-api-key-here` |
| `ofbizTimeout` | Request timeout in milliseconds | `5000` |

### Example REST Configuration

```
Integration Mode: rest
OFBiz Base URL: https://ofbiz.mycompany.com
Auth Endpoint: /rest/auth/login
User Endpoint: /rest/user/info
API Key: abc123def456ghi789
Timeout: 5000
Enabled Realms: mycompany,customers
```

### REST Mode Features

- ✅ User authentication via REST API calls
- ✅ User lookup by username and email via REST
- ✅ Tenant information from REST API responses
- ✅ Custom attributes from REST API responses
- ❌ User search operations (disabled for security)
- ❌ User count operations (disabled for security)

## OFBiz REST API Requirements

When using REST mode, your OFBiz instance must provide the following REST endpoints:

### Authentication Endpoint

**POST** `/rest/auth/login`

Request body:
```json
{
  "username": "john.doe",
  "password": "userpassword"
}
```

Response (success):
```json
{
  "success": true
}
```

Response (failure):
```json
{
  "success": false,
  "errorMessage": "Invalid credentials"
}
```

### User Information Endpoint

**GET** `/rest/user/info?username=john.doe`
**GET** `/rest/user/info?email=john.doe@company.com`

Response (success):
```json
{
  "success": true,
  "user": {
    "username": "john.doe",
    "firstName": "John",
    "lastName": "Doe",
    "email": "john.doe@company.com",
    "enabled": true,
    "tenant": "COMPANY_A",
    "attributes": {
      "department": "IT",
      "employeeId": "EMP001",
      "costCenter": "CC100"
    }
  }
}
```

Response (not found):
```json
{
  "success": false,
  "errorMessage": "User not found"
}
```

## Security Considerations

### Database Mode
- Ensure database credentials are securely stored
- Use connection pooling for performance
- Consider read-only database user for security
- Network security between Keycloak and database

### REST Mode  
- Use HTTPS for all REST API communications
- Implement proper API key management
- Consider rate limiting on OFBiz REST endpoints
- Validate SSL certificates in production

## Performance Comparison

| Feature | Database Mode | REST Mode |
|---------|---------------|-----------|
| Authentication Speed | Fast (direct DB) | Medium (HTTP overhead) |
| User Lookup Speed | Fast (optimized SQL) | Medium (HTTP + JSON) |
| Network Dependencies | Database only | OFBiz web service |
| Caching | Connection pooling | HTTP-level caching |
| Scalability | DB connection limits | REST API limits |

## Choosing the Right Mode

### Use Database Mode When:
- You have direct database access to OFBiz
- Performance is critical
- You need user search/admin operations
- Your OFBiz and Keycloak are in the same network

### Use REST Mode When:
- OFBiz database access is restricted
- You want to leverage OFBiz business logic
- You need to audit authentication through OFBiz
- OFBiz and Keycloak are in different networks/clouds
- You want to maintain OFBiz as the single point of authentication

## Logging and Monitoring

Both modes provide comprehensive logging:

```
✅ DATABASE AUTH SUCCESS: User 'john.doe' authenticated via database in realm 'mycompany'
✅ REST AUTH SUCCESS: User 'john.doe' authenticated via OFBiz REST API
❌ DATABASE AUTH FAILED: Invalid credentials for user 'john.doe' in realm 'mycompany'
❌ REST AUTH FAILED: User 'john.doe' authentication failed via REST API
```

## Migration Between Modes

You can change integration modes by updating the provider configuration in Keycloak Admin Console. No code changes are required.

### Database to REST Migration Steps:
1. Set up OFBiz REST API endpoints
2. Update provider configuration to REST mode
3. Test authentication with a few users
4. Monitor logs for any issues
5. Gradually migrate all realms

### REST to Database Migration Steps:
1. Ensure database connectivity
2. Update provider configuration to database mode  
3. Test authentication with a few users
4. Monitor connection pool performance
5. Gradually migrate all realms

## Troubleshooting

### Common Database Mode Issues:
- Connection pool exhaustion
- Database connectivity problems
- SQL permission issues
- Password encoding mismatches

### Common REST Mode Issues:
- Network connectivity to OFBiz
- API endpoint configuration errors
- Authentication timeouts
- JSON parsing errors

## Example Configurations

### Small Company (Database Mode)
```
integrationMode: database
dbUrl: jdbc:mysql://localhost:3306/ofbiz
dbUsername: keycloak_readonly
dbPassword: secure_password
connectionPoolSize: 3
enabledRealms: company
```

### Enterprise (REST Mode)
```
integrationMode: rest
ofbizBaseUrl: https://ofbiz-prod.company.com
ofbizAuthEndpoint: /webtools/control/jsonservice/authenticateUser
ofbizUserEndpoint: /webtools/control/jsonservice/getUserInfo
ofbizApiKey: prod-api-key-xyz789
ofbizTimeout: 3000
enabledRealms: employees,partners,customers
```
