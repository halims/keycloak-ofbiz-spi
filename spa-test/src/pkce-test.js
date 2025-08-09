// Direct PKCE test - separate from main app
import Keycloak from 'keycloak-js';

const directPkceTest = async () => {
  console.log('🧪 Starting direct PKCE test...');
  
  // Test Web Crypto API
  const cryptoAvailable = window.crypto && window.crypto.subtle;
  console.log('🔐 Web Crypto API available:', cryptoAvailable);
  
  if (cryptoAvailable) {
    try {
      // Test crypto operations directly
      const encoder = new TextEncoder();
      const data = encoder.encode('test');
      const hashBuffer = await window.crypto.subtle.digest('SHA-256', data);
      console.log('✅ SHA-256 digest test successful, hash length:', hashBuffer.byteLength);
      
      // Test random value generation
      const randomValues = new Uint8Array(32);
      window.crypto.getRandomValues(randomValues);
      console.log('✅ Random value generation successful');
    } catch (error) {
      console.error('❌ Web Crypto API test failed:', error);
    }
  }
  
  // Validate Keycloak URL before creating instance
  const keycloakUrl = 'https://keycloak.local:8444/';
  console.log('🔗 Testing Keycloak URL:', keycloakUrl);
  
  try {
    // Test URL construction
    const testUrl = new URL(keycloakUrl);
    console.log('✅ URL construction successful:', testUrl.toString());
  } catch (urlError) {
    console.error('❌ Invalid Keycloak URL:', urlError);
    return;
  }
  
  // Test Keycloak PKCE manually
  const keycloakConfig = {
    url: keycloakUrl,
    realm: 'ofbiz',
    clientId: 'ofbiz-spa-test'
  };
  
  console.log('🔧 Creating Keycloak instance with config:', keycloakConfig);
  
  let keycloak;
  try {
    keycloak = new Keycloak(keycloakConfig);
    console.log('✅ Keycloak instance created successfully');
  } catch (constructorError) {
    console.error('❌ Keycloak constructor failed:', constructorError);
    return;
  }
  
  console.log('📊 Keycloak capabilities:', {
    'PKCE supported': typeof keycloak.createLoginUrl === 'function',
    'Version': keycloak.constructor.version || 'unknown',
    'Adapter': keycloak.adapter || 'unknown'
  });
  
  // Try to initialize with minimal options
  try {
    console.log('🚀 Initializing Keycloak...');
    const initialized = await keycloak.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      enableLogging: true,
      adapter: 'default',
      responseMode: 'query' // Use query mode for PKCE
    });
    
    console.log('✅ Keycloak initialized:', initialized);
    console.log('🔑 User authenticated:', keycloak.authenticated);
    
    if (!keycloak.authenticated) {
      console.log('🚀 Attempting to create login URL...');
      try {
        // Generate login URL manually to see PKCE parameters
        const loginUrl = keycloak.createLoginUrl({
          redirectUri: window.location.origin
        });
        console.log('🔗 Login URL created successfully');
        console.log('📝 Login URL:', loginUrl);
        
        // Check if URL contains PKCE parameters
        const url = new URL(loginUrl);
        const codeChallenge = url.searchParams.get('code_challenge');
        const codeChallengeMethod = url.searchParams.get('code_challenge_method');
        
        console.log('🔍 PKCE Parameters in URL:');
        console.log('  code_challenge:', codeChallenge ? '✅ Present' : '❌ Missing');
        console.log('  code_challenge_method:', codeChallengeMethod ? `✅ ${codeChallengeMethod}` : '❌ Missing');
        
        if (!codeChallenge || !codeChallengeMethod) {
          console.error('❌ PKCE parameters missing from login URL!');
          console.log('🔧 Full URL parameters:', Array.from(url.searchParams.entries()));
        } else {
          console.log('🎉 PKCE is working correctly!');
        }
      } catch (loginUrlError) {
        console.error('❌ Failed to create login URL:', loginUrlError);
      }
    }
    
  } catch (error) {
    console.error('❌ Keycloak initialization failed:', error);
    console.error('📋 Error details:', {
      name: error.name,
      message: error.message,
      stack: error.stack
    });
  }
};

// Export for manual testing in browser console
window.directPkceTest = directPkceTest;

export default directPkceTest;
