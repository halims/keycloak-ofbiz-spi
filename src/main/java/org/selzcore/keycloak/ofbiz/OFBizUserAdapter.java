package org.selzcore.keycloak.ofbiz;

import org.keycloak.common.util.MultivaluedHashMap;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.storage.StorageId;
import org.keycloak.storage.adapter.AbstractUserAdapterFederatedStorage;

import java.util.List;
import java.util.Map;

/**
 * User adapter for OFBiz users in Keycloak
 */
public class OFBizUserAdapter extends AbstractUserAdapterFederatedStorage {

    private final String username;
    private final String firstName;
    private final String lastName;
    private final String email;
    private final boolean enabled;

    public OFBizUserAdapter(KeycloakSession session, RealmModel realm, ComponentModel model,
                           String username, String firstName, String lastName, String email, boolean enabled) {
        super(session, realm, model);
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.enabled = enabled;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void setUsername(String username) {
        // OFBiz usernames are typically not editable through Keycloak
        // This could be implemented to update the OFBiz database if needed
    }

    @Override
    public String getFirstName() {
        return firstName;
    }

    @Override
    public void setFirstName(String firstName) {
        // This could be implemented to update the OFBiz database if needed
        setSingleAttribute("firstName", firstName);
    }

    @Override
    public String getLastName() {
        return lastName;
    }

    @Override
    public void setLastName(String lastName) {
        // This could be implemented to update the OFBiz database if needed
        setSingleAttribute("lastName", lastName);
    }

    @Override
    public String getEmail() {
        return email;
    }

    @Override
    public void setEmail(String email) {
        // This could be implemented to update the OFBiz database if needed
        setSingleAttribute("email", email);
    }

    @Override
    public boolean isEmailVerified() {
        // OFBiz doesn't have built-in email verification
        // This could be customized based on your OFBiz setup
        return email != null && !email.trim().isEmpty();
    }

    @Override
    public void setEmailVerified(boolean verified) {
        setSingleAttribute("emailVerified", Boolean.toString(verified));
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        // This could be implemented to update the OFBiz database if needed
        setSingleAttribute("enabled", Boolean.toString(enabled));
    }

    @Override
    public String getId() {
        return StorageId.keycloakId(storageProviderModel, username);
    }

    @Override
    public Map<String, List<String>> getAttributes() {
        MultivaluedHashMap<String, String> attributes = new MultivaluedHashMap<>();
        attributes.add("username", getUsername());
        if (getFirstName() != null) {
            attributes.add("firstName", getFirstName());
        }
        if (getLastName() != null) {
            attributes.add("lastName", getLastName());
        }
        if (getEmail() != null) {
            attributes.add("email", getEmail());
        }
        attributes.add("enabled", Boolean.toString(isEnabled()));
        attributes.add("emailVerified", Boolean.toString(isEmailVerified()));
        
        // Add any additional attributes from federated storage
        Map<String, List<String>> federatedAttributes = super.getAttributes();
        if (federatedAttributes != null) {
            attributes.putAll(federatedAttributes);
        }
        
        return attributes;
    }
}
