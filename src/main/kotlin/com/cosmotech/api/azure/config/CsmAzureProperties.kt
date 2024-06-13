// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties

/** Configuration Properties for the Cosmo Tech Platform on Azure */
@Suppress("LongParameterList")
@ConfigurationProperties(prefix = "csm.platform.azure")
@ConditionalOnProperty(prefix = "csm.platform.vendor", havingValue = "azure", matchIfMissing = true)
class CsmAzureProperties(

    /** Define if current API use azure services or not */
    val enabled: Boolean = false,

    /** Azure Credentials */
    val credentials: CsmAzureCredentials,

    /** Azure storage services */
    val storage: CsmAzureStorage,

    /** Azure Event Bus configuration */
    val eventBus: CsmAzureEventBus,

    /** Azure Data Explorer simple configuration */
    val dataWarehouseCluster: CsmDataWarehouseCluster,

    /** Azure Data Explorer Data Ingestion */
    val dataIngestion: DataIngestion = DataIngestion(),

    /** Azure keyvault name */
    val keyVault: String,

    /** Azure analytics configuration */
    val analytics: CsmAzureAnalytics,

    /** Azure App URI */
    val appIdUri: String,

    /** Azure role mapping */
    val claimToAuthorityPrefix: Map<String, String> = mutableMapOf("roles" to ""),
) {

  data class CsmAzureCredentials(

      /** The core App Registration credentials - provided by Cosmo Tech */
      val core: CsmAzureCredentialsCore,

      /**
       * Any customer-provided app registration. Useful for example when calling Azure Digital
       * Twins, because of security enforcement preventing from assigning permissions in the context
       * of a managed app, deployed via the Azure Marketplace
       */
      val customer: CsmAzureCredentialsCustomer? = null,
  ) {

    data class CsmAzureCredentialsCore(
        /** The Azure Tenant ID (core App) */
        val tenantId: String,

        /** The Azure Client ID (core App) */
        val clientId: String,

        /** The Azure Client Secret (core App) */
        val clientSecret: String,

        /**
         * The Azure Active Directory Pod Id binding bound to an AKS pod identity linked to a
         * managed identity
         */
        val aadPodIdBinding: String? = null,
    )

    data class CsmAzureCredentialsCustomer(
        /** The Azure Tenant ID (customer App Registration) */
        val tenantId: String?,

        /** The Azure Client ID (customer App Registration) */
        val clientId: String?,

        /** The Azure Client Secret (customer App Registration) */
        val clientSecret: String?,
    )
  }

  data class CsmAzureStorage(
      val connectionString: String,
      val baseUri: String,
      val resourceUri: String
  )

  data class CsmAzureEventBus(
      val baseUri: String,
      val authentication: Authentication = Authentication()
  ) {
    data class Authentication(
        val strategy: Strategy = Strategy.TENANT_CLIENT_CREDENTIALS,
        val sharedAccessPolicy: SharedAccessPolicyDetails? = null,
        val tenantClientCredentials: TenantClientCredentials? = null
    ) {
      enum class Strategy {
        TENANT_CLIENT_CREDENTIALS,
        SHARED_ACCESS_POLICY
      }

      data class SharedAccessPolicyDetails(
          val namespace: SharedAccessPolicyCredentials? = null,
      )

      data class SharedAccessPolicyCredentials(val name: String, val key: String)
      data class TenantClientCredentials(
          val tenantId: String,
          val clientId: String,
          val clientSecret: String
      )
    }
  }

  data class CsmDataWarehouseCluster(val baseUri: String, val options: Options) {
    data class Options(val ingestionUri: String)
  }

  data class CsmAzureAnalytics(
      val resourceUri: String,
      val instrumentationKey: String,
      val connectionString: String
  )

  data class DataIngestion(
      /**
       * Number of seconds to wait after a scenario run workflow end time, before starting to check
       * ADX for data ingestion state. See https://bit.ly/3FXshzE for the rationale
       */
      val waitingTimeBeforeIngestionSeconds: Long = 15,

      /**
       * number of minutes after a scenario run workflow end time during which an ingestion failure
       * detected is considered linked to the current scenario run
       */
      val ingestionObservationWindowToBeConsideredAFailureMinutes: Long = 5,

      /** Data ingestion state handling default behavior */
      val state: State = State()
  ) {
    data class State(
        /**
         * The timeout in second before considering no data in probes measures and control plane is
         * an issue
         */
        val noDataTimeOutSeconds: Long = 180,
    )
  }
}
