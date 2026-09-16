package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppViewModel
import com.example.ui.AppViewModelFactory
import com.example.ui.navigation.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        var isFirebaseInitialized = false
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this)
            }
            isFirebaseInitialized = FirebaseApp.getApps(this).isNotEmpty()
        } catch (e: Exception) {
            isFirebaseInitialized = false
        }

        setContent {
            MyApplicationTheme {
                if (isFirebaseInitialized) {
                    val viewModel: AppViewModel = viewModel(factory = AppViewModelFactory(applicationContext))
                    AppNavigation(viewModel = viewModel)
                } else {
                    MissingGoogleServicesScreen()
                }
            }
        }
    }
}

@Composable
fun MissingGoogleServicesScreen() {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Warning",
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Firebase Configuration Missing",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Please upload your google-services.json file to the 'app' directory to initialize Firebase.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}
