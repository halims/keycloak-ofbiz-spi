#!/bin/bash
# Test script to emulate external app login using OFBiz realm
# This script demonstrates OIDC authorization code flow with the OFBiz SPI

set -e  # Exit on any error

echo "🔐 OFBiz Keycloak Login Test"
echo "============================="

# Configuration
KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
CLIENT_ID="ofbiz-test-client"
CLIENT_SECRET="your-client-secret"  # Will be generated during client creation
USERNAME="admin"
PASSWORD="ofbiz"
REDIRECT_URI="http://localhost:8888/callback"

# Admin credentials for setup
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo ""
echo "📋 Prerequisites Check"
echo "----------------------"

# Check if jq is installed
if ! command -v jq &> /dev/null; then
    echo "❌ jq is required but not installed. Please install jq first."
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   MacOS: brew install jq"
    exit 1
fi

# Check if curl is available
if ! command -v curl &> /dev/null; then
    echo "❌ curl is required but not installed."
    exit 1
fi

echo "✅ Required tools available (curl, jq)"

# Check if Keycloak is running
if ! curl -s -f "$KEYCLOAK_URL/realms/master" > /dev/null; then
    echo "❌ Keycloak is not running at $KEYCLOAK_URL"
    echo "   Please start Keycloak with: docker-compose up -d"
    exit 1
fi

echo "✅ Keycloak is running at $KEYCLOAK_URL"

echo ""
echo "📋 Step 1: Get Admin Token"
echo "--------------------------"

ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "❌ Failed to get admin token. Check admin credentials."
    exit 1
fi

echo "✅ Admin token obtained"

echo ""
echo "📋 Step 2: Check/Create OFBiz Realm"
echo "-----------------------------------"

# Check if realm exists
REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME")

if [ "$REALM_EXISTS" = "404" ]; then
    echo "Creating OFBiz realm..."
    
    REALM_CONFIG='{
        "realm": "'$REALM_NAME'",
        "enabled": true,
        "displayName": "OFBiz Users",
        "loginTheme": "keycloak",
        "accountTheme": "keycloak",
        "adminTheme": "keycloak",
        "emailTheme": "keycloak",
        "accessTokenLifespan": 300,
        "ssoSessionIdleTimeout": 1800,
        "ssoSessionMaxLifespan": 36000,
        "registrationAllowed": false,
        "loginWithEmailAllowed": true,
        "duplicateEmailsAllowed": false
    }'
    
    CREATE_REALM=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$REALM_CONFIG" \
        -w "%{http_code}" -o /dev/null)
    
    if [ "$CREATE_REALM" = "201" ]; then
        echo "✅ OFBiz realm created"
    else
        echo "❌ Failed to create realm (HTTP $CREATE_REALM)"
        exit 1
    fi
elif [ "$REALM_EXISTS" = "200" ]; then
    echo "✅ OFBiz realm already exists"
else
    echo "❌ Error checking realm (HTTP $REALM_EXISTS)"
    exit 1
fi

echo ""
echo "📋 Step 3: Check/Create Test Client"
echo "-----------------------------------"

# Check if client exists
CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" \
    | jq -r '.[0].id // empty')

if [ -z "$CLIENT_UUID" ]; then
    echo "Creating test client..."
    
    CLIENT_CONFIG='{
        "clientId": "'$CLIENT_ID'",
        "name": "OFBiz Test Client",
        "description": "Test client for OFBiz SPI authentication",
        "enabled": true,
        "clientAuthenticatorType": "client-secret",
        "secret": "'$CLIENT_SECRET'",
        "standardFlowEnabled": true,
        "implicitFlowEnabled": false,
        "directAccessGrantsEnabled": true,
        "serviceAccountsEnabled": false,
        "publicClient": false,
        "protocol": "openid-connect",
        "redirectUris": ["'$REDIRECT_URI'", "http://localhost:*"],
        "webOrigins": ["http://localhost:*"],
        "attributes": {
            "saml.assertion.signature": "false",
            "saml.force.post.binding": "false",
            "saml.multivalued.roles": "false",
            "saml.encrypt": "false",
            "saml.server.signature": "false",
            "saml.server.signature.keyinfo.ext": "false",
            "exclude.session.state.from.auth.response": "false",
            "saml_force_name_id_format": "false",
            "saml.client.signature": "false",
            "tls.client.certificate.bound.access.tokens": "false",
            "saml.authnstatement": "false",
            "display.on.consent.screen": "false",
            "saml.onetimeuse.condition": "false"
        }
    }'
    
    CREATE_CLIENT=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H "Content-Type: application/json" \
        -d "$CLIENT_CONFIG" \
        -w "%{http_code}" -o /dev/null)
    
    if [ "$CREATE_CLIENT" = "201" ]; then
        echo "✅ Test client created"
        # Get the newly created client UUID
        CLIENT_UUID=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
            "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients?clientId=$CLIENT_ID" \
            | jq -r '.[0].id')
    else
        echo "❌ Failed to create client (HTTP $CREATE_CLIENT)"
        exit 1
    fi
