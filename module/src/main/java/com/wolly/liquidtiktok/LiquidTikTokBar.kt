package com.wolly.liquidtiktok

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs

private class SliceBackdrop(
    private val snapshot: () -> Bitmap?,
    private val dark: () -> Boolean,
    private val sliceProvider: ((size: Size) -> Pair<Float, Float>)? = null
) : Backdrop {
    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        val bmp = snapshot()
        if (bmp == null || bmp.isRecycled) {
            drawRect(if (dark()) Color(0xFF141414) else Color(0xFFE8E8E8))
            return
        }

        val slice = sliceProvider?.invoke(size)
        drawRect(if (dark()) Color(0xFF141414) else Color(0xFFE8E8E8))
        if (slice == null) {
            drawImage(
                image = bmp.asImageBitmap(),
                srcOffset = IntOffset.Zero,
                srcSize = IntSize(bmp.width, bmp.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        } else {
            val (startXFraction, widthFraction) = slice
            val bmpW = bmp.width.toFloat()
            val srcX = (startXFraction * bmpW).toInt().coerceIn(0, bmp.width - 1)
            val srcW = (widthFraction * bmpW).toInt().coerceIn(1, bmp.width - srcX)

            drawImage(
                image = bmp.asImageBitmap(),
                srcOffset = IntOffset(srcX, 0),
                srcSize = IntSize(srcW, bmp.height),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt())
            )
        }
    }
}

