#!/bin/bash

# Fix PKCE Configuration for SPA Client
# This script ensures the Keycloak client is properly configured for PKCE S256

set -e

echo "🔧 Fixing PKCE configuration for ofbiz-spa-test client..."

# Get admin token
echo "🔐 Getting admin token..."
ADMIN_TOKEN=$(curl -k -s -X POST "https://keycloak.local:8444/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ Failed to get admin token"
  exit 1
fi

# Get client UUID
echo "🔍 Finding client UUID..."
CLIENT_UUID=$(curl -k -s -X GET "https://keycloak.local:8444/admin/realms/ofbiz/clients?clientId=ofbiz-spa-test" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

if [ "$CLIENT_UUID" == "null" ] || [ -z "$CLIENT_UUID" ]; then
  echo "❌ Failed to find client ofbiz-spa-test"
  exit 1
fi

echo "📝 Client UUID: $CLIENT_UUID"

# Update PKCE configuration
echo "🔧 Setting PKCE method to S256..."
curl -k -s -X PUT "https://keycloak.local:8444/admin/realms/ofbiz/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "pkce.code.challenge.method": "S256"
    }
  }'

# Verify the change
echo "✅ Verifying PKCE configuration..."
PKCE_METHOD=$(curl -k -s -X GET "https://keycloak.local:8444/admin/realms/ofbiz/clients?clientId=ofbiz-spa-test" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].attributes."pkce.code.challenge.method"')

if [ "$PKCE_METHOD" == "S256" ]; then
  echo "✅ PKCE method successfully set to: $PKCE_METHOD"
  echo "🎉 SPA authentication should now work with PKCE!"
else
  echo "❌ PKCE method not set correctly. Current value: $PKCE_METHOD"
  exit 1
fi

echo ""
echo "🧪 Next steps:"
echo "1. Open https://spa.local:3443"
echo "2. Click 'Login' button"
echo "3. Enter OFBiz credentials"
echo "4. Should redirect back and complete authentication"
