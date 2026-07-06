package com.v2ray.ang.dto.entities

data class ServerAffiliationInfo(
    var testDelayMillis: Long = 0L,
    var testLocation: String? = null
) {
    fun getTestDelayString(): String {
        if (testDelayMillis == 0L) {
            return ""
        }
        val locationPart = if (!testLocation.isNullOrBlank()) " ${testLocation}" else ""
        return testDelayMillis.toString() + "ms" + locationPart
    }
}