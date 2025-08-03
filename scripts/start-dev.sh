#!/bin/bash

# Build and Start Docker Environment Script
# This script ensures the JAR is built before starting Docker

set -e

echo "🚀 Building and Starting Keycloak OFBiz SPI Development Environment"
echo "=================================================================="

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven is not installed or not in PATH"
    exit 1
fi

# Build the project
echo "📦 Building the SPI JAR..."
mvn clean package

# Check if JAR was built successfully
JAR_FILE="target/keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "❌ JAR file not found: $JAR_FILE"
    echo "Build may have failed"
    exit 1
fi

echo "✅ JAR built successfully: $JAR_FILE"
echo "📋 JAR size: $(du -h $JAR_FILE | cut -f1)"

# Check if docker-compose is available
if ! command -v docker-compose &> /dev/null; then
    echo "❌ docker-compose is not installed or not in PATH"
    exit 1
fi

# Stop any existing containers
echo "🛑 Stopping existing containers..."
docker-compose down

# Choose docker-compose file based on preference
COMPOSE_FILE="docker-compose.yml"
if [ "$1" = "--build" ]; then
    COMPOSE_FILE="docker-compose.build.yml"
    echo "🔨 Using build-based docker-compose (custom Keycloak image)"
else
    echo "🚀 Using volume-mounted docker-compose (faster for development)"
fi

# Start the environment
echo "🐳 Starting Docker environment..."
docker-compose -f $COMPOSE_FILE up -d

# Wait for services to be ready
echo "⏳ Waiting for services to start..."
sleep 10

# Check service status
echo "📊 Service Status:"
docker-compose -f $COMPOSE_FILE ps

# Wait for Keycloak to be ready
echo "⏳ Waiting for Keycloak to be ready..."
timeout=300  # 5 minutes
counter=0
while [ $counter -lt $timeout ]; do
    if curl -s -f http://localhost:8080/health/ready > /dev/null 2>&1; then
        echo "✅ Keycloak is ready!"
        break
    fi
    sleep 5
    counter=$((counter + 5))
    echo "   Waiting... ($counter/$timeout seconds)"
done

if [ $counter -ge $timeout ]; then
    echo "❌ Keycloak did not start within $timeout seconds"
    echo "📋 Checking logs..."
    docker-compose -f $COMPOSE_FILE logs keycloak
    exit 1
fi

echo ""
echo "🎉 Environment started successfully!"
echo ""
echo "📋 Access Information:"
echo "===================="
echo "🌐 Keycloak Admin Console: http://localhost:8080"
echo "   Username: admin"
echo "   Password: admin"
echo ""
echo "🗄️  phpMyAdmin: http://localhost:8081"
echo "   Server: mysql"
echo "   Username: root"
echo "   Password: root"
echo ""
echo "🐳 MySQL Database:"
echo "   Host: localhost:3306"
echo "   Database: ofbiz"
echo "   Username: ofbiz"
echo "   Password: ofbiz"
echo ""
echo "📋 Test Users (in OFBiz database):"
echo "   - admin / password"
echo "   - john.doe / password"
echo "   - jane.smith / password"
echo ""
echo "🔧 To configure the SPI:"
echo "1. Go to Keycloak Admin Console"
echo "2. Select your realm"
echo "3. Go to User Federation"
echo "4. Add provider -> ofbiz-user-storage"
echo "5. Configure database connection"
echo ""
echo "📊 To view logs:"
echo "   docker-compose -f $COMPOSE_FILE logs -f keycloak"
echo ""
echo "🛑 To stop:"
echo "   docker-compose -f $COMPOSE_FILE down"
