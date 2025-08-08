# Service Account Authentication Fix

## Problem Solved

**Issue**: "No authentication token available for user info lookup - authentication required first"

**Root Cause**: The `getUserByUsername()` method was being called during user lookup (before password validation), but the `getUserInfo()` REST API call required an authentication token.

## Solution

Implemented service account authentication to resolve the token availability issue.

### Technical Implementation

#### 1. Added Service Account Configuration

**New Configuration Properties**:
- `serviceAccountUsername`: OFBiz service account username for user lookups
- `serviceAccountPassword`: OFBiz service account password for user lookups

#### 2. Authentication Flow Changes

**Before (Broken)**:
```
User Lookup → getUserInfo() → ❌ No Token → Failure
```

**After (Fixed)**:
```
User Lookup → ensureAuthenticated() → Service Account Auth → getUserInfo() → ✅ Success
```

#### 3. Code Changes

**New Methods Added**:
```java
private boolean authenticateServiceAccount() {
    // Authenticate using configured service account credentials
}

private boolean ensureAuthenticated() {
    // Use existing token or authenticate with service account
}
```

**Updated Methods**:
- `getUserInfo()`: Now calls `ensureAuthenticated()` before API calls
- `getUserInfoByEmail()`: Updated to use service account authentication
- `createUser()`: Updated to use service account authentication
- `createTenant()`: Updated to use service account authentication

### Configuration Example

**In Keycloak Admin Console**:
```json
{
  "serviceAccountUsername": "admin",
  "serviceAccountPassword": "ofbiz",
  "ofbizBaseUrl": "http://localhost:8080",
  "ofbizAuthEndpoint": "/rest/auth/token",
  "ofbizUserEndpoint": "/rest/services/getUserInfo"
}
```

**In Configuration Script** (`configure-spi.sh`):
```bash
UPDATE_CONFIG='{
    "config": {
        "serviceAccountUsername": ["admin"],
        "serviceAccountPassword": ["ofbiz"],
        "ofbizBaseUrl": ["http://host.docker.internal:8080"],
        "ofbizAuthEndpoint": ["/rest/auth/token"],
        "ofbizUserEndpoint": ["/rest/services/getUserInfo"]
    }
}'
```

### Security Considerations

1. **Service Account Permissions**: The service account should have minimal required permissions:
   - Read access to user information
   - Permission to call `getUserInfo` REST service
   - Optional: User creation permissions if enabled

2. **Password Security**: Service account passwords are marked as `secret=true` in configuration and handled securely

3. **Token Reuse**: Authentication tokens are cached and reused to minimize authentication calls

### Authentication Flow Details

#### User Lookup Flow
1. **User requests authentication** → Keycloak calls `getUserByUsername()`
2. **Need user info** → `getUserInfo()` called
3. **Check token** → `ensureAuthenticated()` verifies token availability
4. **No token?** → `authenticateServiceAccount()` gets new token
5. **Token available** → Proceed with `getUserInfo()` API call
6. **Success** → User information retrieved and cached

#### Password Validation Flow
1. **User submits credentials** → Keycloak calls `isValid()`
2. **Validate credentials** → `authenticateUser()` with user's credentials
3. **Success** → User authenticated (token stored for user)
4. **Cache refresh** → User cache updated on successful login

### Benefits

1. **✅ Resolves Token Issue**: User lookups now work without prior authentication
2. **✅ Maintains Security**: Service account has controlled permissions
3. **✅ Improves Performance**: Token caching reduces authentication calls
4. **✅ Backward Compatible**: Existing user authentication flow unchanged
5. **✅ Flexible Configuration**: Service account is optional for basic functionality

### Deployment Notes

#### Required OFBiz Setup
1. **Create Service Account**: Create a dedicated user in OFBiz for SPI operations
2. **Set Permissions**: Grant minimal required permissions to the service account
3. **Configure SPI**: Add service account credentials to Keycloak configuration

#### Optional vs Required
- **User Lookups**: Service account recommended for optimal performance
- **User Authentication**: Uses individual user credentials (no service account needed)
- **User Creation**: Requires service account if user creation is enabled

### Testing

The fix can be tested by:

1. **Configure Service Account**: Add credentials to SPI configuration
2. **Test User Lookup**: Try to find users by username (should work without errors)
3. **Test Authentication**: Verify password validation still works correctly
4. **Check Logs**: Should see service account authentication in debug logs

Example log output:
```
DEBUG [OFBizRestClient] Authenticating with service account: admin
INFO  [OFBizRestClient] ✅ OFBIZ REST SUCCESS: User 'admin' authenticated successfully
DEBUG [OFBizRestClient] Successfully retrieved user info for 'testuser' via REST API
```

This fix ensures reliable operation of the Keycloak OFBiz SPI in production environments.
