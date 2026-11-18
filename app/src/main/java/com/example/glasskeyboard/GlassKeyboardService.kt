package com.example.glasskeyboard

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView

class GlassKeyboardService : InputMethodService() {

    private enum class Mode { LETTERS, NUMBERS, SYMBOLS, EMOJI }

    private var mode = Mode.LETTERS
    private var shift = false
    private var caps = false
    private var lastShiftTap = 0L
    private var lastSpace = 0L
    private var suggestionsOn = true
    private var enterLabel = "return"
    private val handler = Handler(Looper.getMainLooper())
    private var repeater: Runnable? = null

    private lateinit var root: LinearLayout
    private lateinit var suggBar: LinearLayout
    private lateinit var rowsBox: LinearLayout
    private val suggViews = ArrayList<TextView>()

    private val words = ("the be to of and a in that have it for not on with he as you do at this but his by from " +
        "they we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other than " +
        "then now look only come its over think also back after use two how our work first well way even new want " +
        "because any these give day most us is are was were been hello hi thanks thank please yes okay maybe today " +
        "tomorrow tonight love great awesome really sorry morning evening friend family home school coffee dinner " +
        "lunch weekend birthday beautiful amazing happy welcome keyboard message typing").split(" ")

    private val emoji = listOf(
        "😀", "😂", "😍", "🥹", "😎", "🙏", "👍", "🔥",
        "🎉", "❤️", "✨", "😭", "🥰", "😅", "🤔", "💯",
        "😊", "🙌", "👏", "😴", "🤗", "😘", "👀", "🌟"
    )

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun isDark() =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    // ---------- lifecycle ----------

