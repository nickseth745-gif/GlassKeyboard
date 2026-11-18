package com.example.glasskeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        box.addView(TextView(this).apply {
            text = "Glass Keyboard"
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
        })
        box.addView(TextView(this).apply {
            text = "Two quick steps to start using it in every app."
            textSize = 16f
            setPadding(0, pad / 2, 0, pad)
        })
        box.addView(Button(this).apply {
            text = "1. Turn on Glass Keyboard"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        box.addView(Button(this).apply {
            text = "2. Switch to Glass Keyboard"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        box.addView(EditText(this).apply {
            hint = "Try it here"
            setPadding(0, pad, 0, pad)
        })
        setContentView(box)
    }
}
