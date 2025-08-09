#!/bin/bash

# Keycloak SPA Client Setup Script
# This script creates the required Keycloak client for the React SPA

set -e

# Use first argument as Keycloak URL, fallback to default
KEYCLOAK_URL="${1:-http://keycloak.local:8090}"
REALM="ofbiz"
CLIENT_ID="ofbiz-spa-test"
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo "🔧 Setting up Keycloak client for React SPA..."
echo "📍 Keycloak URL: $KEYCLOAK_URL"
echo "🏰 Realm: $REALM"
echo "📱 Client ID: $CLIENT_ID"
echo ""

# Set curl options for HTTPS
CURL_OPTS=""
if [[ $KEYCLOAK_URL == https* ]]; then
  CURL_OPTS="-k"  # Ignore self-signed certificate
  echo "🔒 Using HTTPS with self-signed certificate"
fi

# Get admin access token
echo "🔐 Getting admin access token..."
ADMIN_TOKEN=$(curl $CURL_OPTS -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER" \
  -d "password=$ADMIN_PASS" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" == "null" ] || [ -z "$ADMIN_TOKEN" ]; then
  echo "❌ Failed to get admin access token. Please check Keycloak is running and admin credentials are correct."
  exit 1
fi

echo "✅ Admin access token obtained"

# Check if client already exists
echo "🔍 Checking if client already exists..."
EXISTING_CLIENT=$(curl $CURL_OPTS -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json")

if [ "$(echo $EXISTING_CLIENT | jq '. | length')" -gt 0 ]; then
  echo "⚠️  Client '$CLIENT_ID' already exists. Updating configuration..."
  CLIENT_UUID=$(echo $EXISTING_CLIENT | jq -r '.[0].id')

  # Update existing client with HTTPS-compatible URLs
  curl $CURL_OPTS -s -X PUT "$KEYCLOAK_URL/admin/realms/$REALM/clients/$CLIENT_UUID" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": "'$CLIENT_ID'",
      "name": "OFBiz SPA Test Client",
      "description": "React SPA for testing Keycloak OFBiz realm authentication",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "publicClient": true,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "authorizationServicesEnabled": false,
      "protocol": "openid-connect",
      "attributes": {
        "post.logout.redirect.uris": "http://localhost:3000/*,http://spa.local:3000/*",
        "oauth2.device.authorization.grant.enabled": false,
        "oidc.ciba.grant.enabled": false,
        "pkce.code.challenge.method": "S256"
      },
      "redirectUris": [
        "http://localhost:3000/*",
        "http://spa.local:3000/*"
      ],
      "webOrigins": [
        "http://localhost:3000",
        "http://spa.local:3000"
      ],
      "defaultClientScopes": [
        "web-origins",
        "acr",
        "profile",
        "roles",
        "email"
      ],
      "optionalClientScopes": [
        "address",
        "phone",
        "offline_access",
        "microprofile-jwt"
      ]
    }'

  echo "✅ Client '$CLIENT_ID' updated successfully"
else
  echo "🆕 Creating new client '$CLIENT_ID'..."

  # Create new client with HTTPS-compatible configuration
  curl $CURL_OPTS -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/clients" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "clientId": "'$CLIENT_ID'",
      "name": "OFBiz SPA Test Client",
      "description": "React SPA for testing Keycloak OFBiz realm authentication",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "publicClient": true,
      "standardFlowEnabled": true,
      "implicitFlowEnabled": false,
      "directAccessGrantsEnabled": false,
      "serviceAccountsEnabled": false,
      "authorizationServicesEnabled": false,
      "protocol": "openid-connect",
      "attributes": {
        "post.logout.redirect.uris": "http://localhost:3000/*,http://spa.local:3000/*",
        "oauth2.device.authorization.grant.enabled": false,
        "oidc.ciba.grant.enabled": false,
        "pkce.code.challenge.method": "S256"
      },
      "redirectUris": [
        "http://localhost:3000/*",
        "http://spa.local:3000/*"
      ],
      "webOrigins": [
        "http://localhost:3000",
        "http://spa.local:3000"
      ],
      "defaultClientScopes": [
        "web-origins",
        "acr",
        "profile",
        "roles",
        "email"
      ],
      "optionalClientScopes": [
        "address",
        "phone",
        "offline_access",
        "microprofile-jwt"
      ]
    }'

  echo "✅ Client '$CLIENT_ID' created successfully"
fi

echo ""
echo "🎉 Keycloak client setup completed!"
echo ""
echo "📋 Next steps:"
echo "1. Open http://spa.local:3000"
echo "2. Test login with credentials:"
echo "   • Username: usertest | Password: password123"
echo "   • Username: admin | Password: admin123"
echo ""
echo "🔗 Useful URLs:"
echo "   • SPA Application: http://spa.local:3000"
if [[ $KEYCLOAK_URL == https* ]]; then
  ADMIN_URL="${KEYCLOAK_URL}/admin"
else
  ADMIN_URL="http://keycloak.local:8090/admin"
fi
echo "   • Keycloak Admin: $ADMIN_URL"
echo "   • OFBiz: http://ofbiz.local:8080"
echo ""
