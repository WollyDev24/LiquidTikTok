package com.wolly.liquidtiktok

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XSharedPreferences
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.reflect.Method

class ModuleLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner {
    val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
}

class MainHook : IXposedHookLoadPackage {

    companion object {
        const val TAG = "LiquidTikTok"
        private const val COMPOSE_VIEW_TAG = "liquid_tiktok_nav_bar"
        private var activeLifecycleOwner: ModuleLifecycleOwner? = null
        private var activePopupWindow: PopupWindow? = null
        private val configState = mutableStateOf(LiquidConfig())
        private var configObserver: ContentObserver? = null
        private val xprefs: XSharedPreferences by lazy {
            XSharedPreferences("com.wolly.liquidtiktok", LiquidConfig.PREFS_NAME).apply {
                makeWorldReadable()
            }
        }
        private var lastConfigCheckTime = 0L

        private fun refreshConfig(context: Context? = null, force: Boolean = false) {
            if (context != null) {
                val remote = LiquidConfig.queryRemote(context.contentResolver)
                if (remote != null) {
                    if (configState.value != remote) {
                        configState.value = remote
                        Log.i(TAG, "Loaded config via ContentProvider: $remote")
                    }
                    return
                }
            }
            try {
                if (force || xprefs.hasFileChanged()) {
                    xprefs.reload()
                    val loaded = LiquidConfig(
                        enabled = xprefs.getBoolean(LiquidConfig.KEY_ENABLED, true),
                        blurRadiusDp = xprefs.getFloat(LiquidConfig.KEY_BLUR_RADIUS, 14f).coerceIn(0f, 32f),
                        barOpacity = xprefs.getFloat(LiquidConfig.KEY_BAR_OPACITY, 0.24f).coerceIn(0.05f, 0.65f),
                        refractionIntensity = xprefs.getFloat(LiquidConfig.KEY_REFRACTION_INTENSITY, 1.0f).coerceIn(0f, 2f),
                        chromaticAberration = xprefs.getBoolean(LiquidConfig.KEY_CHROMATIC_ABERRATION, true),
                        mercuryStretch = xprefs.getBoolean(LiquidConfig.KEY_MERCURY_STRETCH, true),
                        useNativeIcons = xprefs.getBoolean(LiquidConfig.KEY_USE_NATIVE_ICONS, true),
                        useProfileAvatar = xprefs.getBoolean(LiquidConfig.KEY_USE_PROFILE_AVATAR, true),
                        bottomOffsetDp = xprefs.getFloat(LiquidConfig.KEY_BOTTOM_OFFSET, 52f).coerceIn(32f, 72f),
                        hapticFeedback = xprefs.getBoolean(LiquidConfig.KEY_HAPTIC_FEEDBACK, true),
                        badgeMode = xprefs.getInt(LiquidConfig.KEY_BADGE_MODE, LiquidConfig.BADGE_MODE_INTRO_DOT).coerceIn(0, 2),
                        badgeIntroSeconds = xprefs.getFloat(LiquidConfig.KEY_BADGE_INTRO_SECONDS, 3.0f).coerceIn(1.0f, 6.0f),
                        liquidTopBar = xprefs.getBoolean(LiquidConfig.KEY_LIQUID_TOP_BAR, true),
                        liquidSeekBar = xprefs.getBoolean(LiquidConfig.KEY_LIQUID_SEEK_BAR, true)
                    )
                    if (configState.value != loaded) {
                        configState.value = loaded
                        Log.i(TAG, "Loaded config via XSharedPreferences: $loaded")
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to reload XSharedPreferences: ${t.message}")
            }
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val packageName = lpparam.packageName
        if (packageName != "com.zhiliaoapp.musically" && packageName != "com.ss.android.ugc.trill") {
            return
        }

        XposedBridge.log("[$TAG] Injected into TikTok ($packageName)!")
        Log.i(TAG, "Injected into TikTok ($packageName)!")

        val mainActivityClass = try {
            lpparam.classLoader.loadClass("com.ss.android.ugc.aweme.main.MainActivity")
        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Failed to find MainActivity: ${t.message}")
            Log.e(TAG, "Failed to find MainActivity", t)
            return
        }

        val onCreateMethod = findMethodInHierarchy(mainActivityClass, "onCreate", Bundle::class.java)
        if (onCreateMethod != null) {
            XposedBridge.hookMethod(onCreateMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    Log.i(TAG, "MainActivity.onCreate called, scheduling attachLiquidBar")
                    activity.window?.decorView?.post {
                        attachLiquidBar(activity)
                    }
                }
            })
        }

        val onResumeMethod = findMethodInHierarchy(mainActivityClass, "onResume")
        if (onResumeMethod != null) {
            XposedBridge.hookMethod(onResumeMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity ?: return
                    refreshConfig(activity, force = true)
                    activeLifecycleOwner?.lifecycleRegistry?.currentState = Lifecycle.State.RESUMED
                    activity.window?.decorView?.post {
                        attachLiquidBar(activity)
                    }
                }
            })
        }

