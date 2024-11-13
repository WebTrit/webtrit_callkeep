package com.webtrit.callkeep.common.helpers

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonBuilder

object JsonHelper {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
}
