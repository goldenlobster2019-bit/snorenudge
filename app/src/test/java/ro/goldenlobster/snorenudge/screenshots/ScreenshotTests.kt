package ro.goldenlobster.snorenudge.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ro.goldenlobster.snorenudge.data.AppSettings
import ro.goldenlobster.snorenudge.ui.BatteryInfo
import ro.goldenlobster.snorenudge.ui.HomeContent
import ro.goldenlobster.snorenudge.ui.LiveTestContent
import ro.goldenlobster.snorenudge.ui.SetupContent
import ro.goldenlobster.snorenudge.ui.history.HistoryContent
import ro.goldenlobster.snorenudge.ui.history.NightDetailContent
import ro.goldenlobster.snorenudge.ui.settings.SettingPickerContent
import ro.goldenlobster.snorenudge.ui.settings.SettingsContent
import ro.goldenlobster.snorenudge.ui.theme.Palette
import ro.goldenlobster.snorenudge.ui.theme.SnoreNudgeTheme
import java.util.Locale
import java.util.TimeZone

/**
 * Renders every screen on the JVM (Robolectric native graphics) with fake data.
 *
 * PNGs are only written when Roborazzi is in record mode, so a plain `testDebugUnitTest` just exercises the
 * composition. Regenerate with `tools/render-screens.sh` (runs `:app:recordRoborazziDebug`).
 * Device: 450x450 px, round, xhdpi (2.0). Time zone Europe/Bucharest, locale en.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w225dp-h225dp-round-xhdpi")
class ScreenshotTests {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var oldZone: TimeZone
    private lateinit var oldLocale: Locale

    @Before
    fun fixEnvironment() {
        oldZone = TimeZone.getDefault()
        oldLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(SampleData.ZONE))
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreEnvironment() {
        TimeZone.setDefault(oldZone)
        Locale.setDefault(oldLocale)
    }

    private fun shot(name: String, content: @Composable () -> Unit) {
        rule.setContent {
            SnoreNudgeTheme {
                // Same TimeText the app shows, but with a frozen clock so output is deterministic.
                AppScaffold(timeText = { TimeText(timeSource = FixedTimeSource) }) {
                    Box(Modifier.fillMaxSize().background(Palette.Background)) { content() }
                }
            }
        }
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("build/screenshots/$name.png")
    }

    private object FixedTimeSource : TimeSource {
        @Composable
        override fun currentTime(): String = "01:30"
    }

    private val battery = BatteryInfo(82, charging = false)

    @Test
    fun home_idle() = shot("01_home_idle") {
        HomeContent(
            snap = SampleData.idle(), now = SampleData.NOW, battery = battery, batteryExempt = true,
            lastNight = SampleData.lastNight(), onStart = {}, onStop = {}, onLiveTest = {}, onHistory = {},
            onSettings = {}, onLastNight = {}, onTestModeOff = {},
        )
    }

    @Test
    fun home_idle_scrolled() = shot("02_home_idle_scrolled") {
        HomeContent(
            snap = SampleData.idle(), now = SampleData.NOW, battery = battery, batteryExempt = true,
            lastNight = SampleData.lastNight(), onStart = {}, onStop = {}, onLiveTest = {}, onHistory = {},
            onSettings = {}, onLastNight = {}, onTestModeOff = {},
            // Items: title 0, Start 1, battery 2, Last night 3, History 4, Live test 5, Settings 6.
            initialCenterItemIndex = 5,
        )
    }

    @Test
    fun home_idle_testmode() = shot("03_home_idle_testmode") {
        HomeContent(
            snap = SampleData.idle().copy(testMode = true), now = SampleData.NOW, battery = battery,
            batteryExempt = true, lastNight = SampleData.lastNight(), onStart = {}, onStop = {},
            onLiveTest = {}, onHistory = {}, onSettings = {}, onLastNight = {}, onTestModeOff = {},
        )
    }

    @Test
    fun home_running() = shot("04_home_running") {
        HomeContent(
            snap = SampleData.running(elapsedMs = SampleData.min(2 * 60 + 14), delayRemainingMs = 0L),
            now = SampleData.NOW, battery = battery, batteryExempt = true, lastNight = null,
            onStart = {}, onStop = {}, onLiveTest = {}, onHistory = {}, onSettings = {}, onLastNight = {},
            onTestModeOff = {},
        )
    }

    @Test
    fun home_running_startdelay() = shot("05_home_running_startdelay") {
        HomeContent(
            snap = SampleData.running(elapsedMs = SampleData.min(6), delayRemainingMs = SampleData.min(14))
                .copy(snoreEventCount = 2, nudgeCount = 0, lastNudgeAt = 0L),
            now = SampleData.NOW, battery = battery, batteryExempt = true, lastNight = null,
            onStart = {}, onStop = {}, onLiveTest = {}, onHistory = {}, onSettings = {}, onLastNight = {},
            onTestModeOff = {},
        )
    }

    @Test
    fun history() = shot("06_history") {
        HistoryContent(sessions = SampleData.history(), onNight = {}, today = SampleData.TODAY)
    }

    @Test
    fun night_detail_top() = shot("07_night_detail_top") {
        val n = SampleData.night()
        NightDetailContent(n.session, n.events, n.nudges)
    }

    @Test
    fun night_detail_timeline() = shot("08_night_detail_timeline") {
        val n = SampleData.night()
        // Items: header 0, pills 1, stats 2-5, timeline 6.
        NightDetailContent(n.session, n.events, n.nudges, initialCenterItemIndex = 6)
    }

    @Test
    fun night_detail_lists() = shot("09_night_detail_lists") {
        val n = SampleData.night()
        // Items: ..., timeline 6, "Nudges" label 7, nudge rows 8.., busiest periods after.
        NightDetailContent(n.session, n.events, n.nudges, initialCenterItemIndex = 8 + n.nudges.size + 2)
    }

    @Test
    fun settings() = shot("10_settings") {
        SettingsContent(settings = AppSettings(), onPick = {}, onTestVibration = {}, onReset = {})
    }

    @Test
    fun settings_picker_cooldown() = shot("11_settings_picker_cooldown") {
        SettingPickerContent(key = "cooldown", settings = AppSettings(cooldownSec = 45), onSelect = {})
    }

    @Test
    fun live_test() = shot("12_live_test") {
        LiveTestContent(snap = SampleData.liveTest(), onStart = {}, onTestModeChange = {}, onVibrate = {})
    }

    @Test
    fun live_test_scrolled() = shot("12b_live_test_scrolled") {
        // Items: header 0, level 1, snoring 2, positive/gate pills 3, details 4.
        LiveTestContent(
            snap = SampleData.liveTest(), onStart = {}, onTestModeChange = {}, onVibrate = {},
            initialCenterItemIndex = 3,
        )
    }

    @Test
    fun setup() = shot("13_setup") {
        SetupContent(
            micGranted = true, notifGranted = false, batteryExempt = false,
            onGrant = {}, onBatteryExemption = {},
        )
    }
}
