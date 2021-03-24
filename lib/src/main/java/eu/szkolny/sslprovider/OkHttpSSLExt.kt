/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-24.
 */

package eu.szkolny.sslprovider

import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

private fun getDefaultTrustManager(): X509TrustManager? {
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    trustManagerFactory.init(null as KeyStore?)
    return trustManagerFactory.trustManagers.firstOrNull {
        it is X509TrustManager
    } as? X509TrustManager
}

/**
 * Creates a SSL socket factory for the [OkHttpClient], having enabled
 * all SSL protocols supported by the device. This includes any additional
 * protocols added by installing Conscrypt/GMS SSL provider.
 *
 * @param enableCleartext whether HTTP traffic should be permitted
 */
@Suppress("DEPRECATION")
fun OkHttpClient.Builder.enableSupportedTls(
    enableCleartext: Boolean = true
): OkHttpClient.Builder {
    val trustManager = SSLProvider.trustManager ?: getDefaultTrustManager()

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustManager?.let { arrayOf(it) }, null)
    val enabledProtocols = sslContext.defaultSSLParameters.protocols
    val socketFactory = TlsSocketFactory(sslContext.socketFactory, enabledProtocols)

    if (trustManager != null)
        sslSocketFactory(socketFactory, trustManager)
    else
        sslSocketFactory(socketFactory)

    val tlsSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
        .tlsVersions(*enabledProtocols)
        .build()
    connectionSpecs(listOfNotNull(
        tlsSpec,
        if (enableCleartext) ConnectionSpec.CLEARTEXT else null
    ))

    return this
}
