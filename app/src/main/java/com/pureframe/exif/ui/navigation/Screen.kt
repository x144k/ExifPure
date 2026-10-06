package com.pureframe.exif.ui.navigation

sealed class Screen(val route: String) {
    data object Gallery : Screen("gallery")
    data object Detail : Screen("detail/{photoId}") {
        fun createRoute(photoId: Long) = "detail/$photoId"
    }
    data object Viewer : Screen("viewer/{photoId}") {
        fun createRoute(photoId: Long) = "viewer/$photoId"
    }
    data object Settings : Screen("settings")
    data object History : Screen("history")
}
