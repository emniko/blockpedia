package com.emniko.blockpedia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.emniko.blockpedia.ui.BlockpediaRoot
import com.emniko.blockpedia.ui.theme.BlockpediaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BlockpediaTheme {
                BlockpediaRoot()
            }
        }
    }
}
