package com.example.askvocate

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.askvocate.navigation.NavGraph
import com.example.askvocate.ui.theme.AskvocateTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AskvocateTheme {
                NavGraph()
            }
        }
    }
}