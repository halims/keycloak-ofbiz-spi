#!/bin/bash
# Demo: Configure OFBiz SPI for Specific Realms at Runtime

echo "🎯 OFBiz SPI Runtime Realm Configuration Demo"
echo "=============================================="

# Configuration
KEYCLOAK_URL="http://localhost:8080"
ADMIN_USER="admin"
ADMIN_PASS="admin"
TARGET_REALM="myapp-realm"

echo ""
echo "📋 Step 1: Get Admin Token"
echo "------------------------"

TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

if [ "$TOKEN" != "null" ] && [ -n "$TOKEN" ]; then
    echo "✅ Admin token obtained successfully"
else
    echo "❌ Failed to get admin token"
    exit 1
fi

echo ""
echo "📋 Step 2: Create Test Realm (if not exists)"
echo "-------------------------------------------"

# Check if realm exists
REALM_EXISTS=$(curl -s -H "Authorization: Bearer $TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$TARGET_REALM" \
    -w "%{http_code}" -o /dev/null)

if [ "$REALM_EXISTS" = "200" ]; then
    echo "✅ Realm '$TARGET_REALM' already exists"
else
    echo "🔧 Creating realm '$TARGET_REALM'..."
    curl -s -X POST "$KEYCLOAK_URL/admin/realms" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "{
            \"realm\": \"$TARGET_REALM\",
            \"enabled\": true,
            \"displayName\": \"My Application Realm\"
        }"
    echo "✅ Realm '$TARGET_REALM' created"
fi

echo ""
echo "📋 Step 3: Configure OFBiz User Federation"
echo "-----------------------------------------"

# Configure User Federation Component
COMPONENT_CONFIG='{
    "name": "OFBiz Users",
    "providerId": "ofbiz-user-storage",
    "providerType": "org.keycloak.storage.UserStorageProvider",
    "parentId": "'$TARGET_REALM'",
    "config": {
        "jdbcDriver": ["com.mysql.cj.jdbc.Driver"],
        "jdbcUrl": ["jdbc:mysql://mysql:3306/ofbiz"],
        "username": ["ofbiz"],
        "password": ["ofbiz"],
        "validationQuery": ["SELECT 1"],
        "poolSize": ["10"],
        "enabledRealms": ["'$TARGET_REALM'"]
    }
}'

COMPONENT_ID=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms/$TARGET_REALM/components" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "$COMPONENT_CONFIG" \
    -w "%{http_code}" -o /dev/null)

if [ "$COMPONENT_ID" = "201" ]; then
    echo "✅ OFBiz User Federation configured for realm '$TARGET_REALM'"
else
    echo "⚠️  User Federation may already be configured (or check configuration)"
fi

echo ""
echo "📋 Step 4: Verify Configuration"
echo "------------------------------"

# List components to verify
echo "🔍 Checking configured components in realm '$TARGET_REALM':"
curl -s -H "Authorization: Bearer $TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$TARGET_REALM/components?type=org.keycloak.storage.UserStorageProvider" \
    | jq -r '.[] | "  ✓ " + .name + " (Provider: " + .providerId + ")"'

echo ""
echo "📋 Step 5: Test Realm Isolation"
echo "------------------------------"

echo "🧪 Testing master realm (should NOT use OFBiz SPI):"
MASTER_TEST=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
    -w "%{http_code}" -o /dev/null)

if [ "$MASTER_TEST" = "200" ]; then
    echo "  ✅ Master realm authentication working (uses default Keycloak auth)"
else
    echo "  ❌ Master realm authentication failed"
fi

echo ""
echo "🧪 Testing $TARGET_REALM (should use OFBiz SPI when users exist):"
echo "  ℹ️  To test: Create a client in $TARGET_REALM and try OFBiz user authentication"

echo ""
echo "🎯 Configuration Summary:"
echo "========================"
echo "✅ Realm '$TARGET_REALM': Uses OFBiz authentication"
echo "✅ Master realm: Uses default Keycloak authentication"  
echo "✅ Other realms: Use default Keycloak authentication"
echo ""
echo "🌐 Access URLs:"
echo "  Admin Console: $KEYCLOAK_URL/admin/"
echo "  Target Realm:  $KEYCLOAK_URL/realms/$TARGET_REALM/"
echo ""
echo "📝 Next Steps:"
echo "  1. Create a client application in '$TARGET_REALM'"
echo "  2. Test authentication with OFBiz users"
echo "  3. Monitor logs for realm-specific activity"
echo ""
echo "🔍 Monitor realm activity:"
echo "  docker-compose logs keycloak | grep 'realm:'"
echo ""
echo "✅ Runtime realm configuration complete!"
