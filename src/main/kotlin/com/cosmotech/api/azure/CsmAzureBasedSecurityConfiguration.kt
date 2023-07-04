// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.spring.cloud.autoconfigure.implementation.aad.configuration.properties.AadAuthenticationProperties
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.constants.AadJwtClaimNames
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.jwt.AadJwtIssuerValidator
import com.azure.spring.cloud.autoconfigure.implementation.aad.security.properties.AadAuthorizationServerEndpoints
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.security.CsmSecurityValidator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.security.oauth2.core.OAuth2TokenValidator
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtClaimValidator
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils

@Component
@ConditionalOnProperty(
    name = ["csm.platform.identityProvider.code"], havingValue = "azure", matchIfMissing = true)
internal class CsmAzureBasedSecurityConfiguration(
    private val csmPlatformProperties: CsmPlatformProperties,
) {

//  @Bean
//  fun aadJwtAuthenticationConverter(): Converter<Jwt, out AbstractAuthenticationToken> {
//    val claimAuthorityMap =
//        AadResourceServerProperties.DEFAULT_CLAIM_TO_AUTHORITY_PREFIX_MAP.toMutableMap()
//
//    csmPlatformProperties.azure?.claimToAuthorityPrefix?.let { claimAuthorityMap.putAll(it) }
//    return AADJwtBearerTokenAuthenticationConverter(
//        csmPlatformProperties.authorization.principalJwtClaim, claimAuthorityMap)
//  }

  // SPOK added
  @Bean
  fun aadAuthenticationProperties(): AadAuthenticationProperties {
    return AadAuthenticationProperties()
  }

  @Bean
  fun csmSecurityValidator(aadAuthenticationProperties: AadAuthenticationProperties) =
      CsmAzureBasedSecurityValidator(
          csmPlatformProperties, aadAuthenticationProperties)
}

internal class CsmAzureBasedSecurityValidator(
    private val csmPlatformProperties: CsmPlatformProperties,
    private val aadAuthenticationProperties: AadAuthenticationProperties) : CsmSecurityValidator {
  override fun getAllowedTenants() =
      listOf(
          csmPlatformProperties.azure?.credentials?.core?.tenantId,
          csmPlatformProperties.azure?.credentials?.customer?.tenantId)

  override fun getJwksSetUri(): String =
      // SPOK tenantId maybe: aadAuthenticationProperties.profile.tenantId
      // I didn't find baseUri.  Is baseUri != appIdUri? To check.
      AadAuthorizationServerEndpoints(
          aadAuthenticationProperties.appIdUri, csmPlatformProperties.azure?.credentials?.tenantId)
          .jwkSetEndpoint

  override fun getValidators(): List<OAuth2TokenValidator<Jwt>> =
      createDefaultValidator(aadAuthenticationProperties)
}


fun createDefaultValidator(aadAuthenticationProperties: AadAuthenticationProperties): List<OAuth2TokenValidator<Jwt>> {
    val validators: MutableList<OAuth2TokenValidator<Jwt>> = ArrayList()
    val validAudiences: MutableList<String> = ArrayList()
    if (StringUtils.hasText(aadAuthenticationProperties.appIdUri)) {
        validAudiences.add(aadAuthenticationProperties.appIdUri)
    }
    if (StringUtils.hasText(aadAuthenticationProperties.credential.clientId)) {
        validAudiences.add(aadAuthenticationProperties.credential.clientId)
    }
    if (validAudiences.isNotEmpty()) {
        validators.add(JwtClaimValidator(
            AadJwtClaimNames.AUD
        ) { c: List<String>? ->
            validAudiences.containsAll(
                c!!
            )
        })
    }
    validators.add(AadJwtIssuerValidator())
    validators.add(JwtTimestampValidator())
    return validators
}
