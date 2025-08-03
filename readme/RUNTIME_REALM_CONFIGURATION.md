# Runtime Realm Configuration Guide

## 🎯 **How to Configure Activated Realms at Runtime**

The OFBiz SPI provides multiple ways to control which realms it should be active in. Here's a comprehensive guide on runtime configuration.

## 🔧 **Method 1: Keycloak Admin Console (Recommended)**

### Step-by-Step Configuration:

#### 1. **Access Keycloak Admin Console**
```
URL: http://localhost:8080/admin/
Username: admin
Password: admin
```

#### 2. **Select Target Realm**
- Click the realm dropdown (top-left corner)
- Choose your application realm (e.g., `myapp-realm`)
- **⚠️ NEVER configure on master realm**

#### 3. **Configure User Federation**
- Go to **User Federation** in the left menu
- Click **Add provider** → **ofbiz-user-storage**
- Fill in the configuration:

```yaml
Console Display Name: "OFBiz Users"
JDBC Driver Class: "com.mysql.cj.jdbc.Driver"
JDBC URL: "jdbc:mysql://mysql:3306/ofbiz"
Database Username: "ofbiz"
Database Password: "ofbiz"
Validation Query: "SELECT 1"
Connection Pool Size: "10"
Enabled Realms: "myapp-realm,staging-realm"  # ← KEY SETTING
```

#### 4. **Save and Test**
- Click **Save**
- Go to **Action** → **Test connection**
- Should show: ✅ "Connection successful"

## 🔧 **Method 2: Environment Variables**

### For Docker Deployments:

```bash
# docker-compose.yml or environment
KEYCLOAK_SPI_OFBIZ_ENABLED_REALMS=myapp-realm,staging-realm
KEYCLOAK_SPI_OFBIZ_DB_URL=jdbc:mysql://mysql:3306/ofbiz
KEYCLOAK_SPI_OFBIZ_DB_USERNAME=ofbiz
KEYCLOAK_SPI_OFBIZ_DB_PASSWORD=ofbiz
```

### For Kubernetes:

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: keycloak-spi-config
data:
  KEYCLOAK_SPI_OFBIZ_ENABLED_REALMS: "production-realm"
  KEYCLOAK_SPI_OFBIZ_DB_URL: "jdbc:postgresql://postgres:5432/ofbiz"
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
spec:
  template:
    spec:
      containers:
      - name: keycloak
        envFrom:
        - configMapRef:
            name: keycloak-spi-config
```

## 🔧 **Method 3: System Properties**

### Startup Arguments:
```bash
# Start Keycloak with system properties
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  -Dspi.ofbiz.enabled.realms=myapp-realm,staging-realm \
  -Dspi.ofbiz.db.url=jdbc:mysql://localhost:3306/ofbiz \
  -Dspi.ofbiz.db.username=ofbiz
```

### JVM Arguments:
```bash
export JAVA_OPTS="-Dspi.ofbiz.enabled.realms=production-realm"
$KEYCLOAK_HOME/bin/kc.sh start
```

## 🔧 **Method 4: Runtime Updates via Admin REST API**

### Update Configuration Programmatically:

```bash
# Get authentication token
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" \
  | jq -r '.access_token')

# Update User Federation component
curl -X PUT "http://localhost:8080/admin/realms/myapp-realm/components/{component-id}" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "config": {
      "enabledRealms": ["myapp-realm", "staging-realm"],
      "jdbcUrl": ["jdbc:mysql://mysql:3306/ofbiz"],
      "username": ["ofbiz"],
      "password": ["ofbiz"]
    }
  }'
```

## 📋 **Configuration Scenarios**

### **Scenario 1: Single Production Realm**
```yaml
Enabled Realms: "production-app"
Result: 
  - ✅ production-app: Uses OFBiz authentication
  - ✅ master: Uses default Keycloak authentication
  - ✅ other realms: Use default Keycloak authentication
```

### **Scenario 2: Multi-Environment**
```yaml
Enabled Realms: "staging-app,production-app"
Result:
  - ✅ staging-app: Uses OFBiz authentication
  - ✅ production-app: Uses OFBiz authentication  
  - ✅ master: Uses default Keycloak authentication
  - ✅ other realms: Use default Keycloak authentication
