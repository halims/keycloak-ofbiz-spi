#!/bin/bash

# Deployment script for Keycloak OFBiz SPI
# This script builds the SPI and deploys it to Keycloak

set -e

# Configuration
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
JAR_NAME="keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar"

# Default Keycloak home (can be overridden)
KEYCLOAK_HOME="${KEYCLOAK_HOME:-/opt/keycloak}"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}Keycloak OFBiz SPI Deployment Script${NC}"
echo "======================================"

# Check if KEYCLOAK_HOME is set and exists
if [ ! -d "$KEYCLOAK_HOME" ]; then
    echo -e "${RED}Error: KEYCLOAK_HOME not set or directory doesn't exist: $KEYCLOAK_HOME${NC}"
    echo "Please set KEYCLOAK_HOME environment variable or create the directory"
    exit 1
fi

echo -e "${YELLOW}Project Directory:${NC} $PROJECT_DIR"
echo -e "${YELLOW}Keycloak Home:${NC} $KEYCLOAK_HOME"

# Build the project
echo -e "\n${YELLOW}Building project...${NC}"
cd "$PROJECT_DIR"
mvn clean package -DskipTests

# Check if JAR was built successfully
JAR_PATH="$PROJECT_DIR/target/$JAR_NAME"
if [ ! -f "$JAR_PATH" ]; then
    echo -e "${RED}Error: JAR file not found: $JAR_PATH${NC}"
    exit 1
fi

echo -e "${GREEN}Build successful!${NC}"

# Create providers directory if it doesn't exist
PROVIDERS_DIR="$KEYCLOAK_HOME/providers"
if [ ! -d "$PROVIDERS_DIR" ]; then
    echo -e "${YELLOW}Creating providers directory: $PROVIDERS_DIR${NC}"
    mkdir -p "$PROVIDERS_DIR"
fi

# Copy JAR to Keycloak providers directory
echo -e "\n${YELLOW}Deploying SPI to Keycloak...${NC}"
cp "$JAR_PATH" "$PROVIDERS_DIR/"

if [ $? -eq 0 ]; then
    echo -e "${GREEN}SPI deployed successfully to: $PROVIDERS_DIR/$JAR_NAME${NC}"
else
    echo -e "${RED}Error: Failed to copy JAR to providers directory${NC}"
    exit 1
fi

# Check if Keycloak is running
KEYCLOAK_PID=$(pgrep -f "keycloak")
if [ ! -z "$KEYCLOAK_PID" ]; then
    echo -e "\n${YELLOW}Keycloak is currently running (PID: $KEYCLOAK_PID)${NC}"
    echo -e "${YELLOW}You need to restart Keycloak to load the new SPI${NC}"
    
    read -p "Do you want to restart Keycloak now? (y/N): " -n 1 -r
    echo
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo -e "${YELLOW}Stopping Keycloak...${NC}"
        "$KEYCLOAK_HOME/bin/kc.sh" stop
        
        echo -e "${YELLOW}Starting Keycloak...${NC}"
        "$KEYCLOAK_HOME/bin/kc.sh" start-dev &
        
        echo -e "${GREEN}Keycloak restarted!${NC}"
    fi
else
    echo -e "\n${GREEN}Keycloak is not running. Start it with:${NC}"
    echo "$KEYCLOAK_HOME/bin/kc.sh start-dev"
fi

echo -e "\n${GREEN}Deployment completed!${NC}"
echo -e "\n${YELLOW}Next Steps:${NC}"
echo "1. Start Keycloak if not already running"
echo "2. Log into Keycloak Admin Console"
echo "3. Navigate to your realm -> User Federation"
echo "4. Add provider -> ofbiz-user-storage"
echo "5. Configure database connection settings"

echo -e "\n${YELLOW}Configuration Parameters:${NC}"
echo "- JDBC Driver: com.mysql.cj.jdbc.Driver (for MySQL)"
echo "- JDBC URL: jdbc:mysql://localhost:3306/ofbiz"
echo "- Username: your_ofbiz_db_user"
echo "- Password: your_ofbiz_db_password"
