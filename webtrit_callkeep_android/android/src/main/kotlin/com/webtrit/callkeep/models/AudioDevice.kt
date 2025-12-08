package com.webtrit.callkeep.models

import android.os.Bundle

enum class AudioDeviceType {
    EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET, STREAMING, UNKNOWN;
}

class AudioDevice(
    val type: AudioDeviceType, val name: String? = null, val id: String? = null
) {
    fun toBundle(): Bundle {
        return Bundle().apply {
            putString("type", type.name)
            name?.let { putString("name", it) }
            id?.let { putString("id", it) }
        }
    }

    companion object {
        fun fromBundle(bundle: Bundle?): AudioDevice? {
            return bundle?.let {
                val type = AudioDeviceType.valueOf(it.getString("type")!!)
                val name = it.getString("name")
                val id = it.getString("id")
                AudioDevice(type, name, id)
            }
        }
    }

    override fun toString(): String {
        return "AudioDevice(type=$type, name=$name, id=$id)"
    }
}