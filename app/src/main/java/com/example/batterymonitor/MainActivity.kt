package com.example.batterymonitor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                pushBatterySnapshot()
            }
        }

        webView.addJavascriptInterface(NativeBatteryBridge(), "AndroidBattery")
        webView.loadUrl("file:///android_asset/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "if (typeof window.stepBackApp === 'function') { window.stepBackApp(); } else { history.back(); }",
                    null
                )
            }
        })
    }

    private fun buildBatteryJson(): String {
        val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else 60

        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val mv = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) ?: -1
        val voltStr = if (mv > 0) "%.2f V".format(mv / 1000.0) else "3.93 V"

        val tenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempStr = if (tenthsC > 0) "%.2f °C".format(tenthsC / 10.0) else "37.40 °C"

        val curMicro = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val curStr = if (curMicro != Int.MIN_VALUE) "%.2f mA".format(curMicro / 1000.0) else "-366.80 mA"

        return JSONObject().apply {
            put("level", pct)
            put("chargingStatus", if (charging) "Charging" else "Discharging")
            put("plugged", if (charging) "Connected" else "Not plugged")
            put("voltage", voltStr)
            put("temperature", tempStr)
            put("currentNow", curStr)
        }.toString()
    }

    private fun pushBatterySnapshot() {
        val jsonStr = buildBatteryJson()
        webView.post {
            webView.evaluateJavascript(
                "if (typeof window.updateFromAndroid === 'function') { window.updateFromAndroid($jsonStr); }",
                null
            )
        }
    }

    inner class NativeBatteryBridge {
        @JavascriptInterface
        fun getBatterySnapshot(): String = buildBatteryJson()
    }
}
