package com.family.grocery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.family.grocery.Repo

/** Окно комментария поверх рабочего стола — открывается нажатием на название товара в виджете. */
class NoteActivity : ComponentActivity() {
    companion object {
        const val EXTRA_ITEM_ID = "itemId"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        val id = intent.getIntExtra(EXTRA_ITEM_ID, -1)
        val item = Repo.state.value.items.firstOrNull { it.id == id }
        if (item == null) {
            finish()
            return
        }
        setContent { AppTheme { NoteDialog(item, onDismiss = { finish() }) } }
    }
}
