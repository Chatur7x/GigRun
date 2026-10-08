package com.gigrun.presentation.comparison

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gigrun.data.repository.PlatformComparisonRepository.PlatformComparison
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders [ComparisonContent] directly — the Hilt-provided ViewModel is not
 * involved, so these tests exercise layout and formatting only.
 */
@RunWith(AndroidJUnit4::class)
class PlatformComparisonScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun rows(vararg rows: PlatformComparison) = rows.toList()

    private fun success(rows: List<PlatformComparison>) = ComparisonUiState.Success(
        rows = rows,
        bestPlatform = rows.firstOrNull()?.takeIf { it.hasTrackedHours }?.platform,
        bestNetPerHour = rows.firstOrNull()?.takeIf { it.hasTrackedHours }?.netPerHourInr ?: 0.0,
        totalRevenue = rows.sumOf { it.revenueInr },
        totalNet = rows.sumOf { it.netInr },
        totalHours = rows.sumOf { it.activeHours },
        rangeLabel = RangeOption.THIS_WEEK.label
    )

    @Test
    fun emptyStateRendersWhenNoRows() {
        composeRule.setContent {
            ComparisonContent(success(emptyList()), RangeOption.THIS_WEEK, {})
        }
        composeRule.onNodeWithTag("best_platform_card").assertIsDisplayed()
        composeRule.onNodeWithTag("empty_comparison").assertIsDisplayed()
    }

    @Test
    fun bestPlatformCardShowsNoDataYetWhenBestIsNull() {
        composeRule.setContent {
            // Revenue present but zero tracked hours → bestPlatform is null by design.
            ComparisonContent(
                success(rows(PlatformComparison("Blinkit", 400.0, 0.0, 0.0, 0, 0.0, 400.0, 0.0))),
                RangeOption.THIS_WEEK,
                {}
            )
        }
        composeRule.onNodeWithTag("best_platform_card").assertIsDisplayed()
        composeRule.onNodeWithTag("best_platform_empty").assertIsDisplayed()
    }

    @Test
    fun platformRowRendersPlatformTag() {
        composeRule.setContent {
            ComparisonContent(
                success(
                    rows(
                        PlatformComparison("Blinkit", 3000.0, 8.0, 60.0, 40, 800.0, 2200.0, 275.0),
                        PlatformComparison("Zepto", 1500.0, 6.0, 45.0, 25, 600.0, 900.0, 150.0)
                    )
                ),
                RangeOption.THIS_WEEK,
                {}
            )
        }
        composeRule.onNodeWithTag("platform_row_Blinkit").assertIsDisplayed()
        composeRule.onNodeWithTag("platform_rate_Blinkit").assertIsDisplayed()
    }

    @Test
    fun platformRowRendersCorrectNetPerHour() {
        composeRule.setContent {
            ComparisonContent(
                success(rows(PlatformComparison("Blinkit", 3000.0, 8.0, 60.0, 40, 800.0, 2200.0, 275.0))),
                RangeOption.THIS_WEEK,
                {}
            )
        }
        composeRule.awaitTextIn("platform_rate_Blinkit", "₹275")
        composeRule.awaitTextIn("best_platform_rate", "₹275")
    }

    @Test
    fun negativeNetRendersMinusSignNotZero() {
        composeRule.setContent {
            ComparisonContent(
                success(rows(PlatformComparison("Rapido", 200.0, 4.0, 30.0, 10, 400.0, -200.0, -50.0))),
                RangeOption.THIS_WEEK,
                {}
            )
        }
        // AnimatedRupees clamps negatives to 0; this asserts we bypass it.
        composeRule.awaitTextIn("platform_rate_Rapido", "-₹50")
    }

    @Test
    fun rangeChipsAreClickableAndInvokeCallback() {
        var selected: RangeOption? = null
        composeRule.setContent {
            ComparisonContent(success(emptyList()), RangeOption.THIS_WEEK, { selected = it })
        }
        composeRule.onNodeWithTag("range_this_week").assertIsDisplayed()
        composeRule.onNodeWithTag("range_last_week").assertIsDisplayed()
        composeRule.onNodeWithTag("range_this_month").assertIsDisplayed()

        composeRule.onNodeWithTag("range_this_month").performClick()
        assertTrue(selected == RangeOption.THIS_MONTH)
    }
}

/** Waits for [AnimatedRupees] to settle, then asserts the node carries [expected]. */
private fun ComposeTestRule.awaitTextIn(tag: String, expected: String) {
    waitUntil(5_000) {
        onAllNodesWithTag(tag).fetchSemanticsNodes().any { node ->
            node.config.getOrNull(SemanticsProperties.Text)
                ?.any { it.text.contains(expected) } == true
        }
    }
}