/*
 *  This file is part of Omega Launcher
 *  Copyright (c) 2021   Omega Launcher Team
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

package com.saggitt.omega.preferences.views

import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.SwitchPreference
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.saggitt.omega.PREFS_PROTECTED_APPS
import com.saggitt.omega.PREFS_TRUST_APPS
import com.saggitt.omega.util.Config
import com.saggitt.omega.util.omegaPrefs

class PrefsDrawerFragment :
    BasePreferenceFragment(R.xml.preferences_drawer, R.string.title__general_drawer) {
    private lateinit var config: Config

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        config = Config(requireContext())
        findPreference<SwitchPreference>(PREFS_PROTECTED_APPS)?.apply {
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    requireActivity().omegaPrefs.enableProtectedApps = newValue as Boolean
                    true
                }

            isVisible = Utilities.ATLEAST_R
        }
        findPreference<SwitchPreference>("pref_exit_apps")?.apply {
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val data = newValue as Boolean
                    val runnable = Runnable() {
                        Utilities.getOmegaPrefs(context)::exitHidden.set(data)
                        isChecked = data
                    }
                    if (data) {
                        runnable.run()
                        requestUsagePermission(context)
                    } else {
                        isChecked = true // not work
                        showPasswordDialog(runnable)
                    }
                    true
                }
        }
        findPreference<SwitchPreference>("pref_reverse_hidden")?.apply {
            onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { _: Preference?, newValue: Any ->
                    val data = newValue as Boolean
                    val runnable = Runnable() {
                        Utilities.getOmegaPrefs(context)::reverseHidden.set(data)
                        isChecked = data
                    }
                    isChecked = !data
                    showPasswordDialog(runnable)
                    true
                }
        }

        findPreference<Preference>(PREFS_TRUST_APPS)?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                val runnable = Runnable() {
                    // 密码正确，启动目标Fragment
                    val fragment = "com.saggitt.omega.preferences.views.HiddenAppsFragment"
                    PreferencesActivity.startFragment(
                        requireContext(),
                        fragment,
                        requireContext().resources.getString(R.string.title__drawer_hide_apps)
                    )
                }
                if (
                    Utilities.getOmegaPrefs(requireContext()).enableProtectedApps &&
                    Utilities.ATLEAST_R
                ) {
                    Config.showLockScreen(
                        requireContext(),
                        getString(R.string.trust_apps_manager_name)
                    ) {
                        showPasswordDialog(runnable)
                    }
                } else {
                    showPasswordDialog(runnable)
                }
                false
            }
        }

        // 获取并处理 Preference
        findPreference<Preference>("pref_trust_auth")?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                // 启动设置密码界面
                val intent = Intent(context, PasswordSettingActivity::class.java)
                startActivity(intent)
                true
            }
        }

        findPreference<Preference>("pref_suggestions")?.apply {
            isVisible = false
            //isVisible = isDspEnabled(context)
        }
    }

    // 检测是否已授权 PACKAGE_USAGE_STATS
    fun hasUsageStatsPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    // 请求权限（如果未授权）
    fun requestUsagePermission(context: Context) {
        if (!hasUsageStatsPermission(context)) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        }
    }

    private fun showPasswordDialog(runnable: Runnable = Runnable { }) {
        val context = requireContext()

        val storedPassword = getStoredPassword()
        if (storedPassword == null || storedPassword == "") {
            runnable.run()
            return
        }
        val builder = AlertDialog.Builder(context)
        builder.setTitle("请输入密码")

        // 输入框
        val input = EditText(context)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        builder.setView(input)

        builder.setPositiveButton("确认") { dialog, _ ->
            val enteredPassword = input.text.toString()

            // 验证密码
            if (verifyPassword(enteredPassword)) {
                runnable.run()
            } else {
                Toast.makeText(context, "密码错误，请重试", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        builder.setNegativeButton("取消") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    private fun verifyPassword(password: String): Boolean {
        val storedPassword = getStoredPassword()
        return password == storedPassword
    }

    private fun getStoredPassword(): String? {
        return Utilities.getOmegaPrefs(context)::passwordHidden.get()
    }

    private fun isDspEnabled(context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo(Config.DPS_PACKAGE, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}