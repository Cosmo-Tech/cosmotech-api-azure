// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.spring.cloud.autoconfigure.aad.configuration.AadResourceServerConfiguration
import com.azure.spring.cloud.autoconfigure.aad.properties.AadAuthenticationProperties
import com.azure.spring.cloud.autoconfigure.aad.properties.AadAuthorizationServerEndpoints
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.CsmSecurityValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "azure", matchIfMissing = true)
internal class CsmAzureBasedSecurityConfiguration(
    private val csmPlatformProperties: CsmPlatformProperties,
) {

  @Bean
  fun csmSecurityValidator(
      aadResourceServerConfiguration: AadResourceServerConfiguration,
      aadAuthenticationProperties: AadAuthenticationProperties,
  ) =
      CsmAzureBasedSecurityValidator(
          csmPlatformProperties, aadResourceServerConfiguration, aadAuthenticationProperties)
}

internal class CsmAzureBasedSecurityValidator(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val aadResourceServerConfiguration: AadResourceServerConfiguration,
    private val aadAuthenticationProperties: AadAuthenticationProperties,
) : CsmSecurityValidator {
  override fun getAllowedTenants() =
      listOf(
          csmPlatformProperties.azure?.credentials?.core?.tenantId,
          csmPlatformProperties.azure?.credentials?.customer?.tenantId)

  override fun getJwksSetUri(): String =
      AadAuthorizationServerEndpoints(
              aadAuthenticationProperties.appIdUri, aadAuthenticationProperties.profile.tenantId)
          .jwkSetEndpoint

  override fun getValidators(): List<OAuth2TokenValidator<Jwt>> =
      aadResourceServerConfiguration.createDefaultValidator(aadAuthenticationProperties)
}
