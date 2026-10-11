package com.kairo.app.feature.tasks.alarm

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AlarmOverlayManager {

    private const val TAG = "AlarmOverlayManager"

    @Volatile
    private var overlayView: View? = null
    private var windowManager: WindowManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeUpdateRunnable: Runnable? = null

    @SuppressLint("ClickableViewAccessibility")
    fun show(
        context: Context,
        taskId: String,
        title: String,
        notes: String?,
        priority: String,
        dueDate: String,
        dueTime: String,
        location: String?,
        attachment: String?
    ) {
        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Cannot show overlay: SYSTEM_ALERT_WINDOW (Display over other apps) not granted")
            return
        }

        mainHandler.post {
            try {
                if (overlayView != null) {
                    dismiss()
                }

                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return@post
                windowManager = wm

                val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                @Suppress("DEPRECATION")
                val flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    layoutType,
                    flags,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                val dp = context.resources.displayMetrics.density

                // Root Container - Simple Pure Black Background
                val root = FrameLayout(context).apply {
                    setBackgroundColor(Color.BLACK)
                    isClickable = true
                    isFocusable = true
                }

                val contentLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding((28 * dp).toInt(), (64 * dp).toInt(), (28 * dp).toInt(), (40 * dp).toInt())
                }

                // 1. Live Time
                val timeTextView = TextView(context).apply {
                    textSize = 54f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                }
                contentLayout.addView(timeTextView)

                val dateTextView = TextView(context).apply {
                    textSize = 14f
                    setTextColor(Color.parseColor("#9CA3AF"))
                    gravity = Gravity.CENTER
                }
                contentLayout.addView(dateTextView)

                fun updateTime() {
                    val now = Date()
                    timeTextView.text = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now)
                    dateTextView.text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now)
                }
                updateTime()

                timeUpdateRunnable = object : Runnable {
                    override fun run() {
                        updateTime()
                        mainHandler.postDelayed(this, 1000L)
                    }
                }
                timeUpdateRunnable?.let { mainHandler.postDelayed(it, 1000L) }

                // Spacer
                contentLayout.addView(View(context), LinearLayout.LayoutParams(1, (36 * dp).toInt()))

                // 2. Middle Content: Task Title & Details
                val scrollView = ScrollView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                        1f
                    )
                    isFillViewport = true
                }

                val taskContainer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                }

                // Task Name
                val titleTextView = TextView(context).apply {
                    text = title
                    textSize = 28f
                    setTextColor(Color.WHITE)
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setLineSpacing(6 * dp, 1.1f)
                }
                taskContainer.addView(titleTextView)

                if (!notes.isNullOrBlank()) {
                    taskContainer.addView(View(context), LinearLayout.LayoutParams(1, (16 * dp).toInt()))
                    val notesTextView = TextView(context).apply {
                        text = notes
                        textSize = 15f
                        setTextColor(Color.parseColor("#9CA3AF"))
                        gravity = Gravity.CENTER
                        setLineSpacing(4 * dp, 1.1f)
                    }
                    taskContainer.addView(notesTextView)
                }

                if (dueTime.isNotBlank()) {
                    taskContainer.addView(View(context), LinearLayout.LayoutParams(1, (16 * dp).toInt()))
                    val dueTextView = TextView(context).apply {
                        text = "Due at $dueTime"
                        textSize = 13f
                        setTextColor(Color.parseColor("#6B7280"))
                        gravity = Gravity.CENTER
                    }
                    taskContainer.addView(dueTextView)
                }

                scrollView.addView(taskContainer)
                contentLayout.addView(scrollView)

                // Spacer
                contentLayout.addView(View(context), LinearLayout.LayoutParams(1, (28 * dp).toInt()))

                // 3. Action Buttons (Completed, Snooze in 5min, Dismiss)
                val buttonLayout = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }

                // 1. Completed Button
                val completeButton = TextView(context).apply {
                    text = "Completed"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.BLACK)
                    gravity = Gravity.CENTER
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = 28 * dp
                        setColor(Color.WHITE)
                    }
                    background = btnBg
                    setPadding((20 * dp).toInt(), (16 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
                    isClickable = true
                    setOnClickListener {
                        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_COMPLETE_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                        }
                        context.sendBroadcast(broadcastIntent)
                        dismiss()
                    }
                }
                buttonLayout.addView(completeButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))

                buttonLayout.addView(View(context), LinearLayout.LayoutParams(1, (12 * dp).toInt()))

                // 2. Snooze in 5min Button
                val snoozeButton = TextView(context).apply {
                    text = "Snooze in 5min"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#F59E0B"))
                    gravity = Gravity.CENTER
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = 28 * dp
                        setColor(Color.parseColor("#1C1917"))
                        setStroke((1.5f * dp).toInt(), Color.parseColor("#78350F"))
                    }
                    background = btnBg
                    setPadding((20 * dp).toInt(), (15 * dp).toInt(), (20 * dp).toInt(), (15 * dp).toInt())
                    isClickable = true
                    setOnClickListener {
                        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_SNOOZE_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                            putExtra(AlarmReceiver.EXTRA_SNOOZE_MINUTES, 5)
                            putExtra(AlarmReceiver.EXTRA_TASK_TITLE, title)
                            putExtra(AlarmReceiver.EXTRA_TASK_NOTES, notes)
                            putExtra(AlarmReceiver.EXTRA_TASK_PRIORITY, priority)
                            putExtra(AlarmReceiver.EXTRA_TASK_DUE_DATE, dueDate)
                            putExtra(AlarmReceiver.EXTRA_TASK_DUE_TIME, dueTime)
                        }
                        context.sendBroadcast(broadcastIntent)
                        dismiss()
                    }
                }
                buttonLayout.addView(snoozeButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))

                buttonLayout.addView(View(context), LinearLayout.LayoutParams(1, (12 * dp).toInt()))

                // 3. Dismiss Button
                val dismissButton = TextView(context).apply {
                    text = "Dismiss"
                    textSize = 15f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Color.parseColor("#9CA3AF"))
                    gravity = Gravity.CENTER
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = 28 * dp
                        setColor(Color.parseColor("#171717"))
                        setStroke((1 * dp).toInt(), Color.parseColor("#262626"))
                    }
                    background = btnBg
                    setPadding((20 * dp).toInt(), (15 * dp).toInt(), (20 * dp).toInt(), (15 * dp).toInt())
                    isClickable = true
                    setOnClickListener {
                        val broadcastIntent = Intent(context, AlarmReceiver::class.java).apply {
                            action = AlarmReceiver.ACTION_DISMISS_ALARM
                            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                        }
                        context.sendBroadcast(broadcastIntent)
                        dismiss()
                    }
                }
                buttonLayout.addView(dismissButton, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))

                contentLayout.addView(buttonLayout)
                root.addView(contentLayout, FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                ))

                wm.addView(root, params)
                overlayView = root
                Log.i(TAG, "Direct full-screen WindowManager overlay displayed successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying full-screen WindowManager overlay: ${e.message}", e)
            }
        }
    }

    fun dismiss() {
        mainHandler.post {
            try {
                timeUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
                timeUpdateRunnable = null

                val view = overlayView
                if (view != null && windowManager != null) {
                    try {
                        windowManager?.removeView(view)
                        Log.i(TAG, "Alarm overlay removed from WindowManager")
                    } catch (e: Exception) {
                        Log.w(TAG, "Error removing overlay view: ${e.message}")
                    }
                    overlayView = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in dismiss: ${e.message}", e)
            }
        }
    }
}
