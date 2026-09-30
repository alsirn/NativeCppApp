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

        val buttonHello = Button(this)
        buttonHello.text = "Panggil C++"

        buttonHello.setOnClickListener {
            textView.text = helloFromCpp()
        }

        val buttonAdd = Button(this)
        buttonAdd.text = "Hitung 10 + 20"

        buttonAdd.setOnClickListener {
            val result = addNumbers(10, 20)
            textView.text = "Hasil C++: $result"
        }

        val layout = LinearLayout(this)

        layout.orientation = LinearLayout.VERTICAL
        layout.addView(textView)
        layout.addView(buttonHello)
        layout.addView(buttonAdd)

        setContentView(layout)
    }

    private external fun addNumbers(a: Int, b: Int): Int
}