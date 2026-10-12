package com.panda.drivetv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.panda.drivetv.player.VipBridge
import com.panda.drivetv.ui.Screen
import com.panda.drivetv.ui.files.FilesScreen
import com.panda.drivetv.ui.home.HomeScreen
import com.panda.drivetv.ui.library.LibraryScreen
import com.panda.drivetv.ui.library.LibraryViewModel
import com.panda.drivetv.ui.library.MovieDetailScreen
import com.panda.drivetv.ui.player.PlayerScreen
import com.panda.drivetv.ui.search.SearchScreen
import com.panda.drivetv.ui.settings.SettingsScreen
import com.panda.drivetv.ui.signin.SignInScreen

/**
 * Actividad única (etapa 1 de la guía): navegación por estado.
 *
 * v0.7.0: la pantalla inicial es el Inicio estilo Netflix (hero + filas);
 * la cuadrícula, el buscador y el explorador cuelgan de él.
 *
 * Tema oscuro material3 (sin androidx.tv: exige compileSdk 35).
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                val app = application as DriveTVApp
                val libraryVm: LibraryViewModel =
                    viewModel(factory = LibraryViewModel.factory(app))
                var screen by remember {
                    mutableStateOf<Screen>(
                        if (app.authManager.isSignedIn()) Screen.Home else Screen.SignIn
                    )
                }
                // A dónde volver al salir del reproductor.
                var playerReturn by remember { mutableStateOf<Screen>(Screen.Home) }
                // A dónde volver al salir del detalle (llega desde varias pantallas).
                var detailReturn by remember { mutableStateOf<Screen>(Screen.Home) }

                fun openDetail(movieId: String, from: Screen) {
                    detailReturn = from
                    screen = Screen.MovieDetail(movieId)
                }

                when (val s = screen) {
                    Screen.SignIn -> SignInScreen(
                        app = app,
                        onSignedIn = { screen = Screen.Home }
                    )
                    Screen.Home -> HomeScreen(
                        app = app,
                        vm = libraryVm,
                        onOpenMovie = { movie -> openDetail(movie.driveId, Screen.Home) },
                        onPlayMovie = { movie ->
                            playerReturn = Screen.Home
                            screen = Screen.Player(movie.driveId, movie.title)
                        },
                        onOpenSearch = { screen = Screen.Search },
                        onOpenLibrary = { screen = Screen.Library },
                        onOpenFiles = { screen = Screen.Files },
                        onOpenSettings = { screen = Screen.Settings },
                        onExit = { finish() }
                    )
                    Screen.Library -> LibraryScreen(
                        app = app,
                        vm = libraryVm,
                        onOpenMovie = { movie -> openDetail(movie.driveId, Screen.Library) },
                        onOpenFiles = { screen = Screen.Files },
                        onOpenSearch = { screen = Screen.Search },
                        onOpenSettings = { screen = Screen.Settings },
                        onBack = { screen = Screen.Home }
                    )
                    Screen.Search -> SearchScreen(
                        vm = libraryVm,
                        onOpenMovie = { movie -> openDetail(movie.driveId, Screen.Search) },
                        onBack = { screen = Screen.Home }
                    )
                    is Screen.MovieDetail -> {
                        val movie = libraryVm.movieById(s.driveId)
                        if (movie != null) {
                            val favorites by libraryVm.favorites.collectAsState()
                            MovieDetailScreen(
                                movie = movie,
                                isFavorite = favorites.contains(movie.driveId),
                                onToggleFavorite = { libraryVm.toggleFavorite(movie) },
                                onPlay = { version ->
                                    playerReturn = s
                                    screen = Screen.Player(version.driveId, movie.title)
                                },
                                onOpenVip = { version ->
                                    VipBridge.playOnVip(
                                        this@MainActivity, app,
                                        version.driveId, movie.title
                                    )
                                },
                                onPlayMpv = { version ->
                                    playerReturn = s
                                    screen = Screen.Player(
                                        version.driveId, movie.title,
                                        Screen.PlayerEngine.MPV
                                    )
                                },
                                onBack = { screen = detailReturn }
                            )
                        } else {
                            // La película ya no está en la lista: volver.
                            androidx.compose.runtime.LaunchedEffect(Unit) {
                                screen = detailReturn
                            }
                        }
                    }
                    Screen.Files -> FilesScreen(
                        app = app,
                        onPlayVideo = { file ->
                            playerReturn = Screen.Files
                            screen = Screen.Player(file.id, file.name)
                        },
                        onOpenSettings = { screen = Screen.Settings },
                        onBackAtRoot = { screen = Screen.Home }
                    )
                    is Screen.Player -> PlayerScreen(
                        fileId = s.fileId,
                        fileName = s.name,
                        app = app,
                        onExit = { screen = playerReturn },
                        onNeedSignIn = { screen = Screen.SignIn },
                        engine = s.engine
                    )
                    Screen.Settings -> SettingsScreen(
                        app = app,
                        onSignOut = { screen = Screen.SignIn },
                        onBack = { screen = Screen.Home }
                    )
                }
            }
        }
    }
}
