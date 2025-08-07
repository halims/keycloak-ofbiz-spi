#!/bin/bash
# Complete User Creation Test with Real OFBiz
# This script tests user creation end-to-end with real OFBiz service

set -e

echo "🧪 Complete User Creation Test Suite"
echo "====================================="

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
KEYCLOAK_URL="http://localhost:8090"
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

# Check if OFBiz is running
echo "🔍 Checking OFBiz connectivity..."
if ! curl -s -f "$OFBIZ_REST_URL" >/dev/null 2>&1; then
    echo_error "OFBiz is not accessible at $OFBIZ_URL"
    echo "   Please ensure OFBiz is running and accessible"
    echo "   Try: ping ofbiz.local"
    echo "   Try: curl -I $OFBIZ_URL"
    exit 1
fi

echo_success "OFBiz is accessible at $OFBIZ_URL"

# Test OFBiz REST API directly
echo_info "Testing OFBiz REST API endpoints..."

# Test basic connectivity
echo "📡 Testing basic REST connectivity..."
REST_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null "$OFBIZ_REST_URL" 2>/dev/null || echo "000")
if [ "$REST_RESPONSE" -eq 200 ] || [ "$REST_RESPONSE" -eq 404 ]; then
    echo_success "OFBiz REST API is responding (HTTP $REST_RESPONSE)"
else
    echo_error "OFBiz REST API not responding properly (HTTP $REST_RESPONSE)"
fi

# Test authentication endpoint
echo "🔐 Testing authentication endpoint..."
AUTH_RESPONSE=$(curl -s -w "%{http_code}" -o /dev/null -X POST "$OFBIZ_REST_URL/auth/token" \
    -H "Authorization: Basic YWRtaW46b2ZiaXo=" 2>/dev/null || echo "000")
if [ "$AUTH_RESPONSE" -eq 200 ] || [ "$AUTH_RESPONSE" -eq 401 ]; then
    echo_success "Authentication endpoint is available (HTTP $AUTH_RESPONSE)"
else
    echo_warning "Authentication endpoint may not be available (HTTP $AUTH_RESPONSE)"
fi

echo ""
echo "🔧 Step 1: Configure SPI for User Creation"
echo "==========================================="

# Run SPI configuration script
if [ -f "$SCRIPT_DIR/configure-user-creation.sh" ]; then
    echo_info "Configuring OFBiz SPI for user creation..."
    bash "$SCRIPT_DIR/configure-user-creation.sh"
else
    echo_warning "SPI configuration script not found, manual configuration may be needed"
fi

echo ""
echo "🧪 Step 2: Monitor OFBiz REST API Calls"
echo "========================================"

echo_info "Setting up monitoring of OFBiz REST API calls..."
echo "We will monitor the following endpoints:"
echo "   • $OFBIZ_REST_URL/auth/token (authentication)"
echo "   • $OFBIZ_REST_URL/services/getUserInfo (user lookup)"
echo "   • $OFBIZ_REST_URL/services/createUser (user creation)"
echo "   • $OFBIZ_REST_URL/services/createPartyGroup (tenant creation)"

# Create a log file for monitoring
LOG_FILE="/tmp/ofbiz-api-monitor-$(date +%s).log"
echo_info "OFBiz API calls will be logged to: $LOG_FILE"

# Function to monitor OFBiz calls
monitor_ofbiz_api() {
    echo "=== OFBiz API Monitor Started at $(date) ===" >> "$LOG_FILE"
    
    # Start monitoring OFBiz logs in background (if accessible)
    if command -v docker >/dev/null 2>&1; then
        # Try to find OFBiz container and tail its logs
        OFBIZ_CONTAINER=$(docker ps --format "table {{.Names}}" | grep -i ofbiz | head -1 2>/dev/null || echo "")
        if [ -n "$OFBIZ_CONTAINER" ]; then
            echo_info "Found OFBiz container: $OFBIZ_CONTAINER"
            echo_info "Monitoring OFBiz container logs..."
            timeout 300 docker logs -f "$OFBIZ_CONTAINER" 2>&1 | grep -E "(createUser|createPartyGroup|getUserInfo|auth/token)" >> "$LOG_FILE" &
            MONITOR_PID=$!
        fi
    fi
}

