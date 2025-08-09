import React, { useState, useEffect } from 'react';
import Keycloak from 'keycloak-js';
import { keycloakConfig, keycloakInitOptions } from './keycloak-config';
import LoginForm from './components/LoginForm';
import Dashboard from './components/Dashboard';
import LoadingSpinner from './components/LoadingSpinner';
import directPkceTest from './pkce-test';
import './App.css';

function App() {
  const [keycloak, setKeycloak] = useState(null);
  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [userInfo, setUserInfo] = useState(null);
  const [tokenInfo, setTokenInfo] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    initializeKeycloak();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const initializeKeycloak = async () => {
    try {
      // Enhanced URL cleanup for callback handling
      const currentUrl = new URL(window.location);
      console.log('🔗 Current URL:', currentUrl.toString());
      
      // Check if this is a callback from Keycloak (has code parameter)
      const isCallback = currentUrl.searchParams.has('code') || currentUrl.searchParams.has('error');
      if (isCallback) {
        console.log('🔄 Detected Keycloak callback, processing...');
        console.log('📝 URL parameters:', Array.from(currentUrl.searchParams.entries()));
      }
      
      // Clear any error parameters from URL that might interfere with PKCE
      const hasErrorParams = currentUrl.searchParams.has('error') || currentUrl.hash.includes('error');
      if (hasErrorParams) {
        console.log('🧹 Clearing error parameters from URL for fresh authentication attempt');
        currentUrl.searchParams.delete('error');
        currentUrl.searchParams.delete('error_description');
        currentUrl.searchParams.delete('state');
        // Clear hash fragments that might contain error info
        if (currentUrl.hash.includes('error')) {
          currentUrl.hash = '';
        }
        window.history.replaceState({}, '', currentUrl.toString());
      }

      console.log('🔧 Initializing Keycloak with config:', keycloakConfig);
      const keycloakInstance = new Keycloak(keycloakConfig);
      
      keycloakInstance.onAuthSuccess = () => {
        console.log('✅ Authentication successful');
        handleAuthSuccess(keycloakInstance);
      };

      keycloakInstance.onAuthError = (error) => {
        console.error('❌ Authentication error:', error);
        setError(`Authentication error: ${error}`);
        setLoading(false);
      };

      keycloakInstance.onAuthLogout = () => {
        console.log('🚪 User logged out');
        handleLogout();
      };

      keycloakInstance.onTokenExpired = () => {
        console.log('⏰ Token expired, attempting refresh...');
        refreshToken(keycloakInstance);
      };

      // Suppress expected browser warnings for development
      const originalWarn = console.warn;
      console.warn = (...args) => {
        const message = args.join(' ');
        if (message.includes('requestStorageAccess') || 
            message.includes('3rd-party cookies') ||
            message.includes('blocking access to 3rd-party cookies') ||
            message.includes('KEYCLOAK] Your browser is blocking access')) {
          // These are expected warnings in development with modern browsers
          console.info('ℹ️ Browser security notice (expected in development):', message);
          return;
        }
        originalWarn.apply(console, args);
      };

      const authenticated = await keycloakInstance.init(keycloakInitOptions);
      
      // Restore original console.warn
      console.warn = originalWarn;
      
      setKeycloak(keycloakInstance);
      setAuthenticated(authenticated);
      
      if (authenticated) {
        await handleAuthSuccess(keycloakInstance);
      } else {
        setLoading(false);
      }
      
    } catch (error) {
      console.error('💥 Keycloak initialization failed:', error);
      
      // Provide helpful error messages for common issues
      let errorMessage = `Keycloak initialization failed: ${error.message}`;
      if (error.message.includes('Web Crypto API')) {
        errorMessage += '\n💡 Solution: Use HTTPS for both SPA and Keycloak in production.';
      }
      if (error.message.includes('3rd-party cookies') || error.message.includes('blocking access')) {
        errorMessage += '\n💡 This is a browser security feature. Authentication will work with redirects.';
      }
      
      setError(errorMessage);
      setLoading(false);
    }
  };

  const handleAuthSuccess = async (keycloakInstance) => {
    try {
      setAuthenticated(true);
      
      // Get user information
      const userProfile = await keycloakInstance.loadUserProfile();
      setUserInfo(userProfile);
      
      // Get token information
      const token = keycloakInstance.token;
      const refreshToken = keycloakInstance.refreshToken;
      const tokenParsed = keycloakInstance.tokenParsed;
      
      setTokenInfo({
        accessToken: token,
        refreshToken: refreshToken,
        tokenParsed: tokenParsed,
        timeSkew: keycloakInstance.timeSkew,
        isTokenExpired: keycloakInstance.isTokenExpired(),
        expires: new Date(tokenParsed.exp * 1000).toLocaleString()
      });
      
      console.log('👤 User Profile:', userProfile);
      console.log('🎫 Token Info:', tokenParsed);
      
    } catch (error) {
      console.error('Error loading user profile:', error);
      setError(`Error loading user profile: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const handleLogin = async (username, password) => {
    if (!keycloak) {
      setError('Keycloak not initialized');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      
      console.log('🔐 Attempting login for user:', username);
      
      // Debug PKCE capabilities
      console.log('🛠️ Keycloak instance capabilities:');
      console.log('  - PKCE Method:', keycloakInitOptions.pkceMethod);
      console.log('  - Web Crypto API available:', window.crypto && window.crypto.subtle);
      console.log('  - Current URL:', window.location.href);
      console.log('  - Redirect URI will be:', window.location.origin);
      
      // For public SPA, we use the Authorization Code flow
      // Redirect to Keycloak login page
      await keycloak.login({
        redirectUri: window.location.origin,
        // Explicitly set PKCE method again (redundant but for debugging)
        pkceMethod: 'S256'
      });
      
    } catch (error) {
      console.error('❌ Login failed:', error);
      setError(`Login failed: ${error.message}`);
      setLoading(false);
    }
  };

  const handleLogout = async () => {
    try {
      setLoading(true);
      console.log('🚪 Starting logout process...');
      
      if (keycloak) {
        const logoutUrl = keycloak.createLogoutUrl({
          redirectUri: window.location.origin
        });
        console.log('🔗 Logout URL:', logoutUrl);
        
        // Use window.location to ensure proper redirect
        window.location.href = logoutUrl;
        return; // Don't continue with local state cleanup yet
      }
      
      // Fallback if keycloak is not available
      setAuthenticated(false);
      setUserInfo(null);
      setTokenInfo(null);
      setError(null);
      
    } catch (error) {
      console.error('Logout error:', error);
      setError(`Logout error: ${error.message}`);
    } finally {
      setLoading(false);
    }
  };

  const refreshToken = async (keycloakInstance) => {
    try {
      const refreshed = await keycloakInstance.updateToken(30);
      if (refreshed) {
        console.log('✅ Token refreshed successfully');
        // Update token info
        setTokenInfo(prev => ({
          ...prev,
          accessToken: keycloakInstance.token,
          refreshToken: keycloakInstance.refreshToken,
          tokenParsed: keycloakInstance.tokenParsed,
          isTokenExpired: keycloakInstance.isTokenExpired(),
          expires: new Date(keycloakInstance.tokenParsed.exp * 1000).toLocaleString()
        }));
      }
    } catch (error) {
      console.error('❌ Token refresh failed:', error);
      setError('Session expired. Please login again.');
      handleLogout();
    }
  };

  const testOFBizAPI = async () => {
    if (!keycloak || !keycloak.token) {
      setError('No access token available');
      return;
    }

    try {
      // Test calling OFBiz API with the access token
      const response = await fetch('http://ofbiz.local:8080/rest/user/info', {
        method: 'GET',
        headers: {
          'Authorization': `Bearer ${keycloak.token}`,
          'Content-Type': 'application/json'
        }
      });

      if (response.ok) {
        const data = await response.json();
        console.log('✅ OFBiz API call successful:', data);
        alert('OFBiz API call successful! Check console for details.');
      } else {
        console.error('❌ OFBiz API call failed:', response.status, response.statusText);
        alert(`OFBiz API call failed: ${response.status} ${response.statusText}`);
      }
    } catch (error) {
      console.error('💥 OFBiz API call error:', error);
      alert(`OFBiz API call error: ${error.message}`);
    }
  };

  if (loading) {
    return <LoadingSpinner message="Initializing Keycloak..." />;
  }

  return (
    <div className="App">
      <div className="container">
        <header className="app-header">
          <h1>🔐 Keycloak OFBiz SPA Test</h1>
          <p>Testing authentication against OFBiz realm</p>
          
          {/* Modern Browser Security Notice */}
          <div className="browser-notice">
            <h3>🛡️ Browser Security Notice</h3>
            <p>Modern browsers restrict 3rd-party cookies for security. This means:</p>
            <ul>
              <li>✅ Login/logout works normally with redirects</li>
              <li>⚠️ Silent token refresh requires user interaction</li>
              <li>⚠️ Session monitoring across tabs is limited</li>
            </ul>
            <p><strong>This is normal and secure behavior!</strong></p>
          </div>
        </header>

        {error && (
          <div className="error-banner">
            <h3>❌ Error</h3>
            <p>{error}</p>
            <button onClick={() => setError(null)}>Dismiss</button>
          </div>
        )}

        {!authenticated ? (
          <>
            <LoginForm 
              onLogin={handleLogin} 
              loading={loading}
              keycloakUrl={keycloakConfig.url}
              realm={keycloakConfig.realm}
            />
            
            {/* Debug PKCE Test Button */}
            <div className="debug-section">
              <h3>🔧 Debug Tools</h3>
              <button 
                onClick={directPkceTest}
                className="debug-button"
                style={{
                  background: '#ff6b35',
                  color: 'white',
                  border: 'none',
                  padding: '10px 20px',
                  borderRadius: '5px',
                  cursor: 'pointer',
                  margin: '10px 0'
                }}
              >
                🧪 Run Direct PKCE Test
              </button>
              <p style={{ fontSize: '0.9em', color: '#666' }}>
                Click to test PKCE directly and check browser console for results
              </p>
            </div>
          </>
        ) : (
          <Dashboard 
            userInfo={userInfo}
            tokenInfo={tokenInfo}
            onLogout={handleLogout}
            onTestAPI={testOFBizAPI}
            onRefreshToken={() => refreshToken(keycloak)}
          />
        )}

        <footer className="app-footer">
          <p>
            🏗️ Built with React + Keycloak JS | 
            🌐 Keycloak: <code>{keycloakConfig.url}</code> | 
            🏰 Realm: <code>{keycloakConfig.realm}</code> |
            📱 Client: <code>{keycloakConfig.clientId}</code>
          </p>
        </footer>
      </div>
    </div>
  );
}

export default App;
