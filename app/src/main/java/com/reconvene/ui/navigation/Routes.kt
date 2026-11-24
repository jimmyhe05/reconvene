package com.reconvene.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
data object DiscoverRoute

@Serializable
data object GroupsRoute

@Serializable
data object ProfileRoute

@Serializable
data object CreatePodRoute

@Serializable
// walkMinutes comes from Discover's Nearest first. Only the minutes travel, never the user's position.
data class PodDetailRoute(val podId: String, val walkMinutes: Int? = null)

@Serializable
data class GroupRoute(val groupId: String)
