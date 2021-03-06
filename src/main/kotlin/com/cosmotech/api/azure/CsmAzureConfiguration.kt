// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure

import com.azure.cosmos.CosmosClient
import com.azure.cosmos.CosmosClientBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.batch.BlobBatchClient
import com.azure.storage.blob.batch.BlobBatchClientBuilder
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
internal open class CsmAzureConfiguration(
    private val cosmosClientBuilder: CosmosClientBuilder,
    private val blobServiceClientBuilder: BlobServiceClientBuilder,
) {

  @Bean open fun cosmosClient(): CosmosClient = cosmosClientBuilder.buildClient()

  @Bean open fun storageClient(): BlobServiceClient = blobServiceClientBuilder.buildClient()

  @Bean
  open fun batchStorageClient(): BlobBatchClient =
      BlobBatchClientBuilder(storageClient()).buildClient()
}
