package com.suseoaa.locationspoofer.data.model

data class RootSetupTestResult(
    val hasRoot: Boolean,
    val idOutput: String,
    val solution: RootSolution,
    val toolUsed: String?,
    val typeRuleOk: Boolean,
    val allowRuleResults: List<Pair<String, Boolean>>,
    val labelCheckRaw: String?,
    val labelVerified: Boolean,
    val configFileChconResults: List<Pair<String, Boolean>>,
    val rawScriptOutput: String,
    val overallVerified: Boolean
)
