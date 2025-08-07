#!/bin/bash
# Test OFBiz REST API endpoints directly
# This script tests the OFBiz REST API that the SPI will use

set -e

OFBIZ_URL="http://ofbiz.local:8080"
OFBIZ_REST_URL="${OFBIZ_URL}/rest"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo_success() { echo -e "${GREEN}✅ $1${NC}"; }
echo_error() { echo -e "${RED}❌ $1${NC}"; }
echo_info() { echo -e "${BLUE}ℹ️  $1${NC}"; }
echo_warning() { echo -e "${YELLOW}⚠️  $1${NC}"; }

echo "🧪 OFBiz REST API Direct Test"
echo "=============================="
echo "Testing OFBiz REST API at: $OFBIZ_URL"
echo ""

# Test 1: Basic connectivity
echo "📡 Test 1: Basic Connectivity"
echo "------------------------------"
BASIC_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null "$OFBIZ_URL" 2>/dev/null || echo "000")
if [ "$BASIC_RESPONSE" -eq 200 ] || [ "$BASIC_RESPONSE" -eq 302 ] || [ "$BASIC_RESPONSE" -eq 404 ]; then
    echo_success "OFBiz is accessible (HTTP $BASIC_RESPONSE)"
    if [ "$BASIC_RESPONSE" -eq 404 ]; then
        echo_info "   HTTP 404 is expected for root path - OFBiz is running"
    fi
else
    echo_error "OFBiz not accessible (HTTP $BASIC_RESPONSE)"
    echo "   Check: ping ofbiz.local"
    echo "   Check: curl -I $OFBIZ_URL"
    exit 1
fi

# Test 2: REST API base
echo ""
echo "🔌 Test 2: REST API Base"
echo "-------------------------"
REST_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null "$OFBIZ_REST_URL" 2>/dev/null || echo "000")
echo_info "REST API response: HTTP $REST_RESPONSE"

# Test 3: Authentication endpoint
echo ""
echo "🔐 Test 3: Authentication"
echo "--------------------------"
echo_info "Testing authentication with admin/ofbiz..."

AUTH_RESPONSE=$(curl -s -X POST "$OFBIZ_REST_URL/auth/token" \
    -H "Authorization: Basic YWRtaW46b2ZiaXo=" \
    -w "\nHTTP_STATUS:%{http_code}" 2>/dev/null)

HTTP_STATUS=$(echo "$AUTH_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
RESPONSE_BODY=$(echo "$AUTH_RESPONSE" | sed '/HTTP_STATUS:/d')

echo "   HTTP Status: $HTTP_STATUS"
echo "   Response:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"

if [ "$HTTP_STATUS" = "200" ]; then
    echo_success "Authentication successful"
    ACCESS_TOKEN=$(echo "$RESPONSE_BODY" | jq -r '.data.access_token // empty' 2>/dev/null)
    if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ] && [ "$ACCESS_TOKEN" != "empty" ]; then
        echo "   Access Token: ${ACCESS_TOKEN:0:50}..." # Show first 50 chars for verification
        echo "   Token Length: ${#ACCESS_TOKEN}"
    else
        echo_warning "Token extraction failed"
        echo "   Raw response: $RESPONSE_BODY"
        ACCESS_TOKEN=""
    fi
else
    echo_warning "Authentication failed or endpoint not available"
    ACCESS_TOKEN=""
fi

# Test 4: User creation endpoint
echo ""
echo "🔨 Test 4: User Creation Endpoint"
echo "----------------------------------"

if [ -z "$ACCESS_TOKEN" ]; then
    echo_error "No access token available - skipping user creation test"
    USER_CREATED=false
