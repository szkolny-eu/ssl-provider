/*
 * Copyright (c) Kuba Szczodrzyński 2021-3-24.
 */

package eu.szkolny.sslprovider

import java.net.InetAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class TlsSocketFactory(
    private val sslSocketFactory: SSLSocketFactory,
    private val enabledProtocols: Array<String>
) : SSLSocketFactory() {

    override fun createSocket() =
        patchSocket(sslSocketFactory.createSocket())

    override fun createSocket(host: String, port: Int) =
        patchSocket(sslSocketFactory.createSocket(host, port))

    override fun createSocket(host: InetAddress, port: Int) =
        patchSocket(sslSocketFactory.createSocket(host, port))

    override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int) =
        patchSocket(sslSocketFactory.createSocket(host, port, localHost, localPort))

    override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int) =
        patchSocket(sslSocketFactory.createSocket(address, port, localAddress, localPort))

    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean) =
        patchSocket(sslSocketFactory.createSocket(s, host, port, autoClose))

    override fun getDefaultCipherSuites(): Array<String> = sslSocketFactory.defaultCipherSuites
    override fun getSupportedCipherSuites(): Array<String> = sslSocketFactory.supportedCipherSuites

    private fun patchSocket(socket: Socket): Socket {
        if (socket is SSLSocket)
            socket.enabledProtocols = enabledProtocols
        return socket
    }
}
