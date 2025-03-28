/*
 *  This file is part of Omega Launcher
 *  Copyright (c) 2021   Saul Henriquez
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.saggitt.omega.util

import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.WRITE_EXTERNAL_STORAGE
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Environment
import android.os.Process
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.android.launcher3.AppFilter
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.pm.UserCache
import com.android.launcher3.util.ComponentKey
import com.android.launcher3.util.Executors.MAIN_EXECUTOR
import com.android.launcher3.util.PackageManagerHelper
import com.saggitt.omega.allapps.CustomAppFilter
import com.saggitt.omega.theme.ThemeOverride
import java.io.File
import java.io.IOException
import java.util.Locale

class Config(val context: Context) {

    //TODO: Use ContextWrapper instead of UpdateConfiguration
    fun setAppLanguage(languageCode: String) {
        val locale = getLocaleByAndroidCode(languageCode)
        val config = context.resources.configuration
        val mLocale =
            if (languageCode.isNotEmpty()) locale else Resources.getSystem().configuration.locales[0]
        config.setLocale(mLocale)
        context.resources.updateConfiguration(config, context.resources.displayMetrics)
    }

    fun getLocaleByAndroidCode(languageCode: String): Locale {
        return if (!TextUtils.isEmpty(languageCode)) {
            if (languageCode.contains("-r")) Locale(
                languageCode.substring(0, 2),
                languageCode.substring(4, 6)
            ) // de-rAt
            else Locale(languageCode) // de
        } else Resources.getSystem().configuration.locales[0]
    }

    fun getAppsList(filter: AppFilter?): List<LauncherActivityInfo> {
        val apps = ArrayList<LauncherActivityInfo>()
        val profiles = UserCache.INSTANCE.get(context).userProfiles
        val launcherApps = context.getSystemService(LauncherApps::class.java)
        profiles.forEach { apps += launcherApps.getActivityList(null, it) }
        return if (filter != null) {
            apps.filter { filter.shouldShowApp(it.componentName, it.user) }
        } else {
            apps
        }
    }

    private val FILE_NAME = "neo_launcher_password"

    // 获取密码文件路径
    private fun getPasswordFile(): File {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            FILE_NAME
        )
    }

    // 保存密码
    fun savePassword(password: String) {
        saveFile(FILE_NAME, password)
    }

    // 读取密码（文件不存在返回 null）
    fun getPassword(): String? {
        return getFile(FILE_NAME)
    }

    fun getFile(fileName: String): String? {
        checkFilePermissions(context)
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            fileName
        )
        if (file.exists()) {
            try {
                return file.readText()
            } catch (e: IOException) {
                e.message?.let {
                    Log.e("Config", it)
                }
                Toast.makeText(context, "file read exception", Toast.LENGTH_SHORT).show()
            }
        }
        return context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .getString(fileName, null)
    }

    fun saveFile(fileName: String, data: String) {
        checkFilePermissions(context)
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            fileName
        )
        try {
            file.writeText(data)
        } catch (e: IOException) {
            e.message?.let {
                Log.e("Config", it)
            }
            Toast.makeText(context, "file save exception", Toast.LENGTH_SHORT).show()
        }

        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit() { putString(fileName, data) }
    }

    fun checkFilePermissions(context: Context, test: Boolean = false) {
        // Check if the app has the necessary permissions
        if (ContextCompat.checkSelfPermission(
                context,
                READ_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            startPermissionAc()
            return
        }
        if (test) {

            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                "neo_launcher_test"
            )
            try {
                file.writeText(file.toString())
            } catch (ex: IOException) {
                startPermissionAc()
            }
        }
    }

    fun startPermissionAc() {
        // If permission is not granted, show a message and redirect to settings
        Toast.makeText(
            context,
            "You need to grant storage permissions to continue.",
            Toast.LENGTH_SHORT
        ).show()

        // Redirect user to the app's settings page where they can grant the permission
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    companion object {
        //PERMISSION FLAGS
        const val REQUEST_PERMISSION_STORAGE_ACCESS = 666
        const val REQUEST_PERMISSION_LOCATION_ACCESS = 667
        const val CODE_EDIT_ICON = 100

        const val GOOGLE_QSB = "com.google.android.googlequicksearchbox"
        const val LENS_URI = "google://lens"
        const val LENS_ACTIVITY = "com.google.android.apps.lens.MainActivity"
        const val DPS_PACKAGE = "com.google.android.as"

        //APP DRAWER SORT MODE
        const val SORT_AZ = 0
        const val SORT_ZA = 1
        const val SORT_MOST_USED = 2
        const val SORT_BY_COLOR = 3

        //APP DRAWER LAYOUT MODE
        const val DRAWER_VERTICAL = 0
        const val DRAWER_PAGED = 1

        //COMPOSE THEME COLORS
        const val THEME_LIGHT = 0
        const val THEME_DARK = 1
        const val THEME_BLACK = 2

        val ICON_INTENTS = arrayOf(
            Intent("com.novalauncher.THEME"),
            Intent("org.adw.launcher.THEMES"),
            Intent("org.adw.launcher.icons.ACTION_PICK_ICON"),
            Intent("com.anddoes.launcher.THEME"),
            Intent("com.teslacoilsw.launcher.THEME"),
            Intent("com.fede.launcher.THEME_ICONPACK"),
            Intent("com.gau.go.launcherex.theme"),
            Intent("com.dlto.atom.launcher.THEME"),
        )

        /**
         * Shows authentication screen to confirm credentials (pin, pattern or password) for the current
         * user of the device.
         *
         * @param context The {@code Context} used to get {@code KeyguardManager} service
         * @param title the {@code String} which will be shown as the pompt title
         * @param successRunnable The {@code Runnable} which will be executed if the user does not setup
         *                        device security or if lock screen is unlocked
         */
        @RequiresApi(Build.VERSION_CODES.R)
        fun showLockScreen(context: Context, title: String, successRunnable: Runnable) {
            if (hasSecureKeyguard(context)) {

                val authenticationCallback = object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(
                        result: BiometricPrompt.AuthenticationResult
                    ) {
                        successRunnable.run()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        //Do nothing
                    }
                }

                val bp = BiometricPrompt.Builder(context)
                    .setTitle(title)
                    .setAllowedAuthenticators(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                BiometricManager.Authenticators.DEVICE_CREDENTIAL
                    )
                    .build()

                val handler = MAIN_EXECUTOR.handler
                bp.authenticate(
                    CancellationSignal(), { runnable: Runnable ->
                        handler.post(runnable)
                    },
                    authenticationCallback
                )
            } else {
                // Notify the user a secure keyguard is required for protected apps,
                // but allow to set hidden apps
                Toast.makeText(context, R.string.trust_apps_no_lock_error, Toast.LENGTH_LONG)
                    .show()
                successRunnable.run()
            }
        }

        private fun hasSecureKeyguard(context: Context): Boolean {
            val keyguardManager = context.getSystemService(
                KeyguardManager::class.java
            )
            return keyguardManager != null && keyguardManager.isKeyguardSecure
        }

        /*
            Check is the app is protected
        */
        fun isAppProtected(context: Context, componentKey: ComponentKey): Boolean {
            var result = false
            val protectedApps = ArrayList(
                Utilities.getOmegaPrefs(context).protectedAppsSet
                    .map { Utilities.makeComponentKey(context, it) })

            if (protectedApps.contains(componentKey)) {
                result = true
            }
            return result
        }

        private val PLACE_HOLDERS = arrayOf(
            "com.android.phone",
            "com.samsung.android.dialer",
            "com.whatsapp",
            "com.android.chrome",
            "com.instagram.android",
            "com.google.android.gm",
            "com.facebook.orca",
            "com.google.android.youtube",
            "com.twitter.android",
            "com.facebook.katana",
            "com.google.android.calendar",
            "com.yodo1.crossyroad",
            "com.spotify.music",
            "com.skype.raider",
            "com.snapchat.android",
            "com.viber.voip",
            "com.google.android.deskclock",
            "com.google.android.apps.photos",
            "com.google.android.music",
            "com.google.android.apps.genie.geniewidget",
            "com.netflix.mediaclient",
            "com.google.android.apps.maps",
            "bbc.iplayer.android",
            "com.android.settings",
            "com.google.android.videos",
            "com.amazon.mShop.android.shopping",
            "com.microsoft.office.word",
            "com.google.android.apps.docs",
            "com.google.android.keep",
            "com.google.android.talk"
        )

        fun getPreviewAppInfos(context: Context): List<AppInfo> {
            val launcherApps = context.getSystemService(LauncherApps::class.java)
            val user = Process.myUserHandle()
            val appFilter = CustomAppFilter(context)
            val predefined = PLACE_HOLDERS
                .filter { PackageManagerHelper(context).isAppInstalled(it, user) }
                .mapNotNull { launcherApps.getActivityList(it, user).firstOrNull() }
                .asSequence()
            val randomized = launcherApps.getActivityList(null, Process.myUserHandle())
                .asSequence()
            return (predefined + randomized)
                .filter { appFilter.shouldShowApp(it.componentName, it.user) }
                .take(20)
                .map { AppInfo(it, it.user, false) }
                .toList()
        }

        fun getCurrentTheme(context: Context): Int {
            val themeSet = ThemeOverride.Settings()
            var currentTheme = THEME_LIGHT
            if (themeSet.getTheme(context) == R.style.SettingsTheme_Dark) {
                currentTheme = THEME_DARK
            } else if (themeSet.getTheme(context) == R.style.SettingsTheme_Black) {
                currentTheme = THEME_BLACK
            }
            return currentTheme
        }

    }
}