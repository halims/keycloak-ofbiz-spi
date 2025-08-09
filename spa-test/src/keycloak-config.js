// Check Web Crypto API availability for PKCE
const isWebCryptoAvailable = () => {
  const available = window.crypto && window.crypto.subtle && typeof window.crypto.subtle.digest === 'function';
  console.log('🔐 Web Crypto API available:', available);
  if (!available) {
    console.warn('⚠️ Web Crypto API not available - PKCE may not work');
  }
  return available;
};

// Ensure Web Crypto API is available for PKCE
isWebCryptoAvailable();

// Keycloak configuration for OFBiz realm
const keycloakConfig = {
  url: 'https://keycloak.local:8444/',
  realm: 'ofbiz',
  clientId: 'ofbiz-spa-test'
};

// Keycloak initialization options for public SPA
// Note: Enhanced PKCE configuration for production build compatibility
const keycloakInitOptions = {
  onLoad: 'login-required', // Force login instead of silent check
  pkceMethod: 'S256', // Enable PKCE for HTTPS (Web Crypto API available)
  checkLoginIframe: false, // Disable iframe-based session checking (3rd-party cookie issue)
  flow: 'standard', // Authorization Code flow with PKCE for HTTPS
  // Force PKCE validation
  enableLogging: true,
  messageReceiveTimeout: 60000,
  // Disable all features that require 3rd-party cookies or iframes
  checkLoginIframeInterval: 0, // Completely disable iframe checking
  silentCheckSsoFallback: false, // Don't fall back to iframe methods
  // Use query mode for Authorization Code flow with PKCE (more standard)
  responseMode: 'query', // Use URL query parameters for auth code (PKCE standard)
  scope: 'openid profile email', // Explicit scope definition
  // Additional PKCE debug settings
  adapter: 'default', // Use default adapter
  redirectUri: window.location.origin // Explicit redirect URI
};

export { keycloakConfig, keycloakInitOptions };
