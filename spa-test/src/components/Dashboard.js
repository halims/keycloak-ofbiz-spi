import React from 'react';

const Dashboard = ({ 
  userInfo, 
  tokenInfo, 
  onLogout, 
  onTestAPI, 
  onRefreshToken 
}) => {
  const formatTokenClaims = (tokenParsed) => {
    if (!tokenParsed) return 'No token data available';
    
    return JSON.stringify(tokenParsed, null, 2);
  };

  const getTokenStatus = () => {
    if (!tokenInfo) return { status: 'error', text: 'No Token' };
    
    if (tokenInfo.isTokenExpired) {
      return { status: 'expired', text: 'Expired' };
    }
    
    return { status: 'success', text: 'Valid' };
  };

  const tokenStatus = getTokenStatus();

  return (
    <div className="dashboard">
      <h2>✅ Login Successful - Dashboard</h2>
      
      {/* User Information Section */}
      <div className="user-info">
        <h3>👤 User Information</h3>
        <div className="info-grid">
          <div className="info-item">
            <strong>Username</strong>
            <span>{userInfo?.username || userInfo?.preferred_username || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Email</strong>
            <span>{userInfo?.email || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>First Name</strong>
            <span>{userInfo?.firstName || userInfo?.given_name || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Last Name</strong>
            <span>{userInfo?.lastName || userInfo?.family_name || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>User ID</strong>
            <span>{userInfo?.id || userInfo?.sub || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Email Verified</strong>
            <span>{userInfo?.emailVerified ? '✅ Yes' : '❌ No'}</span>
          </div>
        </div>
      </div>

      {/* Token Information Section */}
      <div className="token-section">
        <h3>
          🎫 Token Information 
          <span className={`status-indicator status-${tokenStatus.status}`}></span>
          {tokenStatus.text}
        </h3>
        <div className="info-grid">
          <div className="info-item">
            <strong>Token Type</strong>
            <span>Bearer</span>
          </div>
          <div className="info-item">
            <strong>Expires At</strong>
            <span>{tokenInfo?.expires || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Issuer</strong>
            <span>{tokenInfo?.tokenParsed?.iss || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Audience</strong>
            <span>{tokenInfo?.tokenParsed?.aud || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Subject</strong>
            <span>{tokenInfo?.tokenParsed?.sub || 'N/A'}</span>
          </div>
          <div className="info-item">
            <strong>Session State</strong>
            <span>{tokenInfo?.tokenParsed?.session_state || 'N/A'}</span>
          </div>
        </div>
        
        <h4 style={{ marginTop: '20px', marginBottom: '10px' }}>🔍 Full Token Claims:</h4>
        <div className="token-display">
          {formatTokenClaims(tokenInfo?.tokenParsed)}
        </div>
        
        <h4 style={{ marginTop: '20px', marginBottom: '10px' }}>🔑 Access Token (first 100 chars):</h4>
        <div className="token-display">
          {tokenInfo?.accessToken ? 
            `${tokenInfo.accessToken.substring(0, 100)}...` : 
            'No access token available'
          }
        </div>
      </div>

      {/* Authentication Details */}
      <div className="user-info">
        <h3>🔐 Authentication Details</h3>
        <div className="info-grid">
          <div className="info-item">
            <strong>Realm</strong>
            <span>{tokenInfo?.tokenParsed?.iss?.split('/').pop() || 'ofbiz'}</span>
          </div>
          <div className="info-item">
            <strong>Client ID</strong>
            <span>{tokenInfo?.tokenParsed?.azp || tokenInfo?.tokenParsed?.aud || 'ofbiz-spa-test'}</span>
          </div>
          <div className="info-item">
            <strong>Auth Time</strong>
            <span>{tokenInfo?.tokenParsed?.auth_time ? 
              new Date(tokenInfo.tokenParsed.auth_time * 1000).toLocaleString() : 'N/A'
            }</span>
          </div>
          <div className="info-item">
            <strong>Issued At</strong>
            <span>{tokenInfo?.tokenParsed?.iat ? 
              new Date(tokenInfo.tokenParsed.iat * 1000).toLocaleString() : 'N/A'
            }</span>
          </div>
          <div className="info-item">
            <strong>Scope</strong>
            <span>{tokenInfo?.tokenParsed?.scope || 'openid profile email'}</span>
          </div>
          <div className="info-item">
            <strong>Token Use</strong>
            <span>{tokenInfo?.tokenParsed?.typ || 'Bearer'}</span>
          </div>
        </div>
      </div>

      {/* Action Buttons */}
      <div className="action-buttons">
        <button 
          className="action-button test-button"
          onClick={onTestAPI}
        >
          🧪 Test OFBiz API
        </button>
        <button 
          className="action-button refresh-button"
          onClick={onRefreshToken}
        >
          🔄 Refresh Token
        </button>
        <button 
          className="action-button logout-button"
          onClick={onLogout}
        >
          🚪 Logout
        </button>
      </div>

      {/* Integration Status */}
      <div style={{ 
        marginTop: '30px', 
        padding: '20px', 
        background: '#e8f5e8', 
        borderRadius: '10px',
        textAlign: 'center'
      }}>
        <h4 style={{ color: '#155724', margin: '0 0 10px 0' }}>
          ✅ Keycloak OFBiz SPI Integration Status
        </h4>
        <p style={{ color: '#155724', margin: '5px 0', fontSize: '0.9em' }}>
          🎯 Successfully authenticated against OFBiz user store via Keycloak SPI
        </p>
        <p style={{ color: '#155724', margin: '5px 0', fontSize: '0.9em' }}>
          🔐 User credentials validated through OFBiz REST API
        </p>
        <p style={{ color: '#155724', margin: '5px 0', fontSize: '0.9em' }}>
          🎫 JWT tokens issued by Keycloak with OFBiz user claims
        </p>
      </div>
    </div>
  );
};

export default Dashboard;
