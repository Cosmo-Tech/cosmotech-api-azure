// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.eventhubs

import com.azure.core.amqp.exception.AmqpErrorCondition
import com.azure.core.amqp.exception.AmqpException
import com.azure.core.credential.TokenCredential
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.messaging.eventhubs.EventData
import com.azure.messaging.eventhubs.EventHubClientBuilder
import com.azure.messaging.eventhubs.EventHubProducerClient
import com.azure.messaging.eventhubs.implementation.EventHubSharedKeyCredential
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.scenario.MetaData
import com.cosmotech.api.scenario.ScenarioMetaData
import com.cosmotech.api.scenario.ScenarioRunMetaData
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service

@Service("csmEventHubs")
@ConditionalOnProperty(name = ["csm.platform.vendor"], havingValue = "azure", matchIfMissing = true)
class AzureEventHubsClient(private val csmPlatformProperties: CsmPlatformProperties) {

  // TODO Make this contribute to the overall Application Health

  private val logger = LoggerFactory.getLogger(AzureEventHubsClient::class.java)

  fun doesEventHubExist(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      sharedAccessPolicy: String,
      sharedAccessKey: String,
      ignoreErrors: Boolean = false,
  ) =
      this.doesEventHubExist(
          fullyQualifiedNamespace,
          eventHubName,
          EventHubSharedKeyCredential(sharedAccessPolicy, sharedAccessKey),
          ignoreErrors)

  fun doesEventHubExist(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      ignoreErrors: Boolean = false,
  ) =
      this.doesEventHubExist(
          fullyQualifiedNamespace,
          eventHubName,
          ClientSecretCredentialBuilder()
              .tenantId(csmPlatformProperties.azure?.credentials?.core?.tenantId!!)
              .clientId(csmPlatformProperties.azure?.credentials?.core?.clientId!!)
              .clientSecret(csmPlatformProperties.azure?.credentials?.core?.clientSecret!!)
              .build(),
          ignoreErrors)

  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  internal fun doesEventHubExist(
      producer: EventHubProducerClient,
      eventHubNamespace: String? = null,
      eventHubName: String? = null,
      ignoreErrors: Boolean = false,
  ): Boolean {
    return try {
      val eventHubProperties = producer.eventHubProperties
      logger.trace("In doesEventHubExist method, eventHub properties: $eventHubProperties")
      true
    } catch (ae: AmqpException) {
      logger.warn(
          "Caught an error while checking for Event Hub {}/{}: [{}] {}",
          eventHubNamespace,
          eventHubName,
          ae.errorCondition,
          ae.message)
      logger.trace(ae.message, ae)
      when (ae.errorCondition) {
        AmqpErrorCondition.NOT_FOUND -> {
          logger.info("Event Hub '$eventHubNamespace/$eventHubName' *not* found!")
          false
        }
        else -> {
          if (!ignoreErrors) {
            throw UnsupportedOperationException(
                "Unhandled error condition while checking for " +
                    "event hub ($eventHubNamespace/$eventHubName): ${ae.errorCondition}")
          }
          false
        }
      }
    } catch (exception: Exception) {
      logger.warn(
          "Caught an error while checking for Event Hub {}/{}: {}",
          eventHubNamespace,
          eventHubName,
          exception.message)
      logger.trace(exception.message, exception)
      if (!ignoreErrors) {
        throw IllegalStateException(exception.message, exception)
      }
      false
    }
  }

  private fun <T : TokenCredential> doesEventHubExist(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      tokenCredential: T,
      ignoreErrors: Boolean = false,
  ) =
      EventHubClientBuilder()
          .credential(fullyQualifiedNamespace, eventHubName, tokenCredential)
          .buildProducerClient()
          .use { doesEventHubExist(it, fullyQualifiedNamespace, eventHubName, ignoreErrors) }
  private fun sendScenarioMetaData(
      producer: EventHubProducerClient,
      scenarioMetaData: ScenarioMetaData
  ) {
    val data =
        "${scenarioMetaData.organizationId},${scenarioMetaData.workspaceId},${scenarioMetaData.scenarioId}," +
            "${scenarioMetaData.name},${scenarioMetaData.description},${scenarioMetaData.parentId}," +
            "${scenarioMetaData.solutionName},${scenarioMetaData.runTemplateName}," +
            "${scenarioMetaData.validationStatus},${scenarioMetaData.updateTime}"
    val eventData = EventData(data)
    val dropTag = "drop-by:" + scenarioMetaData.scenarioId
    eventData.getProperties().put("Tags", "['$dropTag']")
    val eventDataBatch = producer.createBatch()
    eventDataBatch.tryAdd(eventData)
    producer.send(eventDataBatch)
  }
  private fun sendScenarioRunMetaData(
      producer: EventHubProducerClient,
      scenarioRunMetaData: ScenarioRunMetaData
  ) {
    val data =
        "${scenarioRunMetaData.simulationRun},${scenarioRunMetaData.scenarioId}," +
            "${scenarioRunMetaData.scenarioRunStartTime}"
    val eventData = EventData(data)
    val dropTag = "drop-by:" + scenarioRunMetaData.simulationRun
    eventData.properties.put("Tags", "['$dropTag']")
    val eventDataBatch = producer.createBatch()
    eventDataBatch.tryAdd(eventData)
    producer.send(eventDataBatch)
  }

  internal fun sendMetaData(producer: EventHubProducerClient, metaData: MetaData) {
    when (metaData) {
      is ScenarioMetaData -> sendScenarioMetaData(producer, metaData)
      is ScenarioRunMetaData -> sendScenarioRunMetaData(producer, metaData)
    }
  }

  private fun <T : TokenCredential> sendMetaData(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      tokenCredential: T,
      metaData: MetaData
  ) =
      EventHubClientBuilder()
          .credential(fullyQualifiedNamespace, eventHubName, tokenCredential)
          .buildProducerClient()
          .use { sendMetaData(it, metaData) }

  public fun sendMetaData(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      metaData: MetaData
  ) =
      this.sendMetaData(
          fullyQualifiedNamespace,
          eventHubName,
          ClientSecretCredentialBuilder()
              .tenantId(csmPlatformProperties.azure?.credentials?.core?.tenantId!!)
              .clientId(csmPlatformProperties.azure?.credentials?.core?.clientId!!)
              .clientSecret(csmPlatformProperties.azure?.credentials?.core?.clientSecret!!)
              .build(),
          metaData)

  public fun sendMetaData(
      fullyQualifiedNamespace: String,
      eventHubName: String,
      sharedAccessPolicy: String,
      sharedAccessKey: String,
      metaData: MetaData
  ) =
      this.sendMetaData(
          fullyQualifiedNamespace,
          eventHubName,
          EventHubSharedKeyCredential(sharedAccessPolicy, sharedAccessKey),
          metaData)
}
