package app.tyst.keyboard.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import app.tyst.keyboard.ime.TystImeService

/** Android lookups for the settings screens. Nothing here reads personal data. */
object Platform {
    private fun imeId(context: Context) = ComponentName(context, TystImeService::class.java)

    fun isEnabled(context: Context): Boolean {
        val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
        val me = imeId(context)
        return imm.enabledInputMethodList.any { it.component == me }
    }

    fun isDefault(context: Context): Boolean {
        val current = Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD) ?: return false
        return ComponentName.unflattenFromString(current) == imeId(context)
    }

    fun openKeyboardSettings(context: Context) {
        context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun showKeyboardPicker(context: Context) {
        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }

    /** Name of the app chosen as Android's autofill service (for example NordPass), or null. */
    fun autofillServiceName(context: Context): String? {
        val flat = try {
            Settings.Secure.getString(context.contentResolver, "autofill_service")
        } catch (_: SecurityException) {
            null
        } ?: return null
        val component = ComponentName.unflattenFromString(flat) ?: return null
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(component.packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            component.packageName
        }
    }

    /** Opens the system screen where the password manager is chosen. */
    fun openAutofillSettings(context: Context) {
        val intents = buildList {
            if (Build.VERSION.SDK_INT >= 34) add(Intent(Settings.ACTION_CREDENTIAL_PROVIDER))
            add(Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).setData(android.net.Uri.parse("package:${context.packageName}")))
            add(Intent(Settings.ACTION_SETTINGS))
        }
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
    }

    fun openAppPermissions(context: Context) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
        context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    data class PermissionRow(val name: String, val label: String, val granted: Boolean)

    /** The permissions Tyst asks for, read live from the system ("Privacy proof"). */
    fun requestedPermissions(context: Context): List<PermissionRow> {
        val pm = context.packageManager
        val info: PackageInfo = if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        val names = info.requestedPermissions ?: return emptyList()
        val flags = info.requestedPermissionsFlags
        return names.mapIndexed { i, name ->
            val granted = flags != null && flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED != 0
            PermissionRow(name, permissionLabel(name), granted)
        }.filterNot { it.name.endsWith(".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
    }

    fun hasInternetPermission(context: Context): Boolean =
        requestedPermissions(context).any { it.name == android.Manifest.permission.INTERNET }

    private fun permissionLabel(name: String): String = when (name) {
        android.Manifest.permission.RECORD_AUDIO -> "Microphone"
        android.Manifest.permission.INTERNET -> "Internet"
        else -> name.substringAfterLast('.').lowercase().replace('_', ' ').replaceFirstChar { it.uppercaseChar() }
    }

    fun versionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (_: PackageManager.NameNotFoundException) {
        "?"
    }

    fun deviceName(): String = "${Build.MANUFACTURER.replaceFirstChar { it.uppercaseChar() }} ${Build.MODEL}"

    fun androidVersion(): String = Build.VERSION.RELEASE

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000 -> String.format(java.util.Locale.ROOT, "%.1f MB", bytes / 1_000_000.0)
        bytes >= 1_000 -> "${bytes / 1_000} KB"
        else -> "$bytes B"
    }
}
