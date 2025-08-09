#!/bin/bash

# Custom start script for React without WebSocket client
# This completely disables the development server's WebSocket client

echo "🚀 Starting React SPA without WebSocket client for HTTPS environment..."

# Set environment variables to disable WebSocket
export FAST_REFRESH=false
export GENERATE_SOURCEMAP=false 
export BROWSER=none
export WDS_SOCKET_PROTOCOL=wss
export WDS_SOCKET_HOST=spa.local
export WDS_SOCKET_PORT=3443

# Use a custom webpack configuration that removes WebSocket client entirely
REACT_APP_NO_DEV_CLIENT=true \
SKIP_PREFLIGHT_CHECK=true \
DANGEROUSLY_DISABLE_HOST_CHECK=true \
HOST=0.0.0.0 \
PORT=3000 \
npm start -- --no-open
