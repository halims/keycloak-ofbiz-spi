# Copilot Instructions for Keycloak OFBiz SPI Project

<!-- Use this file to provide workspace-specific custom instructions to Copilot. For more details, visit https://code.visualstudio.com/docs/copilot/copilot-customization#_use-a-githubcopilotinstructionsmd-file -->

## Project Overview
This project implements a Keycloak Service Provider Interface (SPI) that integrates Keycloak v26.3.2 with Apache OFBiz v24.09.01 as the backing user store. The SPI allows Keycloak to authenticate users against the OFBiz database while other systems can use Keycloak as an OIDC provider.

## Key Technologies
- Java 11+
- Keycloak 26.x SPI framework
- Apache OFBiz 24.09.01 database schema
- HikariCP for database connection pooling
- Maven for build management
- JUnit 5 for testing

## Architecture
- `OFBizUserStorageProvider`: Main provider that implements user lookup and credential validation
- `OFBizUserStorageProviderFactory`: Factory for creating provider instances with configuration
- `OFBizUserAdapter`: Adapter that maps OFBiz user data to Keycloak user model
- `OFBizConnectionProvider`: Database connection management using HikariCP
- `OFBizPasswordUtil`: Utility for handling OFBiz password hashing and verification

## OFBiz Database Schema
The provider works with the following OFBiz tables:
- `user_login`: Main user authentication table
- `person`: User personal information
- `contact_mech`: Contact information including email addresses

## Password Handling
OFBiz typically uses SHA-1 hashing with salt in the format: `{hashType}salt$hash`
The `OFBizPasswordUtil` class handles password verification and hashing.

## Configuration
The SPI is configured through Keycloak admin console with:
- Database connection details (JDBC URL, credentials)
- Connection pool settings
- Validation queries

## Development Guidelines
1. Always handle SQL exceptions properly with logging
2. Use prepared statements to prevent SQL injection
3. Follow Keycloak SPI best practices for user storage providers
4. Maintain compatibility with OFBiz database schema
5. Write unit tests for password utilities and core functionality
6. Use SLF4J for logging with appropriate log levels

## Deployment
The compiled JAR should be placed in Keycloak's `providers` directory and the provider configured through the admin console under User Federation.
