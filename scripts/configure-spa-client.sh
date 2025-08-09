#!/bin/bash

# Configure Keycloak client for proper PKCE enforcement

set -e

echo "🔧 Configuring ofbiz-spa-test client for PKCE..."

# Get admin token
ADMIN_TOKEN=$(curl -k -s -X POST "https://keycloak.local:8444/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" | jq -r '.access_token')

# Get client UUID
CLIENT_UUID=$(curl -k -s -X GET "https://keycloak.local:8444/admin/realms/ofbiz/clients?clientId=ofbiz-spa-test" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].id')

echo "📝 Updating client configuration..."

# Update client with proper PKCE settings
curl -k -s -X PUT "https://keycloak.local:8444/admin/realms/ofbiz/clients/$CLIENT_UUID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "publicClient": true,
    "standardFlowEnabled": true,
    "implicitFlowEnabled": false,
    "directAccessGrantsEnabled": false,
    "redirectUris": [
      "https://spa.local:3443/*",
      "http://spa.local:3000/*",
      "http://localhost:3000/*"
    ],
    "webOrigins": [
      "https://spa.local:3443",
      "http://spa.local:3000",
      "http://localhost:3000"
    ],
    "attributes": {
      "pkce.code.challenge.method": "S256",
      "post.logout.redirect.uris": "https://spa.local:3443/*,http://spa.local:3000/*,http://localhost:3000/*"
    }
  }'

echo "✅ Client configuration updated"

# Verify
PKCE_METHOD=$(curl -k -s -X GET "https://keycloak.local:8444/admin/realms/ofbiz/clients?clientId=ofbiz-spa-test" \
  -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].attributes."pkce.code.challenge.method"')

echo "🔍 PKCE method: $PKCE_METHOD"
