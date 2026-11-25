package com.wolly.liquidtiktok

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle

data class LiquidConfig(
    val enabled: Boolean = true,
    val blurRadiusDp: Float = 14f,
    val barOpacity: Float = 0.24f,
    val refractionIntensity: Float = 1.0f,
    val chromaticAberration: Boolean = true,
    val mercuryStretch: Boolean = true,
    val useNativeIcons: Boolean = true,
    val useProfileAvatar: Boolean = true,
    val bottomOffsetDp: Float = 52f,
    val hapticFeedback: Boolean = true,
    val badgeMode: Int = BADGE_MODE_INTRO_DOT,
    val badgeIntroSeconds: Float = 3.0f,
    val liquidTopBar: Boolean = true,
    val liquidSeekBar: Boolean = true
) {
    fun toBundle(): Bundle = Bundle().apply {
        putBoolean(KEY_ENABLED, enabled)
        putFloat(KEY_BLUR_RADIUS, blurRadiusDp)
        putFloat(KEY_BAR_OPACITY, barOpacity)
        putFloat(KEY_REFRACTION_INTENSITY, refractionIntensity)
        putBoolean(KEY_CHROMATIC_ABERRATION, chromaticAberration)
        putBoolean(KEY_MERCURY_STRETCH, mercuryStretch)
        putBoolean(KEY_USE_NATIVE_ICONS, useNativeIcons)
        putBoolean(KEY_USE_PROFILE_AVATAR, useProfileAvatar)
        putFloat(KEY_BOTTOM_OFFSET, bottomOffsetDp)
        putBoolean(KEY_HAPTIC_FEEDBACK, hapticFeedback)
        putInt(KEY_BADGE_MODE, badgeMode)
        putFloat(KEY_BADGE_INTRO_SECONDS, badgeIntroSeconds)
        putBoolean(KEY_LIQUID_TOP_BAR, liquidTopBar)
        putBoolean(KEY_LIQUID_SEEK_BAR, liquidSeekBar)
    }

    companion object {
        const val PREFS_NAME = "liquid_prefs"
        const val AUTHORITY = "com.wolly.liquidtiktok.prefs"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/config")

        const val BADGE_MODE_INTRO_DOT = 0
        const val BADGE_MODE_ALWAYS_COUNTER = 1
        const val BADGE_MODE_ALWAYS_DOT = 2

        const val KEY_ENABLED = "enabled"
        const val KEY_BLUR_RADIUS = "blur_radius_dp"
        const val KEY_BAR_OPACITY = "bar_opacity"
        const val KEY_REFRACTION_INTENSITY = "refraction_intensity"
        const val KEY_CHROMATIC_ABERRATION = "chromatic_aberration"
        const val KEY_MERCURY_STRETCH = "mercury_stretch"
        const val KEY_USE_NATIVE_ICONS = "use_native_icons"
        const val KEY_USE_PROFILE_AVATAR = "use_profile_avatar"
        const val KEY_BOTTOM_OFFSET = "bottom_offset_dp"
        const val KEY_HAPTIC_FEEDBACK = "haptic_feedback"
        const val KEY_BADGE_MODE = "badge_mode"
        const val KEY_BADGE_INTRO_SECONDS = "badge_intro_seconds"
        const val KEY_LIQUID_TOP_BAR = "liquid_top_bar"
        const val KEY_LIQUID_SEEK_BAR = "liquid_seek_bar"

        fun fromBundle(b: Bundle?): LiquidConfig {
            if (b == null) return LiquidConfig()
            return LiquidConfig(
                enabled = b.getBoolean(KEY_ENABLED, true),
                blurRadiusDp = b.getFloat(KEY_BLUR_RADIUS, 14f).coerceIn(0f, 32f),
                barOpacity = b.getFloat(KEY_BAR_OPACITY, 0.24f).coerceIn(0.05f, 0.65f),
                refractionIntensity = b.getFloat(KEY_REFRACTION_INTENSITY, 1.0f).coerceIn(0f, 2f),
                chromaticAberration = b.getBoolean(KEY_CHROMATIC_ABERRATION, true),
                mercuryStretch = b.getBoolean(KEY_MERCURY_STRETCH, true),
                useNativeIcons = b.getBoolean(KEY_USE_NATIVE_ICONS, true),
                useProfileAvatar = b.getBoolean(KEY_USE_PROFILE_AVATAR, true),
                bottomOffsetDp = b.getFloat(KEY_BOTTOM_OFFSET, 52f).coerceIn(32f, 72f),
                hapticFeedback = b.getBoolean(KEY_HAPTIC_FEEDBACK, true),
                badgeMode = b.getInt(KEY_BADGE_MODE, BADGE_MODE_INTRO_DOT).coerceIn(0, 2),
                badgeIntroSeconds = b.getFloat(KEY_BADGE_INTRO_SECONDS, 3.0f).coerceIn(1.0f, 6.0f),
                liquidTopBar = b.getBoolean(KEY_LIQUID_TOP_BAR, true),
                liquidSeekBar = b.getBoolean(KEY_LIQUID_SEEK_BAR, true)
            )
        }

        @Suppress("DEPRECATION", "WorldReadableFiles")
        fun getPrefs(context: Context) = try {
            // LSPosed hooks MODE_WORLD_READABLE to make prefs readable across processes
            context.getSharedPreferences(PREFS_NAME, Context.MODE_WORLD_READABLE)
        } catch (_: SecurityException) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }

        fun load(context: Context): LiquidConfig {
            val prefs = getPrefs(context)
            return LiquidConfig(
                enabled = prefs.getBoolean(KEY_ENABLED, true),
                blurRadiusDp = prefs.getFloat(KEY_BLUR_RADIUS, 14f).coerceIn(0f, 32f),
                barOpacity = prefs.getFloat(KEY_BAR_OPACITY, 0.24f).coerceIn(0.05f, 0.65f),
                refractionIntensity = prefs.getFloat(KEY_REFRACTION_INTENSITY, 1.0f).coerceIn(0f, 2f),
                chromaticAberration = prefs.getBoolean(KEY_CHROMATIC_ABERRATION, true),
                mercuryStretch = prefs.getBoolean(KEY_MERCURY_STRETCH, true),
                useNativeIcons = prefs.getBoolean(KEY_USE_NATIVE_ICONS, true),
                useProfileAvatar = prefs.getBoolean(KEY_USE_PROFILE_AVATAR, true),
                bottomOffsetDp = prefs.getFloat(KEY_BOTTOM_OFFSET, 52f).coerceIn(32f, 72f),
                hapticFeedback = prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true),
                badgeMode = prefs.getInt(KEY_BADGE_MODE, BADGE_MODE_INTRO_DOT).coerceIn(0, 2),
                badgeIntroSeconds = prefs.getFloat(KEY_BADGE_INTRO_SECONDS, 3.0f).coerceIn(1.0f, 6.0f),
                liquidTopBar = prefs.getBoolean(KEY_LIQUID_TOP_BAR, true),
                liquidSeekBar = prefs.getBoolean(KEY_LIQUID_SEEK_BAR, true)
            )
        }

        fun save(context: Context, config: LiquidConfig) {
            getPrefs(context).edit()
                .putBoolean(KEY_ENABLED, config.enabled)
                .putFloat(KEY_BLUR_RADIUS, config.blurRadiusDp)
                .putFloat(KEY_BAR_OPACITY, config.barOpacity)
                .putFloat(KEY_REFRACTION_INTENSITY, config.refractionIntensity)
                .putBoolean(KEY_CHROMATIC_ABERRATION, config.chromaticAberration)
                .putBoolean(KEY_MERCURY_STRETCH, config.mercuryStretch)
                .putBoolean(KEY_USE_NATIVE_ICONS, config.useNativeIcons)
                .putBoolean(KEY_USE_PROFILE_AVATAR, config.useProfileAvatar)
                .putFloat(KEY_BOTTOM_OFFSET, config.bottomOffsetDp)
                .putBoolean(KEY_HAPTIC_FEEDBACK, config.hapticFeedback)
                .putInt(KEY_BADGE_MODE, config.badgeMode)
                .putFloat(KEY_BADGE_INTRO_SECONDS, config.badgeIntroSeconds)
                .putBoolean(KEY_LIQUID_TOP_BAR, config.liquidTopBar)
                .putBoolean(KEY_LIQUID_SEEK_BAR, config.liquidSeekBar)
                .apply()
            try {
                context.contentResolver.notifyChange(CONTENT_URI, null)
            } catch (_: Throwable) {}
        }

        fun queryRemote(resolver: ContentResolver): LiquidConfig? {
            return try {
                val bundle = resolver.call(CONTENT_URI, "get_config", null, null)
                if (bundle != null) fromBundle(bundle) else null
            } catch (_: Throwable) {
                null
            }
        }
    }
}

class PrefsProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val ctx = context ?: return null
        return when (method) {
            "get_config" -> LiquidConfig.load(ctx).toBundle()
            else -> null
        }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val ctx = context ?: return null
        val cfg = LiquidConfig.load(ctx)
        val cursor = MatrixCursor(
            arrayOf(
                LiquidConfig.KEY_ENABLED,
                LiquidConfig.KEY_BLUR_RADIUS,
                LiquidConfig.KEY_BAR_OPACITY,
                LiquidConfig.KEY_REFRACTION_INTENSITY,
                LiquidConfig.KEY_CHROMATIC_ABERRATION,
                LiquidConfig.KEY_MERCURY_STRETCH,
                LiquidConfig.KEY_USE_NATIVE_ICONS,
                LiquidConfig.KEY_BOTTOM_OFFSET
            )
        )
        cursor.addRow(
            arrayOf<Any>(
                if (cfg.enabled) 1 else 0,
                cfg.blurRadiusDp,
                cfg.barOpacity,
                cfg.refractionIntensity,
                if (cfg.chromaticAberration) 1 else 0,
                if (cfg.mercuryStretch) 1 else 0,
                if (cfg.useNativeIcons) 1 else 0,
                cfg.bottomOffsetDp
            )
        )
        return cursor
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.${LiquidConfig.AUTHORITY}.config"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
