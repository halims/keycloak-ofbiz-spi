# Custom Tenant and Attributes Mapping Guide

This guide explains how to configure and use the enhanced OFBiz User Storage Provider with custom tenant mapping and user attributes.

## Overview

Version 0.0.2 introduces support for:
- **Tenant Mapping**: Automatically map OFBiz user data to tenant information in Keycloak
- **Custom Attributes**: Map any OFBiz database fields to Keycloak user attributes
- **Flexible Configuration**: Support for different mapping strategies and data sources

## Configuration Properties

### Tenant Attribute Name
- **Property**: `tenantAttribute`
- **Default**: `tenant`
- **Description**: Name of the user attribute to store tenant information
- **Example**: Set to `organization` to use `organization` attribute instead of `tenant`

### Custom Attribute Mappings
- **Property**: `customAttributes`
- **Format**: `attributeName:ofbizField,attributeName2:ofbizField2`
- **Description**: Comma-separated list of attribute mappings
- **Examples**:
  - `department:party_attribute.department,location:party_attribute.location`
  - `manager:person.supervisor_id,cost_center:party.description`

## Tenant Determination Logic

The SPI automatically determines the tenant for each user using the following priority:

1. **Party Group**: From `party_group_member` table → `party_group.group_id`
2. **Party Role**: From `party_role` table → `role_type_id`
3. **Party Type**: From `party` table → `party_type_id`
4. **Party ID**: Falls back to the user's `party_id`

### SQL Query Enhancement

The enhanced query includes tenant-related tables:

```sql
SELECT ul.user_login_id, ul.current_password, ul.enabled, ul.party_id,
       p.first_name, p.last_name, p.personal_title,
       cm.info_string as email,
       pr.role_type_id as tenant_role,
       pg.group_id as tenant_group,
       pt.party_type_id as party_type
FROM user_login ul
LEFT JOIN person p ON ul.party_id = p.party_id
LEFT JOIN party_contact_mech pcm ON p.party_id = pcm.party_id AND pcm.thru_date IS NULL
LEFT JOIN contact_mech cm ON pcm.contact_mech_id = cm.contact_mech_id
  AND cm.contact_mech_type_id = 'EMAIL_ADDRESS'
LEFT JOIN party_role pr ON ul.party_id = pr.party_id AND pr.thru_date IS NULL
LEFT JOIN party_group_member pgm ON ul.party_id = pgm.party_id AND pgm.thru_date IS NULL
LEFT JOIN party_group pg ON pgm.party_id_to = pg.party_id
LEFT JOIN party pt ON ul.party_id = pt.party_id
WHERE ul.user_login_id = ? AND ul.enabled = 'Y'
```

## Custom Attribute Field Mappings

### Supported Mapping Formats

#### 1. Party Attributes (`party_attribute.attr_name`)
Maps to the `party_attribute` table:
```
department:party_attribute.DEPARTMENT
cost_center:party_attribute.COST_CENTER
```

#### 2. Party Fields (`party.field_name`)
Maps to fields in the `party` table:
```
description:party.description
external_id:party.external_id
```

#### 3. Person Fields (`person.field_name`)
Maps to fields in the `person` table:
```
title:person.personal_title
middle_name:person.middle_name
suffix:person.suffix
```

## Configuration Examples

### Example 1: Basic Tenant Mapping
```
Tenant Attribute Name: organization
Custom Attribute Mappings: (empty)
```
Result: Users get an `organization` attribute with their tenant value.

### Example 2: Department and Location Mapping
```
Tenant Attribute Name: tenant
Custom Attribute Mappings: department:party_attribute.DEPARTMENT,location:party_attribute.LOCATION
```
Result: Users get `tenant`, `department`, and `location` attributes.

### Example 3: Comprehensive Mapping
```
Tenant Attribute Name: company
Custom Attribute Mappings: department:party_attribute.DEPT,manager:person.supervisor_id,title:person.personal_title,cost_center:party.description
```
Result: Users get `company`, `department`, `manager`, `title`, and `cost_center` attributes.

## Usage in Keycloak

