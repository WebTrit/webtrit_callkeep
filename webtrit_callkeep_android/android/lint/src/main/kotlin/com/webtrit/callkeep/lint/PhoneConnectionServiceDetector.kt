package com.webtrit.callkeep.lint

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import com.android.tools.lint.detector.api.SourceCodeScanner
import org.jetbrains.uast.UCallExpression

/**
 * Lint rule that prevents direct calls to [PhoneConnectionService] static methods
 * from the main process outside of the allowed packages.
 *
 * All main-process interactions with PhoneConnectionService must go through
 * [CallkeepCore.instance] so that [MainProcessConnectionTracker] shadow state
 * (addPending, promote, markAnswered, etc.) is always kept in sync with the
 * :callkeep_core process.
 *
 * ## Allowed callers
 * - `services/connection/` — PhoneConnectionService itself and its internal classes
 * - `services/core/`       — InProcessCallkeepCore (the only legitimate dispatch point)
 *
 * ## All other callers produce a Lint ERROR.
 */
class PhoneConnectionServiceDetector :
    Detector(),
    SourceCodeScanner {
    override fun getApplicableUastTypes() = listOf(UCallExpression::class.java)

    override fun createUastHandler(context: JavaContext) =
        object : UElementHandler() {
            override fun visitCallExpression(node: UCallExpression) {
                val method = node.resolve() ?: return
                val containingClass = method.containingClass?.qualifiedName ?: return

                val isPhoneConnectionService =
                    containingClass == PHONE_CONNECTION_SERVICE_FQN ||
                        containingClass == "$PHONE_CONNECTION_SERVICE_FQN.Companion"

                if (!isPhoneConnectionService) return

                val filePath = context.file.path
                if (ALLOWED_PATH_SEGMENTS.any { filePath.contains(it) }) return

                context.report(
                    ISSUE,
                    node,
                    context.getNameLocation(node),
                    "Direct call to `PhoneConnectionService` from the main process. " +
                        "Use `CallkeepCore.instance` instead to keep `MainProcessConnectionTracker` in sync.",
                )
            }
        }

    companion object {
        private const val PHONE_CONNECTION_SERVICE_FQN =
            "com.webtrit.callkeep.services.services.connection.PhoneConnectionService"

        private val ALLOWED_PATH_SEGMENTS =
            listOf(
                "/services/connection/",
                "/services/core/",
            )

        @JvmField
        val ISSUE: Issue =
            Issue.create(
                id = "PhoneConnectionServiceDirectCall",
                briefDescription = "Direct `PhoneConnectionService` call bypasses `CallkeepCore`",
                explanation = """
                All main-process code must interact with `PhoneConnectionService` through \
                `CallkeepCore.instance`. Direct calls bypass `MainProcessConnectionTracker` \
                bookkeeping (`addPending`, `promote`, etc.) and break the dual-process \
                architecture contract, potentially causing call-state inconsistencies.

                Replace the direct call with the equivalent `CallkeepCore.instance.<method>()`.
            """,
                category = Category.CORRECTNESS,
                priority = 9,
                severity = Severity.ERROR,
                implementation =
                    Implementation(
                        PhoneConnectionServiceDetector::class.java,
                        Scope.JAVA_FILE_SCOPE,
                    ),
            )
    }
}
