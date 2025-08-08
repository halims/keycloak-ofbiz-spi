# Authentication Flow Fix

## Problem Identified

The previous authentication flow was backwards - it was attempting to call `getUserInfo()` (which requires an authentication token) before actually authenticating the user's credentials. This created a chicken-and-egg problem where:

1. Keycloak calls `getUserByUsername()` during authentication process
2. `getUserByUsername()` tried to call `restClient.getUserInfo()` 
3. `getUserInfo()` required a service account token to access OFBiz REST API
4. But we're in the middle of authenticating the actual user!

## Correct Authentication Flow

The corrected flow now follows proper Keycloak SPI patterns:

### 1. User Lookup Phase (`getUserByUsername()`)
- **Purpose**: Keycloak needs to know if the user exists before attempting authentication
- **Action**: Return a placeholder `UserModel` with basic information
- **No OFBiz API calls**: This method should NOT make REST calls to OFBiz
- **Placeholder data**: Uses username, email placeholder, default names

### 2. Credential Validation Phase (`isValid()`)
- **Purpose**: Validate the user's password against OFBiz
- **Action**: Call `restClient.authenticateUser(username, password)`
- **Primary authentication**: This is where actual OFBiz authentication happens via `/rest/auth/token`
- **Cache update**: After successful authentication, populate user cache with full user info

### 3. User Information Enrichment (Post-Authentication)
- **Purpose**: Get complete user profile after successful authentication
- **Action**: Call `restClient.getUserInfo(username)` using the token from step 2
- **Cache storage**: Store complete user information for subsequent requests

## Key Changes Made

### OFBizUserStorageProvider.java

1. **Modified `getUserByUsername()`**:
   ```java
   // OLD: Called restClient.getUserInfo() - WRONG!
   OFBizRestClient.OFBizUserInfo userInfo = restClient.getUserInfo(username);
   
   // NEW: Return placeholder user model
   return new AbstractUserAdapterFederatedStorage(session, realm, model) {
       // Placeholder implementation
   };
   ```

2. **Enhanced `isValid()`**:
   ```java
   // Primary authentication call
   boolean isValid = restClient.authenticateUser(username, password);
   
   if (isValid) {
       // After successful auth, get full user info and cache it
       OFBizRestClient.OFBizUserInfo userInfo = restClient.getUserInfo(username);
       if (userInfo != null) {
           UserModel fullUser = createUserModel(realm, username, userInfo);
           userCache.put(cacheKey, fullUser);
       }
   }
   ```

3. **Updated `getUserByEmail()`**:
   - Similar placeholder approach for email-based lookups
   - Checks cache first for already authenticated users

## Authentication Sequence

```
1. User enters credentials in Keycloak login form
2. Keycloak calls getUserByUsername() → Returns placeholder UserModel
3. Keycloak calls isValid() with user credentials
4. isValid() calls authenticateUser() → OFBiz /rest/auth/token
5. If authentication succeeds:
   a. Call getUserInfo() with authenticated token
   b. Create full UserModel with real user data
   c. Cache the complete user information
6. Keycloak completes login with full user profile
```

## Benefits

1. **Correct flow**: Authentication happens before user info lookup
2. **No token dependencies**: User lookup doesn't require pre-authentication
3. **Performance**: Cached user info after successful authentication
4. **Security**: Service account tokens only used for post-authentication enrichment
5. **Keycloak compliance**: Follows standard SPI patterns

## Service Account Usage

Service accounts are now properly used only for:
- User creation operations (`createUser()`)
- Tenant creation operations (`createTenant()`)
- Post-authentication user info enrichment

They are NOT used during the primary authentication flow, which correctly uses the actual user's credentials.

## Testing

✅ Build successful with all tests passing
✅ Proper separation of concerns between lookup and authentication
✅ No circular dependencies in authentication flow
✅ Compatible with Keycloak SPI contracts

The authentication flow is now correct and production-ready.
