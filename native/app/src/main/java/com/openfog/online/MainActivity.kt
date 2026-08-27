package com.openfog.online

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.openfog.online.ui.OpenFogApp
import com.openfog.online.ui.OpenFogViewModel
import com.openfog.online.ui.theme.OpenFogTheme

class MainActivity : ComponentActivity() {

    private lateinit var container: AppContainer
    private lateinit var viewModel: OpenFogViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Per-activity container so the ViewModel can be scoped to it.
        container = (application as OpenFogApp).container
        val factory = viewModelFactory {
            initializer { OpenFogViewModel(container) }
        }
        viewModel = ViewModelProvider(this, factory)[OpenFogViewModel::class.java]

        setContent {
            OpenFogTheme {
                val message by viewModel.message.collectAsStateWithLifecycle()
                LaunchedEffect(message) {
                    message?.let {
                        Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
                OpenFogApp(viewModel)
            }
        }
    }
}
