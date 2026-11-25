package com.wolly.liquidtiktok

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.roundToInt

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LiquidSettingsScreen()
        }
    }
}

@Composable
private fun LiquidSettingsScreen() {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    var config by remember { mutableStateOf(LiquidConfig.load(context)) }
    var previewTab by remember { mutableIntStateOf(0) }
    var scrollEnabled by remember { mutableStateOf(true) }

    fun updateConfig(transform: (LiquidConfig) -> LiquidConfig) {
        val next = transform(config)
        config = next
        LiquidConfig.save(context, next)
    }

    val textColor = if (dark) Color.White else Color(0xFF141414)
    val subTextColor = if (dark) Color.White.copy(alpha = 0.70f) else Color(0xFF141414).copy(alpha = 0.65f)
    val cardSurface = if (dark) Color(0xFF141414).copy(alpha = 0.36f) else Color.White.copy(alpha = 0.42f)

    val backgroundBackdrop = rememberLayerBackdrop()

    Box(Modifier.fillMaxSize()) {
        // Rich colorful mesh-like backdrop for AndroidLiquidGlass components & live bar preview
        Box(
            Modifier
                .fillMaxSize()
                .layerBackdrop(backgroundBackdrop)
                .background(
                    Brush.linearGradient(
                        colors = if (dark) {
                            listOf(
                                Color(0xFF0E1324),
                                Color(0xFF28123B),
                                Color(0xFF123047),
                                Color(0xFF0B1018)
                            )
                        } else {
                            listOf(
                                Color(0xFFDCEBFF),
                                Color(0xFFF7D8EE),
                                Color(0xFFD5F4F0),
                                Color(0xFFEDF2FA)
                            )
                        },
                        start = Offset.Zero,
                        end = Offset(1100f, 2200f)
                    )
                )
        ) {
            // Soft ambient color orbs behind glass cards
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 24.dp)
                    .clip(RoundedCornerShape(100.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFFE2C55).copy(alpha = if (dark) 0.45f else 0.35f),
                                Color.Transparent
                            )
                        )
                    )
                    .height(220.dp)
                    .fillMaxWidth(0.65f)
            )
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .clip(RoundedCornerShape(120.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFF25F4EE).copy(alpha = if (dark) 0.35f else 0.30f),
                                Color.Transparent
                            )
                        )
                    )
                    .height(260.dp)
                    .fillMaxWidth(0.75f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState(), enabled = scrollEnabled)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
                BasicText(
                    text = "LiquidTikTok",
                    style = TextStyle(
                        color = textColor,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    text = "Xposed Liquid Glass Nav Bar Settings",
                    style = TextStyle(
                        color = subTextColor,
                        fontSize = 14.sp
                    )
                )
            }

            // Live Interactive Preview Card with sample image
            GlassCard(
                backdrop = backgroundBackdrop,
                surfaceColor = cardSurface
            ) {
                BasicText(
                    text = "Live Preview",
                    style = TextStyle(
                        color = subTextColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                val previewBackdrop = rememberLayerBackdrop()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .lockParentScroll { scrollEnabled = !it }
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.sample_preview),
                        contentDescription = "Sample Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .layerBackdrop(previewBackdrop)
                    )
                    LiquidTikTokBar(
                        externalBackdrop = previewBackdrop,
                        selectedTabIndex = { previewTab },
                        onTabSelected = { previewTab = it },
                        isDarkTheme = { dark },
                        config = { config },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    )
                }
            }

            // Core Toggles Card
            GlassCard(
                backdrop = backgroundBackdrop,
                surfaceColor = cardSurface
            ) {
                SectionTitle("General", textColor)
                ToggleRow(
                    title = "Enable Liquid Nav Bar",
                    subtitle = "Replaces TikTok's bottom bar (restart TikTok if toggled off)",
                    checked = { config.enabled },
                    onCheckedChange = { v -> updateConfig { it.copy(enabled = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Authentic TikTok Icons",
                    subtitle = "Extract native icons from TikTok instead of fallback vectors",
                    checked = { config.useNativeIcons },
                    onCheckedChange = { v -> updateConfig { it.copy(useNativeIcons = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Profile Picture Icon",
                    subtitle = "Replace the generic Profile icon with your own profile picture",
                    checked = { config.useProfileAvatar },
                    onCheckedChange = { v -> updateConfig { it.copy(useProfileAvatar = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Chromatic Aberration",
                    subtitle = "Rainbow dispersion along the liquid blob edge while dragging",
                    checked = { config.chromaticAberration },
                    onCheckedChange = { v -> updateConfig { it.copy(chromaticAberration = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Mercury Stretch",
                    subtitle = "Stretch the liquid blob horizontally during drag & transitions",
                    checked = { config.mercuryStretch },
                    onCheckedChange = { v -> updateConfig { it.copy(mercuryStretch = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Liquid Top Bar",
                    subtitle = "Glass capsule behind Following / For You & Search",
                    checked = { config.liquidTopBar },
                    onCheckedChange = { v -> updateConfig { it.copy(liquidTopBar = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Glass Seek Bar",
                    subtitle = "Style the video scrubber with a frosted glass pill",
                    checked = { config.liquidSeekBar },
                    onCheckedChange = { v -> updateConfig { it.copy(liquidSeekBar = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
                ToggleRow(
                    title = "Haptic Feedback",
                    subtitle = "Subtle vibration ticks when sliding or tapping tabs",
                    checked = { config.hapticFeedback },
                    onCheckedChange = { v -> updateConfig { it.copy(hapticFeedback = v) } },
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor
                )
            }

            // Notifications Badge Card
            GlassCard(
                backdrop = backgroundBackdrop,
                surfaceColor = cardSurface
            ) {
                SectionTitle("Notifications Badge", textColor)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val modes = listOf(
                        LiquidConfig.BADGE_MODE_INTRO_DOT to "Intro → Dot",
                        LiquidConfig.BADGE_MODE_ALWAYS_COUNTER to "Counter",
                        LiquidConfig.BADGE_MODE_ALWAYS_DOT to "Dot"
                    )
                    modes.forEach { (modeId, label) ->
                        val isSelected = config.badgeMode == modeId
                        LiquidButton(
                            onClick = { updateConfig { it.copy(badgeMode = modeId) } },
                            backdrop = backgroundBackdrop,
                            tint = if (isSelected) Color(0xFFFE2C55) else Color.Transparent,
                            modifier = Modifier.weight(1f)
                        ) {
                            BasicText(
                                text = label,
                                style = TextStyle(
                                    color = if (isSelected) Color.White else textColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }
                if (config.badgeMode == LiquidConfig.BADGE_MODE_INTRO_DOT) {
                    SliderRow(
                        title = "Counter Intro Duration",
                        valueText = "${String.format("%.1f", config.badgeIntroSeconds)} s",
                        value = { config.badgeIntroSeconds },
                        onValueChange = { v -> updateConfig { it.copy(badgeIntroSeconds = v) } },
                        valueRange = 1.0f..6.0f,
                        visibilityThreshold = 0.1f,
                        backdrop = backgroundBackdrop,
                        textColor = textColor,
                        subTextColor = subTextColor,
                        onInteractingChange = { scrollEnabled = !it }
                    )
                }
            }

            // Sliders Card
            GlassCard(
                backdrop = backgroundBackdrop,
                surfaceColor = cardSurface
            ) {
                SectionTitle("Glass & Layout", textColor)
                SliderRow(
                    title = "Blur Radius",
                    valueText = "${config.blurRadiusDp.roundToInt()} dp",
                    value = { config.blurRadiusDp },
                    onValueChange = { v -> updateConfig { it.copy(blurRadiusDp = v) } },
                    valueRange = 0f..32f,
                    visibilityThreshold = 0.25f,
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    onInteractingChange = { scrollEnabled = !it }
                )
                SliderRow(
                    title = "Bar Tint Opacity",
                    valueText = "${(config.barOpacity * 100).roundToInt()}%",
                    value = { config.barOpacity },
                    onValueChange = { v -> updateConfig { it.copy(barOpacity = v) } },
                    valueRange = 0.05f..0.65f,
                    visibilityThreshold = 0.01f,
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    onInteractingChange = { scrollEnabled = !it }
                )
                SliderRow(
                    title = "Refraction Intensity",
                    valueText = "${(config.refractionIntensity * 100).roundToInt()}%",
                    value = { config.refractionIntensity },
                    onValueChange = { v -> updateConfig { it.copy(refractionIntensity = v) } },
                    valueRange = 0f..2f,
                    visibilityThreshold = 0.02f,
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    onInteractingChange = { scrollEnabled = !it }
                )
                SliderRow(
                    title = "Feed Bottom Offset",
                    valueText = "${config.bottomOffsetDp.roundToInt()} dp",
                    value = { config.bottomOffsetDp },
                    onValueChange = { v -> updateConfig { it.copy(bottomOffsetDp = v) } },
                    valueRange = 32f..72f,
                    visibilityThreshold = 0.5f,
                    backdrop = backgroundBackdrop,
                    textColor = textColor,
                    subTextColor = subTextColor,
                    onInteractingChange = { scrollEnabled = !it }
                )
            }

            // Reset Button using AndroidLiquidGlass LiquidButton
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                LiquidButton(
                    onClick = { updateConfig { LiquidConfig() } },
                    backdrop = backgroundBackdrop,
                    tint = Color(0xFFFE2C55),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicText(
                        text = "Reset to Defaults",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GlassCard(
    backdrop: Backdrop,
    surfaceColor: Color,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBackdrop(
                backdrop = backdrop,
                shape = { shape },
                effects = {
                    vibrancy()
                    colorControls(saturation = 1.25f)
                    blur(18f.dp.toPx())
                    lens(16f.dp.toPx(), 20f.dp.toPx(), depthEffect = true)
                },
                highlight = { Highlight.Default.copy(alpha = 0.5f) },
                shadow = { Shadow(alpha = 0.22f, radius = 12.dp) },
                innerShadow = { InnerShadow(radius = 4.dp, alpha = 0.18f) },
                onDrawSurface = { drawRect(surfaceColor) }
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        content()
    }
}

@Composable
private fun SectionTitle(title: String, textColor: Color) {
    BasicText(
        text = title,
        style = TextStyle(
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: () -> Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    textColor: Color,
    subTextColor: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = textColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = subtitle,
                style = TextStyle(
                    color = subTextColor,
                    fontSize = 12.sp
                )
            )
        }
        LiquidToggle(
            selected = checked,
            onSelect = onCheckedChange,
            backdrop = backdrop
        )
    }
}

@Composable
private fun SliderRow(
    title: String,
    valueText: String,
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    textColor: Color,
    subTextColor: Color,
    onInteractingChange: (Boolean) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                text = title,
                style = TextStyle(
                    color = textColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            )
            BasicText(
                text = valueText,
                style = TextStyle(
                    color = subTextColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            )
        }
        Box(Modifier.lockParentScroll(onInteractingChange)) {
            LiquidSlider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                backdrop = backdrop
            )
        }
    }
}

private fun Modifier.lockParentScroll(onInteractingChange: (Boolean) -> Unit): Modifier =
    pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val anyDown = event.changes.any { it.pressed }
                onInteractingChange(anyDown)
            }
        }
    }
