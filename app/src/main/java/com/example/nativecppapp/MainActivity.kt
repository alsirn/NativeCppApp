package com.example.nativecppapp

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.LinearLayout

class MainActivity : Activity() {

    companion object {
        init {
            System.loadLibrary("native-lib")
        }
    }

    private external fun helloFromCpp(): String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textView = TextView(this)
        textView.text = "Belum memanggil C++"
        textView.textSize = 24f

        val button = Button(this)
        button.text = "Panggil C++"

        button.setOnClickListener {
            textView.text = helloFromCpp()
        }

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.addView(textView)
        layout.addView(button)

        setContentView(layout)
    }
}