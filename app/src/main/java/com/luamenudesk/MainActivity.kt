package com.luamenudesk

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var chooserCallback: ValueCallback<Array<Uri>>? = null

    private val pickFile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uris = WebChromeClient.FileChooserParams.parseResult(res.resultCode, res.data)
        chooserCallback?.onReceiveValue(uris)
        chooserCallback = null
    }

    /** Called from the page as window.AndroidBridge.saveFile(name, base64, folder). */
    inner class Bridge {
        @JavascriptInterface
        fun saveFile(filename: String, base64: String, folder: String): String {
            return try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                "OK:" + FileSaver.save(applicationContext, filename, bytes, folder)
            } catch (e: Exception) {
                "ERR:" + (e.message ?: e.javaClass.simpleName)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // the page remembers each script's layout in localStorage
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                chooserCallback?.onReceiveValue(null)
                chooserCallback = callback
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"   // .lua has no registered MIME type
                }
                return try {
                    pickFile.launch(intent)
                    true
                } catch (e: Exception) {
                    chooserCallback = null
                    callback.onReceiveValue(null)
                    false
                }
            }
        }
        webView.addJavascriptInterface(Bridge(), "AndroidBridge")

        if (savedInstanceState == null) {
            webView.loadUrl("file:///android_asset/desk.html")
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}
