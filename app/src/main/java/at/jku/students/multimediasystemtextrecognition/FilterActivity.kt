package at.jku.students.multimediasystemtextrecognition

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView

class FilterActivity : Activity() {

    private val listItems = arrayOf("Binary Filter", "Contrast Filter", "Sharpening Filter", "Median Filter", "Averaging Filter", "Grayscale Filter", "Brightness HSV Filter", "Edge Coloring Filter", "Saturation HSV Filter", "HUE HSV Filter")
    lateinit var adapter : ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.filter_settings_layout)

        val listView = findViewById<ListView>(R.id.list)
        adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_multiple_choice, listItems)
        listView.adapter = adapter

        val button = findViewById<Button>(R.id.done)
        button.setOnClickListener {
            finish()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        super.onCreateOptionsMenu(menu)
        val inflater = menuInflater
        inflater.inflate(R.menu.filter_menu, menu)
        return true
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        super.onContextItemSelected(item)
        val id = item.itemId
        Log.i("DEBUG", "ID: $id")
        return true
    }
}