else
    echo "✅ Test client already exists"
fi

# Get or set client secret
if [ "$CLIENT_SECRET" = "your-client-secret" ]; then
    echo "Getting client secret..."
    CLIENT_SECRET=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
        "$KEYCLOAK_URL/admin/realms/$REALM_NAME/clients/$CLIENT_UUID/client-secret" \
        | jq -r '.value')
    
    if [ "$CLIENT_SECRET" = "null" ] || [ -z "$CLIENT_SECRET" ]; then
        echo "❌ Failed to get client secret"
        exit 1
    fi
    echo "✅ Client secret retrieved"
fi

echo ""
echo "📋 Step 4: Test Authentication Methods"
echo "======================================="

echo ""
echo "🔑 Method 1: Direct Access Grant (Username/Password)"
echo "---------------------------------------------------"

echo "Attempting login with username: $USERNAME, password: $PASSWORD"

TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=password" \
    -d "client_id=$CLIENT_ID" \
    -d "client_secret=$CLIENT_SECRET" \
    -d "username=$USERNAME" \
    -d "password=$PASSWORD" \
    -d "scope=openid email profile")

# Check if we got an access token
ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty')
REFRESH_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.refresh_token // empty')
ID_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.id_token // empty')

if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
    echo "✅ Direct grant login successful!"
    
    # Show token info
    echo ""
    echo "📊 Token Information:"
    echo "   Token Type: $(echo "$TOKEN_RESPONSE" | jq -r '.token_type')"
    echo "   Expires In: $(echo "$TOKEN_RESPONSE" | jq -r '.expires_in') seconds"
    echo "   Scope: $(echo "$TOKEN_RESPONSE" | jq -r '.scope')"
    
    # Decode and show JWT payload (access token)
    echo ""
    echo "🔍 Access Token Claims:"
    ACCESS_TOKEN_PAYLOAD=$(echo "$ACCESS_TOKEN" | cut -d. -f2)
    # Add padding if needed for base64 decoding
    case $((${#ACCESS_TOKEN_PAYLOAD} % 4)) in
        2) ACCESS_TOKEN_PAYLOAD="${ACCESS_TOKEN_PAYLOAD}==" ;;
        3) ACCESS_TOKEN_PAYLOAD="${ACCESS_TOKEN_PAYLOAD}=" ;;
    esac
    echo "$ACCESS_TOKEN_PAYLOAD" | base64 -d 2>/dev/null | jq '.' || echo "   (Unable to decode token payload)"
    
    # Test userinfo endpoint
    echo ""
    echo "👤 Getting User Information:"
    USER_INFO=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
        "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/userinfo")
    
    if echo "$USER_INFO" | jq -e . >/dev/null 2>&1; then
        echo "$USER_INFO" | jq '.'
    else
        echo "   Failed to get user info or invalid JSON response"
    fi
    
    # Test token introspection
    echo ""
    echo "🔍 Token Introspection:"
    INTROSPECT_RESPONSE=$(curl -s -X POST \
        "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token/introspect" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "token=$ACCESS_TOKEN" \
        -d "client_id=$CLIENT_ID" \
        -d "client_secret=$CLIENT_SECRET")
    
    if echo "$INTROSPECT_RESPONSE" | jq -e . >/dev/null 2>&1; then
        echo "   Active: $(echo "$INTROSPECT_RESPONSE" | jq -r '.active')"
        echo "   Username: $(echo "$INTROSPECT_RESPONSE" | jq -r '.username')"
        echo "   Client ID: $(echo "$INTROSPECT_RESPONSE" | jq -r '.client_id')"
        echo "   Scope: $(echo "$INTROSPECT_RESPONSE" | jq -r '.scope')"
    else
        echo "   Failed to introspect token"
    fi
    
