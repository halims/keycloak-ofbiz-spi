#!/bin/bash

# Start Complete HTTPS Environment for Keycloak SPA Testing

set -e

echo "🚀 Starting Complete HTTPS Keycloak SPA Environment"
echo "=================================================="

# Check if we're in the correct directory
if [ ! -f "docker-compose.spa-https.yaml" ]; then
  echo "❌ Error: Please run this script from the keycloak-spi root directory"
  exit 1
fi

# Add required hosts to /etc/hosts if not already present
echo "🔧 Setting up local hosts..."

if ! grep -q "keycloak.local" /etc/hosts; then
  echo "127.0.0.1 keycloak.local" | sudo tee -a /etc/hosts
  echo "✅ Added keycloak.local to /etc/hosts"
fi

if ! grep -q "spa.local" /etc/hosts; then
  echo "127.0.0.1 spa.local" | sudo tee -a /etc/hosts
  echo "✅ Added spa.local to /etc/hosts"
fi

if ! grep -q "ofbiz.local" /etc/hosts; then
  echo "127.0.0.1 ofbiz.local" | sudo tee -a /etc/hosts
  echo "✅ Added ofbiz.local to /etc/hosts"
fi

# Generate SSL certificates if they don't exist
if [ ! -f "docker/ssl/spa.crt" ] || [ ! -f "docker/ssl/spa.key" ]; then
  echo "🔐 Generating SSL certificates..."
  ./scripts/generate-ssl-cert.sh
fi

# Start the complete environment
echo "🐳 Starting Docker containers..."
docker-compose -f docker-compose.spa-https.yaml down --remove-orphans 2>/dev/null || true
docker-compose -f docker-compose.spa-https.yaml up -d

echo "⏳ Waiting for services to start..."
sleep 45

# Wait for Keycloak to be ready
echo "🔍 Waiting for Keycloak to be ready..."
until curl -k -s https://keycloak.local:8444/health/ready > /dev/null 2>&1; do
  echo "   Still waiting for Keycloak..."
  sleep 5
done

echo "✅ Keycloak is ready!"

# Configure Keycloak with the SPA client
echo "⚙️ Configuring Keycloak realm and client..."
cd spa-test/
./setup-complete.sh https://keycloak.local:8444
cd ..

echo ""
echo "🎉 HTTPS Environment is Ready!"
echo "=============================="
echo ""
echo "🔗 Access URLs:"
echo "   • React SPA (HTTPS): https://spa.local:3443"
echo "   • React SPA (HTTP):  http://spa.local:3000"
echo "   • Keycloak Admin:     https://keycloak.local:8444/admin"
echo ""
echo "🔐 Test Credentials:"
echo "   • Admin Console: admin / admin"
echo "   • Test User 1: usertest / password123"
echo "   • Test User 2: admin / admin123"
echo ""
echo "🧪 Testing Steps:"
echo "1. Open https://spa.local:3443 in your browser"
echo "2. Accept the self-signed certificate warnings (both Keycloak and SPA)"
echo "3. Click 'Login with Keycloak'"
echo "4. Use any of the test credentials above"
echo "5. You should be redirected back to the HTTPS SPA with user info"
echo ""
echo "⚠️ Note: You'll need to accept self-signed certificates for both:"
echo "   - https://keycloak.local:8444 (Keycloak server)"
echo "   - https://spa.local:3443 (React SPA)"
echo ""
echo "📱 To view logs:"
echo "   docker-compose -f docker-compose.spa-https.yaml logs -f"
echo ""
echo "🛑 To stop environment:"
echo "   docker-compose -f docker-compose.spa-https.yaml down"
echo ""