@Composable
fun LiquidTikTokBar(
    backgroundSnapshot: () -> Bitmap? = { null },
    externalBackdrop: Backdrop? = null,
    tabIcons: () -> List<Bitmap?> = { emptyList() },
    profileAvatar: () -> Bitmap? = { null },
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    isDarkTheme: () -> Boolean = { true },
    config: () -> LiquidConfig = { LiquidConfig() },
    inboxBadge: () -> String? = { null },
    friendsBadge: () -> String? = { null },
    modifier: Modifier = Modifier
) {
    val dark = isDarkTheme()
    val cfg = config()
    val currentView = LocalView.current

    val contentColor = if (dark) Color.White else Color(0xFF1A1A1A)
    val containerColor = if (dark) Color(0xFF141414).copy(cfg.barOpacity) else Color(0xFFF5F5F5).copy((cfg.barOpacity + 0.04f).coerceAtMost(0.8f))

    val tabsCount = 5

    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var currentIndex by remember(selectedTabIndex) {
        mutableIntStateOf(selectedTabIndex())
    }

    // Direct 1:1 real-time drag tracking animatables
    val valueAnim = remember { Animatable(selectedTabIndex().toFloat()) }
    val pressProgressAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    val motionRefractionAnim = remember { Animatable(0f) }
    val springSpec = remember { spring<Float>(dampingRatio = 0.72f, stiffness = 750f) }

    LaunchedEffect(selectedTabIndex) {
        snapshotFlow { selectedTabIndex() }
            .drop(1)
            .collectLatest { index ->
                if (currentIndex != index) {
                    currentIndex = index
                    animationScope.launch {
                        motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                    }
                    valueAnim.animateTo(index.toFloat(), springSpec)
                    motionRefractionAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f))
                }
            }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16f.dp, end = 16f.dp, top = 6f.dp, bottom = 4f.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val totalWidth = constraints.maxWidth.toFloat()
        val horizontalPad = 4f.dp
        val horizontalPadPx = with(density) { horizontalPad.toPx() }
        val tabWidth = (totalWidth - horizontalPadPx * 2) / tabsCount

        // 1. Gesture detector spanning the ENTIRE capsule: instant 1:1 tracking & taps
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(tabWidth, isLtr) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        currentView.parent?.requestDisallowInterceptTouchEvent(true)

                        val startX = down.position.x
                        val initialVal = valueAnim.value
                        var isDragging = false
                        var lastHapticTab = initialVal.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        val touchSlop = viewConfiguration.touchSlop

                        val pressJob = animationScope.launch {
                            launch { pressProgressAnim.animateTo(1f, spring(1f, 800f)) }
                            launch { scaleAnim.animateTo(1.08f, spring(0.7f, 500f)) }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUpIgnoreConsumed()) {
                                break
                            }

                            val dx = change.position.x - startX
                            if (!isDragging && abs(dx) > touchSlop) {
                                isDragging = true
                                animationScope.launch {
                                    motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                                }
                            }

                            if (isDragging) {
                                change.consume()
                                val delta = (if (isLtr) dx else -dx) / tabWidth
                                val nextVal = (initialVal + delta).fastCoerceIn(0f, (tabsCount - 1).toFloat())
                                val hoveredTab = nextVal.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                                if (hoveredTab != lastHapticTab) {
                                    lastHapticTab = hoveredTab
                                    if (config().hapticFeedback) {
                                        currentView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                }
                                animationScope.launch {
                                    valueAnim.snapTo(nextVal)
                                }
                            }
                        }

                        currentView.parent?.requestDisallowInterceptTouchEvent(false)

                        val targetIndex = if (isDragging) {
                            valueAnim.value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                        } else {
                            val tapX = (startX - horizontalPadPx).coerceAtLeast(0f)
                            val tapTab = if (isLtr) {
                                (tapX / tabWidth).toInt()
                            } else {
                                ((totalWidth - horizontalPadPx * 2 - tapX) / tabWidth).toInt()
                            }
                            tapTab.fastCoerceIn(0, tabsCount - 1)
                        }

                        val isMovingToOtherTab = (targetIndex != currentIndex)
                        val wasMovingOrDragging = isDragging || isMovingToOtherTab
                        if (!isDragging && isMovingToOtherTab && config().hapticFeedback) {
                            currentView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        }

                        currentIndex = targetIndex
                        animationScope.launch {
                            launch { pressProgressAnim.animateTo(0f, spring(1f, 800f)) }
                            launch { scaleAnim.animateTo(1f, spring(0.7f, 500f)) }
                            if (wasMovingOrDragging) {
                                launch {
                                    if (motionRefractionAnim.value < 0.5f) {
                                        motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                                    }
                                }
                                launch {
                                    valueAnim.animateTo(targetIndex.toFloat(), springSpec)
                                    motionRefractionAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f))
                                }
                            } else {
                                launch { valueAnim.animateTo(targetIndex.toFloat(), springSpec) }
                                launch { motionRefractionAnim.animateTo(0f, spring(1f, 800f)) }
                            }
                        }
                        onTabSelected(targetIndex)
                    }
                }
        )

        val containerBackdrop = externalBackdrop ?: remember(backgroundSnapshot, dark) {
            SliceBackdrop(backgroundSnapshot, { dark })
        }

        val blurredContainerBackdrop = rememberLayerBackdrop()
        val tabsBackdrop = rememberLayerBackdrop()

        // 2. Base glass container capsule
        Box(
            Modifier
                .drawBackdrop(
                    backdrop = containerBackdrop,
                    shape = { Capsule() },
                    exportedBackdrop = blurredContainerBackdrop,
                    effects = {
                        val c = config()
                        vibrancy()
                        colorControls(
                            brightness = if (dark) 0.10f else 0.05f,
                            saturation = 1.45f
                        )
                        if (c.blurRadiusDp > 0.1f) {
                            blur(c.blurRadiusDp.dp.toPx())
                        }
                        if (c.refractionIntensity > 0.01f) {
                            lens(
                                refractionHeight = 24f.dp.toPx() * c.refractionIntensity,
                                refractionAmount = 24f.dp.toPx() * c.refractionIntensity,
                                depthEffect = true
                            )
                        }
                    },
                    highlight = {
                        val progress = pressProgressAnim.value
                        Highlight.Default.copy(alpha = 0.65f + 0.35f * progress)
                    },
                    shadow = {
                        Shadow(alpha = 0.35f, radius = 10f.dp)
                    },
                    innerShadow = {
                        InnerShadow(radius = 5f.dp, alpha = 0.30f)
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .height(48f.dp)
                .fillMaxWidth()
        )

        // 2b. Offscreen Recorded Icons: captured into tabsBackdrop at 100% opacity for the liquid lens shader
        // drawToScreen = false ensures this layer is NEVER drawn directly to the screen canvas (ZERO ghost/duplicate icons)
        Row(
            Modifier
                .layerBackdrop(tabsBackdrop, drawToScreen = false)
                .padding(horizontal = horizontalPad)
                .height(48f.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentIcons = if (cfg.useNativeIcons) tabIcons() else emptyList()
            val avatarBmp = if (cfg.useProfileAvatar) profileAvatar() else null
            TikTokTabsContent(
                tabIcons = currentIcons,
                profileAvatar = avatarBmp,
                contentColor = contentColor,
                dark = dark,
                friendsBadge = friendsBadge(),
                inboxBadge = inboxBadge(),
                badgeMode = cfg.badgeMode,
                badgeIntroSeconds = cfg.badgeIntroSeconds,
                tabAlphas = { 1.0f },
                tabScales = { 1.0f }
            )
        }

        // 3. Sliding liquid lens indicator blob (refracts the exact blurred region behind it AND icons with mercury stretch & chromatic aberration)
        val combinedBackdrop = rememberCombinedBackdrop(blurredContainerBackdrop, tabsBackdrop)

        Box(
            Modifier
                .padding(horizontal = horizontalPad, vertical = 2f.dp)
                .graphicsLayer {
                    val currentVal = valueAnim.value
                    translationX =
                        if (isLtr) currentVal * tabWidth
                        else (tabsCount - 1f - currentVal) * tabWidth

                    val s = scaleAnim.value
                    val p = pressProgressAnim.value
                    val refract = motionRefractionAnim.value
                    // Fluid liquid blob deformation: stretches horizontally like liquid mercury when moving/dragging
                    if (cfg.mercuryStretch) {
                        scaleX = s * (1f + 0.08f * p * refract)
                        scaleY = s * (1f - 0.04f * p * refract)
                    } else {
                        scaleX = s
                        scaleY = s
                    }
                }
                .fillMaxWidth(1f / tabsCount)
                .height(44f.dp)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { Capsule() },
                    effects = {
                        val c = config()
                        val progress = pressProgressAnim.value
                        val refract = motionRefractionAnim.value * c.refractionIntensity
                        vibrancy()
                        if (refract > 0.001f) {
                            lens(
                                refractionHeight = 20f.dp.toPx() * refract,
                                refractionAmount = 18f.dp.toPx() * refract * (0.85f + 0.35f * progress),
                                depthEffect = true,
                                chromaticAberration = c.chromaticAberration && (refract > 0.1f)
                            )
                        }
                    },
                    highlight = {
                        val progress = pressProgressAnim.value
                        val refract = motionRefractionAnim.value
                        Highlight(
                            width = 0.85f.dp,
                            blurRadius = 0.45f.dp,
                            alpha = (0.55f + 0.40f * refract) + 0.05f * progress
                        )
                    },
                    shadow = {
                        val progress = pressProgressAnim.value
                        val refract = motionRefractionAnim.value
                        Shadow(
                            radius = 8f.dp,
                            alpha = (0.45f + 0.25f * refract) + 0.10f * progress
                        )
                    },
                    innerShadow = {
                        val progress = pressProgressAnim.value
                        val refract = motionRefractionAnim.value
                        InnerShadow(
                            radius = 6f.dp,
                            alpha = (0.35f + 0.25f * refract) + 0.10f * progress
                        )
                    },
                    onDrawSurface = {
                        val progress = pressProgressAnim.value
                        val refract = motionRefractionAnim.value
                        // Liquid droplet surface: subtle glossy frosted tint
                        val pillSurface = if (dark) Color.White.copy(0.08f + 0.04f * refract) else Color.White.copy(0.14f + 0.04f * refract)
                        drawRect(pillSurface, alpha = 1f - progress * 0.30f)
                    }
                )
        )

        // 4. Visible Icons outside the sliding pill: clipped strictly outside the pill capsule
        // so icons never fade early before the pill arrives and never duplicate inside the pill!
        Row(
            Modifier
                .padding(horizontal = horizontalPad)
                .height(48f.dp)
                .fillMaxWidth()
                .drawWithContent {
                    val currentVal = valueAnim.value
                    val pillLeft = if (isLtr) currentVal * tabWidth else (tabsCount - 1f - currentVal) * tabWidth
                    val s = scaleAnim.value
                    val p = pressProgressAnim.value
                    val refract = motionRefractionAnim.value
                    val sx = if (cfg.mercuryStretch) s * (1f + 0.08f * p * refract) else s
                    val sy = if (cfg.mercuryStretch) s * (1f - 0.04f * p * refract) else s
                    val pillW = tabWidth * sx
                    val pillH = 44f.dp.toPx() * sy
                    val pillCenterX = pillLeft + tabWidth / 2f
                    val pillCenterY = size.height / 2f
                    val r = pillH / 2f

                    val barPath = Path().apply {
                        addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                    }
                    val pillPath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = pillCenterX - pillW / 2f,
                                top = pillCenterY - pillH / 2f,
                                right = pillCenterX + pillW / 2f,
                                bottom = pillCenterY + pillH / 2f,
                                cornerRadius = CornerRadius(r, r)
                            )
                        )
                    }
                    val outsidePillPath = Path.combine(PathOperation.Difference, barPath, pillPath)
                    clipPath(outsidePillPath) {
                        this@drawWithContent.drawContent()
                    }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            val currentIcons = if (cfg.useNativeIcons) tabIcons() else emptyList()
            val avatarBmp = if (cfg.useProfileAvatar) profileAvatar() else null
            TikTokTabsContent(
                tabIcons = currentIcons,
                profileAvatar = avatarBmp,
                contentColor = contentColor,
                dark = dark,
                friendsBadge = friendsBadge(),
                inboxBadge = inboxBadge(),
                badgeMode = cfg.badgeMode,
                badgeIntroSeconds = cfg.badgeIntroSeconds,
                tabAlphas = { 0.50f },
                tabScales = { 1.0f }
            )
        }
    }
}

