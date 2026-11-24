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
data class PodDetailRoute(val podId: String)

@Serializable
data class GroupRoute(val groupId: String)
