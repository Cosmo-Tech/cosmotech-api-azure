// Copyright (c) Cosmo Tech.
// Licensed under the MIT license.
package com.cosmotech.api.azure.adx

import com.cosmotech.api.config.CsmPlatformProperties
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure
import com.cosmotech.api.config.CsmPlatformProperties.CsmPlatformAzure.CsmPlatformAzureDataWarehouseCluster
import com.cosmotech.api.events.CsmEventPublisher
import com.cosmotech.api.events.ScenarioRunEndTimeRequest
import com.cosmotech.api.scenariorun.DataIngestionState
import com.microsoft.azure.kusto.data.Client
import com.microsoft.azure.kusto.data.KustoOperationResult
import com.microsoft.azure.kusto.data.KustoResultSetTable
import com.microsoft.azure.kusto.data.exceptions.DataClientException
import com.microsoft.azure.kusto.data.exceptions.DataServiceException
import com.microsoft.azure.kusto.ingest.IngestClient
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.spyk
import java.time.ZonedDateTime
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.actuate.health.Status

@ExtendWith(MockKExtension::class)
class AzureDataExplorerClientTests {

  @MockK private lateinit var csmPlatformProperties: CsmPlatformProperties
  @MockK private lateinit var kustoClient: Client
  @MockK private lateinit var ingestClient: IngestClient
  @MockK private lateinit var eventPublisher: CsmEventPublisher

  private lateinit var azureDataExplorerClient: AzureDataExplorerClient

  @BeforeTest
  fun beforeTest() {
    this.csmPlatformProperties = mockk()
    this.kustoClient = mockk()
    this.ingestClient = mockk()
    this.eventPublisher = mockk()
    val csmPlatformPropertiesAzure = mockk<CsmPlatformAzure>()
    val csmPlatformPropertiesAzureDataWarehouseCluster =
        mockk<CsmPlatformAzureDataWarehouseCluster>()
    every { csmPlatformPropertiesAzureDataWarehouseCluster.baseUri } returns
        "https://my-datawarehouse.cluster"
    every { csmPlatformPropertiesAzureDataWarehouseCluster.options.ingestionUri } returns
        "https://my-ingestdatawarehouse.cluster"
    every { csmPlatformPropertiesAzure.dataWarehouseCluster } returns
        csmPlatformPropertiesAzureDataWarehouseCluster
    every { csmPlatformProperties.azure } returns csmPlatformPropertiesAzure
    every { csmPlatformProperties.dataIngestion } returns CsmPlatformProperties.DataIngestion()
    this.azureDataExplorerClient =
        AzureDataExplorerClient(this.csmPlatformProperties, this.eventPublisher)
    this.azureDataExplorerClient.setKustoClient(kustoClient)
  }

