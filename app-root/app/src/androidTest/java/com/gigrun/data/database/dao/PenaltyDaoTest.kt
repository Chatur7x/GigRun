package com.gigrun.data.database.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gigrun.data.database.AppDatabase
import com.gigrun.data.database.entities.Penalty
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PenaltyDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PenaltyDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.penaltyDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertReturnsNonZeroId() = runTest {
        val id = dao.insert(Penalty(
            platform = "Blinkit",
            amountInr = 50.0,
            reason = "Late delivery",
            timestamp = System.currentTimeMillis()
        ))
        assertThat(id, not(equalTo(0L)))
    }

    @Test
    fun getAllPenaltiesFlowEmitsInsertedRow() = runTest {
        val inserted = dao.insert(Penalty(
            platform = "Blinkit",
            amountInr = 50.0,
            reason = "Late delivery",
            timestamp = System.currentTimeMillis()
        ))
        val firstEmission = dao.getAllPenalties().first()
        assertThat(firstEmission.size, equalTo(1))
        assertThat(firstEmission[0].id, equalTo(inserted))
        assertThat(firstEmission[0].platform, equalTo("Blinkit"))
        assertThat(firstEmission[0].amountInr, equalTo(50.0))
    }

    @Test
    fun getMonthlyTotalFlowReturnsCorrectSum() = runTest {
        val now = System.currentTimeMillis()
        val startOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val endOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        dao.insert(Penalty(platform = "Blinkit", amountInr = 100.0, reason = "A", timestamp = now))
        dao.insert(Penalty(platform = "Uber", amountInr = 200.0, reason = "B", timestamp = now))
        dao.insert(Penalty(platform = "Rapido", amountInr = 50.0, reason = "C", timestamp = now - 86_400_000)) // outside range

        val total = dao.getMonthlyTotal(startOfMonth, endOfMonth).first()
        assertThat(total, equalTo(350.0))
    }

    @Test
    fun getMonthlyTotalReturnsZeroWhenEmpty() = runTest {
        val startOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val endOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        val total = dao.getMonthlyTotal(startOfMonth, endOfMonth).first()
        assertThat(total, equalTo(0.0))
    }

    @Test
    fun getMonthlyTotalRespectsRangeBounds() = runTest {
        val startOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val endOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        // Insert one inside the month, one outside
        dao.insert(Penalty(platform = "Blinkit", amountInr = 100.0, reason = "Inside", timestamp = System.currentTimeMillis()))
        dao.insert(Penalty(platform = "Uber", amountInr = 500.0, reason = "Outside", timestamp = endOfMonth + 86_400_000))

        val total = dao.getMonthlyTotal(startOfMonth, endOfMonth).first()
        assertThat(total, equalTo(100.0)) // Only the inside one counts
    }

    @Test
    fun getPlatformBreakdownGroupsCorrectly() = runTest {
        val now = System.currentTimeMillis()
        val startOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()
        val endOfMonth = java.time.LocalDate.now().withDayOfMonth(1)
            .plusMonths(1).atStartOfDay(java.time.ZoneId.systemDefault())
            .toInstant().toEpochMilli()

        dao.insert(Penalty(platform = "Blinkit", amountInr = 100.0, reason = "A", timestamp = now))
        dao.insert(Penalty(platform = "Blinkit", amountInr = 50.0, reason = "B", timestamp = now))
        dao.insert(Penalty(platform = "Uber", amountInr = 200.0, reason = "C", timestamp = now))

        val breakdown = dao.getPlatformBreakdown(startOfMonth, endOfMonth).first()
        assertThat(breakdown.size, equalTo(2))
        val blinkit = breakdown.find { it.platform == "Blinkit" } ?: error("Blinkit missing")
        val uber = breakdown.find { it.platform == "Uber" } ?: error("Uber missing")
        assertThat(blinkit.total, equalTo(150.0))
        assertThat(uber.total, equalTo(200.0))
    }

    @Test
    fun setDisputedUpdatesFlag() = runTest {
        val id = dao.insert(Penalty(
            platform = "Blinkit",
            amountInr = 100.0,
            reason = "Test",
            timestamp = System.currentTimeMillis(),
            isDisputed = false
        ))

        dao.setDisputed(id, true)

        val updated = dao.getAllPenalties().first().first()
        assertThat(updated.isDisputed, equalTo(true))
        assertThat(updated.id, equalTo(id))
    }

    @Test
    fun deleteByIdRemovesRow() = runTest {
        val id = dao.insert(Penalty(
            platform = "Blinkit",
            amountInr = 100.0,
            reason = "Test",
            timestamp = System.currentTimeMillis()
        ))

        val before = dao.getAllPenalties().first()
        assertThat(before.size, equalTo(1))

        dao.deleteById(id)

        val after = dao.getAllPenalties().first()
        assertThat(after.isEmpty(), equalTo(true))
    }
}