package com.mindpalace.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Plan 02 §3.6 T-MAN1/T-MAN2 (untagged): regression test for the D25 crash, where a receiver was
 * declared in the manifest at the wrong path and never actually resolved to a class.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ManifestComponentsTest {

    private val flags = PackageManager.GET_RECEIVERS or
        PackageManager.GET_ACTIVITIES or
        PackageManager.GET_PROVIDERS or
        PackageManager.GET_SERVICES

    /**
     * Exported components this app does not declare itself — merged in by debug-only tooling
     * (Compose preview, androidx.test instrumentation) or by libraries that export these
     * components by design (WorkManager's job service/diagnostics receiver, profileinstaller's
     * install receiver). None of these grant any capability this app's own manifest doesn't
     * already request; they're excluded here rather than asserted on, since they're outside this
     * app's control and can change with library versions.
     */
    private val benignLibraryComponents = setOf(
        "androidx.compose.ui.tooling.PreviewActivity",
        "androidx.test.core.app.InstrumentationActivityInvoker\$BootstrapActivity",
        "androidx.test.core.app.InstrumentationActivityInvoker\$EmptyActivity",
        "androidx.test.core.app.InstrumentationActivityInvoker\$EmptyFloatingActivity",
        "androidx.work.impl.background.systemjob.SystemJobService",
        "androidx.work.impl.diagnostics.DiagnosticsReceiver",
        "androidx.profileinstaller.ProfileInstallReceiver",
    )

    private fun packageInfo() = ApplicationProvider.getApplicationContext<Context>()
        .packageManager
        .getPackageInfo(ApplicationProvider.getApplicationContext<Context>().packageName, flags)

    @Test
    fun `every declared component resolves to a real class`() {
        val info = packageInfo()
        val names = listOfNotNull(info.activities, info.receivers, info.providers, info.services)
            .flatMap { it.toList() }
            .map { it.name }

        assertTrue("expected at least one declared component", names.isNotEmpty())
        for (name in names) {
            Class.forName(name)
        }
    }

    @Test
    fun `exported components are exactly MainActivity and ShareReceiverActivity`() {
        val info = packageInfo()
        val exportedActivities = info.activities.orEmpty()
            .filter { it.exported && it.name !in benignLibraryComponents }
            .map { it.name }
            .toSet()
        val exportedOther = listOfNotNull(info.receivers, info.providers, info.services)
            .flatMap { it.toList() }
            .filter { it.exported && it.name !in benignLibraryComponents }
            .map { it.name }

        assertEquals(
            setOf(
                "com.mindpalace.app.MainActivity",
                "com.mindpalace.app.capture.ShareReceiverActivity",
            ),
            exportedActivities,
        )
        assertTrue(
            "no receiver/provider/service of this app should be exported, found: $exportedOther",
            exportedOther.isEmpty(),
        )
    }
}
