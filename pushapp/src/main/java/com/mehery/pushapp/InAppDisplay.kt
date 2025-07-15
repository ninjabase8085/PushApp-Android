package com.mehery.pushapp

import android.app.Activity
import android.graphics.Color
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout

class InAppDisplay(private val activity: Activity) {

    fun showInApp(data: Map<String, Any>) {
        // The data object structure should have "data" as nested Map,
        // containing "type" and "template" keys
        val dataObject = data["data"] as? Map<*, *> ?: run {
            println("Invalid in-app data: missing 'data' object")
            return
        }
        val layout = dataObject["type"] as? String ?: run {
            println("Invalid in-app data: missing 'type'")
            return
        }
        val template = dataObject["template"] as? Map<*, *> ?: run {
            println("Invalid in-app data: missing 'template'")
            return
        }

        when (layout) {
            "popup" -> showPopup(template)
            "banner" -> showBanner(template)
            "pip" -> showPictureInPicture(template)
            else -> println("Unknown layout type: $layout")
        }
    }

    private fun showPopup(template: Map<*, *>) {
        val contentList = ((template["data"] as? Map<*, *>)?.get("content") as? List<*>) ?: return
        val html = contentList.firstOrNull() as? String ?: return

        activity.runOnUiThread {
            val webView = WebView(activity)
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)

            val container = FrameLayout(activity)
            container.setBackgroundColor(Color.parseColor("#B3000000")) // semi-transparent black

            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            container.layoutParams = params

            container.addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))

            val closeButton = Button(activity).apply {
                text = "✕"
                setBackgroundColor(Color.parseColor("#80000000")) // semi-transparent black
                setTextColor(Color.WHITE)
                textSize = 22f
                setOnClickListener { (container.parent as? ViewGroup)?.removeView(container) }
            }

            val closeParams = FrameLayout.LayoutParams(
                dpToPx(48), dpToPx(48)
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = dpToPx(40)
                rightMargin = dpToPx(20)
            }

            container.addView(closeButton, closeParams)

            // Add to root content view
            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            rootView.addView(container)
        }
    }

    private fun showBanner(template: Map<*, *>) {
        val contentList = ((template["data"] as? Map<*, *>)?.get("content") as? List<*>) ?: return
        val html = contentList.firstOrNull() as? String ?: return

        activity.runOnUiThread {
            val bannerHeight = dpToPx(100)

            val webView = WebView(activity).apply {
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                setBackgroundColor(Color.TRANSPARENT)
            }

            val container = FrameLayout(activity).apply {
                setBackgroundColor(Color.WHITE)
                elevation = dpToPx(6).toFloat()
            }

            val closeButton = Button(activity).apply {
                text = "✕"
                setBackgroundColor(Color.parseColor("#80000000"))
                setTextColor(Color.WHITE)
                textSize = 18f
                setOnClickListener {
                    (container.parent as? ViewGroup)?.removeView(container)
                }
            }

            val closeParams = FrameLayout.LayoutParams(
                dpToPx(36), dpToPx(36)
            ).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }

            container.addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                bannerHeight
            ))

            container.addView(closeButton, closeParams)

            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            val layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                bannerHeight
            ).apply {
                gravity = Gravity.TOP
                setMargins(dpToPx(16), dpToPx(16), dpToPx(16), 0)
            }

            rootView.addView(container, layoutParams)
        }
    }


    private fun showPictureInPicture(template: Map<*, *>) {
        val contentList = ((template["data"] as? Map<*, *>)?.get("content") as? List<*>) ?: return
        val html = contentList.firstOrNull() as? String ?: return

        activity.runOnUiThread {
            val pipWidth = dpToPx(200)
            val pipHeight = dpToPx(150)

            val webView = WebView(activity).apply {
                loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
                setBackgroundColor(Color.TRANSPARENT)
            }

            val container = FrameLayout(activity).apply {
                setBackgroundColor(Color.TRANSPARENT)
                elevation = dpToPx(8).toFloat()
                clipToOutline = true
            }

            val expandButton = Button(activity).apply {
                text = "⇱"
                setBackgroundColor(Color.parseColor("#80000000"))
                setTextColor(Color.WHITE)
                textSize = 16f
                setOnClickListener {
                    (container.parent as? ViewGroup)?.removeView(container)
                    showPopup(template)
                }
            }

            container.addView(webView, FrameLayout.LayoutParams(
                pipWidth,
                pipHeight
            ))

            val expandParams = FrameLayout.LayoutParams(dpToPx(36), dpToPx(36)).apply {
                gravity = Gravity.END or Gravity.TOP
                topMargin = dpToPx(4)
                rightMargin = dpToPx(4)
            }

            container.addView(expandButton, expandParams)

            val rootView = activity.window.decorView.findViewById<ViewGroup>(android.R.id.content)
            val layoutParams = FrameLayout.LayoutParams(pipWidth, pipHeight).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                marginEnd = dpToPx(20)
                bottomMargin = dpToPx(100)
            }

            rootView.addView(container, layoutParams)
        }
    }


    private fun dpToPx(dp: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            activity.resources.displayMetrics
        ).toInt()
}
