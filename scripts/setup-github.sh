#!/bin/bash

# GitHub Repository Setup Script
# This script helps you create and push to a GitHub repository

echo "🚀 Keycloak OFBiz SPI - GitHub Repository Setup"
echo "================================================"

# Check if gh CLI is available
if command -v gh &> /dev/null; then
    echo "✅ GitHub CLI (gh) is available"
    
    # Check if user is logged in
    if gh auth status &> /dev/null; then
        echo "✅ You are logged into GitHub CLI"
        
        echo ""
        echo "Creating GitHub repository..."
        
        # Create repository
        gh repo create keycloak-ofbiz-spi \
            --public \
            --description "Keycloak SPI for integrating with Apache OFBiz user management system" \
            --clone=false
        
        if [ $? -eq 0 ]; then
            echo "✅ Repository created successfully!"
            
            # Get GitHub username
            USERNAME=$(gh api user | jq -r .login)
            REPO_URL="https://github.com/$USERNAME/keycloak-ofbiz-spi.git"
            
            echo "🔗 Repository URL: https://github.com/$USERNAME/keycloak-ofbiz-spi"
            
            # Add remote origin
            git remote add origin $REPO_URL
            
            # Push to GitHub
            echo ""
            echo "Pushing code to GitHub..."
            git push -u origin main
            
            if [ $? -eq 0 ]; then
                echo "✅ Code pushed successfully!"
                echo ""
                echo "🎉 Repository setup complete!"
                echo ""
                echo "📦 JAR Download Links:"
                echo "===================="
                echo "Direct download: https://github.com/$USERNAME/keycloak-ofbiz-spi/raw/main/target/keycloak-ofbiz-spi-1.0.0-SNAPSHOT.jar"
                echo "Repository: https://github.com/$USERNAME/keycloak-ofbiz-spi"
                echo ""
                echo "📋 Next Steps:"
                echo "1. Visit your repository: https://github.com/$USERNAME/keycloak-ofbiz-spi"
                echo "2. Create a release tag to trigger automated builds"
                echo "3. Download the JAR from the releases section"
                echo ""
                echo "🏷️  To create a release:"
                echo "git tag -a v1.0.0 -m 'Release version 1.0.0'"
                echo "git push origin v1.0.0"
            else
                echo "❌ Failed to push code"
            fi
        else
            echo "❌ Failed to create repository"
        fi
    else
        echo "❌ You need to login to GitHub CLI first"
        echo "Run: gh auth login"
    fi
else
    echo "❌ GitHub CLI (gh) is not installed"
    echo ""
    echo "Manual Setup Instructions:"
    echo "========================="
    echo "1. Go to https://github.com/new"
    echo "2. Create a new repository named 'keycloak-ofbiz-spi'"
    echo "3. Make it public"
    echo "4. Don't initialize with README (we already have one)"
    echo "5. Copy the repository URL"
    echo "6. Run these commands:"
    echo ""
    echo "   git remote add origin <YOUR_REPO_URL>"
    echo "   git push -u origin main"
fi

echo ""
echo "📁 Project Files Ready:"
echo "======================"
echo "✅ Source code in src/"
echo "✅ Documentation (README.md, QUICKSTART.md)"
echo "✅ Docker environment (docker-compose.yml)"
echo "✅ Build scripts and CI/CD"
echo "✅ Compiled JAR in target/"
