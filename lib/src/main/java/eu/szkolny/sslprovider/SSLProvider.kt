/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-24.
 */

package eu.szkolny.sslprovider

import android.content.Context
import android.os.Build
import dalvik.system.DexClassLoader
import okhttp3.*
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.Provider
import java.security.Security
import java.util.concurrent.TimeUnit
import java.util.zip.ZipFile
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

object SSLProvider {
    enum class FinishReason {
        FINISHED_NOT_NEEDED,
        FINISHED_ALREADY_INSTALLED,
        FINISHED_GMS,
        FINISHED_CONSCRYPT_EXISTING,
        FINISHED_CONSCRYPT_DOWNLOADED
    }

    /**
     * The default security provider name, used for creating
     * Conscrypt SSL provider.
     */
    var defaultProviderName = "SzkolnyEu_Conscrypt"

    /**
     * Equivalent of `Conscrypt.getDefaultX509TrustManager()`. Set only when
     * Conscrypt was installed.
     */
    var trustManager: X509TrustManager? = null

    /**
     * URL to a ZIP containing the Conscrypt library. See the README for details.
     */
    var conscryptLibraryUrl = "http://s1.szkolny.eu/ssl-provider/conscrypt-android.zip"

    /**
     * Get the default path to store the library ZIP.
     */
    fun getLibFile(context: Context) =
        File(context.getDir("lib", Context.MODE_PRIVATE), "conscrypt-android.zip")

    /**
     * Installs modern TLS support as a security provider.
     *
     * @param context the application context
     * @param downloadIfNeeded whether to download the Conscrypt JSP if GMS not available, else fail
     * @param supportTls13 whether TLSv1.3 support is required. This will never use the GMS provider.
     * @param libFile path to the Conscrypt library ZIP, by default in the app's private storage.
     * @param onFinish installation finished listener
     * @param onError exception listener
     */
    fun install(
        context: Context,
        downloadIfNeeded: Boolean,
        supportTls13: Boolean = false,
        libFile: File = getLibFile(context),
        onFinish: (reason: FinishReason) -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        try {
            installImpl(context, downloadIfNeeded, supportTls13, libFile, onFinish, onError)
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun installImpl(
        context: Context,
        downloadIfNeeded: Boolean,
        supportTls13: Boolean,
        libFile: File,
        onFinish: (reason: FinishReason) -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && !supportTls13
            || Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1
        ) {
            onFinish(FinishReason.FINISHED_NOT_NEEDED)
            return
        }

        val providers = Security.getProviders().map { it.name }

        // provider already installed
        // GmsCore_OpenSSL - Google Play Services' SSL provider
        if (providers.contains(defaultProviderName) || providers.contains("GmsCore_OpenSSL") && !supportTls13) {
            onFinish(FinishReason.FINISHED_ALREADY_INSTALLED)
            return
        }

        // the GMS provider does not support TLSv1.3
        if (!supportTls13) {
            try {
                // install the Google Play Services' SSL provider
                val packageContext = context.createPackageContext(
                    "com.google.android.gms",
                    Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
                )
                val cls =
                    packageContext.classLoader.loadClass("com.google.android.gms.common.security.ProviderInstallerImpl")
                val method = cls.getMethod("insertProvider", Context::class.java)
                method.invoke(null, packageContext)
                onFinish(FinishReason.FINISHED_GMS)
                return
            } catch (e: Exception) {
                // fallback to Conscrypt on an exception
                e.printStackTrace()
            }
        }

        if (libFile.exists()) {
            loadLibrary(context, libFile, onSuccess = {
                onFinish(FinishReason.FINISHED_CONSCRYPT_EXISTING)
            }, onError)
            return
        }

        if (downloadIfNeeded) {
            downloadLibrary(libFile, onSuccess = {
                loadLibrary(context, libFile, onSuccess = {
                    onFinish(FinishReason.FINISHED_CONSCRYPT_DOWNLOADED)
                }, onError)
            }, onError)
        } else {
            onError(FileNotFoundException("Conscrypt library file not found."))
        }
    }

    /**
     * Download the library ZIP from [conscryptLibraryUrl].
     */
    fun downloadLibrary(
        libFile: File,
        onSuccess: () -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        val http = OkHttpClient.Builder()
            .callTimeout(10L, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(conscryptLibraryUrl)
            .build()

        val call = http.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.body()?.byteStream()?.use { input ->
                    libFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                onSuccess()
            }
        })
    }

    /**
     * Load the Conscrypt library from a ZIP file, install the security provider.
     */
    fun loadLibrary(
        context: Context,
        libFile: File,
        onSuccess: () -> Unit,
        onError: (e: Exception) -> Unit
    ) {
        val libDir = libFile.parentFile!!
        val jniFile = File(libDir, "libconscrypt_jni.so")

        if (!jniFile.exists()) {
            try {
                unpackJni(libFile, jniFile)
            } catch (e: Exception) {
                onError(e)
                return
            }
        }

        try {
            val classLoader = DexClassLoader(
                libFile.absolutePath,
                libDir.absolutePath,
                libDir.absolutePath,
                context.classLoader
            )

            insertProvider(classLoader)
            onSuccess()
        } catch (e: Exception) {
            onError(e)
        }
    }

