package net.alan.jupiter.domain.summary

import net.alan.jupiter.domain.model.CheckStatus
import net.alan.jupiter.domain.model.SyncState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 总汇总本地聚合：应到/实到/请假/旷宿/未查 + 待同步标注 + 按楼栋/班级分组。
 */
class SummaryAggregatorTest {

    private fun row(
        studentId: String,
        status: CheckStatus?,
        state: SyncState?,
        taskId: String = "task-1",
        taskDate: String? = "2026-11-05",
        building: String = "B4",
        className: String = "铁道机车2601"
    ) = SummaryRow(
        taskId = taskId,
        taskDate = taskDate,
        buildingName = building,
        className = className,
        roomNumber = "302",
        studentId = studentId,
        statusCode = status?.code,
        syncState = state?.name
    )

    @Test
    fun `counts split actual leave absent and unchecked`() {
        val rows = listOf(
            row("s1", CheckStatus.PRESENT, SyncState.ACKED),
            row("s2", CheckStatus.PERSONAL_LEAVE, SyncState.QUEUED),
            row("s3", CheckStatus.ABSENT, SyncState.ACKED),
            row("s4", null, null)
        )
        val result = SummaryAggregator.aggregate(SummaryScope.ALL, rows, asOf = 1000L)

        assertEquals(4, result.counts.should)
        assertEquals(1, result.counts.actual)
        assertEquals(1, result.counts.leave)
        assertEquals(1, result.counts.absent)
        assertEquals(1, result.counts.unchecked)
        assertEquals(1, result.pending)
        assertEquals(3, result.counts.checked)
        assertEquals(0.25f, result.counts.completionRate, 0.0001f)
        assertEquals(4, result.studentCount)
        assertEquals(1, result.taskCount)
        assertEquals(1000L, result.asOf)
    }

    @Test
    fun `local draft does not count as checked`() {
        val rows = listOf(
            row("s1", CheckStatus.PRESENT, SyncState.LOCAL_DRAFT),
            row("s2", CheckStatus.PRESENT, SyncState.ACKED)
        )
        val result = SummaryAggregator.aggregate(SummaryScope.ALL, rows, asOf = 0L)

        assertEquals(2, result.counts.should)
        assertEquals(1, result.counts.actual)
        assertEquals(1, result.counts.unchecked)
        assertEquals(0, result.pending)
    }

    @Test
    fun `late return counts as actual`() {
        val result = SummaryAggregator.aggregate(
            SummaryScope.ALL,
            listOf(row("s1", CheckStatus.LATE_RETURN, SyncState.ACKED)),
            asOf = 0L
        )
        assertEquals(1, result.counts.actual)
        assertEquals(1, result.counts.checked)
    }

    @Test
    fun `failed records still count as local estimate and pending`() {
        val result = SummaryAggregator.aggregate(
            SummaryScope.ALL,
            listOf(row("s1", CheckStatus.PRESENT, SyncState.FAILED)),
            asOf = 0L
        )
        assertEquals(1, result.counts.actual)
        assertEquals(1, result.pending)
    }

    @Test
    fun `buildings and classes group independently`() {
        val rows = listOf(
            row("s1", CheckStatus.PRESENT, SyncState.ACKED, building = "B4", className = "A"),
            row("s2", CheckStatus.PRESENT, SyncState.ACKED, building = "B4", className = "B"),
            row("s3", CheckStatus.ABSENT, SyncState.ACKED, building = "B5", className = "A")
        )
        val result = SummaryAggregator.aggregate(SummaryScope.ALL, rows, asOf = 0L)

        val b4 = result.buildings.first { it.key == "B4" }
        val b5 = result.buildings.first { it.key == "B5" }
        assertEquals(2, b4.should)
        assertEquals(2, b4.actual)
        assertEquals(1.0f, b4.rate, 0.0001f)
        assertEquals(1, b5.should)
        assertEquals(0, b5.actual)

        val classA = result.classes.first { it.key == "A" }
        val classB = result.classes.first { it.key == "B" }
        assertEquals(2, classA.should)
        assertEquals(1, classA.actual)
        assertEquals(1, classB.actual)
    }

    @Test
    fun `today scope filters by task date`() {
        val today = LocalDate.now().toString()
        val rows = listOf(
            row("s1", CheckStatus.PRESENT, SyncState.ACKED, taskId = "t-today", taskDate = today),
            row("s2", CheckStatus.PRESENT, SyncState.ACKED, taskId = "t-old", taskDate = "2020-01-01")
        )
        val todayResult = SummaryAggregator.aggregate(SummaryScope.TODAY, rows, asOf = 0L)
        assertEquals(1, todayResult.counts.should)
        assertEquals(1, todayResult.taskCount)

        val allResult = SummaryAggregator.aggregate(SummaryScope.ALL, rows, asOf = 0L)
        assertEquals(2, allResult.counts.should)
        assertEquals(2, allResult.taskCount)
    }

    @Test
    fun `week scope covers the last seven days`() {
        val today = LocalDate.now()
        val rows = listOf(
            row("s1", CheckStatus.PRESENT, SyncState.ACKED, taskDate = today.toString()),
            row("s2", CheckStatus.PRESENT, SyncState.ACKED, taskDate = today.minusDays(6).toString()),
            row("s3", CheckStatus.PRESENT, SyncState.ACKED, taskDate = today.minusDays(7).toString())
        )
        val result = SummaryAggregator.aggregate(SummaryScope.WEEK, rows, asOf = 0L)
        assertEquals(2, result.counts.should)
    }

    @Test
    fun `empty input produces zeroed summary`() {
        val result = SummaryAggregator.aggregate(SummaryScope.ALL, emptyList(), asOf = 42L)
        assertEquals(0, result.counts.should)
        assertEquals(0f, result.counts.completionRate, 0.0001f)
        assertTrue(result.buildings.isEmpty())
        assertTrue(result.classes.isEmpty())
        assertEquals(42L, result.asOf)
    }
}
