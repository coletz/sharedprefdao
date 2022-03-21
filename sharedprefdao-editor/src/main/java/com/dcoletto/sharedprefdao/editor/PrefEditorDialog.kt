package com.dcoletto.sharedprefdao.editorimport android.content.SharedPreferencesimport android.os.Bundleimport android.view.Viewimport android.view.ViewGroupimport android.widget.ArrayAdapterimport android.widget.Buttonimport android.widget.EditTextimport android.widget.Spinnerimport androidx.appcompat.app.AppCompatActivityimport androidx.fragment.app.DialogFragmentclass PrefEditorDialog : DialogFragment(R.layout.preference_editor_dialog) {    companion object {        const val TAG = "PrefEditor"        const val PREF_FILE_NAME = "extra.PREF_FILE_NAME"        const val SHARED_PREF_ITEM = "extra.SHARED_PREF_ITEM"        fun newInstance(sharedPrefFileName: String, prefItem: SharedPrefItem? = null): PrefEditorDialog {            val bundle = Bundle()            bundle.putString(PREF_FILE_NAME, sharedPrefFileName)            prefItem?.run { bundle.putParcelable(SHARED_PREF_ITEM, this) }            return PrefEditorDialog().also { it.arguments = bundle }        }    }    private lateinit var prefTypeView: Spinner    private lateinit var prefKeyView: EditText    private lateinit var prefValueView: EditText    private lateinit var saveBtn: Button    private lateinit var sharedPref: SharedPreferences    private var onPrefEditedListener: () -> Unit = {}    override fun onCreate(savedInstanceState: Bundle?) {        super.onCreate(savedInstanceState)        val sharedPrefFileName = arguments?.getString(PREF_FILE_NAME) ?: throw IllegalArgumentException()        sharedPref = requireContext().getSharedPreferences(sharedPrefFileName, AppCompatActivity.MODE_PRIVATE)    }    override fun onStart() {        super.onStart()        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)    }    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {        super.onViewCreated(view, savedInstanceState)        prefTypeView = view.findViewById(R.id.preference_type_spinner)        prefTypeView.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, SharedPrefType.values())        prefKeyView = view.findViewById(R.id.preference_key_et)        prefValueView = view.findViewById(R.id.preference_value_et)        saveBtn = view.findViewById(R.id.save_btn)        saveBtn.setOnClickListener(::onSaveClicked)        preload()    }    private fun preload(){        val args = arguments ?: return        if (!args.containsKey(SHARED_PREF_ITEM)) return        val (type, key, value) = requireNotNull(args.getParcelable<SharedPrefItem>(SHARED_PREF_ITEM))        prefTypeView.setSelection(SharedPrefType.values().indexOf(type))        prefKeyView.setText(key)        prefValueView.setText(value.toString())    }    private fun onSaveClicked(view: View) {        createPreference(            SharedPrefType.values()[prefTypeView.selectedItemPosition],            prefKeyView.text.toString(),            prefValueView.text.toString()        )        onPrefEditedListener()        dismiss()    }    private fun createPreference(type: SharedPrefType, key: String, value: String) {        with(sharedPref.edit()) {            when (type) {                SharedPrefType.BOOLEAN -> putBoolean(key, value.toBoolean())                SharedPrefType.FLOAT -> putFloat(key, value.toFloat())                SharedPrefType.INTEGER -> putInt(key, value.toInt())                SharedPrefType.LONG -> putLong(key, value.toLong())                SharedPrefType.STRING -> putString(key, value)                SharedPrefType.STRING_SET -> putStringSet(key, value.split(",").map { it.trim() }.toSet())            }        }.apply()    }    fun onPrefEdited(listener: () -> Unit): PrefEditorDialog = apply {        onPrefEditedListener = listener    }}