#!/bin/bash
# Quick User Creation Test
# Usage: ./test-user-quick.sh [username] [password]

set -e

USERNAME="${1:-newuser$(date +%s)@example.com}"
PASSWORD="${2:-password123}"
KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
CLIENT_ID="admin-cli"

echo "🔨 Quick User Creation Test"
echo "=========================="
echo "Testing: $USERNAME"
echo ""

# Test authentication (which should trigger user creation if enabled)
echo "🔑 Attempting authentication..."

TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=$CLIENT_ID" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "scope=openid profile email")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)

if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
  echo "✅ Authentication successful!"
  
  # Get user info
  USER_INFO=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/userinfo")
  
  echo ""
  echo "👤 User Information:"
  echo "$USER_INFO" | jq '.'
  
  # Check if user was created by Keycloak
  CREATED_BY_KC=$(echo "$USER_INFO" | jq -r '.createdByKeycloak // "false"')
  if [ "$CREATED_BY_KC" = "true" ]; then
    echo ""
    echo "🎉 SUCCESS: User was automatically created by Keycloak SPI!"
  else
    echo ""
    echo "ℹ️  User already existed in OFBiz"
  fi

else
  echo "❌ Authentication failed!"
  echo ""
  echo "Response:"
  echo "$TOKEN_RESPONSE" | jq '.' 2>/dev/null || echo "$TOKEN_RESPONSE"
  
  ERROR=$(echo "$TOKEN_RESPONSE" | jq -r '.error // empty' 2>/dev/null)
  if [ "$ERROR" = "invalid_grant" ]; then
    echo ""
    echo "💡 This usually means:"
    echo "   • User creation is disabled in SPI config, OR"
    echo "   • OFBiz backend is not accessible, OR"  
    echo "   • Configuration error in the SPI"
  fi
fi
