package com.scrudio.tv.ui.pair

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.scrudio.tv.R
import com.scrudio.tv.data.dto.DeviceCodeDto
import com.scrudio.tv.data.repository.RealDebridAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Real-Debrid OAuth pairing screen.
 *
 * 1. Calls `requestDeviceCode` and renders the user_code + QR (pointing at
 *    `direct_verification_url`, so the phone scan jumps straight to the
 *    pre-filled approval page on real-debrid.com).
 * 2. Polls `pollOnce` every `interval` seconds until the user approves the
 *    pairing on their phone, or the device_code expires.
 * 3. On success, exchanges for a real OAuth token, stores the bundle in
 *    [com.scrudio.tv.data.settings.ScrudioSettings] and finishes with
 *    [Activity.RESULT_OK]. The Settings screen reads its preferences again
 *    on resume and updates the "Real-Debrid token" summary.
 */
class PairActivity : FragmentActivity() {

    private val auth by lazy { RealDebridAuthRepository.get(this) }
    private lateinit var statusView: TextView
    private lateinit var userCodeView: TextView
    private lateinit var urlView: TextView
    private lateinit var qrView: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_pair)

        statusView = findViewById(R.id.pair_status)
        userCodeView = findViewById(R.id.pair_user_code)
        urlView = findViewById(R.id.pair_url)
        qrView = findViewById(R.id.pair_qr)

        startPairing()
    }

    private fun startPairing() {
        lifecycleScope.launch {
            val device = try {
                statusView.text = getString(R.string.pair_status_init)
                withContext(Dispatchers.IO) { auth.requestDeviceCode() }
            } catch (e: Exception) {
                Log.e(TAG, "requestDeviceCode failed", e)
                statusView.text = getString(R.string.pair_status_error, e.message ?: "")
                return@launch
            }

            renderDeviceCode(device)
            pollLoop(device)
        }
    }

    private fun renderDeviceCode(d: DeviceCodeDto) {
        userCodeView.text = d.userCode
        urlView.text = d.verificationUrl.removePrefix("https://").removePrefix("http://")

        // QR points to the direct URL when available — the phone scanner
        // jumps straight to a pre-filled approval page (no typing).
        val target = d.directVerificationUrl?.takeIf { it.isNotBlank() } ?: d.verificationUrl
        try {
            qrView.setImageBitmap(QrCodeGenerator.encode(target, QR_SIZE_PX))
        } catch (e: Exception) {
            Log.w(TAG, "QR encode failed", e)
        }

        statusView.text = getString(R.string.pair_status_waiting)
    }

    /** Polls for credentials until they appear, the code expires, or we're cancelled. */
    private suspend fun pollLoop(d: DeviceCodeDto) {
        val intervalMs = (d.intervalSeconds.coerceAtLeast(2)) * 1_000L
        val deadline = System.currentTimeMillis() + d.expiresInSeconds * 1_000L

        while (System.currentTimeMillis() < deadline && !isFinishing) {
            delay(intervalMs)

            val creds = try {
                withContext(Dispatchers.IO) { auth.pollOnce(d.deviceCode) }
            } catch (e: Exception) {
                Log.e(TAG, "poll failed", e)
                statusView.text = getString(R.string.pair_status_error, e.message ?: "")
                return
            } ?: continue   // not authorized yet → keep waiting

            // User approved. Exchange + persist.
            statusView.text = getString(R.string.pair_status_authorizing)
            val token = try {
                withContext(Dispatchers.IO) { auth.exchangeForToken(d.deviceCode, creds) }
            } catch (e: Exception) {
                Log.e(TAG, "token exchange failed", e)
                statusView.text = getString(R.string.pair_status_error, e.message ?: "")
                return
            }

            withContext(Dispatchers.IO) { auth.saveAuth(token, creds) }
            statusView.text = getString(R.string.pair_status_done)
            setResult(Activity.RESULT_OK)
            // Brief pause so the user sees the success message before we close.
            delay(1_500)
            finish()
            return
        }

        statusView.text = getString(R.string.pair_status_expired)
    }

    companion object {
        private const val TAG = "PairActivity"
        private const val QR_SIZE_PX = 480

        fun start(context: Context) {
            context.startActivity(Intent(context, PairActivity::class.java))
        }
    }
}
