package net.alan.jupiter.domain.sync

import net.alan.jupiter.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 上行队列纯逻辑：clientOpId 复用、失败状态机、taskDate 过滤与分组。
 *
 * 注：一次房间提交 = 一个 clientOpId，房间内所有学生记录共用它；
 * 重试/补交复用同一个值（服务端按 (clientOpId, studentId) 去重）。
 */
class SyncPolicyTest {

    @Test
    fun `new client op id is a uuid`() {
        val id = SyncPolicy.newClientOpId()
        assertEquals(36, id.length)
        assertEquals(4, id.count { it == '-' })
    }

    @Test
    fun `retry reuses existing client op id`() {
        val existing = "9ac1f0e0-0000-4000-8000-000000000000"
        assertEquals(existing, SyncPolicy.resolveClientOpId(existing))
        assertEquals(existing, SyncPolicy.resolveClientOpId("  $existing  "))
    }

    @Test
    fun `missing client op id generates a fresh one`() {
        val first = SyncPolicy.resolveClientOpId(null)
        val second = SyncPolicy.resolveClientOpId("   ")
        assertTrue(first.isNotBlank())
        assertTrue(second.isNotBlank())
        assertNotEquals(first, second)
    }

    @Test
    fun `failure keeps queueing until the third retry`() {
        assertEquals(SyncState.QUEUED, SyncPolicy.nextStateAfterFailure(1))
        assertEquals(SyncState.QUEUED, SyncPolicy.nextStateAfterFailure(2))
        assertEquals(SyncState.QUEUED, SyncPolicy.nextStateAfterFailure(3))
        assertEquals(SyncState.FAILED, SyncPolicy.nextStateAfterFailure(4))
    }

    @Test
    fun `only queued and failed records are submittable`() {
        assertTrue(SyncPolicy.canSubmit(SyncState.QUEUED))
        assertTrue(SyncPolicy.canSubmit(SyncState.FAILED))
        assertFalse(SyncPolicy.canSubmit(SyncState.LOCAL_DRAFT))
        assertFalse(SyncPolicy.canSubmit(SyncState.IN_FLIGHT))
        assertFalse(SyncPolicy.canSubmit(SyncState.ACKED))
        assertFalse(SyncPolicy.canSubmit(SyncState.CONFLICT))
    }

    @Test
    fun `short client op id for logs`() {
        assertEquals("9ac1f0e0", SyncPolicy.shortClientOpId("9ac1f0e0-0000-4000-8000-000000000000"))
        assertEquals("--", SyncPolicy.shortClientOpId(null))
    }

    // ---------------- taskDate 时间段 ----------------

    @Test
    fun `empty window selects everything including unknown dates`() {
        assertTrue(TaskDateWindow.inWindow("2026-11-05"))
        assertTrue(TaskDateWindow.inWindow(null))
        assertTrue(TaskDateWindow.inWindow(null, "", ""))
    }

    @Test
    fun `window is inclusive on both ends`() {
        assertTrue(TaskDateWindow.inWindow("2026-11-05", "2026-11-05", "2026-11-07"))
        assertTrue(TaskDateWindow.inWindow("2026-11-07", "2026-11-05", "2026-11-07"))
        assertTrue(TaskDateWindow.inWindow("2026-11-06", "2026-11-05", "2026-11-07"))
    }

    @Test
    fun `window excludes records outside the range`() {
        assertFalse(TaskDateWindow.inWindow("2026-11-04", "2026-11-05", "2026-11-07"))
        assertFalse(TaskDateWindow.inWindow("2026-11-08", "2026-11-05", "2026-11-07"))
    }

    @Test
    fun `window keeps records without taskDate`() {
        // 日期未知时不能静默丢弃，宁可补交
        assertTrue(TaskDateWindow.inWindow(null, "2026-11-05", "2026-11-07"))
        assertTrue(TaskDateWindow.inWindow("", "2026-11-05", "2026-11-07"))
    }

    @Test
    fun `open ended windows work`() {
        assertTrue(TaskDateWindow.inWindow("2026-11-09", "2026-11-05", null))
        assertFalse(TaskDateWindow.inWindow("2026-11-04", "2026-11-05", null))
        assertTrue(TaskDateWindow.inWindow("2026-11-04", null, "2026-11-05"))
        assertFalse(TaskDateWindow.inWindow("2026-11-06", null, "2026-11-05"))
    }

    @Test
    fun `iso date validation`() {
        assertTrue(TaskDateWindow.isValidIsoDate("2026-11-05"))
        assertFalse(TaskDateWindow.isValidIsoDate("2026-13-45"))
        assertFalse(TaskDateWindow.isValidIsoDate("11-05-2026"))
        assertFalse(TaskDateWindow.isValidIsoDate(null))
    }

    // ---------------- 分组 ----------------

    private fun submission(
        id: String,
        taskId: String,
        taskDate: String?,
        createdAt: Long = 0L
    ) = PendingSubmission(
        clientOpId = id,
        taskId = taskId,
        roomId = "room-$id",
        taskDate = taskDate,
        retryCount = 0,
        createdAt = createdAt
    )

    @Test
    fun `select sorts by taskDate then creation time`() {
        val selected = SubmitGrouper.select(
            listOf(
                submission("c", "t3", "2026-11-07", 3),
                submission("a", "t1", "2026-11-05", 2),
                submission("b", "t2", "2026-11-05", 1)
            )
        )
        assertEquals(listOf("b", "a", "c"), selected.map { it.clientOpId })
    }

    @Test
    fun `select filters by task date window`() {
        val selected = SubmitGrouper.select(
            listOf(
                submission("old", "t1", "2026-11-01"),
                submission("in", "t2", "2026-11-05"),
                submission("new", "t3", "2026-11-09"),
                submission("unknown", "t4", null)
            ),
            fromIso = "2026-11-05",
            toIso = "2026-11-07"
        )
        // 无日期记录也保留，并按 taskDate 空值排在最前
        assertEquals(listOf("unknown", "in"), selected.map { it.clientOpId })
    }

    @Test
    fun `group by task date keeps batches of the same day together`() {
        val groups = SubmitGrouper.groupByTaskDate(
            SubmitGrouper.select(
                listOf(
                    submission("a", "t1", "2026-11-05", 1),
                    submission("b", "t1", "2026-11-05", 2),
                    submission("c", "t2", "2026-11-06", 3)
                )
            )
        )
        assertEquals(2, groups.size)
        assertEquals("2026-11-05", groups[0].taskDate)
        assertEquals(listOf("a", "b"), groups[0].submissions.map { it.clientOpId })
        assertEquals("2026-11-06", groups[1].taskDate)
        assertEquals(listOf("c"), groups[1].submissions.map { it.clientOpId })
    }
}
