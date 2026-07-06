package com.v2ray.ang.handler

import com.v2ray.ang.AppConfig
import com.v2ray.ang.dto.IPAPIInfo
import com.v2ray.ang.dto.UrlContentRequest
import com.v2ray.ang.util.HttpUtil
import com.v2ray.ang.util.JsonUtil
import com.v2ray.ang.util.LogUtil
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException

object SpeedtestManager {

    private val tcpTestingSockets = ArrayList<Socket?>()

    /**
     * Country code to Chinese name mapping.
     */
    val countryCodeToChinese: Map<String, String> = mapOf(
        "US" to "美国",
        "JP" to "日本",
        "KR" to "韩国",
        "HK" to "香港",
        "TW" to "台湾",
        "SG" to "新加坡",
        "GB" to "英国",
        "DE" to "德国",
        "FR" to "法国",
        "NL" to "荷兰",
        "CA" to "加拿大",
        "AU" to "澳大利亚",
        "RU" to "俄罗斯",
        "IN" to "印度",
        "BR" to "巴西",
        "IT" to "意大利",
        "ES" to "西班牙",
        "SE" to "瑞典",
        "NO" to "挪威",
        "FI" to "芬兰",
        "DK" to "丹麦",
        "PL" to "波兰",
        "CZ" to "捷克",
        "AT" to "奥地利",
        "CH" to "瑞士",
        "BE" to "比利时",
        "IE" to "爱尔兰",
        "NZ" to "新西兰",
        "ZA" to "南非",
        "AE" to "阿联酋",
        "TR" to "土耳其",
        "TH" to "泰国",
        "VN" to "越南",
        "MY" to "马来西亚",
        "PH" to "菲律宾",
        "ID" to "印度尼西亚",
        "MO" to "澳门",
        "AR" to "阿根廷",
        "MX" to "墨西哥",
        "IL" to "以色列",
        "UA" to "乌克兰",
        "RO" to "罗马尼亚",
        "PT" to "葡萄牙",
        "GR" to "希腊",
        "HU" to "匈牙利",
        "EG" to "埃及",
        "SA" to "沙特阿拉伯",
        "CL" to "智利",
        "CO" to "哥伦比亚",
        "PE" to "秘鲁",
        "KE" to "肯尼亚",
        "NG" to "尼日利亚",
        "IS" to "冰岛",
        "LU" to "卢森堡",
        "MT" to "马耳他",
        "CY" to "塞浦路斯"
    )

    /**
     * Converts an English country code/name to Chinese.
     */
    fun toChineseLocation(countryCodeOrName: String?): String? {
        if (countryCodeOrName.isNullOrBlank()) return null
        return countryCodeToChinese[countryCodeOrName.uppercase()]
            ?: countryCodeToChinese.entries.firstOrNull { it.value == countryCodeOrName }?.value
            ?: countryCodeOrName
    }

    /**
     * Measures the TCP connection time to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    suspend fun tcping(url: String, port: Int): Long {
        var time = -1L
        for (k in 0 until 2) {
            val one = socketConnectTime(url, port)
            if (!currentCoroutineContext().isActive) {
                break
            }
            if (one != -1L && (time == -1L || one < time)) {
                time = one
            }
        }
        return time
    }

    /**
     * Measures the time taken to establish a TCP connection to a given URL and port.
     *
     * @param url The URL to connect to.
     * @param port The port to connect to.
     * @return The connection time in milliseconds, or -1 if the connection failed.
     */
    fun socketConnectTime(url: String, port: Int): Long {
        try {
            val socket = Socket()
            synchronized(this) {
                tcpTestingSockets.add(socket)
            }
            val start = System.currentTimeMillis()
            socket.connect(InetSocketAddress(url, port), 3000)
            val time = System.currentTimeMillis() - start
            synchronized(this) {
                tcpTestingSockets.remove(socket)
            }
            socket.close()
            return time
        } catch (e: UnknownHostException) {
            LogUtil.e(AppConfig.TAG, "Unknown host: $url", e)
        } catch (e: IOException) {
            LogUtil.e(AppConfig.TAG, "socketConnectTime IOException: $e")
        } catch (e: Exception) {
            LogUtil.e(AppConfig.TAG, "Failed to establish socket connection to $url:$port", e)
        }
        return -1
    }

    /**
     * Closes all TCP sockets that are currently being tested.
     */
    fun closeAllTcpSockets() {
        synchronized(this) {
            tcpTestingSockets.forEach {
                it?.close()
            }
            tcpTestingSockets.clear()
        }
    }

    /**
     * Parses IP API response and returns location string (country name in Chinese).
     */
    private fun parseIpLocation(content: String): String? {
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        return toChineseLocation(country) ?: country
    }

    /**
     * Fetches the geolocation of a server address via IP API.
     * Uses ip-api.com which accepts both IPs and domain names,
     * avoiding the need for local DNS resolution.
     */
    fun fetchIpLocation(serverAddress: String): String? {
        // Use ip-api.com directly — accepts both IPs and domains,
        // works in China, no DNS resolution needed on client side
        val queryUrl = "http://ip-api.com/json/${serverAddress}?fields=countryCode"

        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = queryUrl,
                timeout = 5000
            )
        ) ?: return null

        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null
        return toChineseLocation(ipInfo.countryCode) ?: ipInfo.countryCode
    }

    /**
     * Fetches the current connection's IP info via the running proxy.
     * Returns a formatted string like "(美国) 1.2.3.4".
     */
    fun getRemoteIPInfo(): String? {
        val url = MmkvManager.decodeSettingsString(AppConfig.PREF_IP_API_URL)
            .takeIf { !it.isNullOrBlank() } ?: AppConfig.IP_API_URL

        val proxyUsername = SettingsManager.getSocksUsername()
        val proxyPassword = SettingsManager.getSocksPassword()
        val httpPort = SettingsManager.getHttpPort()
        if (httpPort == 0) return null
        val content = HttpUtil.getUrlContent(
            UrlContentRequest(
                url = url,
                timeout = 5000,
                httpPort = httpPort,
                proxyUsername = proxyUsername,
                proxyPassword = proxyPassword
            )
        ) ?: return null
        val ipInfo = JsonUtil.fromJsonSafe(content, IPAPIInfo::class.java) ?: return null

        val ip = listOf(
            ipInfo.ip,
            ipInfo.clientIp,
            ipInfo.ip_addr,
            ipInfo.query
        ).firstOrNull { !it.isNullOrBlank() }

        val country = listOf(
            ipInfo.country_code,
            ipInfo.country,
            ipInfo.countryCode,
            ipInfo.location?.country_code
        ).firstOrNull { !it.isNullOrBlank() }

        val cnCountry = toChineseLocation(country)

        return "(${cnCountry ?: "unknown"}) ${ip ?: "unknown"}"
    }
}
