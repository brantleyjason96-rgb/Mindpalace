package com.mindpalace.app

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PRIVACY GATE (plan §3.6 + §16: "no INTERNET permission … a test that fails if it appears").
 * These read the MERGED manifest, so they fail even if a permission sneaks in via a library's
 * manifest-merger rule. Adding ANY permission requires the owner's sign-off (D4).
 *
 * One known exception: androidx.core merges in
 * `com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` — an app-private,
 * signature-level self-protection permission (it scopes dynamic broadcast receivers to this
 * app only). It grants NO external capability; anything else still fails the test.
 *
 * RECORD_AUDIO is sign-off-approved (docs/PERMISSIONS.md, D15) for the on-device Voice capture
 * screen only; no raw audio is ever stored, and this is still an offline, local-only permission.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyManifestTest {

    /** Documented, benign, app-private permission added by androidx.core. Not a capability. */
    private val androidxInternalSelfProtection =
        setOf("com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

    /** Sign-off-approved permissions beyond the androidx self-protection one (docs/PERMISSIONS.md). */
    private val approvedPermissions =
        setOf("android.permission.RECORD_AUDIO")

    private fun requestedPermissions(): List<String> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val info = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        return info.requestedPermissions?.toList() ?: emptyList()
    }

    @Test
    fun `no INTERNET permission`() {
        assertFalse(requestedPermissions().contains("android.permission.INTERNET"))
    }

    @Test
    fun `no ACCESS_NETWORK_STATE permission`() {
        assertFalse(requestedPermissions().contains("android.permission.ACCESS_NETWORK_STATE"))
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
}