        val onPauseMethod = findMethodInHierarchy(mainActivityClass, "onPause")
        if (onPauseMethod != null) {
            XposedBridge.hookMethod(onPauseMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    activeLifecycleOwner?.lifecycleRegistry?.currentState = Lifecycle.State.STARTED
                }
            })
        }

        val onDestroyMethod = findMethodInHierarchy(mainActivityClass, "onDestroy")
        if (onDestroyMethod != null) {
            XposedBridge.hookMethod(onDestroyMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as? Activity
                    configObserver?.let { obs ->
                        try { activity?.contentResolver?.unregisterContentObserver(obs) } catch (_: Throwable) {}
                    }
                    configObserver = null
                    try {
                        activePopupWindow?.dismiss()
                    } catch (_: Throwable) {}
                    activePopupWindow = null
                    activeLifecycleOwner?.lifecycleRegistry?.currentState = Lifecycle.State.DESTROYED
                    activeLifecycleOwner = null
                }
            })
        }

        val onWindowFocusChangedMethod = findMethodInHierarchy(mainActivityClass, "onWindowFocusChanged", Boolean::class.javaPrimitiveType ?: java.lang.Boolean.TYPE)
        if (onWindowFocusChangedMethod != null) {
            XposedBridge.hookMethod(onWindowFocusChangedMethod, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val hasFocus = param.args.getOrNull(0) as? Boolean ?: false
                    if (hasFocus) {
                        val activity = param.thisObject as? Activity ?: return
                        refreshConfig(activity, force = true)
                        activity.window?.decorView?.post {
                            attachLiquidBar(activity)
                        }
                    }
                }
            })
        }
    }

    private fun findMethodInHierarchy(clazz: Class<*>, methodName: String, vararg paramTypes: Class<*>): Method? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                val m = current.getDeclaredMethod(methodName, *paramTypes)
                m.isAccessible = true
                return m
            } catch (e: NoSuchMethodException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun findFieldInHierarchy(clazz: Class<*>, fieldName: String): java.lang.reflect.Field? {
        var current: Class<*>? = clazz
        while (current != null && current != Any::class.java) {
            try {
                val f = current.getDeclaredField(fieldName)
                f.isAccessible = true
                return f
            } catch (e: NoSuchFieldException) {
                current = current.superclass
            }
        }
        return null
    }

    private fun attachLiquidBar(activity: Activity) {
        try {
            val decorView = activity.window?.decorView as? ViewGroup ?: return

            if (decorView.findViewWithTag<View>(COMPOSE_VIEW_TAG) != null) {
                return
            }

            refreshConfig(activity, force = true)
            if (!configState.value.enabled) {
                Log.i(TAG, "LiquidTikTok disabled in module settings")
                return
            }
            if (configObserver == null) {
                val obs = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        refreshConfig(activity, force = true)
                    }
                }
                configObserver = obs
                try {
                    activity.contentResolver.registerContentObserver(LiquidConfig.CONTENT_URI, true, obs)
                } catch (_: Throwable) {}
            }

            val res = activity.resources
            val pkg = activity.packageName

            fun getId(name: String): Int = try {
                res.getIdentifier(name, "id", pkg)
            } catch (e: Throwable) {
                0
            }

            val oqqId = getId("oqq")
            val oqlId = getId("oql")
            val oxrId = getId("oxr")
            val cphId = getId("cph")
            var hasDumpedTabIcons = false

            fun hasDescendantText(v: View, target: String): Boolean {
                if (v.contentDescription?.toString()?.equals(target, ignoreCase = true) == true) return true
                if (v is TextView && v.text?.toString()?.equals(target, ignoreCase = true) == true) return true
                if (v is ViewGroup) {
                    for (ci in 0 until v.childCount) {
                        if (hasDescendantText(v.getChildAt(ci), target)) return true
                    }
                }
                return false
            }

            fun isValidBottomBar(v: ViewGroup, allowUnlaidOut: Boolean = false): Boolean {
                if (v.childCount != 5) return false
                val c0 = v.getChildAt(0)
                val c4 = v.getChildAt(4)
                if (hasDescendantText(c0, "Home") || hasDescendantText(c4, "Profile")) return true
                if (v !is android.widget.LinearLayout) return false
                if (allowUnlaidOut && v.height == 0) return true
                return v.height in 60..280 && v.width >= decorView.width / 2
            }

            fun findBottomBarStructural(root: View): ViewGroup? {
                if (root is ViewGroup && isValidBottomBar(root, allowUnlaidOut = false)) {
                    return root
                }
                if (root is ViewGroup) {
                    for (ci in 0 until root.childCount) {
                        findBottomBarStructural(root.getChildAt(ci))?.let { return it }
                    }
                }
                return null
            }

            fun findOriginalBar(): ViewGroup? {
                if (oxrId != 0) decorView.findViewById<ViewGroup>(oxrId)?.takeIf { isValidBottomBar(it, allowUnlaidOut = true) }?.let { return it }
                if (oqlId != 0) decorView.findViewById<ViewGroup>(oqlId)?.takeIf { isValidBottomBar(it, allowUnlaidOut = true) }?.let { return it }
                return findBottomBarStructural(decorView)
            }

            val originalBar = findOriginalBar()
            val oqqView = if (oqqId != 0 && originalBar != null) {
                decorView.findViewById<ViewGroup>(oqqId)?.takeIf {
                    originalBar.parent === it || (originalBar.id != View.NO_ID && it.findViewById<View>(originalBar.id) === originalBar)
                }
            } else null
            val container = oqqView ?: (originalBar?.parent as? ViewGroup)

            if (originalBar == null || container == null) {
                Log.d(TAG, "attachLiquidBar: container ($container) or originalBar ($originalBar) not ready yet")
                return
            }

            for (i in 0 until originalBar.childCount) {
                val child = originalBar.getChildAt(i)
                val entry = try { res.getResourceEntryName(child.id) } catch (e: Exception) { "" }
                fun getChildText(v: View): String {
                    if (v is TextView && v.text?.isNotEmpty() == true) return v.text.toString()
                    if (v is ViewGroup) {
                        for (ci in 0 until v.childCount) {
                            val t = getChildText(v.getChildAt(ci))
                            if (t.isNotEmpty()) return t
                        }
                    }
                    return ""
                }
                val txt = getChildText(child)
                Log.i(TAG, "ORIGINAL TAB $i: id=$entry (${child.id}), desc='${child.contentDescription}', text='$txt', class=${child.javaClass.simpleName}")
                if (child is ViewGroup) {
                    for (j in 0 until child.childCount) {
                        val c = child.getChildAt(j)
                        val cEntry = try { res.getResourceEntryName(c.id) } catch (e: Exception) { "" }
                        Log.i(TAG, "  subchild $j: class=${c.javaClass.simpleName}, id=$cEntry, vis=${c.visibility}")
                        if (c is android.widget.ImageView) {
                            Log.i(TAG, "    ImageView drawable=${c.drawable?.javaClass?.simpleName}, w=${c.drawable?.intrinsicWidth}, h=${c.drawable?.intrinsicHeight}")
                        }
                    }
                }
            }

            // Helper to dynamically get the original TikTok tab view by index
            fun getOriginalTab(index: Int): View? {
                val bar = findOriginalBar() ?: originalBar
                if (bar != null && index in 0 until bar.childCount) {
                    return bar.getChildAt(index)
                }
                val idNames = listOf("oxj", "oxi", "oxf", "oxk", "oxl", "oqd", "oqc", "oq_", "oqe", "oqf")
                val idName = idNames.getOrNull(index) ?: return null
                val id = getId(idName)
                return if (id != 0) decorView.findViewById(id) else null
            }

            val selectedTabIndex = mutableIntStateOf(0)
            val isDarkTheme = mutableStateOf(true)
            val tabBitmaps = mutableStateListOf<Bitmap?>(null, null, null, null, null)
            val inboxBadgeState = mutableStateOf<String?>(null)
            val friendsBadgeState = mutableStateOf<String?>(null)
            val avatarCacheFile = java.io.File(activity.filesDir, "liquid_tiktok_avatar.png")
            val profileAvatarBitmap = mutableStateOf<Bitmap?>(
                try {
                    if (avatarCacheFile.exists()) android.graphics.BitmapFactory.decodeFile(avatarCacheFile.absolutePath) else null
                } catch (_: Throwable) { null }
            )
            var lastAvatarDrawable: Drawable? = null
            var lastAvatarCheckTime = 0L
            val bq4Id = getId("bq4")
            val lastDrawables = arrayOfNulls<Drawable>(5)
            val lastSelected = BooleanArray(5)
            var activeComposeView: View? = null
            var popupWindow: PopupWindow? = null
            var lastPopupX = Int.MIN_VALUE
            var lastPopupY = Int.MIN_VALUE
            var lastPopupW = -1
            val barHeightPx = (58f * activity.resources.displayMetrics.density).toInt()

            val topTabTitles = mutableStateOf<List<String>>(emptyList())
            val selectedTopTabIndex = mutableIntStateOf(0)
            val topBackgroundSnapshot = mutableStateOf<Bitmap?>(null)
            var topComposeView: View? = null
            var topPopupWindow: PopupWindow? = null
            var lastTopPopupX = Int.MIN_VALUE
            var lastTopPopupY = Int.MIN_VALUE
            var lastTopPopupW = -1
            val topBarHeightPx = (48f * activity.resources.displayMetrics.density).toInt()
            var activeTopTabStrip: ViewGroup? = null
            var activeSearchBtn: View? = null
            var topBmpA: Bitmap? = null
            var topBmpB: Bitmap? = null
            var topUiBmp: Bitmap? = null
            val topSvBmps = arrayOfNulls<Bitmap>(4)
            var topUseA = true
            var isTopCopying = false

            val seekProgressState = androidx.compose.runtime.mutableFloatStateOf(0f)
            val seekBackgroundSnapshot = mutableStateOf<Bitmap?>(null)
            var seekComposeView: View? = null
            var seekPopupWindow: PopupWindow? = null
            var lastSeekPopupX = Int.MIN_VALUE
            var lastSeekPopupY = Int.MIN_VALUE
            var lastSeekPopupW = -1
            val seekBarHeightPx = (36f * activity.resources.displayMetrics.density).toInt()
            var activeSeekBar: android.widget.SeekBar? = null
            var activeVsb: View? = null
            var isSeekScrubbing = false
            val isSeekPressedState = mutableStateOf(false)
            var hookedSeekBarClassName: String? = null
            var hookedDecorTouchClassName: String? = null
            var seekBmpA: Bitmap? = null
            var seekBmpB: Bitmap? = null
            var seekUiBmp: Bitmap? = null
            val seekSvBmps = arrayOfNulls<Bitmap>(4)
            var seekUseA = true
            var isSeekCopying = false

            fun updateIsDark(): Boolean {
                // Feed (tab 0) and Camera/Create (tab 2) are ALWAYS dark mode (white icons) like native TikTok
                if (selectedTabIndex.intValue == 0 || selectedTabIndex.intValue == 2) {
                    return true
                }
                // Other tabs (Inbox, Profile, Friends) follow system night mode
                val isNight = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                return isNight
            }

            val va8Id = getId("va8")
            val wrapperId = getId("view_pager_layout_wrapper")
            val vpcId = getId("viewpager_container")
            val yj_Id = getId("yj_")
            val dw4Id = getId("dw4")
            val xt7Id = getId("xt7")
            val cj3Id = getId("cj3")
            val cpbId = getId("cpb")
            val wxzId = getId("wxz")
            val ctnId = getId("ctn")
            val lkdId = getId("lkd")
            val lkkId = getId("lkk")
            val vsbId = getId("video_seek_bar")
            val kgdId = getId("kgd")
            val kayId = getId("kay")
            val widgetContainerId = getId("widget_container")
            val topWidgetId = getId("top_widget_container")

            // Expand video pager layout to full parent height so videos flow behind the floating bar
            var wrapperListenerAttached = false
            decorView.viewTreeObserver.addOnGlobalLayoutListener {
                if (!wrapperListenerAttached && wrapperId != 0) {
                    val wrapper = decorView.findViewById<View>(wrapperId)
                    if (wrapper != null) {
                        wrapperListenerAttached = true
                        wrapper.addOnLayoutChangeListener { v, _, top, _, bottom, _, _, _, _ ->
                            val parent = v.parent as? View ?: return@addOnLayoutChangeListener
                            val targetHeight = parent.height
                            if (targetHeight > 0 && (bottom - top) != targetHeight) {
                                v.post {
                                    val lp = v.layoutParams
                                    if (lp != null && lp.height != targetHeight) {
                                        Log.i(TAG, "Expanding video layout wrapper from ${bottom - top} to $targetHeight")
                                        lp.height = targetHeight
                                        if (lp is ViewGroup.MarginLayoutParams) {
                                            lp.bottomMargin = 0
                                        }
                                        v.layoutParams = lp
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Permanently hide original bar, its children, and divider line on every frame,
            // synchronize selected tab index, and hide compose bar when camera is active
            var preDrawCount = 0
            val preDrawHider = ViewTreeObserver.OnPreDrawListener {
                preDrawCount++
                // 1. Sync PopupWindow visibility & exact position with container, original bar, camera, and overlays
                val bar = findOriginalBar() ?: originalBar
                val va8 = if (va8Id != 0) decorView.findViewById<View>(va8Id) else null
                val isVa8Ancestor = if (va8 != null) {
                    var p: ViewParent? = originalBar.parent
                    var found = false
                    while (p != null) {
                        if (p === va8) { found = true; break }
                        p = p.parent
                    }
                    found
                } else false
                val isCameraActive = (va8 != null && isVa8Ancestor && !va8.isShown)
                val isOriginalBarHidden = (bar == null || !bar.isAttachedToWindow || bar.visibility == View.GONE ||
                    bar.translationY > 8f || kotlin.math.abs(bar.translationX) > 8f ||
                    container.translationY > 8f || kotlin.math.abs(container.translationX) > 8f ||
                    container.alpha < 0.1f)

                val cLoc = IntArray(2)
                container.getLocationOnScreen(cLoc)
                val targetW = container.width
                val targetX = cLoc[0]
                val targetY = cLoc[1] + container.height - container.paddingBottom - barHeightPx

                // Check if any visible overlay (Comments sheet, Story/Gallery picker, sub-pages) is drawn above container at the bottom bar area
                var isOccluded = false
                var occluderInfo = ""
                if (container.isShown && targetW > 0) {
                    val sibLoc = IntArray(2)
                    fun hasVisibleOverlap(v: View): Boolean {
                        if (v.visibility != View.VISIBLE || v.alpha <= 0.05f || v.width <= 0 || v.height <= 0 || v.id == vsbId) {
                            return false
                        }
                        v.getLocationOnScreen(sibLoc)
                        val l = sibLoc[0]
                        val t = sibLoc[1]
                        val r = l + v.width
                        val b = t + v.height
                        if (r <= targetX || l >= targetX + targetW || b <= targetY || t >= targetY + barHeightPx) {
                            return false
                        }
                        val bg = v.background
                        @Suppress("DEPRECATION")
                        if (bg != null && bg.opacity != android.graphics.PixelFormat.TRANSPARENT) {
                            val entry = try { res.getResourceEntryName(v.id) } catch (_: Throwable) { "${v.id}" }
                            occluderInfo = "${v.javaClass.simpleName}:$entry($l,$t,$r,$b)"
                            return true
                        }
                        if (v !is ViewGroup) {
                            val entry = try { res.getResourceEntryName(v.id) } catch (_: Throwable) { "${v.id}" }
                            occluderInfo = "${v.javaClass.simpleName}:$entry($l,$t,$r,$b)"
                            return true
                        }
                        for (ci in 0 until v.childCount) {
                            if (hasVisibleOverlap(v.getChildAt(ci))) return true
                        }
                        return false
                    }

                    var cur: View = container
                    var p = cur.parent as? ViewGroup
                    while (p != null && !isOccluded) {
                        val idx = p.indexOfChild(cur)
                        if (idx >= 0) {
                            for (si in idx + 1 until p.childCount) {
                                if (hasVisibleOverlap(p.getChildAt(si))) {
                                    isOccluded = true
                                    break
                                }
                            }
                        }
                        if (p === decorView) break
                        cur = p
                        p = p.parent as? ViewGroup
                    }
                }

                val shouldShowBar = container.isShown && !isCameraActive && !isOriginalBarHidden && !isOccluded &&
                    activity.hasWindowFocus() && !activity.isFinishing && !activity.isDestroyed
                if (!shouldShowBar && preDrawCount % 60 == 1) {
                    Log.i(TAG, "HideBar: shown=${container.isShown} cam=$isCameraActive origHidden=$isOriginalBarHidden(vis=${bar?.visibility},ty=${bar?.translationY},cty=${container.translationY},ca=${container.alpha}) occluded=$isOccluded[$occluderInfo] focus=${activity.hasWindowFocus()}")
                }
                val pw = popupWindow
                if (pw != null) {
                    if (!shouldShowBar) {
                        if (pw.isShowing) {
                            try { pw.dismiss() } catch (_: Throwable) {}
                        }
                        if (activeComposeView?.visibility != View.GONE) {
                            activeComposeView?.visibility = View.GONE
                        }
                    } else {
                        if (activeComposeView?.visibility != View.VISIBLE) {
                            activeComposeView?.visibility = View.VISIBLE
                        }
                        if (targetW > 0 && decorView.windowToken != null) {
                            if (!pw.isShowing) {
                                try {
                                    pw.width = targetW
                                    pw.height = barHeightPx
                                    pw.showAtLocation(decorView, Gravity.TOP or Gravity.START, targetX, targetY)
                                    lastPopupX = targetX
                                    lastPopupY = targetY
                                    lastPopupW = targetW
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed to show PopupWindow", t)
                                }
                            } else if (targetX != lastPopupX || targetY != lastPopupY || targetW != lastPopupW) {
                                try {
                                    pw.update(targetX, targetY, targetW, barHeightPx)
                                    lastPopupX = targetX
                                    lastPopupY = targetY
                                    lastPopupW = targetW
                                } catch (_: Throwable) {}
                            }
                        }
                    }
                }

                // 2. Keep video wrapper expanded to full height so video flows behind navbar
                if (wrapperId != 0) {
                    val wrapper = decorView.findViewById<View>(wrapperId)
                    if (wrapper != null) {
                        val parent = wrapper.parent as? View
                        val targetHeight = parent?.height ?: 0
                        if (targetHeight > 0 && wrapper.height != targetHeight) {
                            val lp = wrapper.layoutParams
                            if (lp != null && lp.height != targetHeight) {
                                lp.height = targetHeight
                                if (lp is ViewGroup.MarginLayoutParams) {
                                    lp.bottomMargin = 0
                                }
                                wrapper.layoutParams = lp
                            }
                        }
                    }
                }

                // 3. Remove bottom spacer inside viewpager_container so video wrapper fills 100% height
                val vpc = if (vpcId != 0) decorView.findViewById<ViewGroup>(vpcId) else null
                if (vpc != null && vpc.childCount >= 4) {
                    val child3 = vpc.getChildAt(3)
                    if (child3 != null) {
                        child3.visibility = View.GONE
                        val clp = child3.layoutParams
                        if (clp != null) {
                            clp.height = 0
                            child3.layoutParams = clp
                        }
                        vpc.removeView(child3)
                        vpc.requestLayout()
                        Log.i(TAG, "Removed child 3 spacer from vpc and requested layout")
                    }
                }

                // 4. Collapse bottom spacers dw4 and xt7 across all tabs (Home + Friends) so content flows to screen bottom
                val decorW = decorView.width
                val decorH = decorView.height
                fun collapseAllById(root: View, targetId: Int) {
                    if (targetId == 0) return
                    if (root.id == targetId && root.javaClass == View::class.java &&
                        (decorW <= 0 || root.width >= decorW / 2) && root.height in 60..220
                    ) {
                        if (root.visibility != View.GONE) root.visibility = View.GONE
                        val lp = root.layoutParams
                        if (lp != null && lp.height != 0) {
                            lp.height = 0
                            root.layoutParams = lp
                        }
                    }
                    if (root is ViewGroup) {
                        for (ci in 0 until root.childCount) {
                            collapseAllById(root.getChildAt(ci), targetId)
                        }
                    }
                }
                collapseAllById(decorView, dw4Id)
                collapseAllById(decorView, xt7Id)
                if (wrapperId != 0) {
                    val wrapper = decorView.findViewById<ViewGroup>(wrapperId)
                    if (wrapper != null) {
                        // Reset any excessive padding on top_widget_container
                        if (topWidgetId != 0) {
                            val tw = wrapper.findViewById<View>(topWidgetId)
                            if (tw != null && tw.paddingBottom != 0) {
                                tw.setPadding(tw.paddingLeft, tw.paddingTop, tw.paddingRight, 0)
                            }
                        }
                    }
                }

                // 4b. Collapse bottom spacers cj3 (Inbox), cpb/wxz (Friends), ctn (47.9.3), and any structural bar-sized bottom spacer
                collapseAllById(decorView, cj3Id)
                collapseAllById(decorView, cpbId)
                collapseAllById(decorView, wxzId)
                collapseAllById(decorView, ctnId)
                if (decorW > 0 && decorH > 0) {
                    val tmpLoc = IntArray(2)
                    fun collapseBottomSpacers(root: View) {
                        if (root.javaClass == View::class.java &&
                            root.visibility == View.VISIBLE &&
                            root.width >= decorW / 2 &&
                            root.height in 80..220
                        ) {
                            root.getLocationOnScreen(tmpLoc)
                            if (tmpLoc[1] >= decorH - 260 && tmpLoc[1] + root.height <= decorH - 10) {
                                root.visibility = View.GONE
                                root.layoutParams?.let { lp ->
                                    if (lp.height != 0) {
                                        lp.height = 0
                                        root.layoutParams = lp
                                    }
                                }
                            }
                        }
                        if (root is ViewGroup) {
                            for (ci in 0 until root.childCount) {
                                collapseBottomSpacers(root.getChildAt(ci))
                            }
                        }
                    }
                    collapseBottomSpacers(decorView)
                }

                // 4c. Remove extra bottomMargin on Story progress bar containers (lkd/lkk) so Story UI sits right above the bar
                for (storyBarId in intArrayOf(lkdId, lkkId)) {
                    if (storyBarId != 0) {
                        decorView.findViewById<View>(storyBarId)?.let { storyBar ->
                            if (storyBar.height in 1..120) {
                                val lp = storyBar.layoutParams as? ViewGroup.MarginLayoutParams
                                if (lp != null && lp.bottomMargin != 0) {
                                    lp.bottomMargin = 0
                                    storyBar.layoutParams = lp
                                }
                            }
                        }
                    }
                }

                // 4d. Offset all widget_container instances above the floating navbar via bottomMargin
                if (widgetContainerId != 0) {
                    val density = activity.resources.displayMetrics.density
                    val normalMargin = ((configState.value.bottomOffsetDp + 16f) * density).toInt()
                    fun applyWidgetMargin(root: View) {
                        if (root.id == widgetContainerId) {
                            val tw = if (topWidgetId != 0) root.findViewById<View>(topWidgetId) else null
                            val internalGap = if (root.height > 0 && tw != null && tw.bottom > 0) {
                                (root.height - tw.bottom).coerceAtLeast(0)
                            } else {
                                0
                            }
                            val targetWidgetMargin = (normalMargin - internalGap).coerceAtLeast(0)
                            val lp = root.layoutParams as? ViewGroup.MarginLayoutParams
                            if (lp != null && lp.bottomMargin != targetWidgetMargin) {
                                lp.bottomMargin = targetWidgetMargin
                                root.layoutParams = lp
                            }
                        }
                        if (root is ViewGroup) {
                            for (ci in 0 until root.childCount) {
                                applyWidgetMargin(root.getChildAt(ci))
                            }
                        }
                    }
                    applyWidgetMargin(decorView)
                }

                // 5. Lift video_seek_bar above the floating liquid navbar and host AndroidLiquidGlass LiquidSlider over it
                val vsb = if (vsbId != 0) container.findViewById<View>(vsbId) else null
                var seekShown = false
                if (vsb != null) {
                    val density = activity.resources.displayMetrics.density
                    val lp = vsb.layoutParams as? ViewGroup.MarginLayoutParams
                    val targetMargin = ((configState.value.bottomOffsetDp + 4f) * density).toInt()
                    if (lp != null && lp.bottomMargin != targetMargin) {
                        lp.bottomMargin = targetMargin
                        vsb.layoutParams = lp
                    }
                    if (activeComposeView != null && container.indexOfChild(vsb) < container.indexOfChild(activeComposeView)) {
                        vsb.bringToFront()
                    }
                    fun findSeekBar(v: View): android.widget.SeekBar? {
                        if (v is android.widget.SeekBar) return v
                        if (v is ViewGroup) {
                            for (ci in 0 until v.childCount) {
                                findSeekBar(v.getChildAt(ci))?.let { return it }
                            }
                        }
                        return null
                    }
                    val seekBar = findSeekBar(vsb)
                    activeSeekBar = seekBar
                    activeVsb = vsb
                    if (hookedDecorTouchClassName != decorView.javaClass.name) {
                        hookedDecorTouchClassName = decorView.javaClass.name
                        val decorTouchMethod = findMethodInHierarchy(decorView.javaClass, "dispatchTouchEvent", MotionEvent::class.java)
                        if (decorTouchMethod != null) {
                            val touchSlop = android.view.ViewConfiguration.get(activity).scaledTouchSlop
                            val hitPadPx = 28f * activity.resources.displayMetrics.density
                            var downX = 0f
                            var downY = 0f
                            var trackingSeekTouch = false
                            XposedBridge.hookMethod(decorTouchMethod, object : XC_MethodHook() {
                                override fun beforeHookedMethod(param: MethodHookParam) {
                                    if (param.thisObject !== decorView) return
                                    val ev = param.args[0] as? MotionEvent ?: return
                                    when (ev.actionMasked) {
                                        MotionEvent.ACTION_DOWN -> {
                                            val v = activeVsb
                                            val sb = activeSeekBar
                                            if (v != null && v.isShown && sb != null && sb.max > 0) {
                                                val loc = IntArray(2)
                                                v.getLocationOnScreen(loc)
                                                val rx = ev.rawX
                                                val ry = ev.rawY
                                                if (rx >= loc[0] - hitPadPx && rx <= loc[0] + v.width + hitPadPx &&
                                                    ry >= loc[1] - hitPadPx && ry <= loc[1] + v.height + hitPadPx
                                                ) {
                                                    downX = rx
                                                    downY = ry
                                                    trackingSeekTouch = true
                                                    isSeekPressedState.value = true
                                                }
                                            }
                                        }
                                        MotionEvent.ACTION_MOVE -> {
                                            if (trackingSeekTouch) {
                                                val dx = kotlin.math.abs(ev.rawX - downX)
                                                val dy = kotlin.math.abs(ev.rawY - downY)
                                                if (dy > touchSlop && dy > dx * 1.5f) {
                                                    trackingSeekTouch = false
                                                    isSeekPressedState.value = false
                                                } else {
                                                    if (!isSeekPressedState.value) {
                                                        isSeekPressedState.value = true
                                                    }
                                                    activeSeekBar?.let { sb ->
                                                        if (sb.max > 0) {
                                                            seekProgressState.floatValue = (sb.progress.toFloat() / sb.max.toFloat()).coerceIn(0f, 1f)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                            if (trackingSeekTouch || isSeekPressedState.value) {
                                                trackingSeekTouch = false
                                                isSeekPressedState.value = false
                                            }
                                        }
                                    }
                                }
                            })
                            Log.i(TAG, "Hooked decorView.dispatchTouchEvent for seekbar press tracking")
                        }
                    }
                    if (shouldShowBar && configState.value.liquidSeekBar && vsb.isShown && seekBar != null && seekBar.max > 0) {
                        seekShown = true
                        if (vsb.alpha != 0f) vsb.alpha = 0f
                        val frac = (seekBar.progress.toFloat() / seekBar.max.toFloat()).coerceIn(0f, 1f)
                        if (kotlin.math.abs(seekProgressState.floatValue - frac) > 0.001f) {
                            seekProgressState.floatValue = frac
                        }
                        val vsbLoc = IntArray(2)
                        vsb.getLocationOnScreen(vsbLoc)
                        val seekTargetY = vsbLoc[1] + (vsb.height - seekBarHeightPx) / 2
                        val spw = seekPopupWindow
                        if (spw != null && targetW > 0 && decorView.windowToken != null) {
                            if (seekComposeView?.visibility != View.VISIBLE) {
                                seekComposeView?.visibility = View.VISIBLE
                            }
                            if (!spw.isShowing) {
                                try {
                                    spw.width = targetW
                                    spw.height = seekBarHeightPx
                                    spw.showAtLocation(decorView, Gravity.TOP or Gravity.START, targetX, seekTargetY)
                                    lastSeekPopupX = targetX
                                    lastSeekPopupY = seekTargetY
                                    lastSeekPopupW = targetW
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Failed to show seekPopupWindow", t)
                                }
                            } else if (targetX != lastSeekPopupX || seekTargetY != lastSeekPopupY || targetW != lastSeekPopupW) {
                                try {
                                    spw.update(targetX, seekTargetY, targetW, seekBarHeightPx)
                                    lastSeekPopupX = targetX
                                    lastSeekPopupY = seekTargetY
                                    lastSeekPopupW = targetW
                                } catch (_: Throwable) {}
                            }
                        }
                    } else {
                        if (vsb.alpha != 1f) vsb.alpha = 1f
                    }
                }
                if (!seekShown) {
                    if (isSeekPressedState.value) {
                        Log.i(TAG, "!seekShown reset pressed=false")
                        isSeekPressedState.value = false
                    }
                    val spw = seekPopupWindow
                    if (spw != null && spw.isShowing) {
                        try { spw.dismiss() } catch (_: Throwable) {}
                    }
                    if (seekComposeView?.visibility != View.GONE) {
                        seekComposeView?.visibility = View.GONE
                    }
                }

                // 6. Host Liquid Glass Top Bar (Following / For You + Search) in topPopupWindow
                fun findSearchBtnStructural(root: View): View? {
                    if (root is android.widget.ImageView &&
                        root.isShown &&
                        root.isClickable &&
                        root.contentDescription?.toString()?.equals("Search", ignoreCase = true) == true
                    ) {
                        val loc = IntArray(2)
                        root.getLocationOnScreen(loc)
                        if (loc[1] in 0..400) return root
                    }
                    if (root is ViewGroup) {
                        for (ci in 0 until root.childCount) {
                            findSearchBtnStructural(root.getChildAt(ci))?.let { return it }
                        }
                    }
                    return null
                }
                fun isValidSearchBtn(v: View?): Boolean =
                    v != null && v.isShown && v.contentDescription?.toString()?.equals("Search", ignoreCase = true) == true

                val searchBtn = (if (kgdId != 0) decorView.findViewById<View>(kgdId)?.takeIf { isValidSearchBtn(it) } else null)
                    ?: (if (kayId != 0) decorView.findViewById<View>(kayId)?.takeIf { isValidSearchBtn(it) } else null)
                    ?: findSearchBtnStructural(decorView)
                val topParent = searchBtn?.parent as? ViewGroup
                fun findHsv(v: View): android.widget.HorizontalScrollView? {
                    if (v is android.widget.HorizontalScrollView) return v
                    if (v is ViewGroup) {
                        for (ci in 0 until v.childCount) {
                            findHsv(v.getChildAt(ci))?.let { return it }
                        }
                    }
                    return null
                }
                val hsv = topParent?.let { findHsv(it) }
                val tabStrip = hsv?.getChildAt(0) as? ViewGroup
                activeTopTabStrip = tabStrip
                activeSearchBtn = searchBtn

                val showTopBar = shouldShowBar && configState.value.liquidTopBar &&
                    selectedTabIndex.intValue == 0 &&
                    searchBtn != null && searchBtn.isShown &&
                    hsv != null && hsv.isShown &&
                    tabStrip != null && tabStrip.childCount > 0

                if (showTopBar && hsv != null && searchBtn != null && tabStrip != null) {
                    if (hsv.alpha != 0f) hsv.alpha = 0f
                    if (searchBtn.alpha != 0f) searchBtn.alpha = 0f

                    val titles = ArrayList<String>(tabStrip.childCount)
                    var selTop = 0
                    for (ti in 0 until tabStrip.childCount) {
                        val tab = tabStrip.getChildAt(ti)
                        val tv = tab.findViewById<android.widget.TextView>(android.R.id.text1)
                        titles.add(tv?.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: "Tab ${ti + 1}")
                        if (tab.isSelected) selTop = ti
                    }
                    if (topTabTitles.value != titles) {
                        topTabTitles.value = titles
                    }
                    if (selectedTopTabIndex.intValue != selTop) {
                        selectedTopTabIndex.intValue = selTop
                    }

                    val hsvLoc = IntArray(2)
                    hsv.getLocationOnScreen(hsvLoc)
                    val topTargetY = hsvLoc[1] + ((hsv.height - topBarHeightPx) / 2).coerceAtLeast(0)
                    val tpw = topPopupWindow
                    if (tpw != null && targetW > 0 && decorView.windowToken != null) {
                        if (topComposeView?.visibility != View.VISIBLE) {
                            topComposeView?.visibility = View.VISIBLE
                        }
                        if (!tpw.isShowing) {
                            try {
                                tpw.width = targetW
                                tpw.height = topBarHeightPx
                                tpw.showAtLocation(decorView, Gravity.TOP or Gravity.START, targetX, topTargetY)
                                lastTopPopupX = targetX
                                lastTopPopupY = topTargetY
                                lastTopPopupW = targetW
                            } catch (t: Throwable) {
                                Log.e(TAG, "Failed to show topPopupWindow", t)
                            }
                        } else if (targetX != lastTopPopupX || topTargetY != lastTopPopupY || targetW != lastTopPopupW) {
                            try {
                                tpw.update(targetX, topTargetY, targetW, topBarHeightPx)
                                lastTopPopupX = targetX
                                lastTopPopupY = topTargetY
                                lastTopPopupW = targetW
                            } catch (_: Throwable) {}
                        }
                    }
                } else {
                    if (hsv != null && hsv.alpha == 0f) hsv.alpha = 1f
                    if (searchBtn != null && searchBtn.alpha == 0f) searchBtn.alpha = 1f
                    val tpw = topPopupWindow
                    if (tpw != null && tpw.isShowing) {
                        try { tpw.dismiss() } catch (_: Throwable) {}
                    }
                    if (topComposeView?.visibility != View.GONE) {
                        topComposeView?.visibility = View.GONE
                    }
                }

                // 3. Keep original TikTok bar invisible (preserve View.GONE so we detect when TikTok hides it), zero its layout height, and sync tab selection
                if (bar != null) {
                    if (bar.visibility == View.VISIBLE) bar.visibility = View.INVISIBLE
                    if (bar.alpha != 0f) bar.alpha = 0f
                    bar.setOnTouchListener { _, _ -> true }
                    val lp = bar.layoutParams
                    if (lp != null && lp.height != 0) {
                        lp.height = 0
                        bar.layoutParams = lp
                    }

                    // Extract & update authentic TikTok tab drawables
                    if (bar.childCount >= 5) {
                        fun findImageView(v: View): ImageView? {
                            if (v is ImageView && v.javaClass == ImageView::class.java && v.visibility == View.VISIBLE && v.drawable != null) return v
                            if (v is ViewGroup) {
                                for (ci in 0 until v.childCount) {
                                    val found = findImageView(v.getChildAt(ci))
                                    if (found != null) return found
                                }
                            }
                            if (v is ImageView && v.visibility == View.VISIBLE && v.drawable != null) return v
                            return null
                        }
                        for (i in 0 until 5) {
                            val child = bar.getChildAt(i) ?: continue
                            val iv = findImageView(child) ?: continue
                            val d = iv.drawable ?: continue
                            val sel = child.isSelected
                            if (d !== lastDrawables[i] || sel != lastSelected[i] || tabBitmaps[i] == null) {
                                lastDrawables[i] = d
                                lastSelected[i] = sel
                                try {
                                    val bmp = if (d is BitmapDrawable && d.bitmap != null && !d.bitmap.isRecycled) {
                                        d.bitmap
                                    } else {
                                        val w = d.intrinsicWidth.takeIf { it > 0 } ?: 126
                                        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 126
                                        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                        val c = android.graphics.Canvas(b)
                                        val oldBounds = d.bounds
                                        d.setBounds(0, 0, w, h)
                                        d.draw(c)
                                        d.bounds = oldBounds
                                        b
                                    }
                                    if (tabBitmaps[i] != bmp) {
                                        tabBitmaps[i] = bmp
                                    }
                                } catch (e: Throwable) {
                                    Log.e(TAG, "Failed to capture tab $i icon", e)
                                }
                            }
                        }
                    }

                    for (i in 0 until bar.childCount) {
                        val child = bar.getChildAt(i)
                        if (child != null) {
                            if (child.visibility != View.INVISIBLE) child.visibility = View.INVISIBLE
                            if (child.alpha != 0f) child.alpha = 0f
                            if (child.isSelected && selectedTabIndex.intValue != i) {
                                selectedTabIndex.intValue = i
                                isDarkTheme.value = updateIsDark()
                            }
                        }
                    }

                    // Extract Inbox (3) and Friends (1) notification counters / badges
                    fun extractTabBadge(tabView: View?): String? {
                        if (tabView == null) return null
                        var foundText: String? = null
                        var hasDot = false
                        fun walk(v: View) {
                            if (foundText != null) return
                            if (v.visibility == View.GONE) return
                            if (v !== tabView && v.visibility != View.VISIBLE) return
                            if (v is TextView && v.text?.isNotBlank() == true) {
                                val t = v.text.toString().trim()
                                if (t.length <= 5 && t.any { it.isDigit() }) {
                                    foundText = t
                                    return
                                }
                            }
                            if (v !== tabView && v !is ViewGroup && v !is TextView) {
                                if (v.width in 4..45 && v.height in 4..45 && (v.background != null || (v is ImageView && v.drawable != null))) {
                                    hasDot = true
                                }
                            }
                            if (v is ViewGroup) {
                                for (ci in 0 until v.childCount) walk(v.getChildAt(ci))
                            }
                        }
                        walk(tabView)
                        if (foundText != null) return foundText
                        val desc = tabView.contentDescription?.toString()
                        if (!desc.isNullOrEmpty()) {
                            val numMatch = Regex("(\\d+\\+?)").find(desc)
                            if (numMatch != null) return numMatch.value
                        }
                        return if (hasDot) "DOT" else null
                    }
                    if (bar.childCount >= 5) {
                        val newFriendsBadge = extractTabBadge(bar.getChildAt(1))
                        val newInboxBadge = extractTabBadge(bar.getChildAt(3))
                        if (friendsBadgeState.value != newFriendsBadge) {
                            friendsBadgeState.value = newFriendsBadge
                            Log.i(TAG, "Friends badge updated: '$newFriendsBadge'")
                        }
                        if (inboxBadgeState.value != newInboxBadge) {
                            inboxBadgeState.value = newInboxBadge
                            Log.i(TAG, "Inbox badge updated: '$newInboxBadge'")
                        }
                    }
                }
                if (cphId != 0) {
                    val divider = decorView.findViewById<View>(cphId)
                    if (divider != null && (divider.parent === container || divider.parent === bar || (divider.height in 1..12 && (decorW <= 0 || divider.width >= decorW / 2)))) {
                        if (divider.visibility != View.GONE) divider.visibility = View.GONE
                        if (divider.alpha != 0f) divider.alpha = 0f
                        val dlp = divider.layoutParams
                        if (dlp != null && dlp.height != 0) {
                            dlp.height = 0
                            divider.layoutParams = dlp
                        }
                    }
                }
                true
            }
            decorView.viewTreeObserver.addOnPreDrawListener(preDrawHider)

            val backgroundView = if (yj_Id != 0) container.findViewById<View>(yj_Id) else container.getChildAt(0)

            val moduleLifecycle = activeLifecycleOwner ?: ModuleLifecycleOwner().also {
                activeLifecycleOwner = it
            }

            // Set lifecycle owners across the tree to ensure resolution never fails
            decorView.setViewTreeLifecycleOwner(moduleLifecycle)
            decorView.setViewTreeSavedStateRegistryOwner(moduleLifecycle)
            container.setViewTreeLifecycleOwner(moduleLifecycle)
            container.setViewTreeSavedStateRegistryOwner(moduleLifecycle)

            val backgroundSnapshot = mutableStateOf<Bitmap?>(null, neverEqualPolicy())
            val mainHandler = Handler(Looper.getMainLooper())
            var bmpA: Bitmap? = null
            var bmpB: Bitmap? = null
            var blendBitmap: Bitmap? = null
            var blendCanvas: android.graphics.Canvas? = null
            var isFirstBlendFrame = true
            val smoothPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                isDither = true
            }
            var uiBmp: Bitmap? = null
            val svBmps = arrayOfNulls<Bitmap>(2)
            var useA = true
            var isCopying = false
            var lastCopyTime = 0L

            fun collectIntersectingSurfaceViews(
                root: View,
                barScreenRect: Rect,
                out: MutableList<Triple<SurfaceView, Rect, Rect>>
            ) {
                if (!root.isShown) return
                if (root is SurfaceView && root.width > 0 && root.height > 0 && root.holder.surface?.isValid == true) {
                    val svLoc = IntArray(2)
                    root.getLocationOnScreen(svLoc)
                    val svLeft = svLoc[0]
                    val svTop = svLoc[1]
                    val svRight = svLeft + root.width
                    val svBottom = svTop + root.height
                    val iLeft = maxOf(barScreenRect.left, svLeft)
                    val iTop = maxOf(barScreenRect.top, svTop)
                    val iRight = minOf(barScreenRect.right, svRight)
                    val iBottom = minOf(barScreenRect.bottom, svBottom)
                    if (iLeft < iRight && iTop < iBottom && out.size < 2) {
                        val sf = root.holder.surfaceFrame
                        val sx = if (sf != null && sf.width() > 0) sf.width().toFloat() / root.width else 1f
                        val sy = if (sf != null && sf.height() > 0) sf.height().toFloat() / root.height else 1f
                        val srcW = if (sf != null && sf.width() > 0) sf.width() else root.width
                        val srcH = if (sf != null && sf.height() > 0) sf.height() else root.height
                        val svSrc = Rect(
                            ((iLeft - svLeft) * sx).toInt().coerceIn(0, (srcW - 1).coerceAtLeast(0)),
                            ((iTop - svTop) * sy).toInt().coerceIn(0, (srcH - 1).coerceAtLeast(0)),
                            ((iRight - svLeft) * sx).toInt().coerceIn(1, srcW.coerceAtLeast(1)),
                            ((iBottom - svTop) * sy).toInt().coerceIn(1, srcH.coerceAtLeast(1))
                        )
                        val dstRect = Rect(
                            iLeft - barScreenRect.left,
                            iTop - barScreenRect.top,
                            iRight - barScreenRect.left,
                            iBottom - barScreenRect.top
                        )
                        if (svSrc.width() > 0 && svSrc.height() > 0 && dstRect.width() > 0 && dstRect.height() > 0) {
                            out.add(Triple(root, svSrc, dstRect))
                        }
                    }
                    return
                }
                if (root is ViewGroup) {
                    for (ci in 0 until root.childCount) {
                        collectIntersectingSurfaceViews(root.getChildAt(ci), barScreenRect, out)
                    }
                }
            }

            lateinit var composeView: ComposeView

            var frameCount = 0
            val frameCallback = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) {
                    val window = activity.window
                    val isAttached = composeView.isAttachedToWindow
                    val isVisible = composeView.visibility == View.VISIBLE && composeView.isShown
                    if (frameCount++ % 60 == 0) {
                        Log.i(TAG, "FrameCheck: attached=$isAttached, shown=${composeView.isShown}, w=${composeView.width}, h=${composeView.height}, isCopying=$isCopying")
                    }
                    if (isAttached && isVisible && window != null && composeView.width > 0 && composeView.height > 0) {
                        val now = SystemClock.uptimeMillis()
                        if (now - lastConfigCheckTime >= 500) {
                            lastConfigCheckTime = now
                            refreshConfig(activity, force = false)
                        }
                        if (selectedTabIndex.intValue == 4 && configState.value.useProfileAvatar && now - lastAvatarCheckTime >= 1000) {
                            lastAvatarCheckTime = now
                            val avatarBtn = if (bq4Id != 0) decorView.findViewById<View>(bq4Id) else null
                            if (avatarBtn is ViewGroup && avatarBtn.isShown) {
                                var bestIv: ImageView? = null
                                var bestArea = 0
                                fun findLargestIv(v: View) {
                                    if (v is ImageView && v.isShown && v.width >= 80 && v.height >= 80 && v.drawable != null) {
                                        val area = v.width * v.height
                                        // Child 0 is story ring (wider), child 1 is the actual profile photo (252x252)
                                        if (v.contentDescription?.toString()?.contains("storyring") != true && area >= bestArea) {
                                            bestArea = area
                                            bestIv = v
                                        }
                                    }
                                    if (v is ViewGroup) {
                                        for (ci in 0 until v.childCount) findLargestIv(v.getChildAt(ci))
                                    }
                                }
                                findLargestIv(avatarBtn)
                                val iv = bestIv
                                val d = iv?.drawable
                                if (iv != null && d != null && d !== lastAvatarDrawable) {
                                    lastAvatarDrawable = d
                                    try {
                                        val outBmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
                                        val c = android.graphics.Canvas(outBmp)
                                        c.scale(128f / iv.width, 128f / iv.height)
                                        iv.draw(c)
                                        profileAvatarBitmap.value = outBmp
                                        Thread {
                                            try {
                                                java.io.FileOutputStream(avatarCacheFile).use { fos ->
                                                    outBmp.compress(Bitmap.CompressFormat.PNG, 95, fos)
                                                }
                                            } catch (_: Throwable) {}
                                        }.start()
                                    } catch (t: Throwable) {
                                        Log.w(TAG, "Failed to capture profile avatar", t)
                                    }
                                }
                            }
                        }
                        if (now - lastCopyTime >= 16 && !isCopying) {
                            val decorW = window.decorView.width
                            val decorH = window.decorView.height
                            val density = activity.resources.displayMetrics.density
                            val compLoc = IntArray(2)
                            val decLoc = IntArray(2)
                            composeView.getLocationOnScreen(compLoc)
                            window.decorView.getLocationOnScreen(decLoc)

                            // Sample EXACTLY what is behind the capsule in activity.window (no self-capture because composeView is in a separate PopupWindow)
                            val padH = (16f * density).toInt()
                            val padTop = (6f * density).toInt()
                            val padBot = (4f * density).toInt()
                            val navLeft = (compLoc[0] - decLoc[0] + padH).coerceIn(0, (decorW - 1).coerceAtLeast(0))
                            val navTop = (compLoc[1] - decLoc[1] + padTop).coerceIn(0, (decorH - 1).coerceAtLeast(0))
                            val navRight = (compLoc[0] - decLoc[0] + composeView.width - padH).coerceIn(navLeft + 1, decorW.coerceAtLeast(navLeft + 1))
                            val navBottom = (compLoc[1] - decLoc[1] + composeView.height - padBot).coerceIn(navTop + 1, decorH.coerceAtLeast(navTop + 1))
                            val rect = Rect(navLeft, navTop, navRight, navBottom)

                            if (rect.width() > 0 && rect.height() > 0 && decorW > 0 && decorH > 0) {
                                isCopying = true
                                lastCopyTime = now
                                val targetBmp = if (useA) {
                                    bmpA?.takeIf { it.width == rect.width() && it.height == rect.height() }
                                        ?: Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.RGB_565).also { bmpA = it }
                                } else {
                                    bmpB?.takeIf { it.width == rect.width() && it.height == rect.height() }
                                        ?: Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.RGB_565).also { bmpB = it }
                                }
                                val curUiBmp = uiBmp?.takeIf { it.width == rect.width() && it.height == rect.height() }
                                    ?: Bitmap.createBitmap(rect.width(), rect.height(), Bitmap.Config.ARGB_8888).also { uiBmp = it }

                                val barScreenRect = Rect(
                                    compLoc[0] + padH,
                                    compLoc[1] + padTop,
                                    compLoc[0] + composeView.width - padH,
                                    compLoc[1] + composeView.height - padBot
                                )
                                val svTargets = ArrayList<Triple<SurfaceView, Rect, Rect>>(2)
                                val searchRoot = (if (wrapperId != 0) decorView.findViewById<View>(wrapperId) else null) ?: decorView
                                collectIntersectingSurfaceViews(searchRoot, barScreenRect, svTargets)

                                val totalRequests = 1 + svTargets.size
                                val remaining = java.util.concurrent.atomic.AtomicInteger(totalRequests)
                                val uiOk = java.util.concurrent.atomic.AtomicBoolean(false)
                                val svResults = arrayOfNulls<Pair<Bitmap, Rect>>(svTargets.size)

                                fun finishBatchIfDone() {
                                    if (remaining.decrementAndGet() == 0) {
                                        val c = android.graphics.Canvas(targetBmp)
                                        c.drawColor(android.graphics.Color.BLACK)
                                        for (resPair in svResults) {
                                            if (resPair != null) {
                                                c.drawBitmap(resPair.first, null, resPair.second, smoothPaint)
                                            }
                                        }
                                        if (uiOk.get()) {
                                            c.drawBitmap(curUiBmp, 0f, 0f, null)
                                        }
                                        isCopying = false
                                        backgroundSnapshot.value = targetBmp
                                        isDarkTheme.value = updateIsDark()
                                        useA = !useA
                                    }
                                }

                                try {
                                    PixelCopy.request(window, rect, curUiBmp, { result ->
                                        if (result == PixelCopy.SUCCESS) {
                                            uiOk.set(true)
                                        }
                                        finishBatchIfDone()
                                    }, mainHandler)
                                } catch (t: Throwable) {
                                    finishBatchIfDone()
                                }

                                for (idx in svTargets.indices) {
                                    val (sv, svSrc, dstRect) = svTargets[idx]
                                    val svBmp = svBmps[idx]?.takeIf { it.width == dstRect.width() && it.height == dstRect.height() }
                                        ?: Bitmap.createBitmap(dstRect.width(), dstRect.height(), Bitmap.Config.RGB_565).also { svBmps[idx] = it }
                                    try {
                                        PixelCopy.request(sv, svSrc, svBmp, { result ->
                                            if (result == PixelCopy.SUCCESS) {
                                                svResults[idx] = Pair(svBmp, dstRect)
                                            }
                                            finishBatchIfDone()
                                        }, mainHandler)
                                    } catch (t: Throwable) {
                                        finishBatchIfDone()
                                    }
                                }
                            }
                        }

                        val tcv = topComposeView
                        if (topPopupWindow?.isShowing == true && tcv != null && tcv.isAttachedToWindow && tcv.isShown && !isTopCopying && now - lastCopyTime >= 0) {
                            val decorW = window.decorView.width
                            val decorH = window.decorView.height
                            val density = activity.resources.displayMetrics.density
                            val topLoc = IntArray(2)
                            val decLoc = IntArray(2)
                            tcv.getLocationOnScreen(topLoc)
                            window.decorView.getLocationOnScreen(decLoc)

                            val padV = (4f * density).toInt()
                            val tLeft = (topLoc[0] - decLoc[0]).coerceIn(0, (decorW - 1).coerceAtLeast(0))
                            val tTop = (topLoc[1] - decLoc[1] + padV).coerceIn(0, (decorH - 1).coerceAtLeast(0))
                            val tRight = (topLoc[0] - decLoc[0] + tcv.width).coerceIn(tLeft + 1, decorW.coerceAtLeast(tLeft + 1))
                            val tBottom = (topLoc[1] - decLoc[1] + tcv.height - padV).coerceIn(tTop + 1, decorH.coerceAtLeast(tTop + 1))
                            val tRect = Rect(tLeft, tTop, tRight, tBottom)

                            if (tRect.width() > 0 && tRect.height() > 0 && decorW > 0 && decorH > 0) {
                                isTopCopying = true
                                val targetTopBmp = if (topUseA) {
                                    topBmpA?.takeIf { it.width == tRect.width() && it.height == tRect.height() }
                                        ?: Bitmap.createBitmap(tRect.width(), tRect.height(), Bitmap.Config.RGB_565).also { topBmpA = it }
                                } else {
                                    topBmpB?.takeIf { it.width == tRect.width() && it.height == tRect.height() }
                                        ?: Bitmap.createBitmap(tRect.width(), tRect.height(), Bitmap.Config.RGB_565).also { topBmpB = it }
                                }
                                val curTopUiBmp = topUiBmp?.takeIf { it.width == tRect.width() && it.height == tRect.height() }
                                    ?: Bitmap.createBitmap(tRect.width(), tRect.height(), Bitmap.Config.ARGB_8888).also { topUiBmp = it }

                                val topScreenRect = Rect(
                                    topLoc[0],
                                    topLoc[1] + padV,
                                    topLoc[0] + tcv.width,
                                    topLoc[1] + tcv.height - padV
                                )
                                val topSvTargets = ArrayList<Triple<SurfaceView, Rect, Rect>>(2)
                                val searchRoot = (if (wrapperId != 0) decorView.findViewById<View>(wrapperId) else null) ?: decorView
                                collectIntersectingSurfaceViews(searchRoot, topScreenRect, topSvTargets)

                                val topRemaining = java.util.concurrent.atomic.AtomicInteger(1 + topSvTargets.size)
                                val topUiOk = java.util.concurrent.atomic.AtomicBoolean(false)
                                val topSvResults = arrayOfNulls<Pair<Bitmap, Rect>>(topSvTargets.size)

                                fun finishTopBatchIfDone() {
                                    if (topRemaining.decrementAndGet() == 0) {
                                        val c = android.graphics.Canvas(targetTopBmp)
                                        c.drawColor(android.graphics.Color.BLACK)
                                        for (resPair in topSvResults) {
                                            if (resPair != null) {
                                                c.drawBitmap(resPair.first, null, resPair.second, smoothPaint)
                                            }
                                        }
                                        if (topUiOk.get()) {
                                            c.drawBitmap(curTopUiBmp, 0f, 0f, null)
                                        }
                                        isTopCopying = false
                                        topBackgroundSnapshot.value = targetTopBmp
                                        topUseA = !topUseA
                                    }
                                }

                                try {
                                    PixelCopy.request(window, tRect, curTopUiBmp, { result ->
                                        if (result == PixelCopy.SUCCESS) topUiOk.set(true)
                                        finishTopBatchIfDone()
                                    }, mainHandler)
                                } catch (_: Throwable) {
                                    finishTopBatchIfDone()
                                }

                                for (idx in topSvTargets.indices) {
                                    val (sv, svSrc, dstRect) = topSvTargets[idx]
                                    val svBmp = topSvBmps[idx]?.takeIf { it.width == dstRect.width() && it.height == dstRect.height() }
                                        ?: Bitmap.createBitmap(dstRect.width(), dstRect.height(), Bitmap.Config.RGB_565).also { topSvBmps[idx] = it }
                                    try {
                                        PixelCopy.request(sv, svSrc, svBmp, { result ->
                                            if (result == PixelCopy.SUCCESS) topSvResults[idx] = Pair(svBmp, dstRect)
                                            finishTopBatchIfDone()
                                        }, mainHandler)
                                    } catch (_: Throwable) {
                                        finishTopBatchIfDone()
                                    }
                                }
                            }
                        }

                        val scv = seekComposeView
                        if (seekPopupWindow?.isShowing == true && scv != null && scv.isAttachedToWindow && scv.isShown && !isSeekCopying && now - lastCopyTime >= 0) {
                            val decorW = window.decorView.width
                            val decorH = window.decorView.height
                            val density = activity.resources.displayMetrics.density
                            val sLoc = IntArray(2)
                            val decLoc = IntArray(2)
                            scv.getLocationOnScreen(sLoc)
                            window.decorView.getLocationOnScreen(decLoc)

                            val padH = (16f * density).toInt()
                            val sLeft = (sLoc[0] - decLoc[0] + padH).coerceIn(0, (decorW - 1).coerceAtLeast(0))
                            val sTop = (sLoc[1] - decLoc[1]).coerceIn(0, (decorH - 1).coerceAtLeast(0))
                            val sRight = (sLoc[0] - decLoc[0] + scv.width - padH).coerceIn(sLeft + 1, decorW.coerceAtLeast(sLeft + 1))
                            val sBottom = (sLoc[1] - decLoc[1] + scv.height).coerceIn(sTop + 1, decorH.coerceAtLeast(sTop + 1))
                            val sRect = Rect(sLeft, sTop, sRight, sBottom)

                            if (sRect.width() > 0 && sRect.height() > 0 && decorW > 0 && decorH > 0) {
                                isSeekCopying = true
                                val targetSeekBmp = if (seekUseA) {
                                    seekBmpA?.takeIf { it.width == sRect.width() && it.height == sRect.height() }
                                        ?: Bitmap.createBitmap(sRect.width(), sRect.height(), Bitmap.Config.RGB_565).also { seekBmpA = it }
                                } else {
                                    seekBmpB?.takeIf { it.width == sRect.width() && it.height == sRect.height() }
                                        ?: Bitmap.createBitmap(sRect.width(), sRect.height(), Bitmap.Config.RGB_565).also { seekBmpB = it }
                                }
                                val curSeekUiBmp = seekUiBmp?.takeIf { it.width == sRect.width() && it.height == sRect.height() }
                                    ?: Bitmap.createBitmap(sRect.width(), sRect.height(), Bitmap.Config.ARGB_8888).also { seekUiBmp = it }

                                val seekScreenRect = Rect(
                                    sLoc[0] + padH,
                                    sLoc[1],
                                    sLoc[0] + scv.width - padH,
                                    sLoc[1] + scv.height
                                )
                                val seekSvTargets = ArrayList<Triple<SurfaceView, Rect, Rect>>(2)
                                val searchRoot = (if (wrapperId != 0) decorView.findViewById<View>(wrapperId) else null) ?: decorView
                                collectIntersectingSurfaceViews(searchRoot, seekScreenRect, seekSvTargets)

                                val seekRemaining = java.util.concurrent.atomic.AtomicInteger(1 + seekSvTargets.size)
                                val seekUiOk = java.util.concurrent.atomic.AtomicBoolean(false)
                                val seekSvResults = arrayOfNulls<Pair<Bitmap, Rect>>(seekSvTargets.size)

                                fun finishSeekBatchIfDone() {
                                    if (seekRemaining.decrementAndGet() == 0) {
                                        val c = android.graphics.Canvas(targetSeekBmp)
                                        c.drawColor(android.graphics.Color.BLACK)
                                        for (resPair in seekSvResults) {
                                            if (resPair != null) {
                                                c.drawBitmap(resPair.first, null, resPair.second, smoothPaint)
                                            }
                                        }
                                        if (seekUiOk.get()) {
                                            c.drawBitmap(curSeekUiBmp, 0f, 0f, null)
                                        }
                                        isSeekCopying = false
                                        seekBackgroundSnapshot.value = targetSeekBmp
                                        seekUseA = !seekUseA
                                    }
                                }

                                try {
                                    PixelCopy.request(window, sRect, curSeekUiBmp, { result ->
                                        if (result == PixelCopy.SUCCESS) seekUiOk.set(true)
                                        finishSeekBatchIfDone()
                                    }, mainHandler)
                                } catch (_: Throwable) {
                                    finishSeekBatchIfDone()
                                }

                                for (idx in seekSvTargets.indices) {
                                    val (sv, svSrc, dstRect) = seekSvTargets[idx]
                                    val svBmp = seekSvBmps[idx]?.takeIf { it.width == dstRect.width() && it.height == dstRect.height() }
                                        ?: Bitmap.createBitmap(dstRect.width(), dstRect.height(), Bitmap.Config.RGB_565).also { seekSvBmps[idx] = it }
                                    try {
                                        PixelCopy.request(sv, svSrc, svBmp, { result ->
                                            if (result == PixelCopy.SUCCESS) seekSvResults[idx] = Pair(svBmp, dstRect)
                                            finishSeekBatchIfDone()
                                        }, mainHandler)
                                    } catch (_: Throwable) {
                                        finishSeekBatchIfDone()
                                    }
                                }
                            }
                        }
                    }
                    Choreographer.getInstance().postFrameCallback(this)
                }
            }

            composeView = ComposeView(activity).apply {
                tag = COMPOSE_VIEW_TAG
                setViewTreeLifecycleOwner(moduleLifecycle)
                setViewTreeSavedStateRegistryOwner(moduleLifecycle)

                // Disallow parent (ViewPager / Video player) from stealing touch / drag events
                setOnTouchListener { v, event ->
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN,
                        MotionEvent.ACTION_MOVE -> {
                            v.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                        MotionEvent.ACTION_UP,
                        MotionEvent.ACTION_CANCEL -> {
                            v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                    }
                    false
                }

                // Explicit recomposer bound to UI dispatcher
                val uiContext = AndroidUiDispatcher.CurrentThread
                val scope = CoroutineScope(uiContext + SupervisorJob())
                val recomposer = Recomposer(uiContext)
                scope.launch {
                    recomposer.runRecomposeAndApplyChanges()
                }
                setParentCompositionContext(recomposer)

                setContent {
                    LiquidTikTokBar(
                        backgroundSnapshot = { backgroundSnapshot.value },
                        tabIcons = { tabBitmaps.toList() },
                        profileAvatar = { profileAvatarBitmap.value },
                        selectedTabIndex = { selectedTabIndex.intValue },
                        onTabSelected = { index: Int ->
                            if (index == 1 && selectedTabIndex.intValue == 1) {
                                return@LiquidTikTokBar
                            }
                            selectedTabIndex.intValue = index
                            isDarkTheme.value = updateIsDark()
                            activity.runOnUiThread {
                                try {
                                    val target = getOriginalTab(index)
                                    var handled = target?.performClick() ?: false
                                    if (!handled && target != null) {
                                        handled = target.callOnClick()
                                    }
                                    if (!handled && target is ViewGroup) {
                                        for (ci in 0 until target.childCount) {
                                            val c = target.getChildAt(ci)
                                            if (c.hasOnClickListeners() && c.performClick()) {
                                                handled = true
                                                break
                                            }
                                        }
                                    }

                                    // Synthesize touch event only if click listeners didn't handle it
                                    if (!handled && target != null) {
                                        val now = SystemClock.uptimeMillis()
                                        val w = if (target.width > 0) target.width.toFloat() else 216f
                                        val h = if (target.height > 0) target.height.toFloat() else 100f
                                        val x = w / 2f
                                        val y = h / 2f
                                        val down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, x, y, 0)
                                        val up = MotionEvent.obtain(now, now + 30, MotionEvent.ACTION_UP, x, y, 0)
                                        target.dispatchTouchEvent(down)
                                        target.dispatchTouchEvent(up)
                                        down.recycle()
                                        up.recycle()
                                    }
                                } catch (t: Throwable) {
                                    Log.e(TAG, "Error switching tab to $index", t)
                                }
                            }
                        },
                        isDarkTheme = { isDarkTheme.value },
                        config = { configState.value },
                        inboxBadge = { inboxBadgeState.value },
                        friendsBadge = { friendsBadgeState.value }
                    )
                }
            }

            // Marker view inside container so attachLiquidBar isn't called twice
            val markerView = View(activity).apply {
                tag = COMPOSE_VIEW_TAG
                visibility = View.GONE
            }
            container.addView(markerView, FrameLayout.LayoutParams(0, 0))

            // Host ComposeView in a separate PopupWindow so PixelCopy on activity.window
            // captures EXACTLY what is behind the bar with zero self-capture feedback loop
            val pw = PopupWindow(
                composeView,
                if (container.width > 0) container.width else ViewGroup.LayoutParams.MATCH_PARENT,
                barHeightPx,
                false
            ).apply {
                isTouchable = true
                isClippingEnabled = false
                setBackgroundDrawable(null)
                animationStyle = 0
                windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            }
            popupWindow = pw
            activePopupWindow = pw
            activeComposeView = composeView

            if (container.isShown && decorView.windowToken != null && container.width > 0) {
                val cLoc = IntArray(2)
                container.getLocationOnScreen(cLoc)
                val targetX = cLoc[0]
                val targetY = cLoc[1] + container.height - container.paddingBottom - barHeightPx
                pw.showAtLocation(decorView, Gravity.TOP or Gravity.START, targetX, targetY)
                lastPopupX = targetX
                lastPopupY = targetY
                lastPopupW = container.width
            }

            // Host Top Bar ComposeView in its own PopupWindow so PixelCopy captures video behind it
            val topCv = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(moduleLifecycle)
                setViewTreeSavedStateRegistryOwner(moduleLifecycle)
                val uiContext = AndroidUiDispatcher.CurrentThread
                val scope = CoroutineScope(uiContext + SupervisorJob())
                val recomposer = Recomposer(uiContext)
                scope.launch { recomposer.runRecomposeAndApplyChanges() }
                setParentCompositionContext(recomposer)
                setContent {
                    LiquidTikTokTopBar(
                        backgroundSnapshot = { topBackgroundSnapshot.value },
                        tabs = { topTabTitles.value },
                        selectedTabIndex = { selectedTopTabIndex.intValue },
                        onTabSelected = { index: Int ->
                            selectedTopTabIndex.intValue = index
                            activity.runOnUiThread {
                                try {
                                    activeTopTabStrip?.getChildAt(index)?.performClick()
                                } catch (_: Throwable) {}
                            }
                        },
                        onSearchClick = {
                            activity.runOnUiThread {
                                try {
                                    activeSearchBtn?.performClick()
                                } catch (_: Throwable) {}
                            }
                        },
                        isDarkTheme = { isDarkTheme.value },
                        config = { configState.value }
                    )
                }
            }
            topComposeView = topCv
            topPopupWindow = PopupWindow(
                topCv,
                if (container.width > 0) container.width else ViewGroup.LayoutParams.MATCH_PARENT,
                topBarHeightPx,
                false
            ).apply {
                isTouchable = true
                isClippingEnabled = false
                setBackgroundDrawable(null)
                animationStyle = 0
                windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            }

            // Host AndroidLiquidGlass LiquidSlider in its own PopupWindow over video_seek_bar
            // Host LiquidTikTokSeekBar as a pass-through glass overlay over TikTok's native video_seek_bar
            // while hooked SeekBar.onTouchEvent drives 1:1 progress & glass droplet press state
            val seekCv = ComposeView(activity).apply {
                setViewTreeLifecycleOwner(moduleLifecycle)
                setViewTreeSavedStateRegistryOwner(moduleLifecycle)
                val uiContext = AndroidUiDispatcher.CurrentThread
                val scope = CoroutineScope(uiContext + SupervisorJob())
                val recomposer = Recomposer(uiContext)
                scope.launch { recomposer.runRecomposeAndApplyChanges() }
                setParentCompositionContext(recomposer)
                setContent {
                    LiquidTikTokSeekBar(
                        backgroundSnapshot = { seekBackgroundSnapshot.value },
                        progress = { seekProgressState.floatValue },
                        isPressed = { isSeekPressedState.value },
                        isDarkTheme = { isDarkTheme.value }
                    )
                }
            }
            seekComposeView = seekCv
            seekPopupWindow = PopupWindow(
                seekCv,
                if (container.width > 0) container.width else ViewGroup.LayoutParams.MATCH_PARENT,
                seekBarHeightPx,
                false
            ).apply {
                isTouchable = false
                isClippingEnabled = false
                setBackgroundDrawable(null)
                animationStyle = 0
                windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
            }

            Choreographer.getInstance().postFrameCallback(frameCallback)
            XposedBridge.log("[$TAG] Successfully attached Liquid Glass Nav Bar (PopupWindow) to TikTok!")
            Log.i(TAG, "Successfully attached Liquid Glass Nav Bar (PopupWindow) to TikTok!")

        } catch (t: Throwable) {
            XposedBridge.log("[$TAG] Error attaching Liquid Glass: ${t.message}")
            Log.e(TAG, "Error attaching Liquid Glass", t)
        }
    }
}
