package io.github.zxyszx.subnest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.plaza.designsys.theme.PlazaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val model = ViewModelProvider(this)[WorkspaceViewModel::class.java]
        setContent {
            PlazaTheme(oneHandMode = false) {
                SubNestRoot(model, model.state.collectAsStateWithLifecycle().value)
            }
        }
    }
}
