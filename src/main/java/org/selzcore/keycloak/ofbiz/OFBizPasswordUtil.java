package org.selzcore.keycloak.ofbiz;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class for OFBiz password handling
 * 
 * OFBiz typically uses SHA-1 hashing with salt for password storage.
 * This class provides methods to verify passwords against OFBiz hashes.
 */
public class OFBizPasswordUtil {

    private static final Logger logger = LoggerFactory.getLogger(OFBizPasswordUtil.class);
    
    private static final String DEFAULT_HASH_TYPE = "SHA";
    private static final String SALT_SEPARATOR = "$";

    /**
     * Verifies a plain text password against an OFBiz stored password hash
     * 
     * @param plainPassword The plain text password to verify
     * @param storedPassword The stored password hash from OFBiz database
     * @return true if the password matches, false otherwise
     */
    public static boolean verifyPassword(String plainPassword, String storedPassword) {
        if (plainPassword == null || storedPassword == null) {
            return false;
        }

        try {
            // OFBiz password format: {hashType}salt$hash
            // Example: {SHA}somesalt$hashedpassword
            
            if (storedPassword.startsWith("{") && storedPassword.contains("}")) {
                // Extract hash type
                int endBrace = storedPassword.indexOf("}");
                String hashType = storedPassword.substring(1, endBrace);
                String saltAndHash = storedPassword.substring(endBrace + 1);
                
                if (saltAndHash.contains(SALT_SEPARATOR)) {
                    String[] parts = saltAndHash.split("\\" + SALT_SEPARATOR, 2);
                    String salt = parts[0];
                    String expectedHash = parts[1];
                    
                    String computedHash = hashPassword(plainPassword, salt, hashType);
                    return expectedHash.equals(computedHash);
                } else {
                    // No salt, just hash
                    String expectedHash = saltAndHash;
                    String computedHash = hashPassword(plainPassword, "", hashType);
                    return expectedHash.equals(computedHash);
                }
            } else {
                // Legacy format or plain text (not recommended)
                logger.warn("Password stored in legacy format or plain text");
                return plainPassword.equals(storedPassword);
            }
        } catch (Exception e) {
            logger.error("Error verifying password", e);
            return false;
        }
    }

    /**
     * Hashes a password using the specified algorithm and salt
     * 
     * @param password The plain text password
     * @param salt The salt to use
     * @param hashType The hash algorithm (SHA, SHA-1, SHA-256, etc.)
     * @return The hashed password
     */
    public static String hashPassword(String password, String salt, String hashType) {
        try {
            // Normalize hash type
            String algorithm = normalizeHashType(hashType);
            
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            
            // Combine password and salt
            String saltedPassword = password + salt;
            byte[] hashBytes = digest.digest(saltedPassword.getBytes("UTF-8"));
            
            // Convert to Base64 (OFBiz typically uses Base64 encoding)
            return Base64.getEncoder().encodeToString(hashBytes);
            
        } catch (Exception e) {
            logger.error("Error hashing password", e);
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    /**
     * Generates a new password hash for storing in OFBiz
     * 
     * @param plainPassword The plain text password
     * @param hashType The hash algorithm to use (optional, defaults to SHA)
     * @return The formatted password hash for OFBiz storage
     */
    public static String generatePasswordHash(String plainPassword, String hashType) {
        if (hashType == null || hashType.trim().isEmpty()) {
            hashType = DEFAULT_HASH_TYPE;
        }
        
        // Generate random salt
        String salt = generateSalt();
        
        // Hash the password
        String hash = hashPassword(plainPassword, salt, hashType);
        
        // Return in OFBiz format: {hashType}salt$hash
        return "{" + hashType + "}" + salt + SALT_SEPARATOR + hash;
    }

    /**
     * Generates a random salt for password hashing
     * 
     * @return A random salt string
     */
    private static String generateSalt() {
        SecureRandom random = new SecureRandom();
        byte[] saltBytes = new byte[16]; // 128-bit salt
        random.nextBytes(saltBytes);
        return Base64.getEncoder().encodeToString(saltBytes);
    }

    /**
     * Normalizes hash type names to standard Java algorithm names
     * 
     * @param hashType The hash type from OFBiz
     * @return The normalized algorithm name
     */
    private static String normalizeHashType(String hashType) {
        if (hashType == null) {
            return "SHA-1";
        }
        
        switch (hashType.toUpperCase()) {
            case "SHA":
                return "SHA-1";
            case "SHA1":
                return "SHA-1";
            case "SHA256":
                return "SHA-256";
            case "SHA512":
                return "SHA-512";
            case "MD5":
                return "MD5";
            default:
                logger.warn("Unknown hash type: {}, defaulting to SHA-1", hashType);
                return "SHA-1";
        }
    }

    /**
     * Validates if a hash type is supported
     * 
     * @param hashType The hash type to validate
     * @return true if supported, false otherwise
     */
    public static boolean isHashTypeSupported(String hashType) {
        try {
            String algorithm = normalizeHashType(hashType);
            MessageDigest.getInstance(algorithm);
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
