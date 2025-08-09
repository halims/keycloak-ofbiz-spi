# Keycloak OFBiz SPA Test Application

This is a React Single Page Application (SPA) designed to test authentication against the Keycloak OFBiz realm.

## Features

- 🔐 **Secure Authentication**: Uses Authorization Code flow with PKCE for public clients
- 🎫 **Token Management**: Displays access token details and claims
- 🔄 **Auto Refresh**: Automatically refreshes tokens when they expire
- 🧪 **API Testing**: Built-in button to test OFBiz API calls with the access token
- 📱 **Responsive Design**: Works on desktop and mobile devices
- 🎨 **Modern UI**: Clean, gradient-based design with glassmorphism effects

## Quick Start

1. **Install Dependencies**:
   ```bash
   cd spa-test
   npm install
   ```

2. **Configure Keycloak Client**:
   - Log into Keycloak admin console at http://localhost:8090
   - Go to the `ofbiz` realm
   - Create a new client with ID: `ofbiz-spa-test`
   - Set Client Type: `OpenID Connect`
   - Set Client authentication: `Off` (public client)
   - Set Standard flow: `Enabled`
   - Set Valid redirect URIs: `http://localhost:3000/*` and `http://spa.local:3000/*`
   - Set Web origins: `http://localhost:3000` and `http://spa.local:3000`

3. **Start the Application**:
   ```bash
   npm start
   ```

4. **Test Authentication**:
   - Open http://spa.local:3000 (or http://localhost:3000)
   - Click "Login with Keycloak"
   - Use test credentials:
     - Username: `usertest` / Password: `password123`
     - Username: `admin` / Password: `admin123`

## Configuration

The application is configured in `src/keycloak-config.js`:

```javascript
const keycloakConfig = {
  url: 'http://localhost:8090/',
  realm: 'ofbiz',
  clientId: 'ofbiz-spa-test'
};
```

## Security Features

- **Authorization Code Flow**: Standard OAuth2/OIDC flow for public clients
- **No Client Secret**: Secure for public SPAs that can't store secrets
- **In-Memory Token Storage**: Tokens are never persisted to localStorage/sessionStorage
- **Auto Token Refresh**: Prevents expired token issues
- **Secure Logout**: Properly clears Keycloak session
- **Development Mode**: PKCE disabled for HTTP development (enable HTTPS for production)

## Testing OFBiz Integration

The dashboard includes a "Test OFBiz API" button that:
1. Uses the current access token
2. Makes a request to `http://ofbiz.local:8080/rest/user/info`
3. Shows success/failure in an alert
4. Logs full response details to browser console

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   React SPA     │    │    Keycloak      │    │     OFBiz       │
│  (Port 3000)    │    │   (Port 8090)    │    │  (Port 8080)    │
├─────────────────┤    ├──────────────────┤    ├─────────────────┤
│ • Login Form    │───▶│ • Authentication │───▶│ • User Store    │
│ • Dashboard     │    │ • Token Issuance │    │ • REST API      │
│ • Token Display │◀───│ • OFBiz SPI      │◀───│ • Password Val. │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

## Development

- **React**: 18.2.0+
- **Keycloak JS**: 26.0.0+
- **ES6+ Features**: Modern JavaScript
- **CSS Grid/Flexbox**: Responsive layout
- **No External CSS Frameworks**: Pure CSS implementation

## Troubleshooting

1. **CORS Issues**: Ensure Keycloak Web Origins includes `http://localhost:3000` and `http://spa.local:3000`
2. **Client Not Found**: Verify the client ID `ofbiz-spa-test` exists in the `ofbiz` realm
3. **Redirect Issues**: Check Valid Redirect URIs includes `http://localhost:3000/*` and `http://spa.local:3000/*`
5. **Invalid Host Header**: For custom domains like `spa.local`, the `.env` file configures the dev server
6. **Web Crypto API Error**: If you see "Web Crypto API is not available", this is resolved by disabling PKCE for HTTP development
7. **Token Issues**: Check browser console for detailed error messages
5. **OFBiz API Calls**: Ensure OFBiz is running and accessible at `http://ofbiz.local:8080`

## Production Deployment

For production deployment:

1. Update `keycloak-config.js` with production URLs
2. Build the application: `npm run build`
3. Serve the `build` folder with a web server
4. Update Keycloak client settings with production URLs
5. Ensure proper HTTPS configuration for security
