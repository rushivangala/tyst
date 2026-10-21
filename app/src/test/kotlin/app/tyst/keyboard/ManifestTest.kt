package app.tyst.keyboard

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tyst.keyboard.ime.TystImeService
import app.tyst.keyboard.ui.Platform
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/** The privacy promises that live in the manifest. */
@RunWith(AndroidJUnit4::class)
class ManifestTest {
    private val pm get() = context().packageManager

    @Test fun noNetworkPermissionOnlyMicrophone() {
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(context().packageName, PackageManager.GET_PERMISSIONS)
        val requested = info.requestedPermissions.orEmpty().toList()
        assertThat(requested).doesNotContain(android.Manifest.permission.INTERNET)
        assertThat(requested).doesNotContain(android.Manifest.permission.ACCESS_NETWORK_STATE)
        assertThat(requested).contains(android.Manifest.permission.RECORD_AUDIO)
        assertThat(Platform.hasInternetPermission(context())).isFalse()
    }

    @Test fun learnedDataIsNotBackedUp() {
        val flags = context().applicationInfo.flags
        assertThat(flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test fun keyboardServiceIsProtected() {
        @Suppress("DEPRECATION")
        val s = pm.getServiceInfo(ComponentName(context(), TystImeService::class.java), PackageManager.GET_META_DATA)
        assertThat(s.permission).isEqualTo(android.Manifest.permission.BIND_INPUT_METHOD)
        assertThat(s.metaData.getInt("android.view.im")).isNotEqualTo(0)
    }
}
