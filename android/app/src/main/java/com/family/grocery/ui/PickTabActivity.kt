package com.family.grocery.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.family.grocery.Repo
import com.family.grocery.widget.WidgetRenderer

/** Окно выбора категории — открывается нажатием на название вкладки в виджете. */
class PickTabActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        setContent {
            AppTheme {
                val s by Repo.state.collectAsState()
                val current by Repo.selectedTab.collectAsState()
                val selected = s.validTab(current)
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text("Категория") },
                    text = {
                        LazyColumn(Modifier.heightIn(max = 480.dp)) {
                            items(s.tabs(), key = { it.id }) { t ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            Repo.setSelectedTab(t.id)
                                            WidgetRenderer.renderAll(this@PickTabActivity)
                                            finish()
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        t.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = if (t.id == selected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (t.id == selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (t.needed > 0) {
                                        Text("купить ${t.needed}", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { finish() }) { Text("Закрыть") } },
                )
            }
        }
    }
}
