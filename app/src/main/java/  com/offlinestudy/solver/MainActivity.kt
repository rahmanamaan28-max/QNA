package com.offlinestudy.solver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.offlinestudy.solver.ui.answer.AnswerScreen
import com.offlinestudy.solver.ui.camera.CameraScreen
import com.offlinestudy.solver.ui.home.HomeScreen
import com.offlinestudy.solver.ui.materials.MaterialsScreen
import com.offlinestudy.solver.ui.question.QuestionScreen
import com.offlinestudy.solver.ui.search.SearchMaterialsScreen
import com.offlinestudy.solver.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val CAMERA = "camera"
    const val QUESTIONS = "questions"
    const val ANSWER = "answer"
    const val MATERIALS = "materials"
    const val SETTINGS = "settings"
    const val SEARCH = "search"
}

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    val navController = rememberNavController()
                    val state by viewModel.uiState.collectAsState()

                    NavHost(navController = navController, startDestination = Routes.HOME) {
                        composable(Routes.HOME) {
                            HomeScreen(
                                state = state,
                                onAskFromPhoto = { navController.navigate(Routes.CAMERA) },
                                onAskTyped = { navController.navigate(Routes.QUESTIONS) },
                                onMyMaterials = { navController.navigate(Routes.MATERIALS) },
                                onSettings = { navController.navigate(Routes.SETTINGS) },
                                onSearch = { navController.navigate(Routes.SEARCH) }
                            )
                        }
                        composable(Routes.SEARCH) {
                            SearchMaterialsScreen(
                                retriever = (application as OfflineStudyApp).retriever,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.CAMERA) {
                            CameraScreen(
                                onPhotoCaptured = { bmp ->
                                    viewModel.onPhotoCaptured(bmp)
                                    navController.navigate(Routes.QUESTIONS)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.QUESTIONS) {
                            QuestionScreen(
                                state = state,
                                onModeSelected = viewModel::setAnswerMode,
                                onAskQuestion = { q ->
                                    viewModel.askQuestion(q)
                                    navController.navigate(Routes.ANSWER)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.ANSWER) {
                            AnswerScreen(
                                state = state,
                                onFollowUp = { q -> viewModel.askQuestion(q, isFollowUp = true) },
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.MATERIALS) {
                            MaterialsScreen(
                                state = state,
                                onImport = { uri, name, mime -> viewModel.importDocument(uri, name, mime) },
                                onReindex = viewModel::reindexDocument,
                                onDelete = viewModel::deleteDocument,
                                onBack = { navController.popBackStack() }
                            )
                        }
                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                state = state,
                                onInstallModel = viewModel::installLlmModel,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
