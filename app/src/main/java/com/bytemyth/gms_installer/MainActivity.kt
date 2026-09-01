package com.bytemyth.gms_installer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.bytemyth.gms_installer.ui.GmsApp
import com.bytemyth.gms_installer.ui.GmsViewModel
import com.bytemyth.gms_installer.ui.theme.GmsInstallerTheme

class MainActivity : ComponentActivity() {

    private val viewModel: GmsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GmsInstallerTheme {
                Surface(Modifier.fillMaxSize()) {
                    GmsApp(viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}
