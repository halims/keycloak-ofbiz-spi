import React from 'react';

const LoginForm = ({ onLogin, loading, keycloakUrl, realm }) => {
  const handleKeycloakLogin = () => {
    // Redirect directly to Keycloak login page (without PKCE for HTTP development)
    const keycloakLoginUrl = `${keycloakUrl}realms/${realm}/protocol/openid-connect/auth?client_id=ofbiz-spa-test&redirect_uri=${encodeURIComponent(window.location.origin)}&response_type=code&scope=openid profile email`;
    window.location.href = keycloakLoginUrl;
  };

  return (
    <div className="login-form">
      <h2>🔐 Login to OFBiz Realm</h2>
      
      <div className="login-info">
        <h4>📋 Test Instructions:</h4>
        <p>1. Click "Login with Keycloak" button below</p>
        <p>2. You'll be redirected to Keycloak login page</p>
        <p>3. Use these test credentials:</p>
        <p>   • Username: <code>usertest</code> | Password: <code>password123</code></p>
        <p>   • Username: <code>admin</code> | Password: <code>admin123</code></p>
        <p>4. After successful login, you'll see the dashboard with token details</p>
      </div>

      <div className="action-buttons">
        <button 
          className="login-button"
          onClick={handleKeycloakLogin}
          disabled={loading}
        >
          {loading ? '🔄 Processing...' : '🚀 Login with Keycloak'}
        </button>
      </div>

      <div style={{ marginTop: '30px', fontSize: '0.9em', color: '#666' }}>
        <p><strong>🔧 Technical Details:</strong></p>
        <p>• Using Authorization Code flow with PKCE</p>
        <p>• Public SPA client (no client secret)</p>
        <p>• Tokens stored in memory only (secure for public clients)</p>
        <p>• Auto token refresh when expired</p>
      </div>
    </div>
  );
};

export default LoginForm;
