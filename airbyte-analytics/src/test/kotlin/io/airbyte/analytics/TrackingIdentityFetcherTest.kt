/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.analytics

import io.airbyte.api.client.generated.WorkspaceApi
import io.airbyte.api.client.model.generated.WorkspaceIdRequestBody
import io.airbyte.api.client.model.generated.WorkspaceRead
import io.airbyte.commons.constants.GEOGRAPHY_AUTO
import io.airbyte.config.Organization
import io.airbyte.config.ScopeType
import io.airbyte.data.services.OrganizationService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID
import java.util.function.Function

class TrackingIdentityFetcherTest {
  private lateinit var workspaceApi: WorkspaceApi
  private lateinit var organizationService: OrganizationService
  private lateinit var trackingIdentityFetcher: TrackingIdentityFetcher
  private lateinit var workspaceFetcher: Function<UUID, WorkspaceRead>
  private lateinit var organizationFetcher: Function<UUID, Organization>

  @BeforeEach
  fun setup() {
    workspaceApi = mockk()
    organizationService = mockk()
    workspaceFetcher =
      Function { workspaceId: UUID ->
        workspaceApi.getWorkspace(WorkspaceIdRequestBody(workspaceId = workspaceId, includeTombstone = true))
      }
    organizationFetcher =
      Function { organizationId: UUID ->
        organizationService.getOrganization(organizationId).orElseThrow()
      }
    trackingIdentityFetcher =
      TrackingIdentityFetcher(
        workspaceFetcher = workspaceFetcher,
        organizationFetcher = organizationFetcher,
      )
  }

  @Test
  fun testGetTrackingIdentityRespectsWorkspaceId() {
    val customerId1 = UUID.randomUUID()
    val customerId2 = UUID.randomUUID()
    val workspaceId1 = UUID.randomUUID()
    val workspaceId2 = UUID.randomUUID()
    val workspaceRequestBody1 = WorkspaceIdRequestBody(workspaceId = workspaceId1, includeTombstone = true)
    val workspaceRequestBody2 = WorkspaceIdRequestBody(workspaceId = workspaceId2, includeTombstone = true)
    val workspaceRead1 =
      WorkspaceRead(
        workspaceId = workspaceId1,
        customerId = customerId1,
        name = "",
        slug = "",
        initialSetupComplete = true,
        organizationId = workspaceId1,
      )
    val workspaceRead2 =
      WorkspaceRead(
        workspaceId = workspaceId2,
        customerId = customerId2,
        name = "",
        slug = "",
        initialSetupComplete = true,
        organizationId = workspaceId2,
      )

    every { workspaceApi.getWorkspace(workspaceRequestBody1) } returns workspaceRead1
    every { workspaceApi.getWorkspace(workspaceRequestBody2) } returns workspaceRead2

    val workspace1Actual: TrackingIdentity = trackingIdentityFetcher.apply(workspaceId1, ScopeType.WORKSPACE)
    val workspace2Actual: TrackingIdentity = trackingIdentityFetcher.apply(workspaceId2, ScopeType.WORKSPACE)

    val workspace1Expected =
      TrackingIdentity(
        customerId = customerId1,
        email = null,
        anonymousDataCollection = null,
        news = null,
        securityUpdates = null,
      )
    val workspace2Expected =
      TrackingIdentity(
        customerId = customerId2,
        email = null,
        anonymousDataCollection = null,
        news = null,
        securityUpdates = null,
      )
    Assertions.assertEquals(workspace1Expected, workspace1Actual)
    Assertions.assertEquals(workspace2Expected, workspace2Actual)
  }

  @Test
  fun testGetTrackingIdentityInitialSetupNotComplete() {
    val customerId1 = UUID.randomUUID()
    val workspaceId1 = UUID.randomUUID()
    val workspaceRequestBody1 = WorkspaceIdRequestBody(workspaceId = workspaceId1, includeTombstone = true)
    val workspaceRead1 =
      WorkspaceRead(
        workspaceId = workspaceId1,
        customerId = customerId1,
        name = "",
        slug = "",
        initialSetupComplete = true,
        organizationId = workspaceId1,
      )

    every { workspaceApi.getWorkspace(workspaceRequestBody1) } returns workspaceRead1

    val actual: TrackingIdentity = trackingIdentityFetcher.apply(workspaceId1, ScopeType.WORKSPACE)

    val expected =
      TrackingIdentity(
        customerId = customerId1,
        email = null,
        anonymousDataCollection = null,
        news = null,
        securityUpdates = null,
      )
    Assertions.assertEquals(expected, actual)
  }

  @Test
  fun testGetTrackingIdentityNonAnonymous() {
    val customerId = UUID.randomUUID()
    val workspaceId = UUID.randomUUID()
    val workspaceRequestBody = WorkspaceIdRequestBody(workspaceId = workspaceId, includeTombstone = true)
    val workspaceRead =
      WorkspaceRead(
        workspaceId = workspaceId,
        customerId = customerId,
        email = EMAIL,
        anonymousDataCollection = false,
        news = true,
        securityUpdates = true,
        defaultGeography = GEOGRAPHY_AUTO,
        name = "",
        slug = "",
        initialSetupComplete = true,
        organizationId = workspaceId,
      )

    every { workspaceApi.getWorkspace(workspaceRequestBody) } returns workspaceRead
    val actual: TrackingIdentity = trackingIdentityFetcher.apply(workspaceId, ScopeType.WORKSPACE)

    val expected =
      TrackingIdentity(
        customerId = customerId,
        email = EMAIL,
        anonymousDataCollection = false,
        news = true,
        securityUpdates = true,
      )
    Assertions.assertEquals(expected, actual)
  }

  @Test
  fun testGetTrackingIdentityAnonymous() {
    val customerId = UUID.randomUUID()
    val workspaceId = UUID.randomUUID()
    val workspaceRequestBody = WorkspaceIdRequestBody(workspaceId = workspaceId, includeTombstone = true)
    val workspaceRead =
      WorkspaceRead(
        workspaceId = workspaceId,
        customerId = customerId,
        email = EMAIL,
        anonymousDataCollection = true,
        news = true,
        securityUpdates = true,
        defaultGeography = GEOGRAPHY_AUTO,
        name = "",
        slug = "",
        initialSetupComplete = true,
        organizationId = workspaceId,
      )

    every { workspaceApi.getWorkspace(workspaceRequestBody) } returns workspaceRead
    val actual: TrackingIdentity = trackingIdentityFetcher.apply(workspaceId, ScopeType.WORKSPACE)

    val expected =
      TrackingIdentity(
        customerId = customerId,
        email = null,
        anonymousDataCollection = true,
        news = true,
        securityUpdates = true,
      )
    Assertions.assertEquals(expected, actual)
  }

  companion object {
    const val EMAIL = "a@airbyte.io"
  }
}