    private fun unpackJni(
        libFile: File,
        jniFile: File,
    ) {
        val cpuAbi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Build.SUPPORTED_ABIS[0]
        } else {
            Build.CPU_ABI
        }

        val jniName = "$cpuAbi/libconscrypt_jni.so"

        val zip = ZipFile(libFile)
        val jniEntry = zip.getEntry(jniName)
            ?: throw FileNotFoundException("JNI file $jniName not found in library ZIP.")

        zip.getInputStream(jniEntry).use { input ->
            jniFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        zip.close()
    }

    private fun insertProvider(
        classLoader: DexClassLoader
    ) {
        val providerClass = classLoader.loadClass("org.conscrypt.OpenSSLProvider")
        val conscryptClass = classLoader.loadClass("org.conscrypt.Conscrypt")

        //val cryptoClass = classLoader.loadClass("org.conscrypt.NativeCrypto")
        //val provider = getProvider(cryptoClass, providerClass)

        val constr = providerClass.getDeclaredConstructor(String::class.java)
        val provider = constr.newInstance(defaultProviderName) as Provider?

        val tmMethod = conscryptClass.getDeclaredMethod("getDefaultX509TrustManager")
        trustManager = tmMethod.invoke(null) as? X509TrustManager

        if (Security.insertProviderAt(provider, 1) == 1) {
            Security.setProperty(
                "ssl.SocketFactory.provider",
                "org.conscrypt.OpenSSLSocketFactoryImpl"
            )
            Security.setProperty(
                "ssl.ServerSocketFactory.provider",
                "org.conscrypt.OpenSSLServerSocketFactoryImpl"
            )

            SSLContext.setDefault(SSLContext.getInstance("Default"))
            HttpsURLConnection.setDefaultSSLSocketFactory(SSLContext.getDefault().socketFactory)
        } else {
            throw RuntimeException("Failed to insert SSL provider")
        }
    }

    /* https://github.com/microg/GmsCore/blob/v0.2.10.19420/play-services-core/src/main/java/com/google/android/gms/common/security/ProviderInstallerImpl.java */

    // alternative method, not really working - would require
    // loading the native library manually, not possible with
    // the same classLoader

    /*private fun getProvider(
        cryptoClass: Class<*>,
        providerClass: Class<*>
    ): Provider {
        val loadError = cryptoClass.getDeclaredField("loadError")
        loadError.isAccessible = true
        loadError.set(null, null)

        val clinit = cryptoClass.getDeclaredMethod("clinit")
        clinit.isAccessible = true

        val get_cipher_names =
            cryptoClass.getDeclaredMethod("get_cipher_names", String::class.java)
        get_cipher_names.isAccessible = true

        val cipherSuiteToJava =
            cryptoClass.getDeclaredMethod("cipherSuiteToJava", String::class.java)
        cipherSuiteToJava.isAccessible = true

        val EVP_has_aes_hardware = cryptoClass.getDeclaredMethod("EVP_has_aes_hardware")
        EVP_has_aes_hardware.isAccessible = true

        var f = cryptoClass.getDeclaredField("SUPPORTED_TLS_1_2_CIPHER_SUITES_SET")
        f.isAccessible = true
        val SUPPORTED_TLS_1_2_CIPHER_SUITES_SET = f.get(null) as MutableSet<String>

        f = cryptoClass.getDeclaredField("SUPPORTED_LEGACY_CIPHER_SUITES_SET")
        f.isAccessible = true
        val SUPPORTED_LEGACY_CIPHER_SUITES_SET = f.get(null) as MutableSet<String>

        f = cryptoClass.getDeclaredField("SUPPORTED_TLS_1_2_CIPHER_SUITES")
        f.isAccessible = true

        return try {
            clinit.invoke(null)

            val allCipherSuites = get_cipher_names.invoke(null, "ALL:!DHE") as Array<String>
            val size = allCipherSuites.size

            val SUPPORTED_TLS_1_2_CIPHER_SUITES = arrayOfNulls<String>(size / 2 + 2)
            var i = 0
            while (i < size) {
                val cipherSuite = cipherSuiteToJava.invoke(null, allCipherSuites[i]) as String
                SUPPORTED_TLS_1_2_CIPHER_SUITES[i / 2] = cipherSuite
                SUPPORTED_TLS_1_2_CIPHER_SUITES_SET.add(cipherSuite)
                SUPPORTED_LEGACY_CIPHER_SUITES_SET.add(allCipherSuites[i + 1])
                i += 2
            }
            SUPPORTED_TLS_1_2_CIPHER_SUITES[size / 2] = "TLS_EMPTY_RENEGOTIATION_INFO_SCSV"
            SUPPORTED_TLS_1_2_CIPHER_SUITES[size / 2 + 1] = "TLS_FALLBACK_SCSV"
            f.set(null, SUPPORTED_TLS_1_2_CIPHER_SUITES)

            f = cryptoClass.getDeclaredField("HAS_AES_HARDWARE")
            f.isAccessible = true
            f.set(null, EVP_has_aes_hardware.invoke(null) as Int == 1)

            val constr = providerClass.getDeclaredConstructor(String::class.java)
            constr.newInstance(defaultProviderName) as Provider
        } catch (inner: InvocationTargetException) {
            if (inner.targetException is UnsatisfiedLinkError) {
                loadError.set(null, inner.targetException)
            }
            throw inner
        }
    }*/
}
