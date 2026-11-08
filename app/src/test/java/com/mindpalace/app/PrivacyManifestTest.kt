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
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrivacyManifestTest {

    /** Documented, benign, app-private permission added by androidx.core. Not a capability. */
    private val androidxInternalSelfProtection =
        setOf("com.mindpalace.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION")

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
    fun `app requests no permissions beyond the androidx internal self-protection one`() {
        val unknown = requestedPermissions().filter { it !in androidxInternalSelfProtection }
        assertEquals(
            "Merged manifest must request zero permissions (app-private androidx-core " +
                "self-protection excepted).Any new permission needs sign-off + an entry above " +
                "with a written justification.",
            emptyList<String>(),
            unknown,
        )
    }
}
