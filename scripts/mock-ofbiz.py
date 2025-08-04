#!/usr/bin/env python3
"""
Mock OFBiz REST API for testing Keycloak SPI
This creates a simple HTTP server that mimics OFBiz REST endpoints
"""

import json
import base64
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs
import hashlib

class MockOFBizHandler(BaseHTTPRequestHandler):
    # Mock user database
    USERS = {
        'admin': {
            'password': 'ofbiz',  # Plain text for simplicity
            'userLoginId': 'admin',
            'firstName': 'THE',
            'lastName': 'ADMINISTRATOR',
            'email': 'ofbiztest@example.com',
            'tenantId': 'default',
            'enabled': True
        },
        'demo': {
            'password': 'demo',
            'userLoginId': 'demo',
            'firstName': 'Demo',
            'lastName': 'User',
            'email': 'demo@example.com',
            'tenantId': 'company',
            'enabled': True
        }
    }
    
    def _send_response(self, status_code, data):
        self.send_response(status_code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Authorization')
        self.end_headers()
        
        response = json.dumps(data, indent=2)
        self.wfile.write(response.encode('utf-8'))
        
        print(f"[MOCK] {self.command} {self.path} -> {status_code}")
        print(f"[MOCK] Response: {response}")
    
    def _get_basic_auth(self):
        """Extract username/password from Basic Auth header"""
        auth_header = self.headers.get('Authorization', '')
        if auth_header.startswith('Basic '):
            try:
                encoded = auth_header[6:]
                decoded = base64.b64decode(encoded).decode('utf-8')
                username, password = decoded.split(':', 1)
                return username, password
            except:
                return None, None
        return None, None
    
    def _get_post_data(self):
        """Get POST data as JSON"""
        try:
            content_length = int(self.headers['Content-Length'])
            post_data = self.rfile.read(content_length)
            return json.loads(post_data.decode('utf-8'))
        except:
            return {}
    
    def do_OPTIONS(self):
        """Handle CORS preflight requests"""
        self._send_response(200, {})
    
    def do_POST(self):
        """Handle POST requests"""
        path = urlparse(self.path).path
        
        if path == '/rest/auth/token':
            self._handle_auth_token()
        elif path == '/rest/services/getUserInfo':
            self._handle_get_user_info()
        else:
            self._send_response(404, {
                'error': 'Not Found',
                'message': f'Endpoint {path} not found'
            })
    
    def _handle_auth_token(self):
        """Handle authentication token request"""
        username, password = self._get_basic_auth()
        
        if not username or not password:
            self._send_response(401, {
                'error': 'Unauthorized',
                'message': 'Basic authentication required'
            })
            return
        
        user = self.USERS.get(username)
        if not user or user['password'] != password or not user['enabled']:
            self._send_response(401, {
                'error': 'Invalid credentials',
                'message': 'Username or password incorrect'
            })
            return
        
        # Generate a mock token (just base64 encoded user info)
        token_data = {
            'userLoginId': username,
            'exp': 9999999999  # Far future
        }
        token = base64.b64encode(json.dumps(token_data).encode()).decode()
        
        self._send_response(200, {
            'access_token': token,
            'token_type': 'Bearer',
            'expires_in': 3600
        })
    
    def _handle_get_user_info(self):
        """Handle get user info request"""
        post_data = self._get_post_data()
        username = post_data.get('userLoginId')
        
        if not username:
            self._send_response(400, {
                'error': 'Bad Request',
                'message': 'userLoginId parameter required'
            })
            return
        
        user = self.USERS.get(username)
        if not user or not user['enabled']:
            self._send_response(404, {
                'error': 'User not found',
                'message': f'User {username} not found or disabled'
            })
            return
        
        self._send_response(200, {
            'success': True,
            'data': {
                'userLoginId': user['userLoginId'],
                'firstName': user['firstName'],
                'lastName': user['lastName'],
                'email': user['email'],
                'tenantId': user['tenantId']
            }
        })
    
    def log_message(self, format, *args):
        """Override to customize logging"""
        print(f"[MOCK] {format % args}")

if __name__ == '__main__':
    PORT = 8081
    print(f"🚀 Starting Mock OFBiz REST API on port {PORT}")
    print("Available users:")
    for username, user in MockOFBizHandler.USERS.items():
        print(f"  - {username} / {user['password']} ({user['email']})")
    print()
    print("Endpoints:")
    print(f"  - POST http://localhost:{PORT}/rest/auth/token (Basic Auth)")
    print(f"  - POST http://localhost:{PORT}/rest/services/getUserInfo (JSON)")
    print()
    
    server = HTTPServer(('0.0.0.0', PORT), MockOFBizHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\n🛑 Shutting down mock server")
        server.server_close()
