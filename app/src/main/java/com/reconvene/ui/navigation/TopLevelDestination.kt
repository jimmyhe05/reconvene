package com.reconvene.ui.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.reconvene.R

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val label: Int,
    @param:DrawableRes val icon: Int,
) {
    DISCOVER(DiscoverRoute, R.string.nav_discover, R.drawable.ic_compass),
    GROUPS(GroupsRoute, R.string.nav_groups, R.drawable.ic_users),
    PROFILE(ProfileRoute, R.string.nav_profile, R.drawable.ic_user),
}
