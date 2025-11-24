// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.eventhubs

import com.azure.core.amqp.exception.AmqpErrorCondition
import com.azure.core.amqp.exception.AmqpException
import com.azure.messaging.eventhubs.EventHubProducerClient
import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.scenario.ScenarioMetaData
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.time.Instant
import java.util.Date
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith

@ExtendWith(MockKExtension::class)
class AzureEventHubsClientTests {

  @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties

  private lateinit var eventHubsClient: AzureEventHubsClient

  @BeforeTest
  fun beforeTest() {
    this.csmPlatformProperties = mockk()
    eventHubsClient = AzureEventHubsClient(csmPlatformProperties)
  }

  @Test
  fun `PROD-7420 - doesEventHubExist returns true if the eventHub does exist`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } returns mockk()
    assertTrue { eventHubsClient.doesEventHubExist(producer) }
  }

  @Test
  fun `PROD-7420 - doesEventHubExist returns false if the eventHub does not exist`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws
        AmqpException(true, AmqpErrorCondition.NOT_FOUND, "Not Found", null)
    assertFalse { eventHubsClient.doesEventHubExist(producer) }
  }

  @TestFactory
  fun `PROD-7420 - doesEventHubExist throws if errors other than NOT_FOUND are reported`() =
      AmqpErrorCondition.values()
          .filterNot { it == AmqpErrorCondition.NOT_FOUND }
          .map { amqpErrorCondition ->
            dynamicTest(amqpErrorCondition.name) {
              val producer = mockk<EventHubProducerClient>()
              every { producer.eventHubProperties } throws
                  AmqpException(true, amqpErrorCondition, amqpErrorCondition.name, null)
              assertThrows<UnsupportedOperationException> {
                eventHubsClient.doesEventHubExist(producer)
              }
            }
          }

  @TestFactory
  fun `PROD-7420 - doesEventHubExist returns false if errors are ignored`() =
      AmqpErrorCondition.values().map { amqpErrorCondition ->
        dynamicTest(amqpErrorCondition.name) {
          val producer = mockk<EventHubProducerClient>()
          every { producer.eventHubProperties } throws
              AmqpException(true, amqpErrorCondition, amqpErrorCondition.name, null)
          assertFalse { eventHubsClient.doesEventHubExist(producer, ignoreErrors = true) }
        }
      }

  @Test
  fun `PROD-7420 - doesEventHubExist returns false if errors other than AMQPException are ignored`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws IllegalArgumentException()
    assertFalse { eventHubsClient.doesEventHubExist(producer, ignoreErrors = true) }
  }

  @Test
  fun `PROD-7420 - doesEventHubExist throws if errors other than AMQPException are not ignored`() {
    val producer = mockk<EventHubProducerClient>()
    every { producer.eventHubProperties } throws Exception()
    assertThrows<IllegalStateException> { eventHubsClient.doesEventHubExist(producer) }
  }

  @Test
  fun `PROD-14934 - test ScenarioMetadaData conversion for ADX`() {
    val organizationId = "organization-Id"
    val workspaceId = "workspace-Id"
    val scenarioId = "scenario-Id"
    val name = "My wonderful scenario"
    val description =
        """Dans l'obscurité silencieuse d'une nuit étoilée, mon âme vagabonde sur les ailes du vent.
            Chaque souffle murmure une histoire ancienne, un chant oublié porté par le temps.
            Les étoiles, telles des sentinelles lumineuses, veillent sur mes pensées,
            éclairant les recoins secrets de mon cœur épris de liberté.

            Au cœur de la forêt enchantée, les arbres dansent sous le souffle d'une brise légère.
            Chaque feuille vibrant d'une mélodie "douce" évoque le passage du temps et la magie des instants fugaces.
            Le murmure de la rivière, compagnon fidèle de mes errances,
            chante une ode à l'espoir et à l'éternel renouveau.

            Dans ce tableau vivant, chaque instant est une perle rare, suspendue entre le passé et l'avenir.
            Mon regard se perd dans l'infini, cherchant la vérité qui se cache derrière l'horizon.
            Les échos de mes rêves s'entrelacent aux couleurs de l'aube,
            tissant la toile d'une destinée incertaine mais sublime."""
    val parentId = "parent-Id"
    val solutionName = "this is a solution name"
    val runTemplateName = "this is a run template name"
    val validationStatus = "this is a validation status"
    val updateTime = Date.from(Instant.now()).toString()

    val csvData =
        eventHubsClient.constructScenarioData(
            ScenarioMetaData(
                organizationId,
                workspaceId,
                scenarioId,
                name,
                description,
                parentId,
                solutionName,
                runTemplateName,
                validationStatus,
                updateTime))

    AzureEventHubsClientTests::class.java.getResourceAsStream("/test-scenariodata.csv")!!.use {
      assertEquals(it.bufferedReader().readText().replace("\$dateTime", updateTime), csvData)
    }
  }
}
