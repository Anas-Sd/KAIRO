package com.kairo.app

import com.kairo.app.ui.utils.DateUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class DateUtilsTest {

    @Test
    fun testParseDueDateMillis_relativeDates() {
        val todayMillis = DateUtils.parseDueDateMillis("Today")
        val cal = Calendar.getInstance().apply { timeInMillis = todayMillis }
        assertEquals(12, cal.get(Calendar.HOUR_OF_DAY))

        val tomorrowMillis = DateUtils.parseDueDateMillis("Tomorrow")
        val tomCal = Calendar.getInstance().apply { timeInMillis = tomorrowMillis }
        val diffDays = (tomCal.timeInMillis - cal.timeInMillis) / (24 * 60 * 60 * 1000L)
        assertEquals(1, diffDays)

        val yesterdayMillis = DateUtils.parseDueDateMillis("Yesterday")
        val yestCal = Calendar.getInstance().apply { timeInMillis = yesterdayMillis }
        val diffYest = (cal.timeInMillis - yestCal.timeInMillis) / (24 * 60 * 60 * 1000L)
        assertEquals(1, diffYest)
    }

    @Test
    fun testParseDueDateMillis_formattedDates() {
        val parsed = DateUtils.parseDueDateMillis("2026-10-25")
        val cal = Calendar.getInstance().apply { timeInMillis = parsed }
        assertEquals(2026, cal.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, cal.get(Calendar.MONTH))
        assertEquals(25, cal.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun testCalculateNextRepeatDueDate_everyday() {
        val now = System.currentTimeMillis()
        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = now,
            currentDueDateStr = "Today",
            repeatType = "EVERYDAY",
            repeatDays = null,
            repeatDates = null
        )

        assertNotNull(result)
        val (displayDate, nextMillis) = result!!
        assertTrue(nextMillis > now)

        val cal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        val todayCal = Calendar.getInstance().apply { timeInMillis = now }
        // Next occurrence is 1 day later
        val expectedDay = (todayCal.get(Calendar.DAY_OF_YEAR) % 365) + 1
        assertEquals(expectedDay, (cal.get(Calendar.DAY_OF_YEAR) % 365))
    }

    @Test
    fun testCalculateNextRepeatDueDate_allSevenDays() {
        val now = System.currentTimeMillis()
        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = now,
            currentDueDateStr = "Today",
            repeatType = "DAYS",
            repeatDays = "MON,TUE,WED,THU,FRI,SAT,SUN",
            repeatDates = null
        )

        assertNotNull(result)
        val (displayDate, nextMillis) = result!!
        assertTrue(nextMillis > now)
    }

    @Test
    fun testCalculateNextRepeatDueDate_monthly() {
        val now = System.currentTimeMillis()
        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = now,
            currentDueDateStr = "Today",
            repeatType = "MONTHLY_DATES",
            repeatDays = null,
            repeatDates = "15"
        )

        assertNotNull(result)
        val (displayDate, nextMillis) = result!!
        val cal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        val nowCal = Calendar.getInstance().apply { timeInMillis = now }
        val expectedMonth = (nowCal.get(Calendar.MONTH) + 1) % 12
        assertEquals(expectedMonth, cal.get(Calendar.MONTH))
    }

    @Test
    fun testCalculateNextRepeatDueDate_specificDays() {
        // Future Monday to ensure base date is not in the past
        val mondayCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 7)
            set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
            set(Calendar.HOUR_OF_DAY, 12)
        }
        val futureMondayMillis = mondayCal.timeInMillis

        // Next repeat on WED,FRI from future Monday should be Wednesday
        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = futureMondayMillis,
            currentDueDateStr = "Future Mon",
            repeatType = "DAYS",
            repeatDays = "WED,FRI",
            repeatDates = null
        )

        assertNotNull(result)
        val (_, nextMillis) = result!!
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextMillis }
        assertEquals(Calendar.WEDNESDAY, nextCal.get(Calendar.DAY_OF_WEEK))
    }

    @Test
    fun testCalculateNextRepeatDueDate_pastOverdueDateRollsToFuture() {
        // Base date 10 days in the past
        val pastCal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -10)
        }
        val pastMillis = pastCal.timeInMillis

        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = pastMillis,
            currentDueDateStr = "Oct 1",
            repeatType = "EVERYDAY",
            repeatDays = null,
            repeatDates = null
        )

        assertNotNull(result)
        val (_, nextMillis) = result!!
        // The rolled over occurrence must be tomorrow (in the future relative to today)
        assertTrue(nextMillis >= System.currentTimeMillis())
    }

    @Test
    fun testCalculateNextRepeatDueDate_noneReturnsNull() {
        val result = DateUtils.calculateNextRepeatDueDate(
            currentDueDateMillis = System.currentTimeMillis(),
            currentDueDateStr = "Today",
            repeatType = "NONE",
            repeatDays = null,
            repeatDates = null
        )
        assertNull(result)
    }
}