@Composable
private fun RowScope.TikTokTabsContent(
    tabIcons: List<Bitmap?>,
    profileAvatar: Bitmap? = null,
    contentColor: Color,
    dark: Boolean,
    friendsBadge: String?,
    inboxBadge: String?,
    badgeMode: Int = LiquidConfig.BADGE_MODE_INTRO_DOT,
    badgeIntroSeconds: Float = 3.0f,
    tabAlphas: (index: Int) -> Float,
    tabScales: (index: Int) -> Float
) {
    // Tab 0: Home (Icon only)
    TikTokNavTab(
        bitmap = tabIcons.getOrNull(0),
        fallbackPainter = rememberVectorPainter(TikTokIcons.Home),
        contentColor = contentColor,
        alpha = tabAlphas(0),
        scale = tabScales(0),
        badge = null,
        badgeMode = badgeMode,
        badgeIntroSeconds = badgeIntroSeconds
    )

    // Tab 1: Friends (Icon only)
    TikTokNavTab(
        bitmap = tabIcons.getOrNull(1),
        fallbackPainter = rememberVectorPainter(TikTokIcons.Friends),
        contentColor = contentColor,
        alpha = tabAlphas(1),
        scale = tabScales(1),
        badge = friendsBadge,
        badgeMode = badgeMode,
        badgeIntroSeconds = badgeIntroSeconds
    )

    // Tab 2: Create (+) (Icon only)
    TikTokCreateTab(
        bitmap = tabIcons.getOrNull(2),
        scale = tabScales(2),
        alpha = (tabAlphas(2) / 0.50f).coerceIn(0f, 1f),
        dark = dark
    )

    // Tab 3: Inbox (Icon only)
    TikTokNavTab(
        bitmap = tabIcons.getOrNull(3),
        fallbackPainter = rememberVectorPainter(TikTokIcons.Inbox),
        contentColor = contentColor,
        alpha = tabAlphas(3),
        scale = tabScales(3),
        badge = inboxBadge,
        badgeMode = badgeMode,
        badgeIntroSeconds = badgeIntroSeconds
    )

    // Tab 4: Profile (Avatar or Icon)
    if (profileAvatar != null && !profileAvatar.isRecycled) {
        TikTokAvatarTab(
            avatar = profileAvatar,
            contentColor = contentColor,
            alpha = (tabAlphas(4) / 0.50f).coerceIn(0.75f, 1.0f),
            scale = tabScales(4),
            isActive = tabAlphas(4) >= 0.99f
        )
    } else {
        TikTokNavTab(
            bitmap = tabIcons.getOrNull(4),
            fallbackPainter = rememberVectorPainter(TikTokIcons.Profile),
            contentColor = contentColor,
            alpha = tabAlphas(4),
            scale = tabScales(4),
            badge = null,
            badgeMode = badgeMode,
            badgeIntroSeconds = badgeIntroSeconds
        )
    }
}