### Accessing Tenant Information

Once configured, tenant information is available as user attributes in:

#### 1. Token Claims
Configure mappers in Keycloak to include tenant in JWT tokens:
- Go to Client → Mappers → Create
- Mapper Type: "User Attribute"
- User Attribute: `tenant` (or your configured attribute name)
- Token Claim Name: `tenant`

#### 2. User Profile
Tenant information appears in the user's profile attributes.

#### 3. Admin API
Access via REST API:
```bash
GET /admin/realms/{realm}/users/{user-id}
```

### Multi-Tenant Applications

Applications can use the tenant information for:
- **Data Isolation**: Filter data by tenant
- **Authorization**: Implement tenant-based access control
- **Branding**: Customize UI based on tenant
- **Routing**: Direct users to tenant-specific resources

## Logging and Debugging

The enhanced SPI provides detailed logging for tenant and attribute mapping:

```
DEBUG: Mapping user 'john.doe' with tenant info - Role: 'EMPLOYEE', Group: 'ACME_CORP', PartyType: 'PERSON'
TRACE: Using tenant group 'ACME_CORP' as tenant for party '12345'
DEBUG: Loading custom attributes for party '12345' with config: department:party_attribute.DEPT
TRACE: Mapped custom attribute 'department' = 'IT' for party '12345'
DEBUG: Final attributes for user 'john.doe': 8 attributes including 2 custom attributes
```

## OFBiz Database Requirements

### Required Tables
- `user_login` - Core user authentication
- `person` - User personal information
- `party` - Party management
- `party_contact_mech` - Contact information
- `contact_mech` - Email addresses

### Optional Tables (for enhanced tenant mapping)
- `party_role` - User roles for tenant determination
- `party_group_member` - Group membership for tenant determination
- `party_group` - Group information for tenant determination
- `party_attribute` - Custom attributes storage

### Sample OFBiz Data Structure

```sql
-- User with tenant group membership
INSERT INTO party_group_member (party_id, party_id_to, role_type_id, from_date)
VALUES ('user123', 'ACME_CORP', 'EMPLOYEE', NOW());

-- Custom attributes
INSERT INTO party_attribute (party_id, attr_name, attr_value, attr_description)
VALUES ('user123', 'DEPARTMENT', 'IT', 'User Department');

INSERT INTO party_attribute (party_id, attr_name, attr_value, attr_description)
VALUES ('user123', 'LOCATION', 'NYC', 'User Location');
```

## Security Considerations

### Attribute Filtering
- Only configure necessary attributes to minimize data exposure
- Use specific field mappings rather than broad access
- Regular review of mapped attributes

### Tenant Isolation
- Ensure tenant values cannot be manipulated by users
- Validate tenant assignments in your applications
- Use tenant information for authorization, not just identification

### Data Privacy
- Consider data protection regulations when mapping personal information
- Document what attributes are being synchronized
- Implement appropriate data retention policies

## Troubleshooting

### Common Issues

#### 1. Tenant Not Appearing
- Check if user has party_role, party_group_member, or party_type data
- Verify SQL query execution in logs (TRACE level)
- Confirm tenant attribute name configuration

#### 2. Custom Attributes Missing
- Verify field mapping syntax: `attributeName:table.field`
- Check if source fields exist in OFBiz database
- Enable TRACE logging to see attribute loading process

#### 3. Performance Issues
- Monitor database query performance with enhanced SQL
- Consider indexing on party_id fields in related tables
- Limit custom attribute mappings to essential fields

### Debug Commands

```bash
# Enable detailed logging
echo 'logger.org.selzcore.keycloak.ofbiz.level=TRACE' >> keycloak.conf

# Test specific user lookup
# Check Keycloak logs for detailed tenant mapping process
```

## Version History

- **v0.0.2**: Added tenant mapping and custom attributes support
- **v0.0.1**: Basic OFBiz authentication integration

## Future Enhancements

Planned features for future versions:
- Dynamic attribute loading from OFBiz services
- Cached attribute resolution for performance
- Group-based attribute inheritance
- Real-time attribute synchronization
