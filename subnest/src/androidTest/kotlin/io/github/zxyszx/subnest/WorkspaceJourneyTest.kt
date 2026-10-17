package io.github.zxyszx.subnest

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Run only against tools/seed-local-subnest.mjs and its isolated loopback backend. */
@RunWith(AndroidJUnit4::class)
class WorkspaceJourneyTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(object : ExternalResource() {
        override fun before() {
            context.getSharedPreferences("subnest-settings", 0).edit().clear().putString("server", "http://10.0.2.2:43210").commit()
            File(context.noBackupFilesDir, "subnest-session").delete()
        }
    }).around(compose)

    private fun action(label: Int) = compose.onNode(hasText(context.getString(label)) and hasClickAction())
    private fun field(label: Int) = compose.onNode(hasText(context.getString(label)) and hasSetTextAction())
    private fun awaitText(text: String) {
        compose.waitUntil(20_000) { compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun screenshot(name: String) {
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.waitForIdle(100, 5_000)
        val bitmap = requireNotNull(automation.takeScreenshot())
        val output = File(context.getExternalFilesDir(null), "qa/$name.png")
        output.parentFile?.mkdirs()
        output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test
    fun loginFilterRestoreViewSeatsConfirmRenewAndLogout() {
        compose.waitUntil(15_000) { field(R.string.email).fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not() }
        field(R.string.email).performTextInput("android-local@example.com")
        field(R.string.password).performTextInput("Local-test-only-2749")
        action(R.string.login).performClick()
        awaitText(context.getString(R.string.count, 4))
        screenshot("01-subscriptions")
        action(R.string.all_platforms).performClick()
        compose.onNode(hasText("Netflix") and hasClickAction()).performClick()
        awaitText(context.getString(R.string.count, 2))
        compose.onNode(hasText("Netflix") and hasClickAction()).performClick()
        action(R.string.all_platforms).performClick()
        field(R.string.search).performTextInput("2")
        awaitText(context.getString(R.string.count, 1))
        compose.onNode(hasText("Netflix #2")).assertExists()
        compose.onNodeWithContentDescription(context.getString(R.string.clear_search)).performClick()

        action(R.string.today).performClick()
        awaitText(context.getString(R.string.count, 1))
        compose.onNode(hasText("Netflix #1")).assertExists()
        compose.onNode(hasText("Netflix #2")).assertDoesNotExist()
        action(R.string.sharing).performClick()
        awaitText(context.getString(R.string.count, 2))
        screenshot("02-sharing")
        action(R.string.subscriptions).performClick()
        action(R.string.today).assertIsSelected()
        compose.activityRule.scenario.recreate()
        awaitText(context.getString(R.string.count, 1))
        action(R.string.today).assertIsSelected()

        action(R.string.renew).performScrollTo().performClick()
        screenshot("03-confirm-renewal")
        action(R.string.cancel).performClick()
        compose.onNode(hasText(context.getString(R.string.due, LocalDate.now().toString()))).assertExists()
        action(R.string.renew).performScrollTo().performClick()
        action(R.string.confirm).performClick()
        awaitText(context.getString(R.string.count, 0))

        action(R.string.overdue).performClick()
        awaitText(context.getString(R.string.count, 1))
        compose.onNode(hasText("Netflix #2")).assertExists()
        action(R.string.sharing).performClick()
        compose.onNode(hasText(context.getString(R.string.vacant)) and isSelectable()).performClick()
        awaitText(context.getString(R.string.count, 2))
        compose.onAllNodes(hasText(context.getString(R.string.manage)) and hasClickAction())[0].performScrollTo().performClick()
        awaitText(context.getString(R.string.seat_number, 1))
        screenshot("04-seats")
        action(R.string.close).performClick()
        action(R.string.account).performClick()
        action(R.string.logout).performScrollTo().performClick()
        action(R.string.sign_out).performClick()
        awaitText(context.getString(R.string.login))
        compose.activityRule.scenario.recreate()
        field(R.string.password).assertTextContains("")
        action(R.string.subscriptions).assertDoesNotExist()
        screenshot("05-signed-out")
    }
}
