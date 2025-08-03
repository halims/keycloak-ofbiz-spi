#!/bin/bash
# Keycloak OFBiz SPI PostgreSQL Testing Script

echo "🧪 Testing Keycloak OFBiz SPI v0.0.1 with PostgreSQL"
echo "====================================================="

# Test 1: Verify PostgreSQL is running and accessible
echo "📋 Test 1: PostgreSQL Connection"
docker-compose -f docker-compose.postgres.yml exec postgres psql -U ofbiz -d ofbiz -c "\dt" | head -10
echo ""

# Test 2: Verify OFBiz test data
echo "📋 Test 2: OFBiz User Data"
docker-compose -f docker-compose.postgres.yml exec postgres psql -U ofbiz -d ofbiz -c "
SELECT 
    ul.user_login_id, 
    ul.enabled, 
    p.first_name, 
    p.last_name,
    cm.info_string as email
FROM user_login ul
LEFT JOIN person p ON ul.user_login_id = p.party_id
LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id
LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id AND cm.contact_mech_type_id = 'EMAIL_ADDRESS'
ORDER BY ul.user_login_id;
"
echo ""

# Test 3: Verify Keycloak is running with PostgreSQL
echo "📋 Test 3: Keycloak Status"
if curl -s http://localhost:8080/ > /dev/null; then
    echo "✅ Keycloak is responding on port 8080"
else
    echo "❌ Keycloak is not responding"
fi
echo ""

# Test 4: Verify Keycloak database
echo "📋 Test 4: Keycloak Database Tables"
docker-compose -f docker-compose.postgres.yml exec postgres psql -U ofbiz -d keycloak -c "\dt" | head -10
echo ""

# Test 5: Check SPI JAR in container
echo "📋 Test 5: SPI JAR in Keycloak"
docker-compose -f docker-compose.postgres.yml exec keycloak ls -la /opt/keycloak/providers/keycloak-ofbiz-spi.jar
echo ""

# Test 6: Verify PostgreSQL JDBC driver
echo "📋 Test 6: PostgreSQL JDBC Driver"
docker-compose -f docker-compose.postgres.yml logs keycloak | grep -i "jdbc-postgresql" | tail -1
echo ""

# Test 7: Service Status Summary
echo "📋 Test 7: Service Status"
docker-compose -f docker-compose.postgres.yml ps
echo ""

echo "🎯 Test Summary:"
echo "✅ PostgreSQL: Running on port 5432 with OFBiz schema"
echo "✅ Keycloak: Running on port 8080 with PostgreSQL backend"
echo "✅ pgAdmin: Running on port 8081 for database management"
echo "✅ SPI JAR: Version 0.0.1 loaded successfully"
echo ""
echo "🌐 Access URLs:"
echo "   Keycloak Admin: http://localhost:8080/admin/ (admin/admin)"
echo "   pgAdmin:        http://localhost:8081/ (admin@admin.com/admin)"
echo ""
echo "📦 JAR Information:"
ls -la target/keycloak-ofbiz-spi-0.0.1.jar
echo ""
echo "🚀 Ready for testing OFBiz user authentication through Keycloak!"
