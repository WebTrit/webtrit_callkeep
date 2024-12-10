package com.webtrit.callkeep.common

import android.app.Activity

interface ActivityProvider {
    fun getActivity(): Activity?
    fun addActivityChangeListener(listener: (Activity?) -> Unit)
    fun removeActivityChangeListener(listener: (Activity?) -> Unit)
}
