package org.selzcore.keycloak.ofbiz;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for OFBiz Password Utility
 */
class OFBizPasswordUtilTest {

    @Test
    @DisplayName("Should verify password with SHA hash and salt")
    void testVerifyPasswordWithSalt() {
        String plainPassword = "testpassword";
        String salt = "testsalt";
        String hashType = "SHA";
        
        // Generate hash
        String hash = OFBizPasswordUtil.hashPassword(plainPassword, salt, hashType);
        String storedPassword = "{" + hashType + "}" + salt + "$" + hash;
        
        // Verify password
        assertTrue(OFBizPasswordUtil.verifyPassword(plainPassword, storedPassword));
        assertFalse(OFBizPasswordUtil.verifyPassword("wrongpassword", storedPassword));
    }

    @Test
    @DisplayName("Should verify password without salt")
    void testVerifyPasswordWithoutSalt() {
        String plainPassword = "testpassword";
        String hashType = "SHA";
        
        // Generate hash without salt
        String hash = OFBizPasswordUtil.hashPassword(plainPassword, "", hashType);
        String storedPassword = "{" + hashType + "}" + hash;
        
        // Verify password
        assertTrue(OFBizPasswordUtil.verifyPassword(plainPassword, storedPassword));
        assertFalse(OFBizPasswordUtil.verifyPassword("wrongpassword", storedPassword));
    }

    @Test
    @DisplayName("Should generate password hash in OFBiz format")
    void testGeneratePasswordHash() {
        String plainPassword = "testpassword";
        String hashType = "SHA";
        
        String generatedHash = OFBizPasswordUtil.generatePasswordHash(plainPassword, hashType);
        
        // Verify format
        assertTrue(generatedHash.startsWith("{" + hashType + "}"));
        assertTrue(generatedHash.contains("$"));
        
        // Verify password can be verified against generated hash
        assertTrue(OFBizPasswordUtil.verifyPassword(plainPassword, generatedHash));
    }

    @Test
    @DisplayName("Should handle null and empty passwords")
    void testNullAndEmptyPasswords() {
        assertFalse(OFBizPasswordUtil.verifyPassword(null, "{SHA}salt$hash"));
        assertFalse(OFBizPasswordUtil.verifyPassword("password", null));
        assertFalse(OFBizPasswordUtil.verifyPassword("", "{SHA}salt$hash"));
    }

    @Test
    @DisplayName("Should support different hash types")
    void testDifferentHashTypes() {
        String plainPassword = "testpassword";
        
        assertTrue(OFBizPasswordUtil.isHashTypeSupported("SHA"));
        assertTrue(OFBizPasswordUtil.isHashTypeSupported("SHA1"));
        assertTrue(OFBizPasswordUtil.isHashTypeSupported("SHA256"));
        assertTrue(OFBizPasswordUtil.isHashTypeSupported("MD5"));
        
        // Test with different hash types
        String sha1Hash = OFBizPasswordUtil.generatePasswordHash(plainPassword, "SHA1");
        String sha256Hash = OFBizPasswordUtil.generatePasswordHash(plainPassword, "SHA256");
        
        assertTrue(OFBizPasswordUtil.verifyPassword(plainPassword, sha1Hash));
        assertTrue(OFBizPasswordUtil.verifyPassword(plainPassword, sha256Hash));
    }
}
