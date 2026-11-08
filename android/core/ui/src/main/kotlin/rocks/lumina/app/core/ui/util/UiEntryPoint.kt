package rocks.lumina.app.core.ui.util

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import rocks.lumina.app.core.data.ProfileRepository
import rocks.lumina.app.core.data.SettingsRepository
import rocks.lumina.app.core.media.BlossomClient

/** Lets leaf composables (avatars, names, images) reach singletons without threading them through every screen. */
@EntryPoint
@InstallIn(SingletonComponent::class)
public interface UiEntryPoint {
    public fun profiles(): ProfileRepository
    public fun blossom(): BlossomClient
    public fun settings(): SettingsRepository
}

public fun Context.uiEntryPoint(): UiEntryPoint = EntryPointAccessors.fromApplication(applicationContext, UiEntryPoint::class.java)
