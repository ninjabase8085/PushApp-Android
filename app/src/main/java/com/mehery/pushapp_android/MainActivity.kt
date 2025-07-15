package com.mehery.pushapp_android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.mehery.pushapp.PushApp
import com.mehery.pushapp_android.ui.theme.PushAppAndroidTheme

class MainActivity : ComponentActivity() {

    override fun onResume() {
        super.onResume()
        PushApp.getInstance().setPageName(this,"Main")
    }

    override fun onPause() {
        PushApp.getInstance().destroyPageName("Main")
        super.onPause()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        PushApp.getInstance().initialize(this, "demo#7430c39312c5_1752573837822", sandbox = false)
        PushApp.getInstance().login("xyz")
        PushApp.getInstance().sendEvent("EVENT_LOGIN_SUCCESS", mapOf("user_name" to "xyz"))

        enableEdgeToEdge()
        setContent {
            PushAppAndroidTheme {
                AppNavigator()
            }
        }
    }
}

@Composable
fun AppNavigator() {
    val navController = rememberNavController()
    NavHost(navController, startDestination = "screenOne") {
        composable("screenOne") { ScreenOne(navController) }
        composable("screenTwo") { ScreenTwo(navController) }
        composable("screenThree") { ScreenThree(navController) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenOne(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Screen One") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Screen One", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { navController.navigate("screenTwo") }) {
                Text("Go to Screen Two")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenTwo(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Screen Two") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Screen Two", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = { navController.navigate("screenThree") }) {
                Text("Go to Screen Three")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenThree(navController: NavHostController) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Screen Three") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Screen Three", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(20.dp))
            Button(onClick = {
                navController.popBackStack("screenOne", inclusive = false)
            }) {
                Text("Back to Screen One")
            }
        }
    }
}
