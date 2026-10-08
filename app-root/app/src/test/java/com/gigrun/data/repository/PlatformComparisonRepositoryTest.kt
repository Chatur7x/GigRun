package com.gigrun.data.repository

import com.gigrun.data.database.dao.EarningDao
import com.gigrun.data.database.dao.ExpenseDao
import com.gigrun.data.database.dao.FuelLogDao
import com.gigrun.data.database.dao.PlatformActivityRow
import com.gigrun.data.database.dao.PlatformEarningRow
import com.gigrun.data.database.dao.TripDao
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Interface-implementing fakes, not mocks — the repository only reads four
 * aggregate queries, so stubbing the DAO surface is cheaper and survives
 * signature changes on unrelated DAO methods.
 */
private class FakeEarningDao(private val rows: List<PlatformEarningRow>) : EarningDao {
    var lastStart: Long? = null
    var lastEnd: Long? = null
    override suspend fun getRevenueByPlatform(start: Long, end: Long): List<PlatformEarningRow> {
        lastStart = start
        lastEnd = end
        return rows
    }
    override suspend fun insert(earning: com.gigrun.data.database.entities.Earning): Long = 0L
    override suspend fun update(earning: com.gigrun.data.database.entities.Earning) = Unit
    override fun getEarningsForTrip(tripId: Long) = throw UnsupportedOperationException()
    override suspend fun countSimilar(tripId: Long, amount: Double, since: Long) = 0
    override suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double? = null
    override suspend fun getTotalEarningsSince(startTime: Long): Double? = null
    override suspend fun getEarningsByPlatformSince(startTime: Long): List<PlatformEarningRow> = rows
}

private class FakeTripDao(private val rows: List<PlatformActivityRow>) : TripDao {
    override suspend fun getActivityByPlatform(start: Long, end: Long): List<PlatformActivityRow> = rows
    override suspend fun insert(trip: com.gigrun.data.database.entities.Trip): Long = 0L
    override suspend fun update(trip: com.gigrun.data.database.entities.Trip) = Unit
    override suspend fun delete(trip: com.gigrun.data.database.entities.Trip) = Unit
    override fun getTripsForShift(shiftId: Long) = throw UnsupportedOperationException()
    override suspend fun getTripById(id: Long) = null
    override fun getTripsForDay(startOfDay: Long, endOfDay: Long) = throw UnsupportedOperationException()
    override fun getTripsSince(startTime: Long) = throw UnsupportedOperationException()
    override suspend fun getActiveTrip(shiftId: Long) = null
    override suspend fun getLatestTripForShift(shiftId: Long) = null
    override suspend fun getDayTotals(s: Long, e: Long) =
        throw UnsupportedOperationException()
    override suspend fun getSurgeTotals(s: Long, e: Long) =
        throw UnsupportedOperationException()
    override suspend fun getAllOpenTrips(): List<com.gigrun.data.database.entities.Trip> = emptyList()
    override suspend fun attachEarning(
        trip: com.gigrun.data.database.entities.Trip,
        earningDao: EarningDao,
        earning: com.gigrun.data.database.entities.Earning
    ) = Unit
    override suspend fun getTripCountForDay(startOfDay: Long, endOfDay: Long) = 0
    override suspend fun getTotalDistanceForDay(startOfDay: Long, endOfDay: Long): Double? = null
    override suspend fun getTotalWaitTimeForDay(startOfDay: Long, endOfDay: Long): Int? = null
    override suspend fun getTotalEarningsForDay(startOfDay: Long, endOfDay: Long): Double? = null
    override suspend fun getPlatformStats(startTime: Long) = throw UnsupportedOperationException()
}

private class FakeFuelLogDao(private val total: Double?) : FuelLogDao {
    override suspend fun getTotalFuel(start: Long, end: Long): Double? = total
    override suspend fun insert(fuelLog: com.gigrun.data.database.entities.FuelLog): Long = 0L
    override suspend fun update(fuelLog: com.gigrun.data.database.entities.FuelLog) = Unit
    override suspend fun delete(fuelLog: com.gigrun.data.database.entities.FuelLog) = Unit
    override fun getAllFuelLogs() = throw UnsupportedOperationException()
    override fun getFuelLogsForVehicle(vehicleId: Long) = throw UnsupportedOperationException()
    override suspend fun getActiveFuelLogForVehicle(vehicleId: Long) = null
    override suspend fun getActiveFuelLogSync() = null
    override suspend fun getLatestFuelLogWithinTime(vehicleId: Long, timeLimit: Long) = null
}

