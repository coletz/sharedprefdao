package com.dcoletto.sharedprefdao.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.text.InputType
import android.view.View
import android.widget.EditText
import java.io.File

class SharedPrefFileActivity : AppCompatActivity() {

    companion object {
        const val APPLICATION_ID = "extra.APPLICATION_ID"

        fun open(context: Context, applicationId: String) {
            Intent(context, SharedPrefFileActivity::class.java)
                .putExtra(APPLICATION_ID, applicationId)
                .run { context.startActivity(this) }
        }
    }

    private val applicationId: String by lazy { requireNotNull(intent.getStringExtra(APPLICATION_ID)) }
    private val sharedPrefAdapter by lazy { SharedPrefFileAdapter(this, applicationId) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.shared_prefs_file_picker)

        findViewById<RecyclerView>(R.id.shared_preferences_list).apply {
            layoutManager = LinearLayoutManager(this@SharedPrefFileActivity)
            adapter = sharedPrefAdapter
        }

        sharedPrefAdapter.onItemClick = ::onFileSelected

        findViewById<View>(R.id.fab).setOnClickListener(::onNewFileSelected)
    }

    private fun onFileSelected(file: File) {
        openPreference(file.nameWithoutExtension)
    }

    private fun onNewFileSelected(view: View?) {
        val input = EditText(this).apply {
            hint = "File name"
            inputType = InputType.TYPE_CLASS_TEXT
        }

        AlertDialog.Builder(this)
            .setTitle("Enter shared preference file name")
            .setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ -> openPreference(input.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        sharedPrefAdapter.update()
    }

    private fun openPreference(preferenceFileName: String) {
        Intent(this, SharedPrefListActivity::class.java)
            .putExtra(SharedPrefListActivity.PREF_FILE_NAME, preferenceFileName)
            .run { startActivity(this) }
    }
}
