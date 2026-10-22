package app.tyst.keyboard

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import app.tyst.keyboard.ime.AutofillLog
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Password manager pills, end to end: a stand-in autofill service answers like NordPass, and the
 * keyboard must ask for inline suggestions, receive them and draw them in the strip.
 */
@RunWith(AndroidJUnit4::class)
class InlineAutofillEndToEndTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val ime = "app.tyst.keyboard/.ime.TystImeService"
    private val autofill = "app.tyst.keyboard/.TestAutofillService"
    private var previousIme = ""
    private var previousAutofill = ""

    @Before fun setUp() {
        previousIme = device.executeShellCommand("settings get secure default_input_method").trim()
        previousAutofill = device.executeShellCommand("settings get secure autofill_service").trim()
        device.executeShellCommand("ime enable $ime")
        device.executeShellCommand("ime set $ime")
        device.executeShellCommand("settings put secure autofill_service $autofill")
    }

    @After fun tearDown() {
        if (previousIme.isNotEmpty() && previousIme != "null") device.executeShellCommand("ime set $previousIme")
        if (previousAutofill.isNotEmpty() && previousAutofill != "null") device.executeShellCommand("settings put secure autofill_service $previousAutofill")
        else device.executeShellCommand("settings delete secure autofill_service")
    }

    @Test fun passwordManagerPillsAppearInTheStrip() {
        ActivityScenario.launch(TestLoginActivity::class.java).use {
            Thread.sleep(1_500)
            device.wait(Until.findObject(By.desc("Email")), 5_000)?.click() ?: fail("No email field")
            device.wait(Until.hasObject(By.desc("Delete")), 10_000)
            // Wait for the request, the service's answer and the pills to be drawn.
            val until = System.currentTimeMillis() + 15_000
            while (System.currentTimeMillis() < until && AutofillLog.all().none { it.contains("Showing") }) Thread.sleep(250)
            val log = AutofillLog.all().reversed().joinToString(" / ")
            val report = "service requests=${TestAutofillService.requests}, request had inline=${TestAutofillService.lastRequestHadInline}, " +
                "autofill_service=${device.executeShellCommand("settings get secure autofill_service").trim()}, keyboard log: [$log]"
            if (AutofillLog.all().none { it.contains("Showing") }) fail("Pills never shown. $report")
            println("Autofill OK: $report")
        }
    }
}
