# OFBiz Keycloak Login Test Scripts

This directory contains test scripts to validate that external applications can authenticate users through Keycloak using the OFBiz SPI as the backing user store.

## Test Scripts

### 1. `test-ofbiz-login.sh` - Comprehensive Test
**Full-featured test script that demonstrates complete OIDC integration**

```bash
./scripts/test-ofbiz-login.sh
```

**Features:**
- ✅ Checks prerequisites (jq, curl, Keycloak availability)
- ✅ Creates OFBiz realm if needed
- ✅ Creates test OIDC client if needed
- ✅ Tests Direct Access Grant flow (username/password)
- ✅ Shows token introspection and user info
- ✅ Provides Authorization Code flow instructions
- ✅ Comprehensive error diagnosis

**Default credentials:** `admin` / `ofbiz`

### 2. `quick-test.sh` - Simple Test
**Quick validation script for basic authentication**

```bash
./scripts/quick-test.sh [username] [password]
```

**Features:**
- ✅ Fast basic connectivity check
- ✅ Simple username/password authentication test
- ✅ Shows user information from token
- ✅ Minimal setup required

**Examples:**
```bash
# Test with default credentials (admin/ofbiz)
./scripts/quick-test.sh

# Test with custom credentials
./scripts/quick-test.sh myuser mypassword
```

## Prerequisites

1. **Keycloak running:** Start the development environment
   ```bash
   docker-compose up -d
   ```

2. **Required tools:**
   - `curl` - HTTP client
   - `jq` - JSON processor
   - `openssl` - For PKCE (comprehensive test only)

   Install on Ubuntu/Debian:
   ```bash
   sudo apt-get install curl jq openssl
   ```

3. **OFBiz SPI configured:** Ensure the SPI is deployed and configured in Keycloak

## Test Scenarios

### Scenario 1: Basic Authentication Test
```bash
# Quick check if OFBiz SPI is working
./scripts/quick-test.sh admin ofbiz
```

**Expected Result:** ✅ Login successful with user information displayed

### Scenario 2: External App Integration Test
```bash
# Full OIDC flow simulation
./scripts/test-ofbiz-login.sh
```

**Expected Results:**
- ✅ Realm and client created/verified
- ✅ Direct access grant successful
- ✅ Token introspection working
- ✅ User info endpoint accessible
- ✅ Authorization code flow instructions provided

### Scenario 3: Custom User Test
```bash
# Test with different OFBiz user
./scripts/quick-test.sh flexadmin ofbiz
```

## Understanding the Output

### Successful Authentication
```
✅ Login successful!

👤 User Information:
   Username: admin
   Email: ofbiztest@example.com
   Name: OFBiz Administrator
   Tenant: company

🎉 OFBiz SPI authentication working!
```

### Failed Authentication
```
❌ Login failed!
   Error: invalid_grant
   Description: Invalid user credentials

💡 Troubleshooting:
   • Ensure OFBiz SPI is configured in Keycloak
   • Check that the 'ofbiz' realm exists
   • Verify user 'admin' exists in OFBiz
   • Check Keycloak logs: docker logs keycloak-dev -f
```

## Integration Modes

The OFBiz SPI supports two integration modes:

### 1. REST Mode (Recommended)
- Uses OFBiz REST API endpoints
- Better security isolation
- Easier deployment
- Current default configuration

### 2. Database Mode
- Direct database access
- Higher performance
- Requires database connectivity from Keycloak

## Token Flow Examples

### Direct Access Grant (Username/Password)
```bash
# What the test script does internally:
curl -X POST "http://localhost:8080/realms/ofbiz/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=ofbiz-test-client" \
  -d "client_secret=your-client-secret" \
  -d "username=admin" \
  -d "password=ofbiz" \
  -d "scope=openid email profile"
```

### Authorization Code Flow (Browser-based)
1. **Authorization Request:** Redirect user to Keycloak login
2. **User Authentication:** User logs in with OFBiz credentials
3. **Authorization Code:** Keycloak returns code to redirect URI
4. **Token Exchange:** App exchanges code for access token

## Troubleshooting

### Common Issues

**1. "Keycloak is not running"**
```bash
# Start Keycloak
docker-compose up -d

# Check status
docker-compose ps
```

**2. "jq command not found"**
```bash
# Ubuntu/Debian
sudo apt-get install jq

# macOS
brew install jq
```

**3. "Login failed - invalid_grant"**
- Check OFBiz SPI configuration in Keycloak admin console
- Verify OFBiz backend is accessible
- Ensure user exists in OFBiz database
- Check Keycloak logs for detailed errors

**4. "Client not found"**
- Run the comprehensive test script to create the test client
- Or manually create an OIDC client in Keycloak admin console

**5. "Realm not found"**
- Ensure 'ofbiz' realm exists in Keycloak
- Run comprehensive test script to auto-create realm

### Debug Commands

```bash
# Check Keycloak logs
docker logs keycloak-dev -f

# Check Keycloak container status
docker-compose ps keycloak

# Test Keycloak connectivity
curl -s http://localhost:8080/realms/master/.well-known/openid_configuration

# Check OFBiz realm configuration
curl -s http://localhost:8080/realms/ofbiz/.well-known/openid_configuration
```

## Security Notes

⚠️ **These test scripts are for development and testing only**

- Default credentials (`admin`/`ofbiz`) should be changed in production
- Test client secrets should be rotated
- Enable proper SSL/TLS in production environments
- Consider using client certificate authentication for enhanced security

## Next Steps

After successful testing:

1. **Production Setup:** Configure production Keycloak instance
2. **Client Registration:** Create production OIDC clients
3. **User Migration:** Import/sync OFBiz users as needed  
4. **Integration:** Update external applications to use Keycloak OIDC endpoints
5. **Monitoring:** Set up logging and monitoring for authentication flows

## Related Documentation

- [Integration Modes Guide](../readme/INTEGRATION_MODES_GUIDE.md)
- [Configuration Guide](../readme/CONFIGURATION_GUIDE.md)
- [Runtime Realm Configuration](../readme/RUNTIME_REALM_CONFIGURATION.md)
- [OFBiz SPI README](../README.md)
