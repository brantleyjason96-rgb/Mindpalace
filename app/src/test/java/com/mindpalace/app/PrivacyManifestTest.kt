package com.mindpalace.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVACY GATE (plan §3.6 + §16: originally "no INTERNET permission … a test that fails if it
 * appears", narrowed by D26 — see below). These read the MERGED manifest, so they fail even if a
 * permission sneaks in via a library's manifest-merger rule. Adding ANY permission requires the
 * owner's sign-off (D4/rule 2).
 *
 * One known exception: androidx.core merges in
 * `com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — an app-private,
 * signature-level self-protection permission (it scopes dynamic broadcast receivers to this
 * app only). It grants NO external capability; anything else still fails the test.
 *
 * RECORD_AUDIO is sign-off-approved (docs/PERMISSIONS.md, D15) for the on-device Voice capture
 * screen only; no raw audio is ever stored, and this is still an offline, local-only permission.
 *
 * POST_NOTIFICATIONS / SCHEDULE_EXACT_ALARM / RECEIVE_BOOT_COMPLETED are sign-off-approved
 * (docs/PERMISSIONS.md, D19) for time-based reminders only; still no network.
 *
 * WAKE_LOCK / FOREGROUND_SERVICE are sign-off-approved (docs/PERMISSIONS.md, D24), transitively
 * required by WorkManager for nightly AI scheduling; local-only, no network.
 *
 * INTERNET / ACCESS_NETWORK_STATE are sign-off-approved (docs/PERMISSIONS.md, D26) — the one
 * deliberate exception to this app's zero-network design, scoped to a single purpose:
 * downloading the handwriting recognition model once per language. Recognition itself still runs
 * fully on-device; no note content is ever sent anywhere. This is why there's no longer a
 * dedicated "no INTERNET permission" test — the approved-list test below is the gate now.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyManifestTest {

    /** Documented, benign, app-private permission added by androidx.core. Not a capability. */
    private val androidxInternalSelfProtection =
        setOf("com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

    /** Sign-off-approved permissions beyond the androidx self-protection one (docs/PERMISSIONS.md). */
    private val approvedPermissions =
        setOf(
            "android.permission.RECORD_AUDIO",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.SCHEDULE_EXACT_ALARM",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.WAKE_LOCK",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
        )

    private fun requestedPermissions(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList() ?: emptyList()
    }

    @Test
    fun `INTERNET is scoped to the handwriting model download only, per D26`() {
        val perms = requestedPermissions()
        assertEquals(true, perms.contains("android.permission.INTERNET"))
        assertEquals(true, perms.contains("android.permission.ACCESS_NETWORK_STATE"))
    }

    @Test
    fun `app requests no permissions beyond the approved ones`() {
        val unknown = requestedPermissions()
            .filter { it !in androidxInternalSelfProtection && it !in approvedPermissions }
        assertEquals(
            "Merged manifest must request zero permissions beyond the approved list " +
                "(app-private androidx-core self-protection excepted). Any new permission needs " +
                "sign-off + an entry above and in docs/PERMISSIONS.md with a written justification.",
            emptyList<String>(),
            unknown,
        )
    }

    @Test
    fun `RECORD_AUDIO is present for voice capture`() {
        assertEquals(true, requestedPermissions().contains("android.permission.RECORD_AUDIO"))
    }

    @Test
    fun `reminder permissions are present`() {
        val perms = requestedPermissions()
        assertEquals(true, perms.contains("android.permission.POST_NOTIFICATIONS"))
        assertEquals(true, perms.contains("android.permission.SCHEDULE_EXACT_ALARM"))
        assertEquals(true, perms.contains("android.permission.RECEIVE_BOOT_COMPLETED"))
    }
}
