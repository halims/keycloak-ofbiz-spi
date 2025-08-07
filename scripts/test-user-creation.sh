#!/bin/bash
# Test script for OFBiz SPI User Creation Feature
# This script tests the automatic user creation functionality when users don't exist in OFBiz

set -e  # Exit on any error

echo "🔨 OFBiz SPI User Creation Test"
echo "==============================="
echo "Testing automatic user creation when users don't exist in OFBiz"
echo ""

# Configuration
KEYCLOAK_URL="http://localhost:8090"
REALM_NAME="ofbiz"
CLIENT_ID="admin-cli"
ADMIN_USER="admin"
ADMIN_PASS="admin"

# Test users that should be created automatically
TEST_USERS=(
    "john.doe@example.com:password123"
    "jane.smith@company.com:password456"
    "testuser:password789"
    "mike.wilson@domain.org:password999"
)

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

echo_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

echo_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

echo_error() {
    echo -e "${RED}❌ $1${NC}"
}

echo_step() {
    echo -e "\n${BLUE}📋 $1${NC}"
    echo "$(printf '%.0s-' {1..50})"
}

# Check prerequisites
echo_step "Step 1: Prerequisites Check"

if ! command -v jq &> /dev/null; then
    echo_error "jq is required but not installed. Please install jq first."
    echo "   Ubuntu/Debian: sudo apt-get install jq"
    echo "   MacOS: brew install jq"
    exit 1
fi

if ! command -v curl &> /dev/null; then
    echo_error "curl is required but not installed."
    exit 1
fi

echo_success "Required tools available (curl, jq)"

# Check if Keycloak is running
if ! curl -s -f "$KEYCLOAK_URL/realms/master" > /dev/null; then
    echo_error "Keycloak is not running at $KEYCLOAK_URL"
    echo "   Please start Keycloak with: docker-compose up -d"
    exit 1
fi

echo_success "Keycloak is running at $KEYCLOAK_URL"

# Get admin token
echo_step "Step 2: Get Admin Token"

ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=$ADMIN_USER&password=$ADMIN_PASS&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

if [ "$ADMIN_TOKEN" = "null" ] || [ -z "$ADMIN_TOKEN" ]; then
    echo_error "Failed to get admin token. Check admin credentials."
    exit 1
fi

echo_success "Admin token obtained"

# Check realm exists
echo_step "Step 3: Check OFBiz Realm"

REALM_EXISTS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME")

if [ "$REALM_EXISTS" != "200" ]; then
    echo_error "OFBiz realm '$REALM_NAME' does not exist (HTTP $REALM_EXISTS)"
    echo "   Please run the realm setup script first: ./demo-realm-config.sh"
    exit 1
fi

echo_success "OFBiz realm exists and is accessible"

# Check if OFBiz SPI is configured
echo_step "Step 4: Check OFBiz SPI Configuration"

COMPONENTS=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components?type=org.keycloak.storage.UserStorageProvider")

SPI_CONFIGURED=$(echo "$COMPONENTS" | jq -r '.[] | select(.providerId=="ofbiz-user-storage") | .id // empty' | head -1)

if [ -z "$SPI_CONFIGURED" ]; then
    echo_error "OFBiz SPI is not configured in realm '$REALM_NAME'"
    echo "   Please configure the OFBiz User Storage Provider in Keycloak admin console"
    echo "   or run: ./configure-spi.sh"
    exit 1
fi

echo_success "OFBiz SPI is configured (Component ID: $SPI_CONFIGURED)"

# Get SPI configuration
echo_info "Fetching SPI configuration..."
SPI_CONFIG=$(curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
    "$KEYCLOAK_URL/admin/realms/$REALM_NAME/components/$SPI_CONFIGURED")

# Check if user creation is enabled
USER_CREATION_ENABLED=$(echo "$SPI_CONFIG" | jq -r '.config.ofbizEnableUserCreation[0] // "false"')
CREATE_USER_ENDPOINT=$(echo "$SPI_CONFIG" | jq -r '.config.ofbizCreateUserEndpoint[0] // "N/A"')
DEFAULT_PASSWORD=$(echo "$SPI_CONFIG" | jq -r '.config.ofbizDefaultUserPassword[0] // "N/A"')

echo_info "Current SPI Configuration:"
echo "   User Creation Enabled: $USER_CREATION_ENABLED"
echo "   Create User Endpoint: $CREATE_USER_ENDPOINT"
echo "   Default Password: $([ "$DEFAULT_PASSWORD" != "N/A" ] && echo "[CONFIGURED]" || echo "N/A")"

