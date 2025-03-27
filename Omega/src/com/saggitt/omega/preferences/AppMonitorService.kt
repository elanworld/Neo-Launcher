package com.saggitt.omega.preferences

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.Utilities.makeComponentKey
import com.saggitt.omega.util.Config

class AppMonitorService : Service() {
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private var isRunning = true

    override fun onCreate() {
        super.onCreate()
        val config = Config(context = baseContext)
        if (config.getFile("exit_setting") == "true") {
            startForegroundService()
            startMonitoring()
            setRepeatingAlarm(baseContext)
        }
    }

    // 创建前台通知，避免被系统杀死
    private fun startForegroundService() {
        val channelId = "AppMonitorChannel"
        val channel = NotificationChannel(channelId, "App Monitor", NotificationManager.IMPORTANCE_HIGH)
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("App Monitor")
            .setContentText("Check App usage...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .build()

        startForeground(1, notification)
    }

    // 监测前台应用
    private fun startMonitoring() {
        handlerThread = HandlerThread("AppMonitorThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
        handler.post(object : Runnable {
            override fun run() {
                if (isRunning) {
                    val foregroundApp = getForegroundApp()
                    val hiddenApps = Utilities.getOmegaPrefs(baseContext)::hiddenAppSet
                    val componentKeys = hiddenApps.get().map { makeComponentKey(baseContext, it) }
                    if (foregroundApp in componentKeys.map { it.componentName.packageName }) {
                        goHome()
                        if (foregroundApp != null) {
                            forceStopApp(foregroundApp)
                        }
                    }
                    handler.postDelayed(this, 1000)  // 每秒执行
                }
            }
        })

    }

    // 获取前台应用
    private fun getForegroundApp(): String? {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60000, now)

        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    // 强制返回桌面
    private fun goHome() {
        // 返回桌面
        val homeIntent = Intent(Intent.ACTION_MAIN)
        homeIntent.addCategory(Intent.CATEGORY_HOME)
        homeIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(homeIntent)
        // 启动本桌面
        val intent = baseContext.packageManager.getLaunchIntentForPackage(baseContext.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        baseContext.startActivity(intent)
    }
    fun forceStopApp(packageName: String) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop $packageName"))
            process.waitFor()
        } catch (e: Exception) {
            e.message?.let { Log.e("root", it) }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    fun setRepeatingAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, MyAlarmReceiver::class.java)

        // 创建一个 PendingIntent，AlarmManager 会通过它触发广播
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 设置定时任务，每2秒触发一次（使用 RTC_WAKEUP 让设备在休眠时仍然能触发）
        val interval = 2000L // 2秒
        val triggerAtMillis = System.currentTimeMillis() + interval

        // 设置循环定时任务
        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            interval,
            pendingIntent
        )
    }
}

class AppMonitorWorker(context: Context, workerParams: WorkerParameters) : Worker(context, workerParams) {
    override fun doWork(): Result {
        Log.d("AppMonitorWorker", "执行后台任务")
        return Result.success()
    }
}

class MyAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 在此执行你需要定期执行的操作
        Log.d("MyAlarmReceiver", "Alarm triggered!")
    }
}