# 🎯 **Runtime Realm Configuration - Quick Summary**

## **The Key Question:** "How to configure activated realm on runtime?"

### **Answer: Multiple Ways!**

## 🔧 **Method 1: Keycloak Admin Console (Easiest)**

### **Visual Configuration Steps:**
1. **Login**: http://localhost:8080/admin/ (admin/admin)
2. **Create Realm**: Click realm dropdown → "Create Realm" → Enter `myapp-realm`
3. **User Federation**: Go to "User Federation" → "Add provider" → "ofbiz-user-storage"
4. **Key Setting**: Set **"Enabled Realms"** to `myapp-realm`
5. **Save**: Configuration takes effect immediately

### **Result:**
- ✅ `myapp-realm`: Uses OFBiz authentication
- ✅ `master`: Protected, uses default Keycloak auth
- ✅ Other realms: Use default Keycloak auth

---

## 🔧 **Method 2: Environment Variables (DevOps)**

### **Docker Compose:**
```yaml
keycloak:
  environment:
    KEYCLOAK_SPI_OFBIZ_ENABLED_REALMS: "myapp-realm,staging-realm"
    KEYCLOAK_SPI_OFBIZ_DB_URL: "jdbc:mysql://mysql:3306/ofbiz"
```

### **Kubernetes:**
```yaml
env:
- name: KEYCLOAK_SPI_OFBIZ_ENABLED_REALMS
  value: "production-realm"
```

---

## 🔧 **Method 3: REST API (Programmatic)**

### **Quick Script:**
```bash
# Get token
TOKEN=$(curl -s -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "username=admin&password=admin&grant_type=password&client_id=admin-cli" | jq -r '.access_token')

# Update configuration
curl -X PUT "http://localhost:8080/admin/realms/myapp-realm/components/{id}" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"config": {"enabledRealms": ["myapp-realm"]}}'
```

---

## 🔧 **Method 4: System Properties (Startup)**

### **JVM Arguments:**
```bash
$KEYCLOAK_HOME/bin/kc.sh start-dev \
  -Dspi.ofbiz.enabled.realms=myapp-realm,staging-realm
```

---

## 📊 **Configuration Scenarios**

| Scenario | Enabled Realms Setting | Result |
|----------|------------------------|---------|
| **Production** | `"production-app"` | ✅ Only production-app uses OFBiz |
| **Multi-Env** | `"staging,production"` | ✅ Both staging & production use OFBiz |
| **Development** | `""` (empty) | ⚠️ All realms use OFBiz (with warnings) |
| **Complete Isolation** | `"app-realm"` | ✅ Only app-realm, master protected |

---

## ⚡ **Runtime Changes (No Restart Required!)**

### **Admin Console Changes:**
1. Update "Enabled Realms" field
2. Click "Save"
3. ✅ **Takes effect immediately** on next authentication

### **API Changes:**
1. Call REST API to update component
2. ✅ **Changes propagate within seconds**

### **Environment Variables:**
1. Update env vars
2. Restart container/pod
3. ✅ **Safe for production updates**

---

## 🧪 **Quick Test Commands**

### **Verify Realm Isolation:**
```bash
# Check logs for realm activity
docker-compose logs keycloak | grep "realm:"

# Expected output:
# OFBiz provider not active for realm: master, skipping
# Getting user by username: testuser in realm: myapp-realm
```

### **Test Authentication:**
```bash
# Test enabled realm (should work with OFBiz users)
curl -X POST "http://localhost:8080/realms/myapp-realm/protocol/openid-connect/token" \
  -d "username=admin&password=ofbiz_password&grant_type=password&client_id=account"

# Test master realm (should work with Keycloak admin)
curl -X POST "http://localhost:8080/realms/master/protocol/openid-connect/token" \
  -d "username=admin&password=admin123&grant_type=password&client_id=admin-cli"
```

---

## 🚨 **Important Security Rules**

### **✅ DO:**
- Create dedicated application realms
- Set specific realm names in "Enabled Realms"
- Test configuration changes
- Monitor logs for warnings

### **❌ DON'T:**
- Configure SPI on master realm
- Leave "Enabled Realms" empty in production
- Skip testing after configuration changes
- Ignore warning logs

---

## 🎯 **Best Practice Example**

### **Production Setup:**
```yaml
# Realm: production-app
Console Display Name: "Production OFBiz Users"
JDBC URL: "jdbc:postgresql://prod-db:5432/ofbiz"
Database Username: "keycloak_readonly"
Database Password: "secure_password"
Enabled Realms: "production-app"  # ← This is the magic setting!
```

### **Result:**
- ✅ **production-app**: Authenticates against OFBiz database
- ✅ **master**: Completely isolated, uses Keycloak admin
- ✅ **dev-app**: Uses different authentication (if configured)
- ✅ **staging-app**: Uses different authentication (if configured)

---

## 🎉 **Summary**

The **"Enabled Realms"** setting is your **runtime control** for activating the OFBiz SPI:

1. **Set it per realm** in User Federation configuration
2. **Changes take effect immediately** (no restart needed)
3. **Protects master realm** automatically
4. **Isolates realms** from each other
5. **Supports multiple environments** with different configurations

**You now have complete runtime control over which realms use OFBiz authentication!** 🚀
