package com.ss.assistent.memory

/**
 * Local, deterministic safety classification for long-term memory candidates.
 *
 * This is deliberately conservative. The classifier does not inspect the model's intent and
 * does not write anything. Callers can use it to require an additional user decision before
 * persisting sensitive material.
 */
enum class MemorySensitivity {
    NORMAL,
    PERSONAL,
    CREDENTIAL,
    FINANCIAL,
    HEALTH,
    HIGH_RISK
}

data class MemorySensitivityResult(
    val level: MemorySensitivity,
    val reason: String
) {
    val requiresExplicitChoice: Boolean
        get() = level != MemorySensitivity.NORMAL

    val shouldBlockPersistence: Boolean
        get() = level == MemorySensitivity.CREDENTIAL ||
            level == MemorySensitivity.FINANCIAL ||
            level == MemorySensitivity.HIGH_RISK
}

object MemorySensitivityClassifier {
    private val credentialPatterns = listOf(
        Regex("\\bpassword\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpassphrase\\b", RegexOption.IGNORE_CASE),
        Regex("\\bapi[_ -]?key\\b", RegexOption.IGNORE_CASE),
        Regex("\\baccess[_ -]?token\\b", RegexOption.IGNORE_CASE),
        Regex("\\brefresh[_ -]?token\\b", RegexOption.IGNORE_CASE),
        Regex("\\bprivate[_ -]?key\\b", RegexOption.IGNORE_CASE),
        Regex("\\brecovery\\s+code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bbackup\\s+code\\b", RegexOption.IGNORE_CASE)
    )

    private val financialPatterns = listOf(
        Regex("\\bcredit\\s*card\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdebit\\s*card\\b", RegexOption.IGNORE_CASE),
        Regex("\\bbank\\s+account\\b", RegexOption.IGNORE_CASE),
        Regex("\\biban\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpin\\b", RegexOption.IGNORE_CASE),
        Regex("\\bsecurity\\s+code\\b", RegexOption.IGNORE_CASE),
        Regex("\\bcvv\\b", RegexOption.IGNORE_CASE)
    )

    private val healthPatterns = listOf(
        Regex("\\bdiagnos(?:is|ed)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmedication\\b", RegexOption.IGNORE_CASE),
        Regex("\\bprescription\\b", RegexOption.IGNORE_CASE),
        Regex("\\bmedical\\s+record\\b", RegexOption.IGNORE_CASE),
        Regex("\\btherapy\\b", RegexOption.IGNORE_CASE)
    )

    private val highRiskPatterns = listOf(
        Regex("\\bsocial\\s+security\\s+(?:number|no\\.?|#)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bpassport\\s+(?:number|no\\.?|#)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bidentity\\s+(?:number|no\\.?|#)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bgovernment\\s+id\\b", RegexOption.IGNORE_CASE)
    )

    private val personalPatterns = listOf(
        Regex("\\bhome\\s+address\\b", RegexOption.IGNORE_CASE),
        Regex("\\bphone\\s+number\\b", RegexOption.IGNORE_CASE),
        Regex("\\bemail\\s+address\\b", RegexOption.IGNORE_CASE),
        Regex("\\bbirthday\\b", RegexOption.IGNORE_CASE),
        Regex("\\bdate\\s+of\\s+birth\\b", RegexOption.IGNORE_CASE)
    )

    fun classify(text: String): MemorySensitivityResult {
        if (text.isEmpty()) {
            return MemorySensitivityResult(MemorySensitivity.NORMAL, "Empty candidate.")
        }

        when {
            highRiskPatterns.any { it.containsMatchIn(text) } ->
                return MemorySensitivityResult(
                    MemorySensitivity.HIGH_RISK,
                    "Government or identity-document information should not be kept in long-term assistant memory."
                )
            credentialPatterns.any { it.containsMatchIn(text) } ->
                return MemorySensitivityResult(
                    MemorySensitivity.CREDENTIAL,
                    "Authentication secrets should not be persisted as assistant memory."
                )
            financialPatterns.any { it.containsMatchIn(text) } ->
                return MemorySensitivityResult(
                    MemorySensitivity.FINANCIAL,
                    "Financial or payment information should not be persisted as assistant memory."
                )
            healthPatterns.any { it.containsMatchIn(text) } ->
                return MemorySensitivityResult(
                    MemorySensitivity.HEALTH,
                    "Health information is sensitive and requires an explicit user choice before persistence."
                )
            personalPatterns.any { it.containsMatchIn(text) } ->
                return MemorySensitivityResult(
                    MemorySensitivity.PERSONAL,
                    "Personal identifying information requires an explicit user choice before persistence."
                )
            else ->
                return MemorySensitivityResult(
                    MemorySensitivity.NORMAL,
                    "No high-confidence sensitive pattern was detected."
                )
        }
    }
}