  @Test
  fun `health is DOWN if no result set from query`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns false
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if not healthy`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns true
    every { resultSet.getIntegerObject(eq("IsHealthy")) } returns 0
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if a DataServiceException is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws DataServiceException("ingestionSource", "some message", true)
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if a DataClientException is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws DataClientException("ingestionSource", "some message")
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is DOWN if any other exception is thrown while calling ADX`() {
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } throws NullPointerException()
    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.DOWN, health.status)
  }

  @Test
  fun `health is UP if everything is Ok`() {
    val result = mockk<KustoOperationResult>()
    val resultSet = mockk<KustoResultSetTable>()
    every { resultSet.next() } returns true
    every { resultSet.getIntegerObject(eq("IsHealthy")) } returns 1
    every { result.primaryResults } returns resultSet
    every {
      kustoClient.execute(eq(DEFAULT_DATABASE_NAME), eq(HEALTH_KUSTO_QUERY.trimIndent()), any())
    } returns result

    val health = this.azureDataExplorerClient.health()
    assertEquals(Status.UP, health.status)
  }

  @Test
  fun `PROD-7420 - getStateFor returns Unknown if scenario run workflow end time is NULL`() {
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = null
        }
    assertEquals(
        DataIngestionState.Unknown,
        AzureDataExplorerClient(csmPlatformProperties, eventPublisher)
            .getStateFor(
                "my-organization-id",
                "my-workspace-key",
                "sr-myscenarioRunId",
                "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-7420 - getStateFor returns InProgress if scenario run workflow end time is less than waiting time`() {
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(waitingTimeBeforeIngestionSeconds = 3600)
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = ZonedDateTime.now().minusSeconds(1)
        }
    assertEquals(
        DataIngestionState.InProgress,
        AzureDataExplorerClient(csmPlatformProperties, eventPublisher)
            .getStateFor(
                "my-organization-id",
                "my-workspace-key",
                "sr-myscenarioRunId",
                "my-csm-simulation-run"))
  }

  @TestFactory
  fun `PROD-7420 - getStateFor returns In Progress if no control plane messages but no time out`():
      Collection<DynamicTest> {
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = ZonedDateTime.now().minusSeconds(10)
        }

    return DataIngestionState.values().map { dataIngestionState ->
      DynamicTest.dynamicTest(dataIngestionState.name) {
        every { csmPlatformProperties.dataIngestion } returns
            CsmPlatformProperties.DataIngestion(
                waitingTimeBeforeIngestionSeconds = 1,
                state = CsmPlatformProperties.DataIngestion.State(noDataTimeOutSeconds = 60))
        val azureDataExplorerClient =
            spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
        every {
          azureDataExplorerClient.querySentMessagesTotal(
              eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
        } returns 0
        every {
          azureDataExplorerClient.queryProbesMeasuresCount(
              eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
        } returns 42
        assertEquals(
            DataIngestionState.InProgress,
            azureDataExplorerClient.getStateFor(
                "my-organization-id",
                "my-workspace-key",
                "sr-myscenarioRunId",
                "my-csm-simulation-run"))
      }
    }
  }

  @Test
  fun `PROD-7420 getStateFor returns Failure if told so if no control plane messages and time out`() {
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = ZonedDateTime.now().minusSeconds(70)
        }
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(
            waitingTimeBeforeIngestionSeconds = 1,
            state = CsmPlatformProperties.DataIngestion.State(noDataTimeOutSeconds = 60))
    val azureDataExplorerClient =
        spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
    every {
      azureDataExplorerClient.querySentMessagesTotal(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 0
    every {
      azureDataExplorerClient.queryProbesMeasuresCount(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 0
    assertEquals(
        DataIngestionState.Failure,
        azureDataExplorerClient.getStateFor(
            "my-organization-id",
            "my-workspace-key",
            "sr-myscenarioRunId",
            "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-7420 - getStateFor returns Failure if not enough data ingested and there are ingestion failures`() {
    val scenarioRunWorkflowEndTime = ZonedDateTime.now().minusSeconds(10)
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = scenarioRunWorkflowEndTime
        }
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(
            waitingTimeBeforeIngestionSeconds = 1,
        )
    val azureDataExplorerClient =
        spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
    every {
      azureDataExplorerClient.querySentMessagesTotal(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 42
    every {
      azureDataExplorerClient.queryProbesMeasuresCount(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 33
    every {
      azureDataExplorerClient.anyDataPlaneIngestionFailures(
          eq("my-organization-id"),
          eq("my-workspace-key"),
          eq(scenarioRunWorkflowEndTime),
      )
    } returns true

    assertEquals(
        DataIngestionState.Failure,
        azureDataExplorerClient.getStateFor(
            "my-organization-id",
            "my-workspace-key",
            "sr-myscenarioRunId",
            "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-7420 - getStateFor returns InProgress if not enough data ingested and no ingestion failures`() {
    val scenarioRunWorkflowEndTime = ZonedDateTime.now().minusSeconds(10)
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = scenarioRunWorkflowEndTime
        }
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(
            waitingTimeBeforeIngestionSeconds = 1,
        )
    val azureDataExplorerClient =
        spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
    every {
      azureDataExplorerClient.querySentMessagesTotal(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 42
    every {
      azureDataExplorerClient.queryProbesMeasuresCount(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 33
    every {
      azureDataExplorerClient.anyDataPlaneIngestionFailures(
          eq("my-organization-id"),
          eq("my-workspace-key"),
          eq(scenarioRunWorkflowEndTime),
      )
    } returns false

    assertEquals(
        DataIngestionState.InProgress,
        azureDataExplorerClient.getStateFor(
            "my-organization-id",
            "my-workspace-key",
            "sr-myscenarioRunId",
            "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-7420 - getStateFor returns Successful if enough data ingested`() {
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = ZonedDateTime.now().minusSeconds(10)
        }
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(
            waitingTimeBeforeIngestionSeconds = 1,
        )
    val azureDataExplorerClient =
        spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
    every {
      azureDataExplorerClient.querySentMessagesTotal(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 42
    every {
      azureDataExplorerClient.queryProbesMeasuresCount(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 42

    assertEquals(
        DataIngestionState.Successful,
        azureDataExplorerClient.getStateFor(
            "my-organization-id",
            "my-workspace-key",
            "sr-myscenarioRunId",
            "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-7420 - getStateFor returns Successful if more than expected data ingested`() {
    every { eventPublisher.publishEvent(any<ScenarioRunEndTimeRequest>()) } answers
        {
          firstArg<ScenarioRunEndTimeRequest>().response = ZonedDateTime.now().minusSeconds(10)
        }
    every { csmPlatformProperties.dataIngestion } returns
        CsmPlatformProperties.DataIngestion(
            waitingTimeBeforeIngestionSeconds = 1,
        )
    val azureDataExplorerClient =
        spyk(AzureDataExplorerClient(csmPlatformProperties, eventPublisher))
    every {
      azureDataExplorerClient.querySentMessagesTotal(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 42
    every {
      azureDataExplorerClient.queryProbesMeasuresCount(
          eq("my-organization-id"), eq("my-workspace-key"), eq("my-csm-simulation-run"))
    } returns 111

    assertEquals(
        DataIngestionState.Successful,
        azureDataExplorerClient.getStateFor(
            "my-organization-id",
            "my-workspace-key",
            "sr-myscenarioRunId",
            "my-csm-simulation-run"))
  }

  @Test
  fun `PROD-8148 - deleteDataFromADXbyExtentShard failed`() {
    val databaseName = "orgid-wk"
    every { kustoClient.executeToJsonResult(databaseName, any(), any()) } returns ""
    val res =
        azureDataExplorerClient.deleteDataFromADXbyExtentShard("orgId", "wk", "my-scenariorunId")
    assertEquals("", res)
  }
}
