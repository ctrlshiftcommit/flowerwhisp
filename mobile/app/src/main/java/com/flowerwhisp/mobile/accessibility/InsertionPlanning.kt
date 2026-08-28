package com.flowerwhisp.mobile.accessibility

data class InsertionPlan(
    val replacementText: String,
    val cursor: Int,
)

sealed interface InsertionPlanResult {
    data class Planned(val plan: InsertionPlan) : InsertionPlanResult
    data class Rejected(val reason: String) : InsertionPlanResult
}

object InsertionPlanner {
    fun plan(
        existingText: String,
        selectionStart: Int,
        selectionEnd: Int,
        dictatedText: String,
    ): InsertionPlanResult {
        if (dictatedText.isBlank()) {
            return InsertionPlanResult.Rejected("Blank dictation was not inserted")
        }

        val start = selectionStart.takeIf { it >= 0 }
            ?.coerceIn(0, existingText.length)
            ?: existingText.length
        val end = selectionEnd.takeIf { it >= 0 }
            ?.coerceIn(0, existingText.length)
            ?.coerceAtLeast(start)
            ?: start
        val before = existingText.substring(0, start)
        val after = existingText.substring(end)
        val prefix = when {
            before.isEmpty() || before.last().isWhitespace() || dictatedText.first().isWhitespace() -> ""
            else -> " "
        }
        val suffix = when {
            after.isEmpty() || after.first().isWhitespace() || dictatedText.last().isWhitespace() -> ""
            else -> " "
        }
        val insertion = prefix + dictatedText + suffix
        return InsertionPlanResult.Planned(
            InsertionPlan(
                replacementText = before + insertion + after,
                cursor = before.length + insertion.length,
            ),
        )
    }
}
