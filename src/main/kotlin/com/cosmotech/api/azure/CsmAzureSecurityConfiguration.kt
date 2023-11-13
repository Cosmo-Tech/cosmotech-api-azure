// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.spring.cloud.autoconfigure.implementation.aad.configuration.properties.AadAuthenticationProperties
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.constants.AadJwtClaimNames
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.jwt.AadJwtIssuerValidator
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.properties.AadAuthorizationServerEndpoints
import com.azure.spring.cloud.autoconfigure.implementation.aad.utils.AadRestTemplateCreator.createRestTemplate
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.AbstractSecurityConfiguration
import com.cosmotech.api.security.ROLE_ORGANIZATION_USER
import com.cosmotech.api.security.ROLE_ORGANIZATION_VIEWER
import com.cosmotech.api.security.ROLE_PLATFORM_ADMIN
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.client.RestTemplateBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter
import org.springframework.security.web.SecurityFilterChain
import org.springframework.util.StringUtils

@Configuration
@EnableWebSecurity(debug = true)
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "azure", matchIfMissing = true)
@EnableMethodSecurity(securedEnabled = true, prePostEnabled = true, proxyTargetClass = true)
internal open class CsmAzureSecurityConfiguration(
    private val csmPlatformProperties: CsmPlatformProperties,
) : AbstractSecurityConfiguration() {

  private val logger = LoggerFactory.getLogger(CsmAzureSecurityConfiguration::class.java)

  private val microsoftOnlineIssuer = "https://login.microsoftonline.com/"

  private val organizationAdminGroup =
      csmPlatformProperties.identityProvider?.adminGroup ?: ROLE_PLATFORM_ADMIN
  private val organizationUserGroup =
      csmPlatformProperties.identityProvider?.userGroup ?: ROLE_ORGANIZATION_USER
  private val organizationViewerGroup =
      csmPlatformProperties.identityProvider?.viewerGroup ?: ROLE_ORGANIZATION_VIEWER

  @Bean
  open fun filterChain(http: HttpSecurity): SecurityFilterChain? {
    logger.info("Azure Active Directory http security configuration")
    // See
    // https://learn.microsoft.com/en-us/azure/developer/java/spring-framework/spring-security-support?tabs=SpringCloudAzure5x
    val jwtAuthenticationConverter = JwtAuthenticationConverter()
    val jwtGrantedAuthoritiesConverter = JwtGrantedAuthoritiesConverter()

    csmPlatformProperties.azure?.claimToAuthorityPrefix?.get("roles").let {
      jwtGrantedAuthoritiesConverter.setAuthorityPrefix(it)
    }

    jwtGrantedAuthoritiesConverter.setAuthoritiesClaimName(
        csmPlatformProperties.authorization.rolesJwtClaim)
    jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter)

    super.getOAuth2ResourceServer(
            http, organizationAdminGroup, organizationUserGroup, organizationViewerGroup)
        .oauth2ResourceServer { oauth2 ->
          oauth2.jwt { jwt -> run { jwt.jwtAuthenticationConverter(jwtAuthenticationConverter) } }
        }

    return http.build()
  }

  @Bean
  open fun jwtDecoder(
      aadAuthenticationProperties: AadAuthenticationProperties,
      restTemplateBuilder: RestTemplateBuilder
  ): JwtDecoder {

    val identityEndpoints =
        AadAuthorizationServerEndpoints(
            aadAuthenticationProperties.appIdUri, aadAuthenticationProperties.profile.tenantId)

    val nimbusJwtDecoder =
        NimbusJwtDecoder.withJwkSetUri(identityEndpoints.jwkSetEndpoint)
            .restOperations(createRestTemplate(restTemplateBuilder))
            .build()

    val validators = createDefaultValidator(aadAuthenticationProperties)

    val tenantConfiguration =
        mutableListOf(
            csmPlatformProperties.azure?.credentials?.core?.tenantId,
            csmPlatformProperties.azure?.credentials?.customer?.tenantId)
    tenantConfiguration.addAll(csmPlatformProperties.authorization.allowedTenants)

    val allowedTenants = tenantConfiguration.filterNotNull().filterNot(String::isBlank).toSet()

    if (allowedTenants.isEmpty()) {
      logger.warn(
          "Could not determine list of tenants allowed. " +
              "This means no Tenant is allowed to use this API. " +
              "Is this intentional? " +
              "If not, please properly configure any of the following properties: " +
              "'csm.platform.<provider>.credentials.core.tenantId' " +
              "or 'csm.platform.authorization.allowed-tenants'" +
              " or 'csm.platform.<provider>.credentials.customer.tenantId' ")
    }

    if ("*" in allowedTenants) {
      logger.info(
          "All tenants allowed to authenticate, since the following property contains a wildcard " +
              "element: csm.platform.authorization.allowed-tenants")
    } else {
      // Validate against the list of allowed tenants
      val tenantValidator =
          JwtClaimValidator(csmPlatformProperties.authorization.tenantIdJwtClaim) { issuer: String
            ->
            val issuerSplit = issuer.split(microsoftOnlineIssuer)
            if (issuerSplit.size > 1) {
              allowedTenants.contains(issuerSplit[1])
            } else {
              false
            }
          }
      validators.add(tenantValidator)
    }

    nimbusJwtDecoder.setJwtValidator(DelegatingOAuth2TokenValidator(validators))
    return nimbusJwtDecoder
  }

  private fun createDefaultValidator(
      aadAuthenticationProperties: AadAuthenticationProperties
  ): MutableList<OAuth2TokenValidator<Jwt>> {

    val validators: MutableList<OAuth2TokenValidator<Jwt>> = ArrayList()
    val validAudiences: MutableList<String> = ArrayList()

    if (StringUtils.hasText(aadAuthenticationProperties.appIdUri)) {
      validAudiences.add(aadAuthenticationProperties.appIdUri)
    }
    if (StringUtils.hasText(aadAuthenticationProperties.credential.clientId)) {
      validAudiences.add(aadAuthenticationProperties.credential.clientId)
    }
    if (validAudiences.isNotEmpty()) {
      validators.add(
          JwtClaimValidator(AadJwtClaimNames.AUD) { c: List<String> ->
            validAudiences.containsAll(c)
          })
    }
    validators.add(AadJwtIssuerValidator())
    validators.add(JwtTimestampValidator())
    return validators
  }
}
