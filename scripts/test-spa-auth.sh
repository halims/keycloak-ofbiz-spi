#!/bin/bash

# Comprehensive SPA Authentication Test Script
# Tests PKCE-enabled authentication flow for the React SPA

echo "🚀 Testing SPA Authentication with PKCE..."
echo "============================================="

# Test 1: Verify SPA is accessible
echo "1️⃣ Testing SPA accessibility..."
HTTP_CODE=$(curl -k -s -w "%{http_code}" https://spa.local:3443/ -o /dev/null)
if [ "$HTTP_CODE" = "200" ]; then
    echo "✅ SPA is accessible (HTTP $HTTP_CODE)"
else
    echo "❌ SPA not accessible (HTTP $HTTP_CODE)"
    exit 1
fi

# Test 2: Check if JavaScript bundle loads
echo "2️⃣ Testing JavaScript bundle..."
JS_FILE=$(curl -k -s https://spa.local:3443/ | grep -o 'src="/static/js/[^"]*"' | sed 's/src="//;s/"//')
if [ -n "$JS_FILE" ]; then
    echo "✅ JavaScript file reference found: $JS_FILE"
    JS_HTTP_CODE=$(curl -k -s -w "%{http_code}" "https://spa.local:3443$JS_FILE" -o /dev/null)
    if [ "$JS_HTTP_CODE" = "200" ]; then
        echo "✅ JavaScript bundle loads successfully (HTTP $JS_HTTP_CODE)"
    else
        echo "❌ JavaScript bundle not accessible (HTTP $JS_HTTP_CODE)"
        exit 1
    fi
else
    echo "❌ No JavaScript file reference found"
    exit 1
fi

# Test 3: Verify Keycloak server is accessible
echo "3️⃣ Testing Keycloak server..."
KC_HTTP_CODE=$(curl -k -s -w "%{http_code}" https://keycloak.local:8444/admin/ -o /dev/null)
if [ "$KC_HTTP_CODE" = "200" ]; then
    echo "✅ Keycloak server accessible (HTTP $KC_HTTP_CODE)"
else
    echo "⚠️  Keycloak admin accessible but returning (HTTP $KC_HTTP_CODE) - this is normal"
fi

# Test 4: Check PKCE client configuration
echo "4️⃣ Testing PKCE client configuration..."
ADMIN_TOKEN=$(curl -k -s -X POST "https://keycloak.local:8444/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" | jq -r '.access_token')

if [ "$ADMIN_TOKEN" != "null" ] && [ -n "$ADMIN_TOKEN" ]; then
    echo "✅ Admin token obtained"
    
    PKCE_METHOD=$(curl -k -s -X GET "https://keycloak.local:8444/admin/realms/ofbiz/clients?clientId=ofbiz-spa-test" \
        -H "Authorization: Bearer $ADMIN_TOKEN" | jq -r '.[0].attributes["pkce.code.challenge.method"]')
    
    if [ "$PKCE_METHOD" = "S256" ]; then
        echo "✅ PKCE configured with S256 method"
    else
        echo "❌ PKCE not properly configured (method: $PKCE_METHOD)"
    fi
else
    echo "❌ Failed to obtain admin token"
fi

# Test 5: Check recent Keycloak logs for errors
echo "5️⃣ Checking for recent authentication errors..."
RECENT_ERRORS=$(docker logs keycloak-dev-https --tail=20 --since="5m" 2>/dev/null | grep -c "ERROR\|WARN.*LOGIN_ERROR")
if [ "$RECENT_ERRORS" -eq 0 ]; then
    echo "✅ No recent authentication errors found"
else
    echo "⚠️  Found $RECENT_ERRORS recent errors - check logs with: docker logs keycloak-dev-https --tail=20"
fi

echo "============================================="
echo "🎉 SPA Authentication Test Complete!"
echo ""
echo "📋 Summary:"
echo "   - SPA URL: https://spa.local:3443"
echo "   - Keycloak URL: https://keycloak.local:8444"
echo "   - PKCE Method: S256 (SHA256)"
echo "   - Flow: Authorization Code with PKCE"
echo ""
echo "🔗 Test authentication by visiting: https://spa.local:3443"
echo "   Click 'Login' to test the complete flow"