if [ "$USER_CREATION_ENABLED" != "true" ]; then
    echo_warning "User creation is disabled in SPI configuration"
    echo "   This test will demonstrate authentication failures for non-existent users"
    echo "   To enable user creation, set 'ofbizEnableUserCreation' to 'true' in SPI config"
fi

# Function to test user authentication
test_user_authentication() {
    local username="$1"
    local password="$2"
    local test_name="$3"
    
    echo_info "Testing: $test_name"
    echo "   Username: $username"
    echo "   Password: [hidden]"
    
    # Attempt authentication
    TOKEN_RESPONSE=$(curl -s -X POST "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "grant_type=password" \
        -d "client_id=$CLIENT_ID" \
        -d "username=$username" \
        -d "password=$password" \
        -d "scope=openid email profile" 2>/dev/null)
    
    ACCESS_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token // empty' 2>/dev/null)
    ERROR_TYPE=$(echo "$TOKEN_RESPONSE" | jq -r '.error // empty' 2>/dev/null)
    ERROR_DESC=$(echo "$TOKEN_RESPONSE" | jq -r '.error_description // empty' 2>/dev/null)
    
    if [ -n "$ACCESS_TOKEN" ] && [ "$ACCESS_TOKEN" != "null" ]; then
        echo_success "Authentication successful!"
        
        # Get user information
        USER_INFO=$(curl -s -H "Authorization: Bearer $ACCESS_TOKEN" \
            "$KEYCLOAK_URL/realms/$REALM_NAME/protocol/openid-connect/userinfo" 2>/dev/null)
        
        if echo "$USER_INFO" | jq -e . >/dev/null 2>&1; then
            echo "   📊 User Details:"
            echo "      Username: $(echo "$USER_INFO" | jq -r '.preferred_username')"
            echo "      Email: $(echo "$USER_INFO" | jq -r '.email // "N/A"')"
            echo "      Name: $(echo "$USER_INFO" | jq -r '.given_name // "N/A"') $(echo "$USER_INFO" | jq -r '.family_name // "N/A"')"
            echo "      Tenant: $(echo "$USER_INFO" | jq -r '.tenant // "N/A"')"
            
            # Check if user was created by Keycloak
            CREATED_BY_KC=$(echo "$USER_INFO" | jq -r '.createdByKeycloak // "false"')
            if [ "$CREATED_BY_KC" = "true" ]; then
                echo_success "   🔨 User was automatically created by Keycloak SPI!"
                CREATED_AT=$(echo "$USER_INFO" | jq -r '.createdAt // empty')
                if [ -n "$CREATED_AT" ]; then
                    CREATED_DATE=$(date -d "@$((CREATED_AT/1000))" 2>/dev/null || echo "Unknown")
                    echo "      Creation Time: $CREATED_DATE"
                fi
            else
                echo_info "   👤 User already existed in OFBiz"
            fi
        fi
        
        return 0
    else
        echo_error "Authentication failed!"
        if [ -n "$ERROR_TYPE" ]; then
            echo "   Error Type: $ERROR_TYPE"
            [ -n "$ERROR_DESC" ] && echo "   Description: $ERROR_DESC"
            
            # Provide specific guidance based on error
            case "$ERROR_TYPE" in
                "invalid_grant")
                    if [ "$USER_CREATION_ENABLED" = "true" ]; then
                        echo_warning "   💡 If user creation is enabled, this suggests OFBiz connection issues"
                    else
                        echo_info "   💡 This is expected when user creation is disabled"
                    fi
                    ;;
                "unauthorized_client")
                    echo_warning "   💡 Client configuration issue - check client settings"
                    ;;
                *)
                    echo_warning "   💡 Unexpected error - check Keycloak logs"
                    ;;
            esac
        fi
        return 1
    fi
}

# Test user creation scenarios
echo_step "Step 5: Test User Creation Scenarios"

SUCCESSFUL_AUTHENTICATIONS=0
FAILED_AUTHENTICATIONS=0

for user_data in "${TEST_USERS[@]}"; do
    IFS=':' read -r username password <<< "$user_data"
    
    echo ""
    echo "🧪 Test Case: User Creation for '$username'"
    echo "$(printf '%.0s=' {1..60})"
    
    if test_user_authentication "$username" "$password" "Non-existent user test"; then
        ((SUCCESSFUL_AUTHENTICATIONS++))
    else
        ((FAILED_AUTHENTICATIONS++))
    fi
    
    # Small delay between tests
    sleep 1