private class FakeExpenseDao(private val total: Double?) : ExpenseDao {
    override suspend fun getTotalNonFuelExpenses(start: Long, end: Long): Double? = total
    override suspend fun insert(expense: com.gigrun.data.database.entities.Expense): Long = 0L
    override suspend fun update(expense: com.gigrun.data.database.entities.Expense) = Unit
    override suspend fun delete(expense: com.gigrun.data.database.entities.Expense) = Unit
    override fun getAllExpenses() = throw UnsupportedOperationException()
    override fun getExpensesForRange(start: Long, end: Long) = throw UnsupportedOperationException()
    override suspend fun getTotalForRange(start: Long, end: Long): Double? = null
    override suspend fun getDeductibleTotalForRange(start: Long, end: Long): Double? = null
    override suspend fun getCategoryBreakdown(start: Long, end: Long) =
        throw UnsupportedOperationException()
    override suspend fun getTotalByCategory(category: String, start: Long, end: Long): Double? = null
    override suspend fun deleteById(id: Long) = Unit
}

class PlatformComparisonRepositoryTest {

    private fun repo(
        revenue: List<PlatformEarningRow> = emptyList(),
        activity: List<PlatformActivityRow> = emptyList(),
        fuel: Double? = 0.0,
        expenses: Double? = 0.0
    ) = PlatformComparisonRepository(
        earningDao = FakeEarningDao(revenue),
        tripDao = FakeTripDao(activity),
        fuelLogDao = FakeFuelLogDao(fuel),
        expenseDao = FakeExpenseDao(expenses)
    )

    @Test
    fun emptyData_yieldsEmptyList() = runTest {
        assertTrue(repo().getComparison(0L, 1L).isEmpty())
    }

    @Test
    fun revenueWithoutTrips_hasZeroHoursAndZeroRate() = runTest {
        val rows = repo(revenue = listOf(PlatformEarningRow("Blinkit", 500.0))).getComparison(0L, 1L)

        assertEquals(1, rows.size)
        val row = rows.first()
        assertEquals(500.0, row.revenueInr, 0.001)
        assertEquals(0.0, row.activeHours, 0.001)
        // Cost allocation is zero too — share is 0 when total hours is 0.
        assertEquals(0.0, row.allocatedCostInr, 0.001)
        assertEquals(500.0, row.netInr, 0.001)
        assertEquals(0.0, row.netPerHourInr, 0.001)
        assertTrue(!row.hasTrackedHours)
    }

    @Test
    fun twoPlatforms_splitCostByActiveHoursShare() = runTest {
        val rows = repo(
            revenue = listOf(PlatformEarningRow("Blinkit", 3000.0), PlatformEarningRow("Zepto", 1500.0)),
            activity = listOf(
                PlatformActivityRow("Blinkit", 10.0, 40.0, 20),
                PlatformActivityRow("Zepto", 5.0, 20.0, 10)
            ),
            fuel = 300.0,
            expenses = 100.0
        ).getComparison(0L, 1L)

        // totalCost 400 → Blinkit share 10/15 = 2/3 → 266.67, Zepto share 1/3 → 133.33
        val blinkit = rows.first { it.platform == "Blinkit" }
        val zepto = rows.first { it.platform == "Zepto" }

        assertEquals(266.6666, blinkit.allocatedCostInr, 0.01)
        assertEquals(133.3333, zepto.allocatedCostInr, 0.01)
        assertEquals(2733.3333, blinkit.netInr, 0.01)
        assertEquals(273.3333, blinkit.netPerHourInr, 0.01)
        assertEquals(273.3333, zepto.netPerHourInr, 0.01)
        // Equal net rate → tie; ordering between equal keys is unspecified, so only
        // assert the descending property rather than a fixed winner.
        assertTrue(rows.zipWithNext().all { (a, b) -> a.netPerHourInr >= b.netPerHourInr })
    }