```

### **Scenario 3: Development (Not Recommended for Production)**
```yaml
Enabled Realms: "" (empty)
Result:
  - ⚠️ ALL realms: Uses OFBiz authentication (with warnings)
  - ⚠️ master: Shows warning logs but works
```

### **Scenario 4: Complete Isolation**
```yaml
Enabled Realms: "app-realm"
Result:
  - ✅ app-realm: Uses OFBiz authentication
  - ✅ master: Completely protected, no SPI interference
  - ✅ other realms: Completely protected, no SPI interference
```

## 🔄 **Runtime Changes**

### **Hot Configuration Updates:**

1. **Via Admin Console:**
   - Change "Enabled Realms" field
   - Click **Save**
   - Changes take effect immediately (next authentication request)

2. **Via REST API:**
   - Update component configuration
   - No restart required
   - Changes propagate within seconds

3. **Via Environment Variables:**
   - Requires container/pod restart
   - Safe for production updates

## 📊 **Monitoring and Verification**

### **Check Current Configuration:**

```bash
# View Keycloak logs for realm activation
docker-compose logs keycloak | grep "OFBiz.*realm"

# Expected output:
# OFBiz provider not active for realm: master, skipping user lookup
# Getting user by username: testuser in realm: myapp-realm
# OFBiz User Storage Provider for model: OFBiz Users in realm: myapp-realm
```

### **Test Realm Activation:**

```bash
# Test authentication on enabled realm
curl -X POST "http://localhost:8080/realms/myapp-realm/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=password&grant_type=password&client_id=account"

# Should succeed if OFBiz user exists

# Test authentication on master realm  
curl -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=admin&password=admin123&grant_type=password&client_id=admin-cli"

# Should use default Keycloak authentication
```

## ⚡ **Best Practices for Runtime Configuration**

### **1. Use Specific Realm Lists:**
```yaml
✅ Good: "production-app,staging-app"
❌ Bad: "" (empty - allows all realms)
```

### **2. Never Include Master Realm:**
```yaml
✅ Good: "myapp-realm"  
❌ Bad: "master,myapp-realm"
```

### **3. Use Environment-Specific Configuration:**
```yaml
Development: "dev-realm"
Staging: "staging-realm"  
Production: "production-realm"
```

### **4. Monitor Configuration Changes:**
```bash
# Set up log monitoring for realm changes
grep "OFBiz.*realm" /var/log/keycloak/keycloak.log
```

## 🚨 **Troubleshooting**

### **Common Issues:**

#### **SPI Not Working on Target Realm:**
```bash
# Check enabled realms configuration
# Look for: "OFBiz provider not active for realm: target-realm"
# Solution: Add realm to enabledRealms list
```

#### **Master Realm Interference:**
```bash
# Look for: "OFBiz SPI active on master realm - not recommended"
# Solution: Remove master from enabledRealms or leave empty with warnings
```

#### **Configuration Not Taking Effect:**
```bash
# Check component is properly saved
# Restart Keycloak if using system properties
# Verify database connectivity
```

## 📝 **Configuration Templates**

### **Production Template:**
```yaml
Console Display Name: "Production OFBiz Users"
JDBC Driver Class: "org.postgresql.Driver"
JDBC URL: "jdbc:postgresql://prod-db:5432/ofbiz"
Database Username: "keycloak_readonly"
Database Password: "secure_password"
Validation Query: "SELECT 1"
Connection Pool Size: "20"
Enabled Realms: "production-app"
```

### **Development Template:**
```yaml
Console Display Name: "Dev OFBiz Users"
JDBC Driver Class: "com.mysql.cj.jdbc.Driver"  
JDBC URL: "jdbc:mysql://localhost:3306/ofbiz"
Database Username: "ofbiz"
Database Password: "ofbiz"
Validation Query: "SELECT 1"
Connection Pool Size: "5"
Enabled Realms: "dev-realm"
```

This approach gives you complete control over which realms use OFBiz authentication while keeping the master realm and other realms safe with default Keycloak authentication! 🎯
