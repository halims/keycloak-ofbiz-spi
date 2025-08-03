-- PostgreSQL initialization script for OFBiz and Keycloak

-- Create keycloak database for Keycloak itself
CREATE DATABASE keycloak;

-- Grant permissions
GRANT ALL PRIVILEGES ON DATABASE ofbiz TO ofbiz;
GRANT ALL PRIVILEGES ON DATABASE keycloak TO ofbiz;

-- Connect to ofbiz database to create OFBiz tables
\c ofbiz;

-- Create OFBiz user_login table
CREATE TABLE IF NOT EXISTS user_login (
    user_login_id VARCHAR(255) PRIMARY KEY,
    current_password VARCHAR(255),
    password_hint VARCHAR(255),
    is_system CHAR(1) DEFAULT 'N',
    enabled CHAR(1) DEFAULT 'Y',
    has_logged_out CHAR(1) DEFAULT 'N',
    require_password_change CHAR(1) DEFAULT 'N',
    last_currency_uom VARCHAR(3),
    last_locale VARCHAR(10),
    last_time_zone VARCHAR(60),
    disabled_date_time TIMESTAMP,
    successive_failed_logins INTEGER DEFAULT 0,
    external_auth_id VARCHAR(255),
    user_ldap_dn VARCHAR(255),
    disabled_by VARCHAR(255),
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create person table for user details
CREATE TABLE IF NOT EXISTS person (
    party_id VARCHAR(20) PRIMARY KEY,
    salutation VARCHAR(100),
    first_name VARCHAR(100),
    middle_name VARCHAR(100),
    last_name VARCHAR(100),
    personal_title VARCHAR(100),
    suffix VARCHAR(100),
    nickname VARCHAR(100),
    first_name_local VARCHAR(100),
    middle_name_local VARCHAR(100),
    last_name_local VARCHAR(100),
    other_local VARCHAR(100),
    member_id VARCHAR(20),
    gender CHAR(1),
    birth_date DATE,
    deceased_date DATE,
    height FLOAT,
    weight FLOAT,
    mothers_maiden_name VARCHAR(255),
    old_marital_status CHAR(1),
    social_security_number VARCHAR(255),
    passport_number VARCHAR(255),
    passport_expire_date DATE,
    total_years_work_experience DECIMAL(20,0),
    comments TEXT,
    employment_status_enum_id VARCHAR(20),
    residence_status_enum_id VARCHAR(20),
    occupation VARCHAR(100),
    years_with_employer INTEGER,
    months_with_employer INTEGER,
    existing_customer CHAR(1),
    card_id VARCHAR(20),
    created_date TIMESTAMP,
    created_by_user_login VARCHAR(255),
    last_modified_date TIMESTAMP,
    last_modified_by_user_login VARCHAR(255),
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create contact_mech table for contact information
CREATE TABLE IF NOT EXISTS contact_mech (
    contact_mech_id VARCHAR(20) PRIMARY KEY,
    contact_mech_type_id VARCHAR(20),
    info_string VARCHAR(255),
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create party_contact_mech table to link parties with contact mechanisms
CREATE TABLE IF NOT EXISTS party_contact_mech (
    party_id VARCHAR(20),
    contact_mech_id VARCHAR(20),
    from_date TIMESTAMP,
    thru_date TIMESTAMP,
    role_type_id VARCHAR(20),
    allow_solicitation CHAR(1),
    extension VARCHAR(255),
    verified CHAR(1),
    comments TEXT,
    years_with_contact_mech INTEGER,
    months_with_contact_mech INTEGER,
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, contact_mech_id, from_date)
);

-- Insert sample test users with OFBiz-style password hashing
-- Password for all test users is "password123" with SHA-1 hash

INSERT INTO user_login (user_login_id, current_password, enabled) VALUES 
('admin', '{SHA}salt$356a192b7913b04c54574d18c28d46e6395428ab', 'Y'),
('testuser1', '{SHA}salt$da39a3ee5e6b4b0d3255bfef95601890afd80709', 'Y'),
('testuser2', '{SHA}salt$77de68daecd823babbb58edb1c8e14d7106e83bb', 'Y'),
('demouser', '{SHA}salt$da39a3ee5e6b4b0d3255bfef95601890afd80709', 'Y');

INSERT INTO person (party_id, first_name, last_name) VALUES 
('admin', 'Admin', 'User'),
('testuser1', 'Test', 'User One'),
('testuser2', 'Test', 'User Two'),
('demouser', 'Demo', 'User');

INSERT INTO contact_mech (contact_mech_id, contact_mech_type_id, info_string) VALUES 
('email_admin', 'EMAIL_ADDRESS', 'admin@ofbiz.local'),
('email_test1', 'EMAIL_ADDRESS', 'testuser1@ofbiz.local'),
('email_test2', 'EMAIL_ADDRESS', 'testuser2@ofbiz.local'),
('email_demo', 'EMAIL_ADDRESS', 'demouser@ofbiz.local');

INSERT INTO party_contact_mech (party_id, contact_mech_id, from_date) VALUES 
('admin', 'email_admin', CURRENT_TIMESTAMP),
('testuser1', 'email_test1', CURRENT_TIMESTAMP),
('testuser2', 'email_test2', CURRENT_TIMESTAMP),
('demouser', 'email_demo', CURRENT_TIMESTAMP);

-- Create indexes for better performance
CREATE INDEX IF NOT EXISTS idx_user_login_enabled ON user_login(enabled);
CREATE INDEX IF NOT EXISTS idx_person_name ON person(first_name, last_name);
CREATE INDEX IF NOT EXISTS idx_contact_mech_type ON contact_mech(contact_mech_type_id);
CREATE INDEX IF NOT EXISTS idx_party_contact_party ON party_contact_mech(party_id);

COMMIT;
