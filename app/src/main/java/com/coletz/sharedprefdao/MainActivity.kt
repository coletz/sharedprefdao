package com.coletz.sharedprefdao

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.coletz.sharedprefdao.editor.SharedPrefFileActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        SharedPrefFileActivity.open(this, BuildConfig.APPLICATION_ID)
    }

}
