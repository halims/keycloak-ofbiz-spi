-- Sample OFBiz user data for testing Keycloak SPI
-- This script creates basic OFBiz tables and sample users

-- Create user_login table
CREATE TABLE IF NOT EXISTS user_login (
    user_login_id VARCHAR(255) NOT NULL PRIMARY KEY,
    current_password VARCHAR(255),
    password_hint VARCHAR(255),
    is_system CHAR(1) DEFAULT 'N',
    enabled CHAR(1) DEFAULT 'Y',
    has_logged_out CHAR(1) DEFAULT 'N',
    require_password_change CHAR(1) DEFAULT 'N',
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    party_id VARCHAR(20),
    successful_logins BIGINT DEFAULT 0,
    disabled_date_time DATETIME,
    successive_failed_logins BIGINT DEFAULT 0
);

-- Create person table
CREATE TABLE IF NOT EXISTS person (
    party_id VARCHAR(20) NOT NULL PRIMARY KEY,
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
    height DECIMAL(18,6),
    weight DECIMAL(18,6),
    mothers_maiden_name VARCHAR(255),
    old_marital_status CHAR(1),
    employment_status_enum_id VARCHAR(20),
    residence_status_enum_id VARCHAR(20),
    occupation VARCHAR(100),
    years_with_employer BIGINT,
    months_with_employer BIGINT,
    existing_customer CHAR(1),
    card_id VARCHAR(20),
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create contact_mech table
CREATE TABLE IF NOT EXISTS contact_mech (
    contact_mech_id VARCHAR(20) NOT NULL PRIMARY KEY,
    contact_mech_type_id VARCHAR(20),
    info_string VARCHAR(255),
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create party_contact_mech table to link parties with contact mechanisms
CREATE TABLE IF NOT EXISTS party_contact_mech (
    party_id VARCHAR(20) NOT NULL,
    contact_mech_id VARCHAR(20) NOT NULL,
    from_date DATETIME NOT NULL,
    thru_date DATETIME,
    role_type_id VARCHAR(20),
    allow_solicitation CHAR(1) DEFAULT 'Y',
    extension VARCHAR(10),
    verified CHAR(1) DEFAULT 'N',
    comments VARCHAR(255),
    years_with_contact_mech BIGINT,
    months_with_contact_mech BIGINT,
    last_updated_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_updated_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_tx_stamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (party_id, contact_mech_id, from_date)
);

-- Insert sample users
-- Password is 'password' hashed with SHA and salt 'salt123'
-- {SHA}salt123$W6ph5Mm5Pz8GgiULbPgzG37mj9g=

-- Admin user
INSERT INTO person (party_id, first_name, last_name, personal_title) VALUES 
('ADMIN_USER', 'System', 'Administrator', 'Mr.');

INSERT INTO user_login (user_login_id, current_password, enabled, party_id) VALUES 
('admin', '{SHA}salt123$W6ph5Mm5Pz8GgiULbPgzG37mj9g=', 'Y', 'ADMIN_USER');

-- Test user 1
INSERT INTO person (party_id, first_name, last_name, personal_title) VALUES 
('TEST_USER_1', 'John', 'Doe', 'Mr.');

INSERT INTO user_login (user_login_id, current_password, enabled, party_id) VALUES 
('john.doe', '{SHA}salt123$W6ph5Mm5Pz8GgiULbPgzG37mj9g=', 'Y', 'TEST_USER_1');

-- Add email for John Doe
INSERT INTO contact_mech (contact_mech_id, contact_mech_type_id, info_string) VALUES 
('EMAIL_001', 'EMAIL_ADDRESS', 'john.doe@example.com');

INSERT INTO party_contact_mech (party_id, contact_mech_id, from_date, role_type_id) VALUES 
('TEST_USER_1', 'EMAIL_001', NOW(), 'PRIMARY_EMAIL');

-- Test user 2
INSERT INTO person (party_id, first_name, last_name, personal_title) VALUES 
('TEST_USER_2', 'Jane', 'Smith', 'Ms.');

INSERT INTO user_login (user_login_id, current_password, enabled, party_id) VALUES 
('jane.smith', '{SHA}salt123$W6ph5Mm5Pz8GgiULbPgzG37mj9g=', 'Y', 'TEST_USER_2');

-- Add email for Jane Smith
INSERT INTO contact_mech (contact_mech_id, contact_mech_type_id, info_string) VALUES 
('EMAIL_002', 'EMAIL_ADDRESS', 'jane.smith@example.com');

INSERT INTO party_contact_mech (party_id, contact_mech_id, from_date, role_type_id) VALUES 
('TEST_USER_2', 'EMAIL_002', NOW(), 'PRIMARY_EMAIL');

-- Disabled user for testing
INSERT INTO person (party_id, first_name, last_name, personal_title) VALUES 
('DISABLED_USER', 'Disabled', 'User', 'Mr.');

INSERT INTO user_login (user_login_id, current_password, enabled, party_id) VALUES 
('disabled.user', '{SHA}salt123$W6ph5Mm5Pz8GgiULbPgzG37mj9g=', 'N', 'DISABLED_USER');

-- Create Keycloak database for Keycloak itself
CREATE DATABASE IF NOT EXISTS keycloak;

-- Display sample users
SELECT 'Sample users created:' as message;
SELECT ul.user_login_id, ul.enabled, p.first_name, p.last_name, cm.info_string as email
FROM user_login ul 
LEFT JOIN person p ON ul.party_id = p.party_id
LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL
LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id AND cm.contact_mech_type_id = 'EMAIL_ADDRESS'
ORDER BY ul.user_login_id;
