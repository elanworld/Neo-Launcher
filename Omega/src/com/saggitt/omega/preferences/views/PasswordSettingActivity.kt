package com.saggitt.omega.preferences.views
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.android.launcher3.R
import com.saggitt.omega.util.Config

class PasswordSettingActivity : AppCompatActivity() {

    private lateinit var originalPasswordEditText: EditText
    private lateinit var newPasswordEditText: EditText
    private lateinit var confirmPasswordEditText: EditText
    private lateinit var submitButton: Button
    private var config: Config = Config(this)
    private var storedPassword: String? = null // 从文件或安全存储中读取的密码

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_password_setting)

        originalPasswordEditText = findViewById(R.id.originalPasswordEditText)
        newPasswordEditText = findViewById(R.id.newPasswordEditText)
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText)
        submitButton = findViewById(R.id.submitButton)

        // 从存储中获取已存储的密码
        storedPassword = config.getPassword()

        submitButton.setOnClickListener {
            handlePasswordChange()
        }
        config.checkFilePermissions(this, true)
    }

    private fun handlePasswordChange() {
        val originalPassword = originalPasswordEditText.text.toString()
        val newPassword = newPasswordEditText.text.toString()
        val confirmPassword = confirmPasswordEditText.text.toString()

        if (storedPassword != null && originalPassword != storedPassword) {
            Toast.makeText(this, "原密码不正确", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPassword != confirmPassword) {
            Toast.makeText(this, "新密码与确认密码不匹配", Toast.LENGTH_SHORT).show()
            return
        }

        // 更新密码存储
        config.savePassword(newPassword)
        Toast.makeText(this, "密码设置成功", Toast.LENGTH_SHORT).show()
        finish()
    }

}