    override fun onCreateInputView(): View {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(6), dp(6), dp(10))
        }
        suggBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in 0 until 3) {
            val tv = TextView(this).apply {
                gravity = Gravity.CENTER
                textSize = 16f
                maxLines = 1
                setOnClickListener { pickSuggestion(i) }
            }
            suggViews.add(tv)
            suggBar.addView(tv, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        rowsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(suggBar)
        root.addView(rowsBox)
        render()
        return root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val type = info?.inputType ?: 0
        val cls = type and InputType.TYPE_MASK_CLASS
        val variation = type and InputType.TYPE_MASK_VARIATION
        mode = if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) Mode.NUMBERS else Mode.LETTERS
        suggestionsOn = cls == InputType.TYPE_CLASS_TEXT && variation !in setOf(
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_URI
        )
        val opts = info?.imeOptions ?: 0
        enterLabel = if (opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) "return" else
            when (opts and EditorInfo.IME_MASK_ACTION) {
                EditorInfo.IME_ACTION_SEARCH -> "search"
                EditorInfo.IME_ACTION_SEND -> "send"
                EditorInfo.IME_ACTION_GO -> "go"
                EditorInfo.IME_ACTION_DONE -> "done"
                EditorInfo.IME_ACTION_NEXT -> "next"
                else -> "return"
            }
        shift = false
        caps = false
        autoCap()
        updateSuggestions()
        if (::root.isInitialized) render()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (::root.isInitialized) render()
    }

    // ---------- rendering ----------

    private fun render() {
        val dark = isDark()
        val r = dp(26).toFloat()
        root.background = GradientDrawable().apply {
            setColor(if (dark) 0xF01C1E26.toInt() else 0xF2E9EDF6.toInt())
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            setStroke(dp(1), if (dark) 0x33FFFFFF else 0xB3FFFFFF.toInt())
        }
        val inkColor = if (dark) Color.WHITE else 0xFF14161A.toInt()
        suggViews.forEach { it.setTextColor(inkColor) }
        suggBar.visibility = if (suggestionsOn) View.VISIBLE else View.GONE

        rowsBox.removeAllViews()
        when (mode) {
            Mode.LETTERS -> {
                addRow(chars("qwertyuiop"))
                addRow(chars("asdfghjkl"), side = 16)
                addRow(listOf(shiftKey()) + chars("zxcvbnm") + listOf(delKey()))
            }
            Mode.NUMBERS -> {
                addRow(chars("1234567890"))
                addRow(chars("-/:;()$&@\""))
                addRow(listOf(modeKey("#+=", Mode.SYMBOLS)) + chars(".,?!'") + listOf(delKey()))
            }
            Mode.SYMBOLS -> {
                addRow(chars("[]{}#%^*+="))
                addRow(chars("_\\|~<>€£¥•"))
                addRow(listOf(modeKey("123", Mode.NUMBERS)) + chars(".,?!'") + listOf(delKey()))
            }
            Mode.EMOJI -> {
                emoji.chunked(8).forEach { row -> addRow(row.map { e -> key(e, 1f, 0, textSize = 22f) { commit(e) } }) }
            }
        }
        val bottom = ArrayList<TextView>()
        if (mode == Mode.EMOJI) {
            bottom.add(modeKey("ABC", Mode.LETTERS))
            bottom.add(spaceKey(6f))
            bottom.add(delKey())
        } else {
            bottom.add(modeKey(if (mode == Mode.LETTERS) "123" else "ABC", if (mode == Mode.LETTERS) Mode.NUMBERS else Mode.LETTERS))
            bottom.add(modeKey("😊", Mode.EMOJI))
            bottom.add(spaceKey(5f))
            bottom.add(key(enterLabel, 2f, 2, textSize = 15f) { enter() })
        }
        addRow(bottom)
    }

    private fun addRow(keys: List<TextView>, side: Int = 0) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(side), 0, dp(side), 0)
        }
        keys.forEach { row.addView(it) }
        rowsBox.addView(row)
    }

    private fun chars(s: String): List<TextView> = s.map { c ->
        val label = if (mode == Mode.LETTERS && (shift || caps)) c.uppercaseChar().toString() else c.toString()
        key(label, 1f, 0) { commit(label) }
    }

    private fun shiftKey() = key(if (caps) "⇪" else "⇧", 1.4f, if (shift || caps) 3 else 1, textSize = 20f) {
        val now = System.currentTimeMillis()
        if (now - lastShiftTap < 350) { caps = true; shift = true }
        else if (caps) { caps = false; shift = false }
        else shift = !shift
        lastShiftTap = now
        handler.post { render() }
    }

    private fun delKey() = key("⌫", 1.4f, 1, repeat = true, textSize = 20f) { backspace() }

    private fun modeKey(label: String, target: Mode) = key(label, 1.4f, 1, textSize = 15f) {
        mode = target
        handler.post { render() }
    }

    private fun spaceKey(weight: Float) = key("space", weight, 0, textSize = 14f) {
        val ic = currentInputConnection ?: return@key
        val now = System.currentTimeMillis()
        val before = ic.getTextBeforeCursor(2, 0)?.toString() ?: ""
        if (now - lastSpace < 400 && Regex("[A-Za-z0-9] $").containsMatchIn(before)) {
            ic.deleteSurroundingText(1, 0)
            ic.commitText(". ", 1)
        } else {
            ic.commitText(" ", 1)
        }
        lastSpace = now
        afterEdit(consumeShift = true)
    }

    /** style: 0 normal, 1 function, 2 accent, 3 active function */
    private fun key(
        label: String, weight: Float, style: Int,
        repeat: Boolean = false, textSize: Float = 21f, onTap: () -> Unit
    ): TextView {
        val dark = isDark()
        val bg = when (style) {
            1 -> if (dark) 0x993C3F4C.toInt() else 0x99BCC5D6.toInt()
            2 -> 0xFF0A84FF.toInt()
            3 -> if (dark) 0xFFE8E8EC.toInt() else Color.WHITE
            else -> if (dark) 0x80787C8C.toInt() else 0xE6FFFFFF.toInt()
        }
        val ink = when {
            style == 2 -> Color.WHITE
            style == 3 -> Color.BLACK
            dark -> Color.WHITE
            else -> 0xFF14161A.toInt()
        }
        return TextView(this).apply {
            text = label
            this.textSize = textSize
            setTextColor(ink)
            gravity = Gravity.CENTER
            maxLines = 1
            background = GradientDrawable().apply { setColor(bg); cornerRadius = dp(10).toFloat() }
            layoutParams = LinearLayout.LayoutParams(0, dp(46), weight).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            setOnTouchListener { v, e ->
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        v.animate().scaleX(.93f).scaleY(.93f).alpha(.8f).setDuration(60).start()
                        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onTap()
                        if (repeat) startRepeat(onTap)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(90).start()
                        stopRepeat()
                    }
                }
                true
            }
        }
    }

    private fun startRepeat(action: () -> Unit) {
        stopRepeat()
        val r = object : Runnable {
            override fun run() { action(); handler.postDelayed(this, 50) }
        }
        repeater = r
        handler.postDelayed(r, 400)
    }

    private fun stopRepeat() {
        repeater?.let { handler.removeCallbacks(it) }
        repeater = null
    }

    // ---------- typing logic ----------

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        afterEdit(consumeShift = true)
    }

    private fun backspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        afterEdit(consumeShift = false)
    }

    private fun enter() {
        val ei = currentInputEditorInfo
        val action = (ei?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noEnterAction = ((ei?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0
        if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            currentInputConnection?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        afterEdit(consumeShift = true)
    }

    private fun afterEdit(consumeShift: Boolean) {
        val before = shift
        if (consumeShift && shift && !caps) shift = false
        autoCap()
        updateSuggestions()
        if (before != shift && mode == Mode.LETTERS) handler.post { render() }
    }

    private fun autoCap() {
        if (caps || mode != Mode.LETTERS) return
        val ic = currentInputConnection ?: return
        val type = currentInputEditorInfo?.inputType ?: 0
        if (ic.getCursorCapsMode(type) != 0) shift = true
    }

    private fun currentWord(): String {
        val before = currentInputConnection?.getTextBeforeCursor(30, 0)?.toString() ?: ""
        return Regex("[A-Za-z’']+$").find(before)?.value ?: ""
    }

    private fun updateSuggestions() {
        if (!suggestionsOn || suggViews.isEmpty()) return
        val w = currentWord()
        val out = if (w.isEmpty()) listOf("I", "The", "Hello") else {
            listOf(w) + words.filter { it.startsWith(w.lowercase()) && it != w.lowercase() }.take(2)
        }
        for (i in 0 until 3) {
            suggViews[i].text = out.getOrNull(i) ?: ""
            suggViews[i].setTypeface(null, if (i == 0) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
    }

    private fun pickSuggestion(i: Int) {
        val ic = currentInputConnection ?: return
        val s = suggViews[i].text.toString()
        if (s.isEmpty()) return
        val cur = currentWord()
        if (cur.isNotEmpty()) ic.deleteSurroundingText(cur.length, 0)
        val capped = if (cur.isNotEmpty() && cur[0].isUpperCase()) s.replaceFirstChar { it.uppercase() } else s
        ic.commitText("$capped ", 1)
        afterEdit(consumeShift = true)
    }
}
