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
// justCreated is true right after the user made this pod, to confirm it.
data class PodDetailRoute(val podId: String, val walkMinutes: Int? = null, val justCreated: Boolean = false)

@Serializable
data class GroupRoute(val groupId: String)

@Serializable
data object BlockedPeopleRoute

@Serializable
data object MeetingSafelyRoute
