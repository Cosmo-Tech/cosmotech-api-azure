package com.cosmotech.api.azure

import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter


class CsmAzureHttpSecurityConfigurer :
    AbstractHttpConfigurer<CsmAzureHttpSecurityConfigurer, HttpSecurity>() {
    @Throws(Exception::class)
    override fun init(builder: HttpSecurity) {
        super.init(builder)
        builder.oauth2ResourceServer()
            .jwt()
            .jwtAuthenticationConverter(JwtAuthenticationConverter())
    }

    companion object {
        fun resourceServer(): CsmAzureHttpSecurityConfigurer {
            return CsmAzureHttpSecurityConfigurer()
        }
    }

}
