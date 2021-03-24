/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-24.
 */

package eu.szkolny.sslprovider.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.widget.Button
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import eu.szkolny.sslprovider.SSLProvider
import eu.szkolny.sslprovider.enableSupportedTls
import okhttp3.*
import java.io.IOException
import java.net.Inet4Address
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var client: OkHttpClient

    private fun buildClient() {
        client = OkHttpClient.Builder()
            .cache(null)
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .enableSupportedTls()
            .dns { hostname ->
                Dns.SYSTEM.lookup(hostname).filterIsInstance<Inet4Address>()
            }
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        buildClient()

        val urls = mapOf(
            "http://http.badssl.com/" to "BadSSL - HTTP",
            "https://api.szkolny.eu/root" to "API Szkolny.eu",
            "https://tls-v1-0.badssl.com:1010/" to "BadSSL (TLSv1.0)",
            "https://tls-v1-1.badssl.com:1011/" to "BadSSL (TLSv1.1)",
            "https://tls-v1-2.badssl.com:1012/" to "BadSSL (TLSv1.2)",
            "https://tls13.1d.pw/" to "TLS 1.3 (tls13.1d.pw)"
        )

        findViewById<Button>(R.id.button).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Select target address")
                .setItems(urls.values.toTypedArray()) { _, which ->
                    call(urls.keys.toList()[which])
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<Button>(R.id.install).setOnClickListener {
            install(false)
        }

        findViewById<Button>(R.id.install13).setOnClickListener {
            install(true)
        }
    }

    private fun install(supportTls13: Boolean) {
        SSLProvider.install(
            this,
            downloadIfNeeded = true,
            supportTls13,
            onFinish = {
                buildClient()
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Success")
                        .setMessage("SSLProvider installation finished: $it\n\nA new OkHttpClient has been built.")
                        .setPositiveButton("OK", null)
                        .show()
                }
                Log.d("MainActivity", "SSLProvider installation finished: $it")
            },
            onError = {
                runOnUiThread {
                    MaterialAlertDialogBuilder(this)
                        .setTitle("Error")
                        .setMessage(it.toString() + "\n\n" + Log.getStackTraceString(it))
                        .setPositiveButton("OK", null)
                        .show()
                }
                it.printStackTrace()
            }
        )
    }

    private fun call(url: String) {
        val request = Request.Builder()
            .url(url)
            .addHeader("Accept",  "application/json")
            .get()
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Failure")
                        .setMessage(e.toString() + "\n\n" + Log.getStackTraceString(e))
                        .setPositiveButton("OK", null)
                        .show()
                }
                Log.e("MainActivity", "Failure $e")
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body()?.string() ?: "null"
                val sslInfo = "TLS version: ${response.handshake()?.tlsVersion()}\n" +
                        "Cipher suite: ${response.handshake()?.cipherSuite()}\n\n"
                runOnUiThread {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Success")
                        .setMessage(sslInfo + Html.fromHtml(body))
                        .setPositiveButton("OK", null)
                        .show()
                }
                Log.d("MainActivity", body)
            }
        })
    }
}
