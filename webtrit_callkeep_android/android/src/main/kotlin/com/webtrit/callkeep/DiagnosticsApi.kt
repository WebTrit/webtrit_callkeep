package com.webtrit.callkeep

import android.content.Context
import com.webtrit.callkeep.common.CallDiagnostics

class DiagnosticsApi(
    private val context: Context,
) : PHostDiagnosticsApi {

    override fun getDiagnosticReport(callback: (Result<Map<String, Any?>>) -> Unit) {
        try {
            val reportMap = CallDiagnostics.gatherMap(context)
            callback.invoke(Result.success(reportMap))
        } catch (e: Exception) {
            callback.invoke(Result.failure(e))
        }
    }
}
