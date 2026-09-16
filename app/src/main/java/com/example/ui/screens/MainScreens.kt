package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.Product
import com.example.ui.AppViewModel

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val alpha by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
    )
    background(Color.LightGray.copy(alpha = alpha))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val isOnline by viewModel.isOnline.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(isOnline) {
        if (!isOnline) {
            snackbarHostState.showSnackbar("No internet connection. Offline mode.", duration = SnackbarDuration.Indefinite)
        } else {
            snackbarHostState.currentSnackbarData?.dismiss()
        }
    }

    val items = listOf("Home", "Favorites", "Assistant", "Profile")
    val icons = listOf(Icons.Default.Home, Icons.Default.Favorite, Icons.Default.SmartToy, Icons.Default.Person)

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = { Icon(icons[index], contentDescription = item) },
                        label = { Text(item) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> FavoritesScreen(viewModel)
                2 -> AssistantScreen(viewModel)
                3 -> ProfileScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: AppViewModel) {
    val products by viewModel.products.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("ShopFire", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search products...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp)
            )

            if (products == null) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(6) { ProductCardSkeleton() }
                }
            } else if (products!!.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No products found.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(products!!) { product ->
                        val isFav = favorites?.any { it.id == product.id } == true
                        ProductCard(product, isFav) { viewModel.toggleFavorite(product) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(viewModel: AppViewModel) {
    val favorites by viewModel.favorites.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Favorites", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        if (favorites == null) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(6) { ProductCardSkeleton() }
            }
        } else if (favorites!!.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No favorites yet.")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(favorites!!) { product ->
                    ProductCard(product, true) { viewModel.toggleFavorite(product) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(viewModel: AppViewModel) {
    var prompt by remember { mutableStateOf("") }
    val response by viewModel.aiResponse.collectAsState()
    val isLoading by viewModel.isAiLoading.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Gemini Assistant", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.weight(1f).fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        Text(response ?: "Hello! I am your ShopFire Assistant powered by Gemini 3.1 Pro High Thinking. How can I help you today?")
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Ask me anything...") }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = { viewModel.askGemini(prompt); prompt = "" }, enabled = !isLoading && prompt.isNotBlank()) {
                    Text("Send")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: AppViewModel) {
    val userProfile by viewModel.userProfile.collectAsState()
    val isSaving by viewModel.isLoading.collectAsState()
    
    var isEditing by remember { mutableStateOf(false) }
    var editName by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> selectedImageUri = uri }
    )

    LaunchedEffect(userProfile) {
        if (userProfile != null) editName = userProfile!!.name
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Profile", fontWeight = FontWeight.Bold) }) }
    ) { padding ->
        if (userProfile == null) {
            ProfileSkeleton(padding)
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(modifier = Modifier.size(100.dp)) {
                    if (selectedImageUri != null) {
                        AsyncImage(model = selectedImageUri, contentDescription = "Profile", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else if (userProfile!!.profileImage.isNotEmpty()) {
                        AsyncImage(model = userProfile!!.profileImage, contentDescription = "Profile", modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.primary)
                    }
                    if (isEditing) {
                        IconButton(
                            onClick = { photoPickerLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                            modifier = Modifier.align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.surface, CircleShape)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Image", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isEditing) {
                    OutlinedTextField(value = editName, onValueChange = { editName = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.updateProfile(editName, selectedImageUri); isEditing = false }, enabled = !isSaving) {
                        Text(if (isSaving) "Saving..." else "Save Changes")
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(userProfile!!.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { isEditing = true }) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                    }
                    Text(userProfile!!.email, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { viewModel.seedDatabase() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Seed Demo Products (Admin)")
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Logout")
                }
            }
        }
    }
}

@Composable
fun ProfileSkeleton(paddingValues: PaddingValues) {
    Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        Box(modifier = Modifier.height(28.dp).width(150.dp).shimmerEffect())
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.height(20.dp).width(200.dp).shimmerEffect())
    }
}

@Composable
fun ProductCardSkeleton() {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp).shimmerEffect())
            Column(modifier = Modifier.padding(12.dp)) {
                Box(modifier = Modifier.height(20.dp).fillMaxWidth(0.8f).shimmerEffect())
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.height(14.dp).fillMaxWidth().shimmerEffect())
                Spacer(modifier = Modifier.height(4.dp))
                Box(modifier = Modifier.height(14.dp).fillMaxWidth(0.6f).shimmerEffect())
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.height(20.dp).fillMaxWidth(0.4f).shimmerEffect())
            }
        }
    }
}

@Composable
fun ProductCard(product: Product, isFavorite: Boolean, onFavoriteClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = product.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$${product.price}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