# Start monitoring
monitor_ofbiz_api

echo ""
echo "🧪 Step 3: Run User Creation Tests"
echo "==================================="

# Test cases
TEST_USERS=(
    "john.doe@example.com:password123:Email format user"
    "jane.smith:password456:Simple username"
    "test.user@company.org:secret789:Complex email"
    "autouser$(date +%s):pass999:Generated username"
)

PASSED_TESTS=0
TOTAL_TESTS=${#TEST_USERS[@]}

# Function to test direct OFBiz API call
test_ofbiz_direct() {
    local username="$1"
    local first_name="$2"
    local last_name="$3"
    local email="$4"
    local password="$5"
    
    echo_info "📡 Testing direct OFBiz user creation for: $username"
    
    # Test createUser endpoint directly
    CREATE_USER_RESPONSE=$(curl -s -X POST "$OFBIZ_REST_URL/services/createUser" \
        -H "Content-Type: application/json" \
        -H "Authorization: Basic YWRtaW46b2ZiaXo=" \
        -d '{
            "userLoginId": "'$username'",
            "firstName": "'$first_name'",
            "lastName": "'$last_name'",
            "emailAddress": "'$email'",
            "userPassword": "'$password'",
            "tenantId": "default"
        }' \
        -w "\nHTTP_STATUS:%{http_code}" 2>/dev/null)
    
    HTTP_STATUS=$(echo "$CREATE_USER_RESPONSE" | grep "HTTP_STATUS:" | cut -d: -f2)
    RESPONSE_BODY=$(echo "$CREATE_USER_RESPONSE" | sed '/HTTP_STATUS:/d')
    
    echo "   Direct API Response (HTTP $HTTP_STATUS):"
    echo "   $RESPONSE_BODY" | jq '.' 2>/dev/null || echo "   $RESPONSE_BODY"
    
    # Log the API call
    echo "$(date): Direct createUser call for $username - HTTP $HTTP_STATUS" >> "$LOG_FILE"
    echo "$RESPONSE_BODY" >> "$LOG_FILE"
    
    if [ "$HTTP_STATUS" = "200" ] || [ "$HTTP_STATUS" = "201" ]; then
        echo_success "   Direct OFBiz user creation successful"
        return 0
    else
        echo_warning "   Direct OFBiz user creation failed or user exists"
        return 1
    fi
}

for test_case in "${TEST_USERS[@]}"; do
    IFS=':' read -r username password description <<< "$test_case"
    
    echo ""
    echo "🧪 Testing: $description"
    echo "   Username: $username"
    echo "   Password: [hidden]"
    
    # Extract name components for direct API test
    if [[ "$username" == *"@"* ]]; then
        local_part=$(echo "$username" | cut -d@ -f1)
        if [[ "$local_part" == *"."* ]]; then
            first_name=$(echo "$local_part" | cut -d. -f1 | sed 's/\b\w/\U&/g')
            last_name=$(echo "$local_part" | cut -d. -f2 | sed 's/\b\w/\U&/g')
        else
            first_name=$(echo "$local_part" | sed 's/\b\w/\U&/g')
            last_name="User"
        fi
        email="$username"
    else
        first_name=$(echo "$username" | sed 's/\b\w/\U&/g')
        last_name="User"
        email="${username}@example.com"
    fi
    
    # Test 1: Direct OFBiz API call
    echo ""
    echo "   📡 Test 1: Direct OFBiz API Call"
    test_ofbiz_direct "$username" "$first_name" "$last_name" "$email" "$password"
    
    # Test 2: Keycloak authentication (which should trigger SPI user creation)
    echo ""
    echo "   🔑 Test 2: Keycloak Authentication (SPI Integration)"
    if bash "$SCRIPT_DIR/test-user-quick.sh" "$username" "$password" 2>/dev/null; then
        echo_success "   Keycloak authentication successful"
        ((PASSED_TESTS++))
    else
        echo_error "   Keycloak authentication failed"
    fi
    
    # Small delay between tests to allow log monitoring
    sleep 2
done

echo ""
echo "📊 Test Results Summary"
echo "======================="
echo "   Passed: $PASSED_TESTS/$TOTAL_TESTS"
echo "   Success Rate: $((PASSED_TESTS * 100 / TOTAL_TESTS))%"

if [ $PASSED_TESTS -eq $TOTAL_TESTS ]; then
    echo_success "All tests passed! User creation feature is working correctly."
else
    echo_warning "Some tests failed. Check configuration and logs."
fi

echo ""
echo "🔍 Step 4: Verify Created Users & API Calls"
echo "============================================"

echo_info "You can verify created users in:"
echo "   • Keycloak Admin Console: $KEYCLOAK_URL/admin"
echo "   • OFBiz User Management: $OFBIZ_URL/webtools/control/userManagement"

# Stop monitoring if it was started
if [ -n "$MONITOR_PID" ]; then
    kill $MONITOR_PID 2>/dev/null || true
fi

# Show the monitoring log
echo ""
echo "� OFBiz API Call Log:"
echo "======================"
if [ -f "$LOG_FILE" ]; then
    echo_info "Showing OFBiz REST API calls made during testing:"
    echo ""
    cat "$LOG_FILE"
    echo ""
    echo_info "Full log saved to: $LOG_FILE"
else
    echo_warning "No API monitoring log found"
fi

# Test if we can list users from OFBiz (if endpoint exists)
echo ""
echo "📋 Testing OFBiz User List Endpoint:"
echo "======================================"
USER_LIST_RESPONSE=$(curl -s -X GET "$OFBIZ_REST_URL/services/findParty" \
    -H "Authorization: Basic YWRtaW46b2ZiaXo=" \
    -H "Content-Type: application/json" \
    -d '{"partyTypeId": "PERSON"}' 2>/dev/null || echo '{"error": "endpoint not available"}')

echo "$USER_LIST_RESPONSE" | jq '.' 2>/dev/null || echo "$USER_LIST_RESPONSE"

echo ""
echo "📋 Step 5: Manual Testing & Verification"
echo "========================================"

echo_info "For manual testing and verification:"
echo ""
echo "1. Direct OFBiz API Testing:"
echo "   # Test user creation directly"
echo "   curl -X POST '$OFBIZ_REST_URL/services/createUser' \\"
echo "     -H 'Content-Type: application/json' \\"
echo "     -H 'Authorization: Basic YWRtaW46b2ZiaXo=' \\"
echo "     -d '{\"userLoginId\":\"testuser\",\"firstName\":\"Test\",\"lastName\":\"User\",\"emailAddress\":\"test@example.com\"}'"
echo ""
echo "2. Keycloak SPI Testing:"
echo "   # Test via Keycloak authentication"
echo "   ./scripts/test-user-quick.sh newuser@example.com password123"
echo ""
echo "3. Monitor OFBiz Logs:"
echo "   # Watch OFBiz for REST API calls"
echo "   docker logs -f ofbiz-container | grep -E '(createUser|createPartyGroup)'"
echo ""
echo "4. Check Keycloak Logs:"
echo "   # Monitor Keycloak SPI activity"
echo "   docker logs -f keycloak-dev | grep -E '(USER CREATION|REST AUTH)'"

echo ""
echo_success "User creation test with real OFBiz completed!"
echo ""
echo "🔗 Useful URLs:"
echo "   • Keycloak Admin: $KEYCLOAK_URL/admin"
echo "   • OFBiz WebTools: $OFBIZ_URL/webtools"
echo "   • OFBiz REST API: $OFBIZ_REST_URL"

exit 0
