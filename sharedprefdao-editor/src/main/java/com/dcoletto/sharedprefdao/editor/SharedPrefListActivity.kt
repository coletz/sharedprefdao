package com.dcoletto.sharedprefdao.editor

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.View

class SharedPrefListActivity : AppCompatActivity() {

    companion object {
        const val PREF_FILE_NAME = "extra.PREF_FILE_NAME"
    }

    private lateinit var sharedPrefFileName: String
    private lateinit var sharedPref: SharedPreferences
    private lateinit var sharedPrefAdapter: SharedPrefListAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.shared_prefs_file_picker)

        sharedPrefFileName = intent.getStringExtra(PREF_FILE_NAME)?.takeIf { it.isNotBlank() } ?: run {
            finish()
            return
        }

        findViewById<View>(R.id.fab).setOnClickListener(::onNewPrefSelected)

        sharedPref = getSharedPreferences(sharedPrefFileName, MODE_PRIVATE)
        sharedPrefAdapter = SharedPrefListAdapter(sharedPref)

        findViewById<RecyclerView>(R.id.shared_preferences_list).apply {
            layoutManager = LinearLayoutManager(this@SharedPrefListActivity)
            adapter = sharedPrefAdapter
        }

        sharedPrefAdapter.onItemClick = ::onPrefPairSelected
        sharedPrefAdapter.onItemLongPressed = ::onPrefPairLongPressed
    }

    override fun onResume() {
        super.onResume()
        sharedPrefAdapter.update()
    }

    private fun onNewPrefSelected(view: View?) {
        PrefEditorDialog.newInstance(sharedPrefFileName)
            .onPrefEdited { sharedPrefAdapter.update() }
            .show(supportFragmentManager, PrefEditorDialog.TAG)
    }

    private fun onPrefPairSelected(prefItem: SharedPrefItem) {
        PrefEditorDialog.newInstance(sharedPrefFileName, prefItem)
            .onPrefEdited { sharedPrefAdapter.update() }
            .show(supportFragmentManager, PrefEditorDialog.TAG)
    }

    private fun onPrefPairLongPressed(prefItem: SharedPrefItem): Boolean {
        AlertDialog.Builder(this)
            .setTitle("Delete?")
            .setMessage("Delete this preference?")
            .setPositiveButton(R.string.yes) { _, _ -> sharedPref.edit().remove(prefItem.key).apply() }
            .setNegativeButton(R.string.no, null)
            .show()
        return true
    }
}
