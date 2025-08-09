#!/bin/bash

# Complete Keycloak Setup Script for HTTPS environment
# This script creates the realm, sets up users, and configures the SPA client

set -e

# Use first argument as Keycloak URL, fallback to default
KEYCLOAK_URL="${1:-https://keycloak.local:8444}"
REALM="ofbiz"
CLIENT_ID="ofbiz-spa-test"
ADMIN_USER="admin"
ADMIN_PASS="admin"

echo "🔧 Setting up complete Keycloak environment..."
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

# Check if realm exists
echo "🔍 Checking if realm '$REALM' exists..."
REALM_CHECK=$(curl $CURL_OPTS -s -o /dev/null -w "%{http_code}" -X GET "$KEYCLOAK_URL/admin/realms/$REALM" \
  -H "Authorization: Bearer $ADMIN_TOKEN")

if [ "$REALM_CHECK" == "404" ]; then
  echo "🆕 Creating realm '$REALM'..."
  
  curl $CURL_OPTS -s -X POST "$KEYCLOAK_URL/admin/realms" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "realm": "'$REALM'",
      "displayName": "OFBiz Realm",
      "enabled": true,
      "registrationAllowed": false,
      "loginWithEmailAllowed": true,
      "duplicateEmailsAllowed": false,
      "resetPasswordAllowed": true,
      "editUsernameAllowed": false,
      "bruteForceProtected": true,
      "internationalizationEnabled": false,
      "supportedLocales": ["en"],
      "defaultLocale": "en"
    }'
  
  echo "✅ Realm '$REALM' created successfully"
  
  # Wait a moment for realm to be ready
  sleep 2
  
  echo "👤 Creating test users..."
  
  # Create usertest
  curl $CURL_OPTS -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "username": "usertest",
      "enabled": true,
      "emailVerified": true,
      "firstName": "Test",
      "lastName": "User",
      "email": "usertest@example.com",
      "credentials": [{
        "type": "password",
        "value": "password123",
        "temporary": false
      }]
    }'
  
  # Create admin user
  curl $CURL_OPTS -s -X POST "$KEYCLOAK_URL/admin/realms/$REALM/users" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{
      "username": "admin",
      "enabled": true,
      "emailVerified": true,
      "firstName": "Admin",
      "lastName": "User",
      "email": "admin@example.com",
      "credentials": [{
        "type": "password",
        "value": "admin123",
        "temporary": false
      }]
    }'
  
  echo "✅ Test users created"
else
  echo "✅ Realm '$REALM' already exists"
fi

# Create/update SPA client
echo "🔍 Checking if client '$CLIENT_ID' exists..."
EXISTING_CLIENT=$(curl $CURL_OPTS -s -X GET "$KEYCLOAK_URL/admin/realms/$REALM/clients?clientId=$CLIENT_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -H "Content-Type: application/json")

CLIENT_COUNT=$(echo $EXISTING_CLIENT | jq '. | length')

if [ "$CLIENT_COUNT" -gt 0 ]; then
  echo "⚠️  Client '$CLIENT_ID' already exists. Updating configuration..."
  CLIENT_UUID=$(echo $EXISTING_CLIENT | jq -r '.[0].id')

  # Update existing client
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
        "post.logout.redirect.uris": "http://localhost:3000/*,http://spa.local:3000/*,https://spa.local:3443/*",
        "oauth2.device.authorization.grant.enabled": false,
        "oidc.ciba.grant.enabled": false,
        "pkce.code.challenge.method": "S256"
      },
      "redirectUris": [
        "http://localhost:3000/*",
        "http://spa.local:3000/*",
        "https://spa.local:3443/*"
      ],
      "webOrigins": [
        "http://localhost:3000",
        "http://spa.local:3000",
        "https://spa.local:3443"
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

  # Create new client
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
        "post.logout.redirect.uris": "http://localhost:3000/*,http://spa.local:3000/*,https://spa.local:3443/*",
        "oauth2.device.authorization.grant.enabled": false,
        "oidc.ciba.grant.enabled": false,
        "pkce.code.challenge.method": "S256"
      },
      "redirectUris": [
        "http://localhost:3000/*",
        "http://spa.local:3000/*",
        "https://spa.local:3443/*"
      ],
      "webOrigins": [
        "http://localhost:3000",
        "http://spa.local:3000",
        "https://spa.local:3443"
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
echo "🎉 Complete Keycloak setup completed!"
echo ""
echo "📋 Next steps:"
echo "1. Open https://spa.local:3443 (HTTPS)"
echo "2. Test login with credentials:"
echo "   • Username: usertest | Password: password123"
echo "   • Username: admin | Password: admin123"
echo ""
echo "🔗 Useful URLs:"
echo "   • SPA Application (HTTPS): https://spa.local:3443"
echo "   • SPA Application (HTTP): http://spa.local:3000"
if [[ $KEYCLOAK_URL == https* ]]; then
  ADMIN_URL="${KEYCLOAK_URL}/admin"
else
  ADMIN_URL="http://keycloak.local:8090/admin"
fi
echo "   • Keycloak Admin: $ADMIN_URL"
echo "   • OFBiz: http://ofbiz.local:8080"
echo ""
echo "⚠️  For HTTPS Keycloak, you'll need to accept the self-signed certificate in your browser"
echo "    when first accessing the Keycloak admin console."
echo ""
