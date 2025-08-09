// This file is used to configure the webpack dev server proxy
// It helps resolve the WebSocket connection issues when using nginx proxy

module.exports = function(app) {
  console.log('setupProxy.js loaded - Running behind nginx HTTPS proxy');
  console.log('WebSocket connections will be handled by nginx at https://spa.local:3443');
  
  // Disable hot reloading WebSocket to prevent mixed content errors
  if (process.env.NODE_ENV === 'development') {
    console.log('Development mode: Configuring for HTTPS proxy environment');
    
    // Intercept WebSocket upgrade requests and handle them appropriately
    app.use('/ws', (req, res, next) => {
      if (req.headers.upgrade && req.headers.upgrade.toLowerCase() === 'websocket') {
        // Log the attempt but don't process it to prevent mixed content errors
        console.log('WebSocket upgrade request intercepted - preventing mixed content error');
        res.status(426).send('Upgrade Required - Use WSS');
        return;
      }
      next();
    });
  }
};
