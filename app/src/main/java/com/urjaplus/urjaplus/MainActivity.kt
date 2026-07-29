package com.urjaplus.urjaplus

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private var mInterstitialAd: InterstitialAd? = null
    private var pendingUrl: String? = null

    // 🌐 तुमचे कस्टम डोमेन
    private val githubUrl = "https://www.urjaplus.in"
    private val notificationPermissionCode = 101
    private val adUnitId = "ca-app-pub-3940256099942544/1033173712" // Test Ad Unit ID

    // Ad Frequency Capping constants
    private val COOLDOWN_PERIOD = 10 * 60 * 1000 // 10 minutes in milliseconds
    private val PREFS_NAME = "AdPrefs"
    private val LAST_AD_TIME_KEY = "last_ad_show_time"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize Mobile Ads SDK
        MobileAds.initialize(this) {}
        loadInterstitialAd()

        // लेआउट ऐवजी थेट WebView तयार करणे
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
            }
        }

        setContentView(webView)

        // नवीन अँड्रॉइड बॅक बटण हँडलर (Back Gesture support)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })

        // पुश नोटिफिकेशनसाठी परमीशन मागणे
        requestNotificationPermission()

        // वेबव्ह्यू क्लायंट आणि शेअरिंग हँडलर
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // Removed auto-show on page finish to avoid interrupting the user
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()

                // 'share' असलेल्या लिंक्ससाठी शेअरिंग डायलॉग उघडणे
                if (url.startsWith("share://") || url.contains("share")) {
                    sharePage()
                    return true
                }

                // Check if we should show an ad before navigating
                if (shouldShowAd()) {
                    pendingUrl = url
                    showInterstitialAd()
                    return true // Intercept navigation to show ad
                }

                // If not showing ad, proceed normally
                return false 
            }
        }

        // वेबसाईट लोड करा
        webView.loadUrl(githubUrl)
    }

    private fun shouldShowAd(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val lastShowTime = prefs.getLong(LAST_AD_TIME_KEY, 0)
        val currentTime = System.currentTimeMillis()

        // Condition: Ad is loaded AND 10 minutes have passed
        return mInterstitialAd != null && (currentTime - lastShowTime >= COOLDOWN_PERIOD)
    }

    private fun updateLastAdShowTime() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putLong(LAST_AD_TIME_KEY, System.currentTimeMillis()).apply()
    }

    private fun proceedToPendingUrl() {
        pendingUrl?.let {
            webView.loadUrl(it)
            pendingUrl = null
        }
    }

    private fun loadInterstitialAd() {
        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(this, adUnitId, adRequest, object : InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                mInterstitialAd = null
            }

            override fun onAdLoaded(interstitialAd: InterstitialAd) {
                mInterstitialAd = interstitialAd
                
                // Set the FullScreenContentCallback
                mInterstitialAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                    override fun onAdDismissedFullScreenContent() {
                        // Called when ad is dismissed.
                        updateLastAdShowTime()
                        mInterstitialAd = null
                        proceedToPendingUrl()
                        loadInterstitialAd() // Load next ad for future use
                    }

                    override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                        // Called when ad fails to show.
                        mInterstitialAd = null
                        proceedToPendingUrl()
                        loadInterstitialAd()
                    }

                    override fun onAdShowedFullScreenContent() {
                        // Called when ad is shown.
                    }
                }
            }
        })
    }

    private fun showInterstitialAd() {
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
        } else {
            proceedToPendingUrl()
        }
    }

    // सध्याचे पेज शेअर करण्यासाठीचे फंक्शन
    private fun sharePage() {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "UrjaPlus")
            putExtra(Intent.EXTRA_TEXT, webView.url)
        }
        startActivity(Intent.createChooser(shareIntent, "Share via"))
    }

    // अँड्रॉइड १३+ साठी पुश नोटिफिकेशनची परमीशन मागणे
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    notificationPermissionCode
                )
            }
        }
    }
}