package com.webtrit.callkeep.api

import android.app.Activity
import android.content.Context

import com.webtrit.callkeep.PDelegateBackgroundServiceFlutterApi
import com.webtrit.callkeep.PDelegateFlutterApi
import com.webtrit.callkeep.api.background.BackgroundCallkeepApi
import com.webtrit.callkeep.api.background.ProxyBackgroundCallkeepApi
import com.webtrit.callkeep.api.background.TelephonyBackgroundCallkeepApi
import com.webtrit.callkeep.api.foreground.ForegroundCallkeepApi
import com.webtrit.callkeep.api.foreground.ProxyForegroundCallkeepApi
import com.webtrit.callkeep.api.foreground.TelephonyForegroundCallkeepApi
import com.webtrit.callkeep.common.helpers.TelephonyHelper

object CallkeepApiProvider {
    /**
     * Manages the creation of Callkeep APIs for both background and foreground.
     */

    /**
     * Gets the appropriate background Callkeep API based on telephony availability.
     *
     * @param activity The current Android activity.
     * @param api The Flutter API instance for Android service.
     * @return The background Callkeep API instance.
     */
    fun getBackgroundCallkeepApi(
        activity: Context,
        api: PDelegateBackgroundServiceFlutterApi
    ): BackgroundCallkeepApi {
        return if (TelephonyHelper(activity).isAvailableTelephony()) {
            TelephonyBackgroundCallkeepApi(activity, api)
        } else {
            ProxyBackgroundCallkeepApi(activity, api)
        }
    }

    /**
     * Gets the appropriate foreground Callkeep API based on telephony availability.
     *
     * @param activity The current Android activity.
     * @param api The Flutter API instance for foreground.
     * @return The foreground Callkeep API instance.
     */
    fun getForegroundCallkeepApi(
        activity: Context,
        api: PDelegateFlutterApi
    ): ForegroundCallkeepApi {
        return if (TelephonyHelper(activity).isAvailableTelephony()) {
            TelephonyForegroundCallkeepApi(activity, api)
        } else {
            ProxyForegroundCallkeepApi(activity, api)
        }
    }
}
