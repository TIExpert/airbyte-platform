/*
 * Copyright (c) 2020-2025 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.workload.repository

import io.airbyte.db.factory.DSLContextFactory
import io.airbyte.db.instance.DatabaseConstants
import io.airbyte.db.instance.test.TestDatabaseProviders
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.WORKLOAD_ID
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.defaultDeadline
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.labelList1
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.labelList2
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.labelList3
import io.airbyte.workload.repository.WorkloadRepositoryTest.Fixtures.labelList4
import io.airbyte.workload.repository.domain.Workload
import io.airbyte.workload.repository.domain.WorkloadLabel
import io.airbyte.workload.repository.domain.WorkloadStatus
import io.airbyte.workload.repository.domain.WorkloadType
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.PropertySource
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.testcontainers.containers.PostgreSQLContainer
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import javax.sql.DataSource

internal class WorkloadRepositoryTest {
  @AfterEach
  fun cleanDb() {
    workloadLabelRepo.deleteAll()
    workloadRepo.deleteAll()
    workloadQueueRepo.deleteAll()
  }

  @Test
  fun `claiming a pending workload marks the workload as claimed by the dataplane`() {
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = null,
        status = WorkloadStatus.PENDING,
        geography = "US",
      )
    workloadRepo.save(workload)
    val newDeadline = OffsetDateTime.now().plusMinutes(10)
    val dataplaneId = "my-data-plane"
    val actualWorkload = workloadRepo.claim(WORKLOAD_ID, dataplaneId, newDeadline)

    assertEquals(WORKLOAD_ID, actualWorkload?.id)
    assertEquals(dataplaneId, actualWorkload?.dataplaneId)
    assertEquals(WorkloadStatus.CLAIMED, actualWorkload?.status)
  }

  @Test
  fun `claiming a workload that has been claimed by another dataplane fails`() {
    val otherDataplaneId = "my-other-dataplane"
    val originalDeadline = OffsetDateTime.now().withNano(0).plusMinutes(5)
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = otherDataplaneId,
        status = WorkloadStatus.CLAIMED,
        deadline = originalDeadline,
        geography = "US",
      )
    workloadRepo.save(workload)
    val newDeadline = OffsetDateTime.now().plusMinutes(10)
    val dataplaneId = "my-data-plane"
    val claimResult = workloadRepo.claim(WORKLOAD_ID, dataplaneId, newDeadline)
    assertNull(claimResult)

    val actualWorkload = workloadRepo.findById(WORKLOAD_ID).get()
    assertEquals(otherDataplaneId, actualWorkload.dataplaneId)
    assertEquals(WorkloadStatus.CLAIMED, actualWorkload.status)
    assertEquals(originalDeadline, actualWorkload.deadline)
  }

  @Test
  fun `claiming a workload that has been claimed by the same dataplane succeeds and doesn't update the deadline`() {
    val dataplaneId = "my-data-plane"
    val originalDeadline = OffsetDateTime.now().withNano(0).plusMinutes(5)
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = dataplaneId,
        status = WorkloadStatus.CLAIMED,
        deadline = originalDeadline,
        geography = "US",
      )
    workloadRepo.save(workload)
    val newDeadline = OffsetDateTime.now().plusMinutes(10)
    val actualWorkload = workloadRepo.claim(WORKLOAD_ID, dataplaneId, newDeadline)

    assertEquals(WORKLOAD_ID, actualWorkload?.id)
    assertEquals(dataplaneId, actualWorkload?.dataplaneId)
    assertEquals(WorkloadStatus.CLAIMED, actualWorkload?.status)
    // note that we do not refresh the deadline in this case
    assertEquals(originalDeadline, actualWorkload?.deadline)
  }

  @Test
  fun `claiming workload that is running on the same dataplane fails`() {
    val dataplaneId = "my-data-plane"
    val originalDeadline = OffsetDateTime.now().withNano(0).plusMinutes(5)
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = dataplaneId,
        status = WorkloadStatus.RUNNING,
        deadline = originalDeadline,
        geography = "US",
      )
    workloadRepo.save(workload)
    val newDeadline = OffsetDateTime.now().plusMinutes(10)
    val claimResult = workloadRepo.claim(WORKLOAD_ID, dataplaneId, newDeadline)
    assertNull(claimResult)

    val actualWorkload = workloadRepo.findById(WORKLOAD_ID).get()
    // Verifying we didn't update the workload in this case, deadline should remain the original one.
    assertEquals(WORKLOAD_ID, actualWorkload.id)
    assertEquals(dataplaneId, actualWorkload.dataplaneId)
    assertEquals(WorkloadStatus.RUNNING, actualWorkload.status)
    // note that we do not refresh the deadline in this case
    assertEquals(originalDeadline, actualWorkload.deadline)
  }

  @Test
  fun `saving a workload writes all the expected fields`() {
    val label1 =
      WorkloadLabel(
        id = null,
        key = "key1",
        value = "value1",
        workload = null,
      )
    val label2 =
      WorkloadLabel(
        id = null,
        key = "key2",
        value = "value2",
        workload = null,
      )
    val labels = ArrayList<WorkloadLabel>()
    labels.add(label1)
    labels.add(label2)
    val signalInput = "signalInput"
    val dataplaneGroup = "dataplane-group-1"
    val priority = 0
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = null,
        status = WorkloadStatus.PENDING,
        workloadLabels = labels,
        deadline = defaultDeadline,
        signalInput = signalInput,
        dataplaneGroup = dataplaneGroup,
        priority = priority,
      )
    workloadRepo.save(workload)
    val persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertNull(persistedWorkload.get().dataplaneId)
    assertEquals(WorkloadStatus.PENDING, persistedWorkload.get().status)
    assertNotNull(persistedWorkload.get().createdAt)
    assertNotNull(persistedWorkload.get().updatedAt)
    assertNull(persistedWorkload.get().lastHeartbeatAt)
    assertNotNull(persistedWorkload.get().deadline)
    assertEquals(defaultDeadline.toEpochSecond(), persistedWorkload.get().deadline!!.toEpochSecond())
    assertEquals(2, persistedWorkload.get().workloadLabels!!.size)
    assertEquals(signalInput, persistedWorkload.get().signalInput)
    assertEquals(dataplaneGroup, persistedWorkload.get().dataplaneGroup)
    assertEquals(priority, persistedWorkload.get().priority)

    val workloadLabels = persistedWorkload.get().workloadLabels!!.toMutableList()
    workloadLabels.sortWith(Comparator.comparing(WorkloadLabel::key))
    assertEquals("key1", workloadLabels[0].key)
    assertEquals("value1", workloadLabels[0].value)
    assertNotNull(workloadLabels[0].id)
    assertEquals("key2", workloadLabels[1].key)
    assertEquals("value2", workloadLabels[1].value)
    assertNotNull(workloadLabels[1].id)
  }

  @Test
  fun `updating a workload status and deadline should update the workload`() {
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = null,
        status = WorkloadStatus.PENDING,
        geography = "US",
      )
    val newDeadline = OffsetDateTime.now().plusMinutes(10)
    workloadRepo.save(workload)
    workloadRepo.update(WORKLOAD_ID, WorkloadStatus.RUNNING, newDeadline)
    var persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertEquals(WorkloadStatus.RUNNING, persistedWorkload.get().status)
    assertEquals(newDeadline.toEpochSecond(), persistedWorkload.get().deadline!!.toEpochSecond())

    val newDeadline2 = OffsetDateTime.now().plusMinutes(20)
    workloadRepo.update(WORKLOAD_ID, WorkloadStatus.FAILURE, newDeadline2)
    persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertEquals(WorkloadStatus.FAILURE, persistedWorkload.get().status)
    assertEquals(newDeadline2.toEpochSecond(), persistedWorkload.get().deadline!!.toEpochSecond())
  }

  @Test
  fun `heartbeat should update deadline and lastHeartbeatAt`() {
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = null,
        status = WorkloadStatus.PENDING,
        geography = "US",
      )
    workloadRepo.save(workload)
    val now = OffsetDateTime.now()
    workloadRepo.update(WORKLOAD_ID, WorkloadStatus.RUNNING, now, now)
    var persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    // Using .toEpochSecond() here because of dagger, it is passing locally but there is nano second errors on dagger
    assertEquals(now.toEpochSecond(), persistedWorkload.get().lastHeartbeatAt?.toEpochSecond())
    assertEquals(now.toEpochSecond(), persistedWorkload.get().deadline?.toEpochSecond())
    assertEquals(WorkloadStatus.RUNNING, persistedWorkload.get().status)

    val nowPlusOneMinute = now.plus(1, ChronoUnit.MINUTES)
    workloadRepo.update(WORKLOAD_ID, WorkloadStatus.RUNNING, nowPlusOneMinute, nowPlusOneMinute)
    persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertEquals(nowPlusOneMinute.toEpochSecond(), persistedWorkload.get().lastHeartbeatAt?.toEpochSecond())
    assertEquals(nowPlusOneMinute.toEpochSecond(), persistedWorkload.get().deadline?.toEpochSecond())
  }

  // TODO: we should delete this once we are using atomic claims by default because it should be the only way to set a dataplane.
  @Test
  fun `updating a workload with a dataplane should update the workload`() {
    val workload =
      Fixtures.workload(
        id = WORKLOAD_ID,
        dataplaneId = null,
        status = WorkloadStatus.PENDING,
        geography = "AUTO",
      )
    workloadRepo.save(workload)
    workloadRepo.update(WORKLOAD_ID, "dataplaneId1", WorkloadStatus.RUNNING, defaultDeadline)
    var persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertEquals("dataplaneId1", persistedWorkload.get().dataplaneId)

    workloadRepo.update(WORKLOAD_ID, "dataplaneId2", WorkloadStatus.RUNNING, defaultDeadline)
    persistedWorkload = workloadRepo.findById(WORKLOAD_ID)
    assertTrue(persistedWorkload.isPresent)
    assertEquals("dataplaneId2", persistedWorkload.get().dataplaneId)
  }

  @Test
  fun `searchByMutexKeyAndStatusInList returns the workloads matching the search`() {
    val mutexKey = "mutex-search-test"
    val workload1 =
      Fixtures.workload(
        id = "workload-mutex-search-1",
        status = WorkloadStatus.PENDING,
        mutexKey = mutexKey,
      )
    workloadRepo.save(workload1)

    val match = workloadRepo.searchByMutexKeyAndStatusInList(mutexKey, listOf(WorkloadStatus.PENDING, WorkloadStatus.RUNNING))
    assertEquals(1, match.size)
    assertEquals(workload1.id, match[0].id)

    val emptyResult = workloadRepo.searchByMutexKeyAndStatusInList(mutexKey, listOf(WorkloadStatus.CLAIMED, WorkloadStatus.RUNNING))
    assertEquals(0, emptyResult.size)

    val mutexMismatch = workloadRepo.searchByMutexKeyAndStatusInList("mismatch", listOf(WorkloadStatus.PENDING, WorkloadStatus.RUNNING))
    assertEquals(0, mutexMismatch.size)
  }

  @Test
  fun `test search`() {
    val workload1 =
      Fixtures.workload(
        id = "workload1",
        dataplaneId = "dataplane1",
        status = WorkloadStatus.FAILURE,
        geography = "AUTO",
      )
    val workload2 =
      Fixtures.workload(
        id = "workload2",
        dataplaneId = "dataplane2",
        status = WorkloadStatus.SUCCESS,
        geography = "US",
      )
    workloadRepo.save(workload1)
    workloadRepo.save(workload2)
    val now = OffsetDateTime.now()
    val resulSearch1 = sortedSearch(null, null, now.plusDays(1))
    assertEquals(2, resulSearch1.size)
    assertEquals("workload1", resulSearch1[0].id)
    assertEquals("workload2", resulSearch1[1].id)

    val resultSearch2 = sortedSearch(null, listOf(WorkloadStatus.FAILURE, WorkloadStatus.SUCCESS), null)
    assertEquals(2, resultSearch2.size)
    assertEquals("workload1", resultSearch2[0].id)
    assertEquals("workload2", resultSearch2[1].id)

    val resultSearch3 = sortedSearch(listOf("dataplane1", "dataplane2"), null, null)
    assertEquals(2, resultSearch3.size)
    assertEquals("workload1", resultSearch3[0].id)
    assertEquals("workload2", resultSearch3[1].id)

    val resultSearch4 =
      sortedSearch(
        listOf("dataplane1", "dataplane2"),
        listOf(WorkloadStatus.FAILURE, WorkloadStatus.SUCCESS),
        now.plusDays(1),
      )
    assertEquals(2, resultSearch4.size)
    assertEquals("workload1", resultSearch4[0].id)
    assertEquals("workload2", resultSearch4[1].id)

    val resultSearch5 = sortedSearch(null, listOf(WorkloadStatus.FAILURE), now.plusDays(1))
    assertEquals(1, resultSearch5.size)
    assertEquals("workload1", resultSearch5[0].id)

    val resultSearch6 = sortedSearch(listOf("dataplane1"), null, now.plusDays(1))
    assertEquals(1, resultSearch6.size)
    assertEquals("workload1", resultSearch6[0].id)

    val resultSearch7 = sortedSearch(listOf("dataplane1", "dataplane2"), listOf(WorkloadStatus.FAILURE), null)
    assertEquals(1, resultSearch7.size)
    assertEquals("workload1", resultSearch7[0].id)

    val resultSearch8 = sortedSearch(listOf("dataplane1"), listOf(WorkloadStatus.FAILURE, WorkloadStatus.SUCCESS), now.plusDays(1))
    assertEquals(1, resultSearch8.size)
    assertEquals("workload1", resultSearch8[0].id)

    val resultSearch9 =
      sortedSearch(
        listOf("dataplane1", "dataplane2"),
        listOf(WorkloadStatus.FAILURE, WorkloadStatus.SUCCESS),
        now.minusDays(1),
      )
    assertEquals(0, resultSearch9.size)

    val resultSearch10 = sortedSearch(listOf("dataplane1", "dataplane2"), listOf(WorkloadStatus.CLAIMED), now.plusDays(1))
    assertEquals(0, resultSearch10.size)

    val resultSearch11 = sortedSearch(listOf("fakeDataplane"), listOf(WorkloadStatus.FAILURE, WorkloadStatus.SUCCESS), now.plusDays(1))
    assertEquals(0, resultSearch11.size)
  }

  @Test
  fun `test search by type status and creation date`() {
    val workload1 =
      Fixtures.workload(
        id = "workload1",
        dataplaneId = "dataplane1",
        status = WorkloadStatus.RUNNING,
        geography = "AUTO",
        type = WorkloadType.CHECK,
      )
    val workload2 =
      Fixtures.workload(
        id = "workload2",
        dataplaneId = "dataplane2",
        status = WorkloadStatus.SUCCESS,
        geography = "US",
        type = WorkloadType.SYNC,
      )
    workloadRepo.save(workload1)
    workloadRepo.save(workload2)
    val now = OffsetDateTime.now()
    var resultSearch = sortedSearchByTypeStatusCreatedDate(null, null, null, now.plusDays(1))
    assertEquals(2, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)
    assertEquals("workload2", resultSearch[1].id)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, null, listOf(WorkloadType.CHECK), now.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, null, listOf(WorkloadType.SPEC), now.plusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, listOf(WorkloadStatus.RUNNING), null, now.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, listOf(WorkloadStatus.CANCELLED), null, now.plusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, null, null, now.minusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByTypeStatusCreatedDate(null, listOf(WorkloadStatus.RUNNING), listOf(WorkloadType.SYNC), now.plusDays(1))
    assertEquals(0, resultSearch.size)
  }

  @Test
  fun `test search by type expired deadline`() {
    val deadline: OffsetDateTime = OffsetDateTime.now()
    val workload1 =
      Fixtures.workload(
        id = "workload1",
        dataplaneId = "dataplane1",
        status = WorkloadStatus.RUNNING,
        geography = "AUTO",
        type = WorkloadType.CHECK,
        deadline = deadline,
      )
    val workload2 =
      Fixtures.workload(
        id = "workload2",
        dataplaneId = "dataplane2",
        status = WorkloadStatus.CLAIMED,
        geography = "US",
        type = WorkloadType.SYNC,
        deadline = deadline,
      )
    workloadRepo.save(workload1)
    workloadRepo.save(workload2)
    var resultSearch = sortedSearchByExpiredDeadline(null, null, deadline.minusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(null, null, deadline)
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(null, null, deadline.plusDays(1))
    assertEquals(2, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)
    assertEquals("workload2", resultSearch[1].id)

    resultSearch = sortedSearchByExpiredDeadline(null, listOf(WorkloadStatus.RUNNING), deadline.minusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(null, listOf(WorkloadStatus.CANCELLED), deadline.minusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(null, listOf(WorkloadStatus.RUNNING), deadline.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)

    resultSearch = sortedSearchByExpiredDeadline(null, listOf(WorkloadStatus.CANCELLED), deadline.plusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane1", "dataplane2"), listOf(WorkloadStatus.RUNNING), deadline.minusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane1", "dataplane2"), listOf(WorkloadStatus.RUNNING), deadline)
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane1"), listOf(WorkloadStatus.RUNNING), deadline.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane2"), listOf(WorkloadStatus.RUNNING), deadline.plusDays(1))
    assertEquals(0, resultSearch.size)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane2"), listOf(WorkloadStatus.CLAIMED), deadline.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload2", resultSearch[0].id)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane1", "dataplane2"), listOf(WorkloadStatus.RUNNING), deadline.plusDays(1))
    assertEquals(1, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)

    resultSearch = sortedSearchByExpiredDeadline(listOf("dataplane1", "dataplane2"), null, deadline.plusDays(1))
    assertEquals(2, resultSearch.size)
    assertEquals("workload1", resultSearch[0].id)
    assertEquals("workload2", resultSearch[1].id)
  }

  @ParameterizedTest
  @MethodSource("pendingWorkloadMatrix")
  fun `poll workload gets enqueued workloads for provided params`(
    group: String,
    priority: Int,
    workloads: List<Workload>,
  ) {
    val seeds =
      listOf(
        Fixtures.workload(id = "other1"),
        Fixtures.workload(id = "other2"),
        Fixtures.workload(id = "other3"),
      )
    seeds.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(it.dataplaneGroup!!, it.priority!!, it.id)
    }
    workloads.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(group, priority, it.id)
    }

    val result = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)

    assertWorkloadsEqual(workloads, result)
  }

  @ParameterizedTest
  @MethodSource("pendingWorkloadMatrix")
  fun `subsequent calls to poll workload do not return duplicate workloads within poll deadline`(
    group: String,
    priority: Int,
    workloads: List<Workload>,
  ) {
    workloads.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(group, priority, it.id)
    }

    val result1 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)
    assertWorkloadsEqual(workloads, result1, "workloads are delivered as expected")

    val result2 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)
    assertWorkloadsEqual(listOf(), result2, "workloads are not re-delivered")

    val freshlyEnqueued =
      listOf(
        Fixtures.workload(id = "new1", dataplaneGroup = group, priority = priority),
        Fixtures.workload(id = "new2", dataplaneGroup = group, priority = priority),
      )

    freshlyEnqueued.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(group, priority, it.id)
    }

    val result3 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)
    assertWorkloadsEqual(freshlyEnqueued, result3, "new workloads get delivered as expected")
  }

  @ParameterizedTest
  @MethodSource("pendingWorkloadMatrix")
  fun `poll workload will re-deliver (return previously seen) workloads once poll deadline expires`(
    group: String,
    priority: Int,
    workloads: List<Workload>,
  ) {
    workloads.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(group, priority, it.id)
    }
    val result1 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10, redeliveryWindowSecs = 0)
    assertWorkloadsEqual(workloads, result1, "workloads are delivered as expected")

    val result2 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10, redeliveryWindowSecs = 60)
    assertWorkloadsEqual(workloads, result2, "workloads are re-delivered since we passed an empty window in the first poll")

    val result3 = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)
    assertWorkloadsEqual(listOf(), result3, "workloads are not re-delivered since we passed a non 0 window")
  }

  @ParameterizedTest
  @MethodSource("pendingWorkloadMatrix")
  fun `an acked workload is no longer considered enqueued`(
    group: String,
    priority: Int,
    workloads: List<Workload>,
  ) {
    workloads.forEach {
      workloadRepo.save(it)
      workloadQueueRepo.enqueueWorkload(group, priority, it.id)
    }
    // split the workloads in 2
    var index = 0
    val (toAck, unAcked) = workloads.partition { index++ % 2 == 0 }
    // ack half of them
    toAck.forEach {
      workloadQueueRepo.ackWorkloadQueueItem(it.id)
    }

    val result = workloadQueueRepo.pollWorkloadQueue(group, priority, quantity = 10)
    assertWorkloadsEqual(unAcked, result, "only un-acked workloads are delivered")
  }

  object Fixtures {
    const val WORKLOAD_ID = "test"
    val defaultDeadline: OffsetDateTime = OffsetDateTime.now()

    val label1 = { WorkloadLabel(key = "key-1", value = "value-1") }
    val label2 = { WorkloadLabel(key = "key-2", value = "value-2") }
    val label3 = { WorkloadLabel(key = "key-3", value = "value-3") }

    val labelList1 = { arrayListOf(label1(), label2()) }
    val labelList2 = { arrayListOf(label1(), label2(), label3()) }
    val labelList3 = { arrayListOf(label3()) }
    val labelList4 = { arrayListOf(label3(), label2()) }

    fun workload(
      id: String = WORKLOAD_ID,
      dataplaneId: String? = null,
      status: WorkloadStatus = WorkloadStatus.PENDING,
      workloadLabels: List<WorkloadLabel>? = null,
      inputPayload: String = "",
      logPath: String = "/",
      geography: String = "US",
      mutexKey: String = "",
      type: WorkloadType = WorkloadType.SYNC,
      deadline: OffsetDateTime = OffsetDateTime.now(),
      signalInput: String = "",
      dataplaneGroup: String = "",
      priority: Int = 0,
    ): Workload =
      Workload(
        id = id,
        dataplaneId = dataplaneId,
        status = status,
        workloadLabels = workloadLabels,
        inputPayload = inputPayload,
        logPath = logPath,
        mutexKey = mutexKey,
        type = type,
        deadline = deadline,
        signalInput = signalInput,
        dataplaneGroup = dataplaneGroup,
        priority = priority,
      )
  }

  companion object {
    private lateinit var context: ApplicationContext
    lateinit var workloadRepo: WorkloadRepository
    lateinit var workloadLabelRepo: WorkloadLabelRepository
    lateinit var workloadQueueRepo: WorkloadQueueRepository
    private lateinit var jooqDslContext: DSLContext

    // we run against an actual database to ensure micronaut data and jooq properly integrate
    private val container: PostgreSQLContainer<*> =
      PostgreSQLContainer(DatabaseConstants.DEFAULT_DATABASE_VERSION)
        .withDatabaseName("airbyte")
        .withUsername("docker")
        .withPassword("docker")

    @BeforeAll
    @JvmStatic
    fun setup() {
      container.start()
      // set the micronaut datasource properties to match our container we started up
      context =
        ApplicationContext.run(
          PropertySource.of(
            "test",
            mapOf(
              "datasources.default.driverClassName" to "org.postgresql.Driver",
              "datasources.default.db-type" to "postgres",
              "datasources.default.dialect" to "POSTGRES",
              "datasources.default.url" to container.jdbcUrl,
              "datasources.default.username" to container.username,
              "datasources.default.password" to container.password,
            ),
          ),
        )

      // removes micronaut transactional wrapper that doesn't play nice with our non-micronaut factories
      val dataSource = (context.getBean(DataSource::class.java) as DelegatingDataSource).targetDataSource
      jooqDslContext = DSLContextFactory.create(dataSource, SQLDialect.POSTGRES)
      val databaseProviders = TestDatabaseProviders(dataSource, jooqDslContext)

      // this line is what runs the migrations
      databaseProviders.createNewConfigsDatabase()
      workloadRepo = context.getBean(WorkloadRepository::class.java)
      workloadLabelRepo = context.getBean(WorkloadLabelRepository::class.java)
      workloadQueueRepo = context.getBean(WorkloadQueueRepository::class.java)
    }

    @AfterAll
    @JvmStatic
    fun dbDown() {
      container.close()
    }

    private fun sortedSearch(
      dataplaneIds: List<String>?,
      statuses: List<WorkloadStatus>?,
      updatedBefore: OffsetDateTime?,
    ): MutableList<Workload> {
      val workloads = workloadRepo.search(dataplaneIds, statuses, updatedBefore).toMutableList()
      workloads.sortWith(Comparator.comparing(Workload::id))
      return workloads
    }

    private fun sortedSearchByTypeStatusCreatedDate(
      dataplaneIds: List<String>?,
      statuses: List<WorkloadStatus>?,
      types: List<WorkloadType>?,
      createdBefore: OffsetDateTime?,
    ): MutableList<Workload> {
      val workloads = workloadRepo.searchByTypeStatusAndCreationDate(dataplaneIds, statuses, types, createdBefore).toMutableList()
      workloads.sortWith(Comparator.comparing(Workload::id))
      return workloads
    }

    private fun sortedSearchByExpiredDeadline(
      dataplaneIds: List<String>?,
      statuses: List<WorkloadStatus>?,
      deadline: OffsetDateTime,
    ): MutableList<Workload> {
      val workloads = workloadRepo.searchForExpiredWorkloads(dataplaneIds, statuses, deadline).toMutableList()
      workloads.sortWith(Comparator.comparing(Workload::id))
      return workloads
    }

    /**
     * Compares 2 lists of workloads ignoring order and normalizing timestamp precision.
     */
    fun assertWorkloadsEqual(
      a: List<Workload>,
      b: List<Workload>,
      message: String? = null,
    ) {
      val aGroomed =
        a
          .map {
            it.createdAt = it.createdAt?.truncatedTo(ChronoUnit.MILLIS)
            it.updatedAt = it.updatedAt?.truncatedTo(ChronoUnit.MILLIS)
            it.deadline = it.deadline?.truncatedTo(ChronoUnit.MILLIS)
            it.workloadLabels = it.workloadLabels ?: listOf()
            it
          }.sortedBy { it.id }
      val bGroomed =
        b
          .map {
            it.createdAt = it.createdAt?.truncatedTo(ChronoUnit.MILLIS)
            it.updatedAt = it.updatedAt?.truncatedTo(ChronoUnit.MILLIS)
            it.deadline = it.deadline?.truncatedTo(ChronoUnit.MILLIS)
            it.workloadLabels = it.workloadLabels ?: listOf()
            it
          }.sortedBy { it.id }

      assertEquals(aGroomed, bGroomed, message)
    }

    @JvmStatic
    fun pendingWorkloadMatrix(): List<Arguments> =
      listOf(
        Arguments.of(
          "group-1",
          0,
          listOf(
            Fixtures.workload(id = "1", dataplaneGroup = "group-1", priority = 0, workloadLabels = labelList1()),
            Fixtures.workload(id = "2", dataplaneGroup = "group-1", priority = 0, workloadLabels = labelList2()),
            Fixtures.workload(id = "3", dataplaneGroup = "group-1", priority = 0, workloadLabels = labelList3()),
          ),
        ),
        Arguments.of(
          "group-2",
          1,
          listOf(
            Fixtures.workload(id = "12", dataplaneGroup = "group-2", priority = 1, workloadLabels = labelList4()),
            Fixtures.workload(id = "32", dataplaneGroup = "group-2", priority = 1, workloadLabels = labelList2()),
          ),
        ),
        Arguments.of("group-3", 0, listOf(Fixtures.workload(id = "43", dataplaneGroup = "group-3", priority = 0, workloadLabels = labelList3()))),
        Arguments.of(
          "group-1",
          1,
          listOf(
            Fixtures.workload(id = "14", dataplaneGroup = "group-1", priority = 1, workloadLabels = labelList1()),
            Fixtures.workload(id = "24", dataplaneGroup = "group-1", priority = 1, workloadLabels = labelList4()),
          ),
        ),
      )
  }
}
