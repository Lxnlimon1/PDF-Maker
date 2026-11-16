package com.example.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object HomeRoute

@Serializable
object ScannerRoute

@Serializable
object ScanReviewRoute

@Serializable
data class EditorRoute(val docId: String? = null)

@Serializable
object ToolsRoute

@Serializable
data class ToolRunnerRoute(val toolId: String, val preselectedDocId: String? = null)
