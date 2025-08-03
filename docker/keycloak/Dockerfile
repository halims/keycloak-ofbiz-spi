FROM bitnami/keycloak:26.3.2

# Copy the SPI JAR
COPY target/keycloak-ofbiz-spi-0.0.1.jar /opt/bitnami/keycloak/providers/keycloak-ofbiz-spi.jar

# Build Keycloak with the new provider
RUN /opt/bitnami/keycloak/bin/kc.sh build

# Set the entrypoint
ENTRYPOINT ["/opt/bitnami/keycloak/bin/kc.sh"]
