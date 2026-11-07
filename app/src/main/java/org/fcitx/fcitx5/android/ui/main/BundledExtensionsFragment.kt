/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.ui.main

import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.extensions.BundledExtensions
import org.fcitx.fcitx5.android.ui.main.settings.SettingsRoute
import org.fcitx.fcitx5.android.utils.navigateWithAnim

/** Native View implementation of design/extensions.haoke-ui.json; no WebView. */
class BundledExtensionsFragment : Fragment() {
    private val model: MainViewModel by activityViewModels()
    private val switches = mutableMapOf<String, SwitchCompat>()
    private val rows = mutableMapOf<String, LinearLayout>()
    private var actualStates = emptyMap<String, Boolean>()
    private val refresh = MutableStateFlow(0)
    private lateinit var status: TextView
    private lateinit var content: LinearLayout
    private var dark = false
    private val primary get() = Color.parseColor(if (dark) "#9DBFFF" else "#2465D8")
    private val ink get() = Color.parseColor(if (dark) "#E8EEFA" else "#17263D")
    private val muted get() = Color.parseColor(if (dark) "#A8B6CD" else "#58677D")
    private val soft get() = Color.parseColor(if (dark) "#1D3557" else "#E7F0FF")
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun text(value: String, size: Float, color: Int = ink, bold: Boolean = false) = TextView(requireContext()).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        switches.clear(); rows.clear()
        return ScrollView(requireContext()).apply {
            id = R.id.bundled_extensions_scroll
            setBackgroundColor(Color.parseColor(if (dark) "#101C2E" else "#F7F9FC"))
            clipToPadding = false
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
                insets
            }
            content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(16), dp(16), dp(20))
            }
            addView(content, ViewGroup.LayoutParams(-1, -2))
            buildContent()
        }
    }

    private fun buildContent() {
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = rounded(soft, 18); setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        header.addView(text("▦", 28f, primary), LinearLayout.LayoutParams(dp(42), dp(42)))
        header.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(getString(R.string.extensions_bundled_count), 18f, bold = true))
            addView(text(getString(R.string.extensions_bundled_description), 13f, muted).apply { setPadding(0, dp(6), 0, 0) })
        }, LinearLayout.LayoutParams(0, -2, 1f))
        content.addView(header, LinearLayout.LayoutParams(-1, -2))
        status = text(getString(R.string.extensions_loading), 13f, primary).apply {
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            setOnClickListener {
                BundledExtensions.operation.value.let { op ->
                    if (op.failed != null) BundledExtensions.request(op.failed, op.target)
                    else refresh.value += 1
                }
            }
        }
        content.addView(status)
        heading(R.string.extensions_input_engines)
        BundledExtensions.entries.filter { it.id != "clipboard_filter" }.forEach(::addEntry)
        heading(R.string.extensions_clipboard)
        addEntry(BundledExtensions.entries.last())
        content.addView(text(getString(R.string.extensions_keep_data), 13f, muted).apply {
            setPadding(dp(2), dp(20), dp(2), dp(8))
        })
        link(R.string.extensions_input_order) { navigateWithAnim(SettingsRoute.InputMethodList) }
        link(R.string.extensions_external) { navigateWithAnim(SettingsRoute.Plugin) }
    }

    private fun heading(title: Int) {
        content.addView(text(getString(title), 14f, primary, true).apply {
            setPadding(dp(2), dp(20), 0, dp(8))
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun link(title: Int, click: () -> Unit) {
        content.addView(text(getString(title) + "  ›", 14f, primary).apply {
            minHeight = dp(48); gravity = Gravity.CENTER_VERTICAL; isFocusable = true
            setOnClickListener { click() }
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun addEntry(entry: BundledExtensions.Entry) {
        val title = getString(entry.title)
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(64); setPadding(dp(4), dp(8), dp(2), dp(8))
            isFocusable = true
            contentDescription = "$title. ${getString(entry.description)}"
            setOnClickListener { BundledExtensions.request(entry.id, actualStates[entry.id] != true) }
            val attr = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, attr, true)
            foreground = androidx.appcompat.content.res.AppCompatResources.getDrawable(context, attr.resourceId)
        }
        row.addView(text(entry.badge, 16f, primary, true).apply {
            gravity = Gravity.CENTER; background = rounded(soft, 8)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        row.addView(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(8), 0)
            addView(text(title, 16f, bold = true))
            addView(text(getString(entry.description), 12f, muted).apply { setPadding(0, dp(5), 0, 0) })
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = SwitchCompat(requireContext()).apply {
            isClickable = false; isFocusable = false; isSaveEnabled = false; minimumHeight = dp(48)
            showText = false
            thumbTintList = ColorStateList.valueOf(Color.WHITE)
            trackTintList = ColorStateList(arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()), intArrayOf(primary, Color.parseColor("#A8AFBA")))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        switches[entry.id] = toggle; rows[entry.id] = row
        row.isEnabled = false
        row.addView(toggle, LinearLayout.LayoutParams(dp(52), dp(48)))
        ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = "android.widget.Switch"; info.isCheckable = true; info.isChecked = toggle.isChecked
            }
        })
        content.addView(row, LinearLayout.LayoutParams(-1, -2))
        content.addView(View(requireContext()).apply { setBackgroundColor(Color.parseColor(if (dark) "#293B54" else "#DCE3EF")) }, LinearLayout.LayoutParams(-1, dp(1)))
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        super.onViewCreated(view, state)
        BundledExtensions.recoverPending()
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(BundledExtensions.operation, refresh) { operation, _ -> operation }.collect { operation ->
                    val loaded = try {
                        actualStates = withTimeout(30_000) { BundledExtensions.states(model.fcitx) }
                        true
                    } catch (_: TimeoutCancellationException) { false
                    } catch (e: CancellationException) { throw e
                    } catch (_: Exception) { false }
                    switches.forEach { (id, toggle) ->
                        toggle.isChecked = actualStates[id] == true
                        if (org.fcitx.fcitx5.android.data.prefs.AppPrefs.getInstance().advanced.disableAnimation.getValue()) toggle.jumpDrawablesToCurrentState()
                        rows.getValue(id).isEnabled = loaded && operation.busy == null
                        rows.getValue(id).alpha = if (operation.busy == null) 1f else 0.6f
                    }
                    status.visibility = if (!loaded || operation.busy != null || operation.failed != null) View.VISIBLE else View.GONE
                    status.text = when {
                        !loaded -> getString(R.string.extensions_load_retry)
                        operation.busy != null -> getString(R.string.extensions_applying, getString(BundledExtensions.entries.first { it.id == operation.busy }.title))
                        operation.failed != null -> getString(R.string.extensions_retry)
                        else -> ""
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        switches.clear(); rows.clear()
        super.onDestroyView()
    }
}
