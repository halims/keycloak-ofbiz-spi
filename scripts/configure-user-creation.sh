#!/bin/bash
# Configure OFBiz SPI for User Creation Testing
# This script enables user creation features in the existing SPI configuration

set -e

KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo "🔧 OFBiz SPI User Creation Configuration"
echo "======================================="

# Get admin token
echo "🔑 Getting admin token..."
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo "❌ Failed to get admin token"
    exit 1
fi

echo "✅ Admin token obtained"

# Find OFBiz SPI component
echo "🔍 Finding OFBiz SPI component..."
COMPONENTS=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components?type=org.keycloak.storage.UserStorageProvider")

SPI_ID=$(echo "$COMPONENTS" | jq -r '.[] | select(.providerId=="ofbiz-user-storage") | .id' | head -1)

if [ -z "$SPI_ID" ] || [ "$SPI_ID" = "null" ]; then
    echo "❌ OFBiz SPI not found in realm '$REALM_NAME'"
    echo "   Please configure the SPI first using: ./configure-spi.sh"
    exit 1
fi

echo "✅ Found OFBiz SPI component: $SPI_ID"

# Get current configuration
echo "📋 Getting current SPI configuration..."
CURRENT_CONFIG=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components/$SPI_ID")

# Extract current settings and update with user creation config
UPDATED_CONFIG=$(echo "$CURRENT_CONFIG" | jq '
.config += {
  "ofbizEnableUserCreation": ["true"],
  "ofbizCreateUserEndpoint": ["/rest/services/createUser"],
  "ofbizDefaultUserPassword": ["defaultPassword123"],
  "ofbizEnableTenantCreation": ["true"],
  "ofbizCreateTenantEndpoint": ["/rest/services/createPartyGroup"],
  "ofbizDefaultTenantName": ["Default Organization"],
  "ofbizEnableDetailedLogging": ["true"],
  "ofbizLogMissingProfileFields": ["true"],
  "ofbizBaseUrl": ["http://ofbiz.local:8080"]
}')

echo "🔧 Updating SPI configuration with user creation settings..."

# Update the component configuration
UPDATE_RESPONSE=$(curl -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components/$SPI_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$UPDATED_CONFIG" \
    -w "%{http_code}" -o /dev/null)

if [ "$UPDATE_RESPONSE" = "204" ]; then
    echo "✅ SPI configuration updated successfully!"
    
    echo ""
    echo "📊 User Creation Settings Configured:"
    echo "   ✅ Enable User Creation: true"
    echo "   ✅ Create User Endpoint: /rest/services/createUser"
    echo "   ✅ Default User Password: defaultPassword123"
    echo "   ✅ Enable Tenant Creation: true" 
    echo "   ✅ Create Tenant Endpoint: /rest/services/createPartyGroup"
    echo "   ✅ Default Tenant Name: Default Organization"
    echo "   ✅ Enable Detailed Logging: true"
    echo "   ✅ Log Missing Profile Fields: true"
    echo "   ✅ OFBiz Base URL: http://ofbiz.local:8080"
    
    echo ""
    echo "🎉 Configuration complete! You can now test user creation:"
    echo "   ./scripts/test-user-creation.sh"
    echo "   ./scripts/test-user-quick.sh newuser@example.com password123"
    
else
    echo "❌ Failed to update SPI configuration (HTTP $UPDATE_RESPONSE)"
    exit 1
fi

echo ""
echo "🔗 Admin Console: $KEYCLOAK_URL/admin"
echo "   Navigate to: $REALM_NAME → User Federation → OFBiz SPI"
