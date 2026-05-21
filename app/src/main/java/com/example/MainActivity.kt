package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.ui.theme.MyApplicationTheme
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // Let it go
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        requestNotificationPermission()
        setupDailyWorker()

        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    BudgetAppWebView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        context = this
                    )
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun setupDailyWorker() {
        val workRequest = PeriodicWorkRequestBuilder<BudgetWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyBudgetCheck",
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BudgetAppWebView(modifier: Modifier = Modifier, context: Context) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = true
                settings.allowFileAccessFromFileURLs = true
                settings.allowUniversalAccessFromFileURLs = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                webViewClient = WebViewClient()
                webChromeClient = WebChromeClient()
                addJavascriptInterface(WebAppInterface(context), "AndroidInterface")
                loadUrl("file:///android_asset/index.html")
            }
        }
    )
}

class WebAppInterface(private val mContext: Context) {
    @JavascriptInterface
    fun saveDataToAndroid(dataStr: String) {
        val prefs: SharedPreferences = mContext.getSharedPreferences("UniBudgetPrefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("appData", dataStr)
            putLong("lastSaved", System.currentTimeMillis())
            apply()
        }
    }
    
    @JavascriptInterface
    fun logError(msg: String) {
        android.util.Log.e("WebViewError", msg)
        android.widget.Toast.makeText(mContext, "JS Error: $msg", android.widget.Toast.LENGTH_LONG).show()
    }
}
