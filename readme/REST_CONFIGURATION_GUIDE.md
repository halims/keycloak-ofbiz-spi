# How to Configure REST Mode in Keycloak Admin Console

## Step-by-Step Instructions

### 1. Access Keycloak Admin Console
- Open your browser and go to: **http://localhost:8090**
- Login with:
  - Username: `admin`
  - Password: `admin`

### 2. Navigate to User Federation
1. Select your realm (or create a new one - avoid using 'master' realm in production)
2. In the left sidebar, click **"User federation"**
3. Click **"Add provider"**
4. Select **"ofbiz-user-storage"** from the dropdown

### 3. Configure Integration Mode
You should see a configuration form with the following fields:

#### Integration Mode (Dropdown)
- **Field**: "Integration Mode"
- **Options**: 
  - `database` (Default)
  - `rest` 
- **Select**: Choose `rest` to enable REST API integration

### 4. REST Mode Configuration Fields

Once you select `rest` as the integration mode, configure these fields:

#### Required Fields:
- **OFBiz Base URL**: `http://ofbiz.local:8080`
- **OFBiz Authentication Endpoint**: `/rest/services/checkLogin`
- **OFBiz User Info Endpoint**: `/rest/services/getUserInfo`

#### Optional Fields:
- **OFBiz API Key**: (if your OFBiz requires API authentication)
- **OFBiz Request Timeout**: `5000` (milliseconds)
- **Enabled Realms**: (comma-separated list of realms, leave empty for all)

### 5. Example REST Configuration

```
Integration Mode: rest
OFBiz Base URL: http://ofbiz.local:8080
OFBiz Authentication Endpoint: /rest/services/checkLogin
OFBiz User Info Endpoint: /rest/services/getUserInfo
OFBiz API Key: (leave empty if not required)
OFBiz Request Timeout: 5000
Enabled Realms: (leave empty for all realms or specify: mycompany,partners)
Tenant Attribute Name: tenant
Custom Attributes: department,employeeId,costCenter
```

### 6. Save and Test
1. Click **"Save"** at the bottom of the form
2. The provider will be created and you should see it in the User Federation list
3. You can test the configuration by trying to authenticate a user

## Troubleshooting

### If you don't see the "Integration Mode" dropdown:

1. **Check SPI Installation**:
   - Verify the JAR file is in Keycloak's providers directory
   - Check Keycloak logs for any loading errors

2. **Refresh Browser Cache**:
   - Clear browser cache and reload the admin console
   - Try in an incognito/private window

3. **Restart Keycloak**:
   ```bash
   docker compose -f docker-compose.postgres.yml restart keycloak
   ```

4. **Check Logs**:
   ```bash
   docker compose -f docker-compose.postgres.yml logs keycloak
   ```

### Configuration Fields Not Visible

If some configuration fields are not showing up:
- Ensure you're using Keycloak version 26.3.2 or compatible
- The SPI should automatically show/hide fields based on integration mode selection
- All fields should be visible initially - the UI doesn't conditionally hide fields based on the integration mode selection

## Expected OFBiz REST API

Your OFBiz instance should provide these REST endpoints:

### Authentication Endpoint
```
POST /rest/services/checkLogin
Content-Type: application/json

{
  "username": "john.doe",
  "password": "userpassword"
}

Response:
{
  "success": true
}
```

### User Info Endpoint
```
GET /rest/services/getUserInfo?username=john.doe

Response:
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
      "employeeId": "EMP001"
    }
  }
}
```

## Testing the Configuration

Once configured, you can test by:
1. Creating a test user in your realm
2. Setting the user's username to match an OFBiz user
3. Attempting to login with that user's OFBiz credentials
4. Check Keycloak logs for authentication attempts and results

The logs will show:
```
✅ REST AUTH SUCCESS: User 'john.doe' authenticated via OFBiz REST API
✅ REST USER FOUND: User 'john.doe' found via REST API (tenant: 'COMPANY_A')
```

Or in case of errors:
```
❌ REST AUTH FAILED: User 'john.doe' authentication failed
❌ REST USER NOT FOUND: User 'john.doe' not found via REST API
```
