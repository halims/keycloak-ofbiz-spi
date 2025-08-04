#!/bin/bash
# Quick OFBiz Login Test - Simple version
# Usage: ./quick-test.sh [username] [password]

USERNAME="${1:-admin}"
PASSWORD="${2:-ofbiz}"
KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
CLIENT_ID="admin-cli"

echo "🔐 Quick OFBiz Login Test"
echo "========================"
echo "Username: $USERNAME"
echo "Password: [hidden]"
echo "Realm: $REALM_NAME"
echo ""

# Check if Keycloak is running
if ! curl -s -f "$KEYCLOAK_URL/realms/master" >/dev/null; then
  echo "❌ Keycloak is not running at $KEYCLOAK_URL"
  echo "   Start with: docker-compose up -d"
  exit 1
fi

echo "✅ Keycloak is running"

# First, try to get a token using client credentials to test basic connectivity
echo "🔑 Testing authentication..."

# Try direct access grant (username/password flow)
TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password" \
  -d "client_id=admin-cli" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "scope=openid")

ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)

if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
  echo "✅ Login successful!"

  # Get user info
  echo ""
  echo "👤 User Information:"
  USER_INFO=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
    "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/userinfo" 2>/dev/null)

  if echo "$USER_INFO" | jq -e . >/dev/null 2>&1; then
    echo "   Username: $(echo "$USER_INFO" | jq -r '.preferred_username // .sub')"
    echo "   Email: $(echo "$USER_INFO" | jq -r '.email // "N/A"')"
    echo "   Name: $(echo "$USER_INFO" | jq -r '.name // "N/A"')"
    echo "   Tenant: $(echo "$USER_INFO" | jq -r '.tenant // "N/A"')"
  else
    echo "   (Unable to retrieve detailed user info)"
  fi

  echo ""
  echo "🎉 OFBiz SPI authentication working!"

else
  echo "❌ Login failed!"

  # Show error details if available
  ERROR=$(echo "$TOKEN_RESPONSE" | jq -r '.error // empty' 2>/dev/null)
  ERROR_DESC=$(echo "$TOKEN_RESPONSE" | jq -r '.error_description // empty' 2>/dev/null)

  if [ -n "$ERROR" ]; then
    echo "   Error: $ERROR"
    [ -n "$ERROR_DESC" ] && echo "   Description: $ERROR_DESC"
  else
    echo "   Raw response: $TOKEN_RESPONSE"
  fi

  echo ""
  echo "💡 Troubleshooting:"
  echo "   • Ensure OFBiz SPI is configured in Keycloak"
  echo "   • Check that the '$REALM_NAME' realm exists"
  echo "   • Verify user '$USERNAME' exists in OFBiz"
  echo "   • Check Keycloak logs: docker logs keycloak-dev -f"
fi

echo ""
echo "🔗 Useful URLs:"
echo "   • Admin Console: $KEYCLOAK_URL/admin"
echo "   • Realm: $KEYCLOAK_URL/realms/$REALM_NAME"