else
    echo "❌ Direct grant login failed!"
    echo "Error response:"
    echo "$TOKEN_RESPONSE" | jq '.' 2>/dev/null || echo "$TOKEN_RESPONSE"
    
    # Common error analysis
    ERROR_TYPE=$(echo "$TOKEN_RESPONSE" | jq -r '.error // empty')
    ERROR_DESC=$(echo "$TOKEN_RESPONSE" | jq -r '.error_description // empty')
    
    if [ "$ERROR_TYPE" = "invalid_grant" ]; then
        echo ""
        echo "💡 Troubleshooting Tips:"
        echo "   • Check that the OFBiz SPI is properly configured"
        echo "   • Verify that the OFBiz backend is accessible"
        echo "   • Ensure the username '$USERNAME' exists in OFBiz"
        echo "   • Verify the password '$PASSWORD' is correct"
        echo "   • Check Keycloak logs for detailed error information"
    elif [ "$ERROR_TYPE" = "unauthorized_client" ]; then
        echo ""
        echo "💡 Client configuration issue:"
        echo "   • Check that direct access grants are enabled for the client"
        echo "   • Verify client secret is correct"
    fi
fi

echo ""
echo "🔗 Method 2: Authorization Code Flow (Browser-based)"
echo "---------------------------------------------------"

# Generate state and code verifier for PKCE
STATE=$(openssl rand -hex 16)
CODE_VERIFIER=$(openssl rand -base64 32 | tr -d "=+/" | cut -c1-43)
CODE_CHALLENGE=$(echo -n "$CODE_VERIFIER" | openssl dgst -binary -sha256 | openssl base64 | tr -d "=+/" | cut -c1-43)

# Construct authorization URL
AUTH_URL="$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/auth"
AUTH_URL="$AUTH_URL?client_id=$CLIENT_ID"
AUTH_URL="$AUTH_URL&redirect_uri=$(printf '%s' "$REDIRECT_URI" | jq -sRr @uri)"
AUTH_URL="$AUTH_URL&response_type=code"
AUTH_URL="$AUTH_URL&scope=openid%20email%20profile"
AUTH_URL="$AUTH_URL&state=$STATE"
AUTH_URL="$AUTH_URL&code_challenge=$CODE_CHALLENGE"
AUTH_URL="$AUTH_URL&code_challenge_method=S256"

echo "To test browser-based login:"
echo "1. Open this URL in your browser:"
echo "   $AUTH_URL"
echo ""
echo "2. Login with:"
echo "   Username: $USERNAME"
echo "   Password: $PASSWORD"
echo ""
echo "3. After successful login, you'll be redirected to:"
echo "   $REDIRECT_URI?code=<auth_code>&state=$STATE"
echo ""
echo "4. Extract the 'code' parameter and exchange it for tokens using:"
echo "   curl -X POST '$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token' \\"
echo "     -H 'Content-Type: application/x-www-form-urlencoded' \\"
echo "     -d 'grant_type=authorization_code' \\"
echo "     -d 'client_id=$CLIENT_ID' \\"
echo "     -d 'client_secret=$CLIENT_SECRET' \\"
echo "     -d 'code=<extracted_code>' \\"
echo "     -d 'redirect_uri=$REDIRECT_URI' \\"
echo "     -d 'code_verifier=$CODE_VERIFIER'"

echo ""
echo "📋 Summary"
echo "=========="
echo "Realm: $REALM_NAME"
echo "Client ID: $CLIENT_ID"
echo "Test User: $USERNAME"
echo "Keycloak URL: $KEYCLOAK_URL"

if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
    echo "✅ Authentication test: PASSED"
    echo ""
    echo "🎉 The OFBiz SPI is working correctly!"
    echo "   External applications can now authenticate users through Keycloak"
    echo "   using the OFBiz user store as the backing authentication system."
else
    echo "❌ Authentication test: FAILED"
    echo ""
    echo "🔧 Next steps for troubleshooting:"
    echo "   1. Check Keycloak logs: docker logs keycloak-dev -f"
    echo "   2. Verify OFBiz SPI configuration in Keycloak admin console"
    echo "   3. Ensure OFBiz backend is accessible from Keycloak"
    echo "   4. Verify user credentials in OFBiz database"
fi

echo ""
echo "📚 Additional Resources:"
echo "   • Keycloak Admin Console: $KEYCLOAK_URL/admin"
echo "   • OFBiz Realm: $KEYCLOAK_URL/realms/$REALM_NAME"
echo "   • OpenID Connect Endpoints: $KEYCLOAK_URL/realms/$REALM_NAME/.well-known/openid_configuration"
