// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.core.credential.AzureNamedKeyCredential
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.batch.BlobBatchClientBuilder
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(
    name = ["spring.cloud.azure.storage.blob.enabled"],
    havingValue = "true",
    matchIfMissing = false)
internal open class CsmAzureConfiguration {

  @Value("\${spring.cloud.azure.storage.blob.account-name}")
  private lateinit var storageAccountName: String

  @Value("\${spring.cloud.azure.storage.blob.account-key}")
  private lateinit var storageAccountKey: String

  @Value("\${spring.cloud.azure.storage.blob.endpoint}")
  private lateinit var storageAccountEndpoint: String

  @Bean
  open fun storageClient(): BlobServiceClient {
    return BlobServiceClientBuilder()
        .endpoint(storageAccountEndpoint)
        .credential(AzureNamedKeyCredential(storageAccountName, storageAccountKey))
        .buildClient()
  }

  @Bean
  open fun batchStorageClient(): BlobBatchClient =
      BlobBatchClientBuilder(storageClient()).buildClient()
}
