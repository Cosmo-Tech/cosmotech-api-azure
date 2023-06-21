// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.containerregistry

import com.azure.containers.containerregistry.ContainerRegistryClient
import com.azure.containers.containerregistry.ContainerRegistryClientBuilder
import com.azure.containers.containerregistry.models.ContainerRegistryAudience
import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.exceptions.CsmResourceNotFoundException
import java.lang.reflect.InvocationTargetException
import java.util.stream.Collectors
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service("csmContainerRegistry")
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class AzureContainerRegistryClient(private val csmPlatformProperties: CsmPlatformProperties) {

  private val logger = LoggerFactory.getLogger(AzureContainerRegistryClient::class.java)

  private fun getEndPoint() =
      "https://" + csmPlatformProperties.azure?.containerRegistries?.solutions

  public fun checkSolutionImage(repository: String, version: String) {

    val credential =
        ClientSecretCredentialBuilder()
            .tenantId(csmPlatformProperties.azure?.credentials?.core?.tenantId!!)
            .clientId(csmPlatformProperties.azure?.credentials?.core?.clientId!!)
            .clientSecret(csmPlatformProperties.azure?.credentials?.core?.clientSecret!!)
            .build()

    checkSolutionImage(credential, repository, version)
  }
  private fun <T : TokenCredential> checkSolutionImage(
      tokenCredential: T,
      repository: String,
      version: String
  ) {
    val registryClient: ContainerRegistryClient =
        ContainerRegistryClientBuilder()
            .endpoint(getEndPoint())
            .credential(tokenCredential)
            .audience(ContainerRegistryAudience.AZURE_RESOURCE_MANAGER_PUBLIC_CLOUD)
            .buildClient()

    checkSolutionImage(registryClient, repository, version)
  }

  private fun checkSolutionImage(
      registryClient: ContainerRegistryClient,
      repository: String,
      version: String
  ) {
    // the getRepository quietly return an artifact even the artifact doesn't exist
    // but the get TagProperties throws an unknown tag exception
    // to be more concise manually check the repository and throw an exception eventually
    val repositoryFound =
        registryClient
            .listRepositoryNames()
            .stream()
            .collect(Collectors.toList())
            .contains(repository)

    if (!repositoryFound) {
      throw CsmResourceNotFoundException("The repository ${repository} doesn't exist")
    }

    try {
      val repository = registryClient.getRepository(repository)
      repository.getArtifact(version).getTagProperties(version)
    } catch (e: InvocationTargetException) {
      val logMessage =
          "Artifact $repository:$version not found in the registry " +
              "$csmPlatformProperties.azure?.containerRegistries?.solutions"
      logger.warn(logMessage, e)
      throw CsmResourceNotFoundException(logMessage)
    }
  }
}
