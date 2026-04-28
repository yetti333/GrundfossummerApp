package com.example.grundfos_summer_app.data.discovery
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
/**
 * Discovers the ESP device on the local network using Android NsdManager (_http._tcp mDNS/DNS-SD).
 * Call discoverEspIp once at startup or after reconnect.
 * Cache the returned IP and use it directly for all subsequent HTTP requests.
 */
class NsdDiscovery(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    /**
     * Discovers the ESP _http._tcp service on the local network.
     * @param timeoutMs Maximum time to wait (default 5 s).
     * @return Discovered IPv4 address (e.g. "192.168.1.42") or null on timeout/failure.
     */
    suspend fun discoverEspIp(timeoutMs: Long = 5000L): String? {
        Log.d(TAG, "NSD: starting discovery (timeout=${timeoutMs}ms)")
        // Multicast lock prevents Android from dropping mDNS multicast packets in Wi-Fi power-save.
        val multicastLock = wifiManager.createMulticastLock("esp_nsd_lock")
        multicastLock.setReferenceCounted(false)
        multicastLock.acquire()
        return try {
            val ip = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine { cont ->
                    var discoveryListenerRef: NsdManager.DiscoveryListener? = null
                    val resolveListener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "NSD: resolve failed for '${si.serviceName}', error=$errorCode")
                        }
                        override fun onServiceResolved(si: NsdServiceInfo) {
                            val rawAddr = si.host?.hostAddress ?: return
                            // Strip IPv6 scope suffix (e.g. "fe80::1%wlan0") and skip IPv6
                            val cleanIp = rawAddr.substringBefore('%')
                            if (cleanIp.contains(':')) {
                                Log.d(TAG, "NSD: skipping IPv6 $cleanIp for '${si.serviceName}'")
                                return
                            }
                            Log.d(TAG, "NSD: resolved '${si.serviceName}' -> $cleanIp")
                            if (cont.isActive) {
                                try {
                                    discoveryListenerRef?.let { nsdManager.stopServiceDiscovery(it) }
                                } catch (_: Exception) {}
                                cont.resume(cleanIp)
                            }
                        }
                    }
                    val discoveryListener = object : NsdManager.DiscoveryListener {
                        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Log.e(TAG, "NSD: discovery start failed, error=$errorCode")
                            if (cont.isActive) cont.resume(null)
                        }
                        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                            Log.w(TAG, "NSD: stop failed, error=$errorCode")
                        }
                        override fun onDiscoveryStarted(serviceType: String) {
                            Log.d(TAG, "NSD: discovery started for $serviceType")
                        }
                        override fun onDiscoveryStopped(serviceType: String) {
                            Log.d(TAG, "NSD: discovery stopped for $serviceType")
                        }
                        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "NSD: found service '${serviceInfo.serviceName}'")
                            if (serviceInfo.serviceName.contains("grundfos", ignoreCase = true)) {
                                try {
                                    nsdManager.resolveService(serviceInfo, resolveListener)
                                } catch (e: Exception) {
                                    Log.e(TAG, "NSD: resolveService error: ${e.message}")
                                }
                            }
                        }
                        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "NSD: service lost '${serviceInfo.serviceName}'")
                        }
                    }
                    discoveryListenerRef = discoveryListener
                    try {
                        nsdManager.discoverServices(
                            "_http._tcp",
                            NsdManager.PROTOCOL_DNS_SD,
                            discoveryListener
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "NSD: discoverServices threw: ${e.message}")
                        if (cont.isActive) cont.resume(null)
                        return@suspendCancellableCoroutine
                    }
                    cont.invokeOnCancellation {
                        try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
                    }
                }
            }
            Log.d(TAG, "NSD: discovery finished, ip=$ip")
            ip
        } finally {
            try { multicastLock.release() } catch (_: Exception) {}
        }
    }
    companion object {
        private const val TAG = "ESP_NSD"
    }
}
