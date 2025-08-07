#!/usr/bin/env python3
"""
Mock OFBiz REST API for testing Keycloak SPI
This creates a simple HTTP server that mimics OFBiz REST endpoints
"""

import json
import base64
import time
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
    
    def do_GET(self):
        """Handle GET requests"""
        path = urlparse(self.path).path
        
        print(f"[MOCK] 📤 GET {path}")
        
        if path == '/users' or path == '/rest/users':
            self._handle_list_users()
        elif path == '/health' or path == '/rest/health':
            self._send_response(200, {'status': 'OK', 'message': 'Mock OFBiz is running'})
        else:
            self._send_response(404, {
                'error': 'Not Found',
                'message': f'GET endpoint {path} not found'
            })
    
    def _handle_list_users(self):
        """List all users (for debugging)"""
        users_data = []
        for username, user in self.USERS.items():
            user_copy = user.copy()
            user_copy.pop('password', None)  # Don't expose passwords
            users_data.append(user_copy)
        
        self._send_response(200, {
            'success': True,
            'total': len(users_data),
            'users': users_data
        })
    
    def do_POST(self):
        """Handle POST requests"""
        path = urlparse(self.path).path
        
        print(f"[MOCK] 📥 POST {path}")
        print(f"[MOCK] Headers: {dict(self.headers)}")
        
        if path == '/rest/auth/token':
            self._handle_auth_token()
        elif path == '/rest/services/getUserInfo':
            self._handle_get_user_info()
        elif path == '/rest/services/createUser':
            self._handle_create_user()
        elif path == '/rest/services/createPartyGroup':
            self._handle_create_tenant()
        else:
            print(f"[MOCK] ❌ Unknown endpoint: {path}")
            self._send_response(404, {
                'error': 'Not Found',
                'message': f'Endpoint {path} not found'
            })
    
    def _handle_create_user(self):
        """Handle user creation request"""
        print(f"[MOCK] 🔨 USER CREATION REQUEST")
        
        post_data = self._get_post_data()
        print(f"[MOCK] Request data: {json.dumps(post_data, indent=2)}")
        
        # Extract user data
        username = post_data.get('userLoginId')
        first_name = post_data.get('firstName')
        last_name = post_data.get('lastName')
        email = post_data.get('emailAddress')
        password = post_data.get('userPassword', 'defaultPassword123')
        tenant_id = post_data.get('tenantId', 'default')
        
        if not username:
            print(f"[MOCK] ❌ Missing userLoginId")
            self._send_response(400, {
                'error': 'Bad Request',
                'message': 'userLoginId is required'
            })
            return
        
        # Check if user already exists
        if username in self.USERS:
            print(f"[MOCK] ⚠️  User '{username}' already exists")
            self._send_response(409, {
                'error': 'Conflict',
                'message': f'User {username} already exists'
            })
            return
        
        # Create the user
        new_user = {
            'password': password,
            'userLoginId': username,
            'firstName': first_name or username,
            'lastName': last_name or 'User',
            'email': email or f'{username}@example.com',
            'tenantId': tenant_id,
            'enabled': True,
            'createdByKeycloak': True,
            'createdAt': str(int(time.time() * 1000))  # Timestamp in milliseconds
        }
        
        self.USERS[username] = new_user
        
        print(f"[MOCK] ✅ USER CREATED: {username}")
        print(f"[MOCK] User details: {json.dumps(new_user, indent=2)}")
        
        self._send_response(201, {
            'success': True,
            'message': f'User {username} created successfully',
            'data': {
                'userLoginId': username,
                'firstName': new_user['firstName'],
                'lastName': new_user['lastName'],
                'email': new_user['email'],
                'tenantId': new_user['tenantId']
            }
        })
    
    def _handle_create_tenant(self):
        """Handle tenant/organization creation request"""
        print(f"[MOCK] 🏢 TENANT CREATION REQUEST")
        
        post_data = self._get_post_data()
        print(f"[MOCK] Request data: {json.dumps(post_data, indent=2)}")
        
        tenant_id = post_data.get('partyId')
        tenant_name = post_data.get('groupName')
        
        if not tenant_id:
            print(f"[MOCK] ❌ Missing partyId")
            self._send_response(400, {
                'error': 'Bad Request',
                'message': 'partyId is required'
            })
            return
        
        print(f"[MOCK] ✅ TENANT CREATED: {tenant_id} - {tenant_name}")
        
        self._send_response(201, {
            'success': True,
            'message': f'Tenant {tenant_id} created successfully',
            'data': {
                'partyId': tenant_id,
                'groupName': tenant_name or f'{tenant_id} Organization',
                'partyTypeId': 'PARTY_GROUP'
            }
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
