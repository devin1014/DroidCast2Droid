package com.devin.android.cast.core

object Constants {
    const val DEFAULT_SERVER = "ws://192.168.5.126:8080/stream"
    const val IP_ADDRESS_5_126 = "192.168.5.126" // device-1: Redmi
    const val IP_ADDRESS_5_147 = "192.168.5.147" // device-2: Samsung galaxy tab s3
    const val IP_ADDRESS_5_151 = "192.168.5.151" // device-3: Samsung note 3
    const val URL_HOLDER = "ws://@ip:8080/stream"

    var serverUrls: List<String> = listOf(IP_ADDRESS_5_126, IP_ADDRESS_5_147, IP_ADDRESS_5_151)
}