else
    TEST_USERNAME="testuser$(date +%s)"
    echo_info "Testing user creation for: $TEST_USERNAME"
    echo_info "Using token: ${ACCESS_TOKEN:0:20}..."

    CREATE_USER_DATA='{
        "userLoginId": "'$TEST_USERNAME'",
        "currentPassword": "testpassword123",
        "currentPasswordVerify": "testpassword123",
        "firstName": "Test",
        "lastName": "User",
        "emailAddress": "'$TEST_USERNAME'@example.com"
    }'

    echo "   Request data:"
    echo "$CREATE_USER_DATA" | jq '.'

    CREATE_RESPONSE=$(curl -s -X POST "$OFBIZ_REST_URL/services/createUserLogin" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "$CREATE_USER_DATA" \
        -w "\nHTTP_STATUS:%{http_code}" 2>/dev/null)

    HTTP_STATUS=$(echo "$CREATE_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
    RESPONSE_BODY=$(echo "$CREATE_RESPONSE" | sed '/HTTP_STATUS:/d')

    echo "   HTTP Status: $HTTP_STATUS"
    echo "   Response:"
    echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"

    if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
        echo_success "User creation endpoint is working"
        USER_CREATED=true
    else
        echo_error "User creation failed (HTTP $HTTP_STATUS)"
        USER_CREATED=false
    fi
fi

# Test 5: Get user info endpoint
echo ""
echo "🔍 Test 5: Get User Info Endpoint"
echo "----------------------------------"
if [ "$USER_CREATED" = true ]; then
    echo_info "Testing getUserInfo for: $TEST_USERNAME"
    
    GET_USER_DATA='{"userLoginId": "'$TEST_USERNAME'"}'
    
    USER_INFO_RESPONSE=$(curl -s -X POST "$OFBIZ_REST_URL/services/getUserInfo" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $ACCESS_TOKEN" \
        -d "$GET_USER_DATA" \
        -w "\nHTTP_STATUS:%{http_code}" 2>/dev/null)
    
    HTTP_STATUS=$(echo "$USER_INFO_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
    RESPONSE_BODY=$(echo "$USER_INFO_RESPONSE" | sed '/HTTP_STATUS:/d')
    
    echo "   HTTP Status: $HTTP_STATUS"
    echo "   Response:"
    echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"
    
    if [ "$HTTP_STATUS" = "200" ]; then
        echo_success "Get user info endpoint is working"
    else
        echo_warning "Get user info failed (HTTP $HTTP_STATUS)"
    fi
else
    echo_warning "Skipping getUserInfo test - user creation failed"
fi

# Test 6: Tenant info endpoint
echo ""
echo "🏢 Test 6: Tenant Info Endpoint"
echo "--------------------------------"
echo_info "Testing getTenantInfo service"

GET_TENANT_DATA='{
    "tenantId": "default"
}'

echo "   Request data:"
echo "$GET_TENANT_DATA" | jq '.'

TENANT_RESPONSE=$(curl -s -X POST "$OFBIZ_REST_URL/services/getTenantInfo" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer $ACCESS_TOKEN" \
    -d "$GET_TENANT_DATA" \
    -w "\nHTTP_STATUS:%{http_code}" 2>/dev/null)

HTTP_STATUS=$(echo "$TENANT_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
RESPONSE_BODY=$(echo "$TENANT_RESPONSE" | sed '/HTTP_STATUS:/d')

echo "   HTTP Status: $HTTP_STATUS"
echo "   Response:"
echo "$RESPONSE_BODY" | jq '.' 2>/dev/null || echo "$RESPONSE_BODY"

if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
    echo_success "Tenant info endpoint is working"
else
    echo_warning "Tenant info failed (HTTP $HTTP_STATUS)"
fi

# Summary
echo ""
echo "📊 Test Summary"
echo "==============="
echo "OFBiz REST API Available Services:"
echo "   • Authentication: $OFBIZ_REST_URL/auth/token"
echo "   • Get User Info: $OFBIZ_REST_URL/services/getUserInfo"
echo "   • Create User Login: $OFBIZ_REST_URL/services/createUserLogin"
echo "   • Get User with Tenant: $OFBIZ_REST_URL/services/getUserWithTenant"
echo "   • Get Tenant Info: $OFBIZ_REST_URL/services/getTenantInfo"
echo "   • Validate Credentials: $OFBIZ_REST_URL/services/validateUserCredentials"
echo ""
echo "🔗 OFBiz URLs:"
echo "   • Main: $OFBIZ_URL"
echo "   • WebTools: $OFBIZ_URL/webtools"
echo "   • User Management: $OFBIZ_URL/webtools/control/userManagement"

if [ "$USER_CREATED" = true ]; then
    echo ""
    echo_success "✅ OFBiz REST API is ready for user creation!"
    echo_info "The Keycloak SPI should be able to create users via these endpoints."
else
    echo ""
    echo_warning "⚠️  OFBiz REST API may need configuration for user creation."
    echo_info "Check OFBiz logs and ensure the createUser service is available."
fi

echo ""
echo "💡 Next steps:"
echo "   1. Configure Keycloak SPI: ./scripts/configure-user-creation.sh"
echo "   2. Test SPI integration: ./scripts/test-user-creation-complete.sh"
echo "   3. Monitor OFBiz logs during testing"