    @Test
    fun zeroTotalActiveHours_allocatesNothingAndNeverDividesByZero() = runTest {
        val rows = repo(
            revenue = listOf(PlatformEarningRow("Blinkit", 900.0)),
            activity = emptyList(),
            fuel = 500.0,
            expenses = 250.0
        ).getComparison(0L, 1L)

        val row = rows.single()
        assertEquals(0.0, row.allocatedCostInr, 0.001)
        assertEquals(900.0, row.netInr, 0.001)
        assertEquals(0.0, row.netPerHourInr, 0.001)
    }

    @Test
    fun costsExceedingRevenue_produceNegativeNet() = runTest {
        val rows = repo(
            revenue = listOf(PlatformEarningRow("Rapido", 100.0)),
            activity = listOf(PlatformActivityRow("Rapido", 2.0, 8.0, 3)),
            fuel = 400.0,
            expenses = 100.0
        ).getComparison(0L, 1L)

        val row = rows.single()
        assertEquals(500.0, row.allocatedCostInr, 0.001)
        assertEquals(-400.0, row.netInr, 0.001)
        assertEquals(-200.0, row.netPerHourInr, 0.001)
    }

    @Test
    fun rowsAreSortedByNetPerHourDescending() = runTest {
        val rows = repo(
            revenue = listOf(
                PlatformEarningRow("Blinkit", 1000.0),
                PlatformEarningRow("Zepto", 5000.0),
                PlatformEarningRow("Rapido", 2000.0)
            ),
            activity = listOf(
                PlatformActivityRow("Blinkit", 5.0, 10.0, 6),
                PlatformActivityRow("Zepto", 5.0, 10.0, 6),
                PlatformActivityRow("Rapido", 5.0, 10.0, 6)
            )
        ).getComparison(0L, 1L)

        assertEquals(listOf("Zepto", "Rapido", "Blinkit"), rows.map { it.platform })
    }

    @Test
    fun blankPlatformNames_areExcluded() = runTest {
        val rows = repo(
            revenue = listOf(PlatformEarningRow("  ", 100.0), PlatformEarningRow("Blinkit", 50.0))
        ).getComparison(0L, 1L)

        assertEquals(listOf("Blinkit"), rows.map { it.platform })
    }

    @Test
    fun nullAggregateSums_areTreatedAsZero() = runTest {
        val rows = repo(
            revenue = listOf(PlatformEarningRow("Blinkit", 100.0)),
            activity = listOf(PlatformActivityRow("Blinkit", 4.0, 10.0, 4)),
            fuel = null,
            expenses = null
        ).getComparison(0L, 1L)

        val row = rows.single()
        assertEquals(0.0, row.allocatedCostInr, 0.001)
        assertEquals(100.0, row.netInr, 0.001)
        assertEquals(25.0, row.netPerHourInr, 0.001)
    }

    @Test
    fun windowBoundsArePassedThroughUnchanged() = runTest {
        val earningDao = FakeEarningDao(listOf(PlatformEarningRow("Blinkit", 10.0)))
        val subject = PlatformComparisonRepository(
            earningDao = earningDao,
            tripDao = FakeTripDao(emptyList()),
            fuelLogDao = FakeFuelLogDao(0.0),
            expenseDao = FakeExpenseDao(0.0)
        )
        subject.getComparison(1_700_000_000_000L, 1_700_086_400_000L)

        assertEquals(1_700_000_000_000L, earningDao.lastStart)
        assertEquals(1_700_086_400_000L, earningDao.lastEnd)
    }

    @Test
    fun platformWithTripsButNoEarnings_isStillListed() = runTest {
        val rows = repo(
            revenue = emptyList(),
            activity = listOf(PlatformActivityRow("Blinkit", 3.0, 12.0, 4)),
            fuel = 120.0
        ).getComparison(0L, 1L)

        val row = rows.single()
        assertEquals("Blinkit", row.platform)
        assertEquals(0.0, row.revenueInr, 0.001)
        assertEquals(120.0, row.allocatedCostInr, 0.001)
        assertEquals(-120.0, row.netInr, 0.001)
        assertEquals(-40.0, row.netPerHourInr, 0.001)
    }
}