@Composable
private fun RowScope.TikTokAvatarTab(
    avatar: Bitmap,
    contentColor: Color,
    alpha: Float,
    scale: Float,
    isActive: Boolean
) {
    Column(
        Modifier
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                }
                .clip(CircleShape)
                .border(
                    width = if (isActive) 1.6.dp else 1.0.dp,
                    color = contentColor.copy(alpha = if (isActive) 0.95f else 0.45f),
                    shape = CircleShape
                )
        ) {
            Image(
                bitmap = avatar.asImageBitmap(),
                contentDescription = "Profile",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun RowScope.TikTokNavTab(
    bitmap: Bitmap?,
    fallbackPainter: VectorPainter,
    contentColor: Color,
    alpha: Float,
    scale: Float,
    badge: String?,
    badgeMode: Int = LiquidConfig.BADGE_MODE_INTRO_DOT,
    badgeIntroSeconds: Float = 3.0f
) {
    val hasNumericBadge = !badge.isNullOrEmpty() && badge != "DOT"
    var introShown by remember { mutableStateOf(false) }
    var showCounterInsteadOfIcon by remember { mutableStateOf(false) }

    LaunchedEffect(hasNumericBadge, badgeMode) {
        if (badgeMode == LiquidConfig.BADGE_MODE_INTRO_DOT && hasNumericBadge && !introShown) {
            introShown = true
            showCounterInsteadOfIcon = true
            try {
                delay((badgeIntroSeconds * 1000L).toLong().coerceAtLeast(500L))
            } finally {
                showCounterInsteadOfIcon = false
            }
        } else if (badgeMode != LiquidConfig.BADGE_MODE_INTRO_DOT) {
            showCounterInsteadOfIcon = false
        }
    }

    Column(
        Modifier
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = showCounterInsteadOfIcon && hasNumericBadge,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(initialScale = 0.75f, animationSpec = tween(220)))
                    .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.75f, animationSpec = tween(180)))
            },
            label = "TabBadgeIntro"
        ) { showCounter ->
            if (showCounter) {
                Box(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(CircleShape)
                        .background(Color(0xFFFE2C55))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(
                        badge ?: "",
                        style = TextStyle(
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            } else {
                Box(contentAlignment = Alignment.TopEnd) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                this.alpha = alpha
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (bitmap != null && !bitmap.isRecycled) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                colorFilter = ColorFilter.tint(contentColor),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .paint(fallbackPainter, colorFilter = ColorFilter.tint(contentColor))
                            )
                        }
                    }
                    if (!badge.isNullOrEmpty()) {
                        if (badgeMode == LiquidConfig.BADGE_MODE_ALWAYS_COUNTER && hasNumericBadge) {
                            Box(
                                modifier = Modifier
                                    .offset(x = 9.dp, y = (-4).dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFE2C55))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicText(
                                    badge ?: "",
                                    style = TextStyle(
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .offset(x = 3.dp, y = (-1).dp)
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFE2C55))
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RowScope.TikTokCreateTab(
    bitmap: Bitmap?,
    scale: Float,
    alpha: Float = 1.0f,
    dark: Boolean
) {
    val addIconPainter = rememberVectorPainter(TikTokIcons.Add)
    Column(
        Modifier
            .fillMaxHeight()
            .weight(1f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    this.alpha = alpha
                },
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null && !bitmap.isRecycled) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Create",
                    modifier = Modifier.size(40.dp)
                )
            } else {
                Box(
                    modifier = Modifier.size(width = 38.dp, height = 26.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Authentic cyan left wing
                    Box(
                        modifier = Modifier
                            .offset(x = (-3).dp)
                            .size(width = 34.dp, height = 26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF20D5EC))
                    )
                    // Authentic red/magenta right wing
                    Box(
                        modifier = Modifier
                            .offset(x = 3.dp)
                            .size(width = 34.dp, height = 26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFFA2D6C))
                    )
                    // Authentic center pill
                    Box(
                        modifier = Modifier
                            .size(width = 34.dp, height = 26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (dark) Color.White else Color(0xFF161823)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(14.dp)
                                .paint(
                                    addIconPainter,
                                    colorFilter = ColorFilter.tint(if (dark) Color.Black else Color.White)
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiquidTikTokTopBar(
    backgroundSnapshot: () -> Bitmap? = { null },
    tabs: () -> List<String>,
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    onSearchClick: () -> Unit,
    isDarkTheme: () -> Boolean = { true },
    config: () -> LiquidConfig = { LiquidConfig() },
    modifier: Modifier = Modifier
) {
    val tabLabels = tabs()
    if (tabLabels.isEmpty()) return
    val tabsCount = tabLabels.size
    val dark = isDarkTheme()
    val cfg = config()
    val currentView = LocalView.current

    val contentColor = if (dark) Color.White else Color(0xFF1A1A1A)
    val containerColor = if (dark) Color(0xFF141414).copy(cfg.barOpacity) else Color(0xFFF5F5F5).copy((cfg.barOpacity + 0.04f).coerceAtMost(0.8f))
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var currentIndex by remember(tabsCount) {
        mutableIntStateOf(selectedTabIndex().coerceIn(0, tabsCount - 1))
    }

    val valueAnim = remember(tabsCount) { Animatable(selectedTabIndex().coerceIn(0, tabsCount - 1).toFloat()) }
    val pressProgressAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(1f) }
    val motionRefractionAnim = remember { Animatable(0f) }
    val springSpec = remember { spring<Float>(dampingRatio = 0.72f, stiffness = 750f) }

    LaunchedEffect(selectedTabIndex, tabsCount) {
        snapshotFlow { selectedTabIndex().coerceIn(0, tabsCount - 1) }
            .collectLatest { index ->
                if (currentIndex != index) {
                    currentIndex = index
                    animationScope.launch {
                        motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                    }
                    valueAnim.animateTo(index.toFloat(), springSpec)
                    motionRefractionAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f))
                }
            }
    }

    val searchPainter = rememberVectorPainter(TikTokIcons.Search)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val sidePadPx = with(density) { 16.dp.toPx() }
        val gapPx = with(density) { 10.dp.toPx() }
        val searchSize = 40.dp
        val searchSizePx = with(density) { searchSize.toPx() }
        val contentW = constraints.maxWidth.toFloat()
        val fullW = (contentW + sidePadPx * 2f).coerceAtLeast(1f)
        val capsuleW = (contentW - gapPx - searchSizePx).coerceAtLeast(1f)

        val horizontalPad = 4.dp
        val horizontalPadPx = with(density) { horizontalPad.toPx() }
        val tabWidth = ((capsuleW - horizontalPadPx * 2) / tabsCount).coerceAtLeast(1f)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 1. Top Tabs Liquid Glass Capsule
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Gesture detector across the top tabs capsule
                Box(
                    Modifier
                        .matchParentSize()
                        .pointerInput(tabWidth, isLtr, tabsCount) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                currentView.parent?.requestDisallowInterceptTouchEvent(true)

                                val startX = down.position.x
                                val initialVal = valueAnim.value
                                var isDragging = false
                                var lastHapticTab = initialVal.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                                val touchSlop = viewConfiguration.touchSlop

                                animationScope.launch {
                                    launch { pressProgressAnim.animateTo(1f, spring(1f, 800f)) }
                                    launch { scaleAnim.animateTo(1.03f, spring(0.7f, 500f)) }
                                }

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (change.changedToUpIgnoreConsumed()) break

                                    val dx = change.position.x - startX
                                    if (!isDragging && abs(dx) > touchSlop) {
                                        isDragging = true
                                        animationScope.launch {
                                            motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                                        }
                                    }

                                    if (isDragging) {
                                        change.consume()
                                        val delta = (if (isLtr) dx else -dx) / tabWidth
                                        val nextVal = (initialVal + delta).fastCoerceIn(0f, (tabsCount - 1).toFloat())
                                        val hoveredTab = nextVal.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                                        if (hoveredTab != lastHapticTab) {
                                            lastHapticTab = hoveredTab
                                            if (config().hapticFeedback) {
                                                currentView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                            }
                                        }
                                        animationScope.launch { valueAnim.snapTo(nextVal) }
                                    }
                                }

                                currentView.parent?.requestDisallowInterceptTouchEvent(false)

                                val targetIndex = if (isDragging) {
                                    valueAnim.value.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                                } else {
                                    val tapX = (startX - horizontalPadPx).coerceAtLeast(0f)
                                    val tapTab = if (isLtr) {
                                        (tapX / tabWidth).toInt()
                                    } else {
                                        ((capsuleW - horizontalPadPx * 2 - tapX) / tabWidth).toInt()
                                    }
                                    tapTab.fastCoerceIn(0, tabsCount - 1)
                                }

                                val isMovingToOtherTab = (targetIndex != currentIndex)
                                val wasMovingOrDragging = isDragging || isMovingToOtherTab
                                if (!isDragging && isMovingToOtherTab && config().hapticFeedback) {
                                    currentView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                }

                                currentIndex = targetIndex
                                animationScope.launch {
                                    launch { pressProgressAnim.animateTo(0f, spring(1f, 800f)) }
                                    launch { scaleAnim.animateTo(1f, spring(0.7f, 500f)) }
                                    if (wasMovingOrDragging) {
                                        launch {
                                            if (motionRefractionAnim.value < 0.5f) {
                                                motionRefractionAnim.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 800f))
                                            }
                                        }
                                        launch {
                                            valueAnim.animateTo(targetIndex.toFloat(), springSpec)
                                            motionRefractionAnim.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 400f))
                                        }
                                    } else {
                                        launch { valueAnim.animateTo(targetIndex.toFloat(), springSpec) }
                                        launch { motionRefractionAnim.animateTo(0f, spring(1f, 800f)) }
                                    }
                                }
                                onTabSelected(targetIndex)
                            }
                        }
                )

                val tabsContainerBackdrop = remember(backgroundSnapshot, dark, sidePadPx, capsuleW, fullW) {
                    SliceBackdrop(backgroundSnapshot, { dark }) {
                        Pair(sidePadPx / fullW, capsuleW / fullW)
                    }
                }
                val blurredCapsuleBackdrop = rememberLayerBackdrop()
                val topTabsBackdrop = rememberLayerBackdrop()

                // Base glass capsule
                Box(
                    Modifier
                        .drawBackdrop(
                            backdrop = tabsContainerBackdrop,
                            shape = { Capsule() },
                            exportedBackdrop = blurredCapsuleBackdrop,
                            effects = {
                                val c = config()
                                vibrancy()
                                colorControls(
                                    brightness = if (dark) 0.10f else 0.05f,
                                    saturation = 1.45f
                                )
                                if (c.blurRadiusDp > 0.1f) {
                                    blur(c.blurRadiusDp.dp.toPx())
                                }
                                if (c.refractionIntensity > 0.01f) {
                                    lens(
                                        refractionHeight = 20f.dp.toPx() * c.refractionIntensity,
                                        refractionAmount = 20f.dp.toPx() * c.refractionIntensity,
                                        depthEffect = true
                                    )
                                }
                            },
                            highlight = {
                                Highlight.Default.copy(alpha = 0.65f + 0.35f * pressProgressAnim.value)
                            },
                            shadow = { Shadow(alpha = 0.30f, radius = 8f.dp) },
                            innerShadow = { InnerShadow(radius = 4f.dp, alpha = 0.25f) },
                            onDrawSurface = { drawRect(containerColor) }
                        )
                        .fillMaxSize()
                )

                // Offscreen recorded tab labels for the sliding lens
                Row(
                    Modifier
                        .layerBackdrop(topTabsBackdrop, drawToScreen = false)
                        .padding(horizontal = horizontalPad)
                        .fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabLabels.forEach { label ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = contentColor,
                                    fontSize = 13.5f.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                // Sliding liquid lens indicator pill
                val combinedBackdrop = rememberCombinedBackdrop(blurredCapsuleBackdrop, topTabsBackdrop)
                Box(
                    Modifier
                        .padding(horizontal = horizontalPad)
                        .graphicsLayer {
                            val currentVal = valueAnim.value
                            val translationX = if (isLtr) currentVal * tabWidth else -currentVal * tabWidth
                            this.translationX = translationX

                            val c = config()
                            val stretchAmt = if (c.mercuryStretch) 0.06f * motionRefractionAnim.value else 0f
                            scaleX = scaleAnim.value * (1f + stretchAmt)
                            scaleY = scaleAnim.value * (1f - stretchAmt * 0.45f)
                        }
                        .drawBackdrop(
                            backdrop = combinedBackdrop,
                            shape = { Capsule() },
                            effects = {
                                val c = config()
                                val refractionP = motionRefractionAnim.value * c.refractionIntensity
                                vibrancy()
                                colorControls(brightness = 0.08f, saturation = 1.5f)
                                if (refractionP > 0.01f) {
                                    lens(
                                        refractionHeight = 14f.dp.toPx() * refractionP,
                                        refractionAmount = 16f.dp.toPx() * refractionP,
                                        chromaticAberration = c.chromaticAberration,
                                        depthEffect = true
                                    )
                                }
                            },
                            highlight = {
                                val p = pressProgressAnim.value.coerceAtLeast(motionRefractionAnim.value)
                                Highlight.Default.copy(alpha = 0.55f + 0.45f * p)
                            },
                            shadow = { Shadow(alpha = 0.28f, radius = 6f.dp) },
                            innerShadow = { InnerShadow(radius = 3f.dp, alpha = 0.30f) },
                            onDrawSurface = {
                                val progress = pressProgressAnim.value
                                val refract = motionRefractionAnim.value
                                val pillSurface = if (dark) Color.White.copy(0.08f + 0.04f * refract) else Color.White.copy(0.14f + 0.04f * refract)
                                drawRect(pillSurface, alpha = 1f - progress * 0.30f)
                            }
                        )
                        .height(32.dp)
                        .fillMaxWidth(1f / tabsCount)
                )

                // Crisp foreground labels clipped strictly OUTSIDE the sliding pill capsule (zero double-refraction)
                Row(
                    Modifier
                        .padding(horizontal = horizontalPad)
                        .fillMaxSize()
                        .drawWithContent {
                            val currentVal = valueAnim.value
                            val pillLeft = if (isLtr) currentVal * tabWidth else (tabsCount - 1f - currentVal) * tabWidth
                            val s = scaleAnim.value
                            val refract = motionRefractionAnim.value
                            val c = config()
                            val stretchAmt = if (c.mercuryStretch) 0.06f * refract else 0f
                            val sx = s * (1f + stretchAmt)
                            val sy = s * (1f - stretchAmt * 0.45f)
                            val pillW = tabWidth * sx
                            val pillH = 32f.dp.toPx() * sy
                            val pillCenterX = pillLeft + tabWidth / 2f
                            val pillCenterY = size.height / 2f
                            val r = pillH / 2f

                            val barPath = Path().apply {
                                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height))
                            }
                            val pillPath = Path().apply {
                                addRoundRect(
                                    RoundRect(
                                        left = pillCenterX - pillW / 2f,
                                        top = pillCenterY - pillH / 2f,
                                        right = pillCenterX + pillW / 2f,
                                        bottom = pillCenterY + pillH / 2f,
                                        cornerRadius = CornerRadius(r, r)
                                    )
                                )
                            }
                            val outsidePath = Path().apply {
                                op(barPath, pillPath, PathOperation.Difference)
                            }
                            clipPath(outsidePath) {
                                this@drawWithContent.drawContent()
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabLabels.forEachIndexed { index, label ->
                        val distance = abs(valueAnim.value - index.toFloat())
                        val fraction = (1f - distance).coerceIn(0f, 1f)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .graphicsLayer {
                                    val s = 0.96f + 0.06f * fraction
                                    scaleX = s
                                    scaleY = s
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(
                                text = label,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(
                                    color = contentColor.copy(alpha = 0.62f + 0.38f * fraction),
                                    fontSize = 13.5f.sp,
                                    fontWeight = if (fraction > 0.5f) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(10.dp))

            // 2. Circular Liquid Glass Search Button
            val searchBackdrop = remember(backgroundSnapshot, dark, sidePadPx, searchSizePx, fullW) {
                SliceBackdrop(backgroundSnapshot, { dark }) {
                    val startX = (fullW - sidePadPx - searchSizePx).coerceAtLeast(0f)
                    Pair(startX / fullW, searchSizePx / fullW)
                }
            }
            Box(
                modifier = Modifier
                    .size(searchSize)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (config().hapticFeedback) {
                                currentView.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            }
                            onSearchClick()
                        }
                    }
                    .drawBackdrop(
                        backdrop = searchBackdrop,
                        shape = { CircleShape },
                        effects = {
                            val c = config()
                            vibrancy()
                            colorControls(
                                brightness = if (dark) 0.10f else 0.05f,
                                saturation = 1.45f
                            )
                            if (c.blurRadiusDp > 0.1f) {
                                blur(c.blurRadiusDp.dp.toPx())
                            }
                            if (c.refractionIntensity > 0.01f) {
                                lens(
                                    refractionHeight = 16f.dp.toPx() * c.refractionIntensity,
                                    refractionAmount = 16f.dp.toPx() * c.refractionIntensity,
                                    depthEffect = true
                                )
                            }
                        },
                        highlight = { Highlight.Default.copy(alpha = 0.70f) },
                        shadow = { Shadow(alpha = 0.30f, radius = 8f.dp) },
                        innerShadow = { InnerShadow(radius = 4f.dp, alpha = 0.25f) },
                        onDrawSurface = { drawRect(containerColor) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(20.dp)
                        .paint(searchPainter, colorFilter = ColorFilter.tint(contentColor))
                )
            }
        }
    }
}

@Composable
fun LiquidTikTokSeekBar(
    backgroundSnapshot: () -> Bitmap? = { null },
    progress: () -> Float,
    isPressed: () -> Boolean = { false },
    isDarkTheme: () -> Boolean = { true },
    modifier: Modifier = Modifier
) {
    val dark = isDarkTheme()
    val accentColor = if (dark) Color(0xFF0091FF) else Color(0xFF0088FF)
    val trackColor = if (dark) Color(0xFF787880).copy(0.36f) else Color(0xFF787878).copy(0.2f)
    val backdrop = remember(backgroundSnapshot, dark) {
        SliceBackdrop(backgroundSnapshot, { dark })
    }
    val trackBackdrop = rememberLayerBackdrop()
    val pressAnim = remember { Animatable(0f) }
    val scaleAnim = remember { Animatable(0.35f) }

    LaunchedEffect(isPressed) {
        snapshotFlow { isPressed() }.collectLatest { pressed ->
            launch { pressAnim.animateTo(if (pressed) 1f else 0f, spring(1f, 800f)) }
            launch { scaleAnim.animateTo(if (pressed) 1.5f else 0.35f, spring(0.7f, 500f)) }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val frac = progress().coerceIn(0f, 1f)
        val trackHeight = (2.5f + 3.5f * pressAnim.value).dp

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .height(trackHeight)
                    .fillMaxWidth()
            )
            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(trackHeight)
                    .fillMaxWidth(frac)
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    val minTx = -size.width / 2f + size.width * 0.25f * pressAnim.value
                    val maxTx = trackWidth - size.width / 2f - size.width * 0.25f * pressAnim.value
                    val tx = (-size.width / 2f + trackWidth * frac).fastCoerceIn(minTx, maxTx)
                    translationX = if (isLtr) tx else -tx
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { Capsule() },
                    effects = {
                        val p = pressAnim.value
                        blur(8f.dp.toPx() * (1f - p))
                        lens(
                            10f.dp.toPx() * p,
                            14f.dp.toPx() * p,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val p = pressAnim.value
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = p
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4f.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val p = pressAnim.value
                        InnerShadow(
                            radius = 4f.dp * p,
                            alpha = p
                        )
                    },
                    layerBlock = {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                    },
                    onDrawSurface = {
                        val p = pressAnim.value
                        drawRect(Color.White.copy(alpha = 1f - p))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
