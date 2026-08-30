package io.github.customroutes.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.customroutes.app.ui.AppTheme
import io.github.customroutes.app.ui.AppViewModel
import io.github.customroutes.app.ui.CustomRoutesApp

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<AppViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val photoPicker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
                    if (uri != null) viewModel.importPhoto(uri)
                }
                CustomRoutesApp(
                    state = state,
                    viewModel = viewModel,
                    onPickPhoto = {
                        photoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onAppForegrounded()
    }

    override fun onStop() {
        viewModel.onAppBackgrounded()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        viewModel.onMemoryPressure(level)
        super.onTrimMemory(level)
    }

    override fun onLowMemory() {
        viewModel.onLowMemory()
        super.onLowMemory()
    }
}
