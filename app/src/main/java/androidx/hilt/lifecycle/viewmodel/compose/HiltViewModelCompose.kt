package androidx.hilt.lifecycle.viewmodel.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hexadecinull.vineos.di.VineServiceLocator

@Composable
inline fun <reified VM : ViewModel> hiltViewModel(): VM {
    val context = LocalContext.current
    return viewModel(factory = VineServiceLocator.provideViewModelFactory(context))
}
