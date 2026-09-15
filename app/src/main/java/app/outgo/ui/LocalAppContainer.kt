package app.outgo.ui

import androidx.compose.runtime.staticCompositionLocalOf
import app.outgo.di.AppContainer

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("LocalAppContainer not provided — wrap the app in CompositionLocalProvider from MainActivity")
}
