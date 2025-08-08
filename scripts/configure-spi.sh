#!/bin/bash
# Configure OFBiz SPI for testing with REST mode
# This script configures the OFBiz SPI in the ofbiz realm

echo "🔧 Configuring OFBiz SPI for Testing"
echo "===================================="

KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo ""
echo "📋 Step 1: Get Admin Token"
echo "--------------------------"

TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

if [ "$TOKEN" = "null" ] || [ -z "$TOKEN" ]; then
    echo "❌ Failed to get admin token"
    exit 1
fi

echo "✅ Admin token obtained"

echo ""
echo "📋 Step 2: Check Existing OFBiz SPI Configuration"
echo "------------------------------------------------"

# Get existing components
EXISTING_COMPONENTS=$(curl -s -H "Authorization: Bearer $TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components?type=org.keycloak.storage.UserStorageProvider")

OFBIZ_COMPONENT_ID=$(echo "$EXISTING_COMPONENTS" | jq -r '.[] | select(.providerId == "ofbiz-user-storage") | .id // empty')

if [ -n "$OFBIZ_COMPONENT_ID" ]; then
    echo "✅ Found existing OFBiz SPI component: $OFBIZ_COMPONENT_ID"
    
    # Get current configuration
    CURRENT_CONFIG=$(curl -s -H "Authorization: Bearer $TOKEN" \
        "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components/$OFBIZ_COMPONENT_ID")
    
    echo "Current configuration:"
    echo "$CURRENT_CONFIG" | jq '.config'
    
    echo ""
    echo "📋 Step 3: Update OFBiz SPI Configuration"
    echo "----------------------------------------"
    
    # Update the configuration with REST-only settings (including service account)
    UPDATE_CONFIG='{
        "config": {
            "ofbizBaseUrl": ["http://host.docker.internal:8080"],
            "ofbizAuthEndpoint": ["/rest/auth/token"],
            "ofbizUserEndpoint": ["/rest/services/getUserInfo"],
            "ofbizTimeout": ["5000"],
            "enabledRealms": ["ofbiz"],
            "tenantAttribute": ["tenant"],
            "serviceAccountUsername": ["admin"],
            "serviceAccountPassword": ["ofbiz"]
        }
    }'
    
    UPDATE_RESULT=$(curl -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components/$OFBIZ_COMPONENT_ID" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$UPDATE_CONFIG" \
        -w "%{http_code}" -o /dev/null)
    
    if [ "$UPDATE_RESULT" = "204" ]; then
        echo "✅ OFBiz SPI configuration updated"
    else
        echo "❌ Failed to update configuration (HTTP $UPDATE_RESULT)"
    fi
    
else
    echo "ℹ️  No existing OFBiz SPI component found"
    
    echo ""
    echo "📋 Step 3: Create OFBiz SPI Configuration"
    echo "----------------------------------------"
    
    # Create new component configuration for REST-only mode (with service account)
    COMPONENT_CONFIG='{
        "name": "OFBiz Users",
        "providerId": "ofbiz-user-storage",
        "providerType": "org.keycloak.storage.UserStorageProvider",
        "parentId": "'$REALM_NAME'",
        "config": {
            "ofbizBaseUrl": ["http://host.docker.internal:8080"],
            "ofbizAuthEndpoint": ["/rest/auth/token"],
            "ofbizUserEndpoint": ["/rest/services/getUserInfo"],
            "ofbizTimeout": ["5000"],
            "enabledRealms": ["ofbiz"],
            "tenantAttribute": ["tenant"],
            "serviceAccountUsername": ["admin"],
            "serviceAccountPassword": ["ofbiz"]
        }
    }'
    
    CREATE_RESULT=$(curl -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components" \
        -H "Authorization: Bearer $TOKEN" \
        -H "Content-Type: application/json" \
        -d "$COMPONENT_CONFIG" \
        -w "%{http_code}" -o /dev/null)
    
    if [ "$CREATE_RESULT" = "201" ]; then
        echo "✅ OFBiz SPI component created"
    else
        echo "❌ Failed to create component (HTTP $CREATE_RESULT)"
    fi
fi

echo ""
echo "📋 Step 4: Test Configuration"
echo "-----------------------------"

echo "Testing basic connectivity to OFBiz..."
OFBIZ_TEST=$(curl -s -o /dev/null -w "%{http_code}" "http://host.docker.internal:8080/webtools/control/main")

if [ "$OFBIZ_TEST" = "200" ] || [ "$OFBIZ_TEST" = "302" ]; then
    echo "✅ OFBiz instance is accessible"
else
    echo "⚠️  OFBiz instance not accessible (HTTP $OFBIZ_TEST)"
    echo "   The SPI is configured but won't work without a running OFBiz instance"
    echo "   For testing purposes, you might want to:"
    echo "   1. Start a local OFBiz instance on port 8080"
    echo "   2. Or modify the test to use mock data"
fi

echo ""
echo "📋 Summary"
echo "=========="
echo "✅ OFBiz SPI is configured in the '$REALM_NAME' realm"
echo "✅ Integration mode: REST-only (simplified architecture)"
echo "✅ OFBiz URL: http://host.docker.internal:8080"
echo ""
echo "Next steps:"
echo "1. Ensure OFBiz is running on port 8080"
echo "2. Test authentication with: ./scripts/quick-test.sh admin ofbiz"
echo "3. Check admin console: $KEYCLOAK_URL/admin/realms/$REALM_NAME/user-federation"