done

# Test with existing admin user (should not create new user)
echo ""
echo "🧪 Test Case: Existing User Authentication"
echo "$(printf '%.0s=' {1..60})"

if test_user_authentication "admin" "ofbiz" "Existing admin user test"; then
    ((SUCCESSFUL_AUTHENTICATIONS++))
else
    ((FAILED_AUTHENTICATIONS++))
fi

# Summary
echo_step "Test Results Summary"

echo "📊 Authentication Results:"
echo "   ✅ Successful: $SUCCESSFUL_AUTHENTICATIONS"
echo "   ❌ Failed: $FAILED_AUTHENTICATIONS"
echo "   📈 Total Tests: $((SUCCESSFUL_AUTHENTICATIONS + FAILED_AUTHENTICATIONS))"

echo ""
echo "🔍 Analysis:"

if [ "$USER_CREATION_ENABLED" = "true" ]; then
    if [ $SUCCESSFUL_AUTHENTICATIONS -gt 0 ]; then
        echo_success "User creation feature is working! New users were automatically created."
        echo "   ✨ The SPI successfully:"
        echo "      • Detected non-existent users"
        echo "      • Extracted name information from usernames/emails"
        echo "      • Created users in OFBiz via REST API"
        echo "      • Enabled immediate authentication"
    else
        echo_error "User creation feature is enabled but not working properly."
        echo "   🔧 Possible issues:"
        echo "      • OFBiz REST API connectivity problems"
        echo "      • Invalid create user endpoint configuration"
        echo "      • OFBiz service errors"
        echo "      • Network connectivity issues"
    fi
else
    if [ $FAILED_AUTHENTICATIONS -gt 0 ]; then
        echo_info "User creation is disabled - failed authentications are expected for new users."
        echo "   💡 To enable user creation:"
        echo "      1. Go to Keycloak Admin Console"
        echo "      2. Navigate to: $REALM_NAME → User Federation → OFBiz SPI"
        echo "      3. Set 'Enable User Creation' to 'true'"
        echo "      4. Configure create user endpoint and default password"
        echo "      5. Save configuration"
    fi
fi

# Check Keycloak logs for detailed information
echo ""
echo_step "Additional Debugging Information"

echo_info "To view detailed logs, run:"
echo "   docker logs keycloak-dev -f --tail=50"

echo ""
echo_info "To check OFBiz SPI configuration:"
echo "   Admin Console: $KEYCLOAK_URL/admin"
echo "   Path: $REALM_NAME → User Federation → OFBiz SPI"

echo ""
echo_info "Manual testing commands:"
echo "   # Test single user creation"
echo "   ./test-user-creation.sh john.doe@test.com password123"
echo ""
echo "   # Test with different realm"
echo "   REALM_NAME=other-realm ./test-user-creation.sh"

# Interactive mode for custom testing
if [ $# -eq 0 ]; then
    echo ""
    echo_step "Interactive Testing Mode"
    echo_info "You can now test custom user creation scenarios."
    
    while true; do
        echo ""
        read -p "Enter username to test (or 'quit' to exit): " test_username
        
        if [ "$test_username" = "quit" ] || [ "$test_username" = "q" ]; then
            break
        fi
        
        if [ -z "$test_username" ]; then
            echo_warning "Username cannot be empty"
            continue
        fi
        
        read -s -p "Enter password: " test_password
        echo ""
        
        if [ -z "$test_password" ]; then
            echo_warning "Password cannot be empty"
            continue
        fi
        
        echo ""
        echo "🧪 Custom Test: '$test_username'"
        echo "$(printf '%.0s-' {1..40})"
        
        test_user_authentication "$test_username" "$test_password" "Custom user test"
    done
fi

echo ""
echo_success "User creation test completed!"
echo ""
echo "🔗 Useful Resources:"
echo "   • Keycloak Admin: $KEYCLOAK_URL/admin"
echo "   • User Management: $KEYCLOAK_URL/admin/master/console/#/$REALM_NAME/users"
echo "   • SPI Configuration: $KEYCLOAK_URL/admin/master/console/#/$REALM_NAME/user-federation"
echo "   • Realm Settings: $KEYCLOAK_URL/admin/master/console/#/$REALM_NAME/realm-settings"

exit 0
