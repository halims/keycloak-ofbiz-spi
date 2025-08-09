#!/bin/bash

# Generate SSL certificates for local development
# This script creates a self-signed certificate for Keycloak HTTPS

CERT_DIR="./docker/ssl"
DOMAIN="keycloak.local"

# Create SSL directory if it doesn't exist
mkdir -p "$CERT_DIR"

echo "🔐 Generating self-signed SSL certificate for $DOMAIN..."

# Generate private key
openssl genrsa -out "$CERT_DIR/keycloak.key" 2048

# Generate certificate signing request
openssl req -new -key "$CERT_DIR/keycloak.key" -out "$CERT_DIR/keycloak.csr" -subj "/C=US/ST=Dev/L=Local/O=Development/OU=IT/CN=$DOMAIN"

# Generate self-signed certificate (valid for 365 days)
openssl x509 -req -in "$CERT_DIR/keycloak.csr" -signkey "$CERT_DIR/keycloak.key" -out "$CERT_DIR/keycloak.crt" -days 365 -extensions v3_req -extfile <(cat <<EOF
[v3_req]
basicConstraints = CA:FALSE
keyUsage = nonRepudiation, digitalSignature, keyEncipherment
subjectAltName = @alt_names

[alt_names]
DNS.1 = $DOMAIN
DNS.2 = localhost
IP.1 = 127.0.0.1
EOF
)

# Create combined certificate file for Keycloak
cat "$CERT_DIR/keycloak.crt" "$CERT_DIR/keycloak.key" > "$CERT_DIR/keycloak.pem"

# Set proper permissions
chmod 644 "$CERT_DIR/keycloak.crt"
chmod 600 "$CERT_DIR/keycloak.key"
chmod 600 "$CERT_DIR/keycloak.pem"

echo "✅ SSL certificate generated successfully!"
echo "📁 Certificate files:"
echo "   - Certificate: $CERT_DIR/keycloak.crt"
echo "   - Private Key: $CERT_DIR/keycloak.key"
echo "   - Combined PEM: $CERT_DIR/keycloak.pem"
echo ""
echo "🚨 IMPORTANT: Add the following to your /etc/hosts file:"
echo "   127.0.0.1 $DOMAIN"
echo ""
echo "⚠️  You'll need to accept the self-signed certificate in your browser"
echo "   when first accessing https://$DOMAIN:8443"
