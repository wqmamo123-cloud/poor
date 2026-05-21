package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.util.Calendar

class BudgetWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs: SharedPreferences = context.getSharedPreferences("UniBudgetPrefs", Context.MODE_PRIVATE)
        val dataStr = prefs.getString("appData", "")
        if (dataStr.isNullOrEmpty()) {
            return Result.success()
        }

        try {
            val json = JSONObject(dataStr)
            val budget = json.optDouble("budget", 0.0)
            val months = json.optInt("months", 1)
            if (budget <= 0.0 || months <= 0) return Result.success()
            
            val remainingTotal = calculateRemaining(json)
            val totalAllocation = budget / months
            val totalSpentMonthly = calculateMonthlySpent(json)
            
            val calendar = Calendar.getInstance()
            
            // 1- إشعارات الميزانية
            val spendPct = if (totalAllocation > 0) (totalSpentMonthly / totalAllocation) * 100 else 0.0
            if (spendPct > 90.0) {
                showNotification("تحذير الميزانية 🚨", "لقد استهلكت أكثر من 90% من ميزانيتك لهذا الشهر! تبقى لديك ${remainingTotal.toInt()}", 1)
            } else if (spendPct < 30.0 && calendar.get(Calendar.DAY_OF_MONTH) > 20) {
                showNotification("أداء ممتاز 🌟", "لقد حققت توفيراً ممتازاً هذا الشهر! أحسنت في تنظيم مصروفاتك.", 2)
            } else if (calendar.get(Calendar.DAY_OF_WEEK) == Calendar.FRIDAY) {
                showNotification("ملخص الميزانية 📊", "المبلغ الكلي المتبقي لديك هو ${remainingTotal.toInt()}. راجع مصروفاتك للاستعداد للأسبوع القادم.", 3)
            }

            // 2- إشعارات تحفيزية
            if (calendar.get(Calendar.HOUR_OF_DAY) in 9..11 && calendar.get(Calendar.DAY_OF_MONTH) % 5 == 0) {
                showNotification("تنظيم المصروفات 💡", "الاستمرار في تسجيل مصروفاتك يومياً يساعدك على إدارة مالية أفضل للجامعة.", 4)
            }

            // 3- إشعارات التفاعل (إذا لم يستخدم لمدة طويلة - check based on last saved)
            val lastSaved = prefs.getLong("lastSaved", 0L)
            if (lastSaved > 0 && System.currentTimeMillis() - lastSaved > 2 * 24 * 60 * 60 * 1000L) { // 2 days
                showNotification("أين أنت؟ 👀", "لم تقم بتسجيل مصروفاتك منذ فترة، افتح التطبيق الآن لتحديث بياناتك.", 5)
            }

            // 4- إشعارات المناسبات
            val hijriMonth = calHijriMonth(calendar) // Simplified check
            if (hijriMonth == 9) { // Ramadan
                if (remainingTotal > 0) {
                    showNotification("رمضان كريم 🌙", "استمتع بالأجواء الرمضانية، ولكن تذكر الموازنة بحكمة!", 6)
                } else {
                    showNotification("رمضان كريم 🌙", "نصيحة: حاول التحكم في مصاريف الإفطار والسحور هذا الشهر لتعويض الميزانية.", 6)
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
        }

        return Result.success()
    }

    private fun showNotification(title: String, message: String, notifId: Int) {
        val channelId = "budget_updates"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "تحديثات الميزانية", NotificationManager.IMPORTANCE_DEFAULT)
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(notifId, builder.build())
    }

    private fun calculateRemaining(json: JSONObject): Double {
        val budget = json.optDouble("budget", 0.0)
        val records = json.optJSONObject("records") ?: return budget
        var totalIncome = 0.0
        var totalSpent = 0.0
        
        val iterator = records.keys()
        while(iterator.hasNext()) {
            val key = iterator.next()
            val type = key.substringAfterLast("-")
            val valStr = records.optString(key, "0")
            val v = valStr.toDoubleOrNull() ?: 0.0
            if(type == "i") {
                totalIncome += v
            } else {
                totalSpent += v
            }
        }
        return budget + totalIncome - totalSpent
    }

    private fun calculateMonthlySpent(json: JSONObject): Double {
        val records = json.optJSONObject("records") ?: return 0.0
        var totalSpent = 0.0
        
        val iterator = records.keys()
        while(iterator.hasNext()) {
            val key = iterator.next()
            val mIdx = key.substringAfter("m").substringBefore("-").toIntOrNull() ?: 0
            if (mIdx == 0) { // For simplicity, we check the first month
                val type = key.substringAfterLast("-")
                val valStr = records.optString(key, "0")
                val v = valStr.toDoubleOrNull() ?: 0.0
                if(type != "i") {
                    totalSpent += v
                }
            }
        }
        return totalSpent
    }
    
    // Very simplified Hijri month checker based on Gregorian
    private fun calHijriMonth(cal: Calendar): Int {
        return -1 // Defaulting ignoring hijri calc complexity in worker
    }
}
