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
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
    private lateinit var config:Config

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

        findPreference<Preference>(PREFS_TRUST_APPS)?.apply {
            onPreferenceClickListener = Preference.OnPreferenceClickListener {
                if (
                    Utilities.getOmegaPrefs(requireContext()).enableProtectedApps &&
                    Utilities.ATLEAST_R
                ) {
                    Config.showLockScreen(
                        requireContext(),
                        getString(R.string.trust_apps_manager_name)
                    ) {
                        showPasswordDialog()
                    }
                } else {
                    showPasswordDialog()
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

    private fun showPasswordDialog() {
        val context = requireContext()

        if (getStoredPassword() == null || getStoredPassword() == "") {
            val fragment = "com.saggitt.omega.preferences.views.HiddenAppsFragment"
            PreferencesActivity.startFragment(
                context,
                fragment,
                context.resources.getString(R.string.title__drawer_hide_apps)
            )
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
                // 密码正确，启动目标Fragment
                val fragment = "com.saggitt.omega.preferences.views.HiddenAppsFragment"
                PreferencesActivity.startFragment(
                    context,
                    fragment,
                    context.resources.getString(R.string.title__drawer_hide_apps)
                )
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
        return config?.getPassword()
    }

    private fun isDspEnabled(context: Context): Boolean {
        return try {
            context.packageManager.getApplicationInfo(Config.DPS_PACKAGE, 0).enabled
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}