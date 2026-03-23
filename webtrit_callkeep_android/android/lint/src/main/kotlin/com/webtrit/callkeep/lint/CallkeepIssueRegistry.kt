package com.webtrit.callkeep.lint

import com.android.tools.lint.client.api.IssueRegistry
import com.android.tools.lint.client.api.Vendor
import com.android.tools.lint.detector.api.CURRENT_API

class CallkeepIssueRegistry : IssueRegistry() {
    override val issues = listOf(PhoneConnectionServiceDetector.ISSUE)

    override val api: Int = CURRENT_API

    override val minApi: Int = 12

    override val vendor: Vendor =
        Vendor(
            vendorName = "WebTrit",
            identifier = "com.webtrit.callkeep",
            feedbackUrl = "https://github.com/WebTrit/webtrit_callkeep/issues",
        )
}
