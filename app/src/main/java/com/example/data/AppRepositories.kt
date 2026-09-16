package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

// DataStore
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppRepository(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()
    private val client = OkHttpClient()
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_completed")

    val isOnlineFlow: Flow<Boolean> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(true) }
            override fun onLost(network: Network) { trySend(false) }
            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(isConnected)
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        val activeNetwork = connectivityManager.activeNetwork
        val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
        val initial = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(initial)

        connectivityManager.registerNetworkCallback(request, callback)
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }

    val onboardingCompletedFlow: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[ONBOARDING_KEY] ?: false
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ONBOARDING_KEY] = completed
        }
    }

    // --- Auth State ---
    val authStateFlow: Flow<Boolean> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { auth ->
            trySend(auth.currentUser != null)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    fun getCurrentUser() = auth.currentUser

    suspend fun signInWithGoogle(activityContext: Context): Result<UserProfile> {
        return try {
            val credentialManager = CredentialManager.create(activityContext)
            var clientId = ""
            try {
                val resId = activityContext.resources.getIdentifier("default_web_client_id", "string", activityContext.packageName)
                if (resId != 0) {
                    clientId = activityContext.getString(resId)
                }
            } catch (e: Exception) {}

            if (clientId.isEmpty()) {
                return Result.failure(Exception("Google Sign-In is not configured. Please enable it in Firebase Console, add SHA-1, and re-download google-services.json."))
            }

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(activityContext, request)
            val credential = result.credential
            
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = auth.signInWithCredential(authCredential).await()
                
                val user = authResult.user!!
                val profile = UserProfile(
                    uid = user.uid,
                    name = user.displayName ?: "Google User",
                    email = user.email ?: "",
                    profileImage = user.photoUrl?.toString() ?: "",
                    role = "user"
                )
                db.collection("users").document(user.uid).set(profile).await()
                Result.success(profile)
            } else {
                Result.failure(Exception("Unexpected credential type"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserProfile(name: String, imageUri: Uri?): UserProfile? {
        val user = auth.currentUser ?: return null
        var imageUrl: String? = null
        if (imageUri != null) {
            val ref = storage.reference.child("users/${user.uid}/profile.jpg")
            ref.putFile(imageUri).await()
            imageUrl = ref.downloadUrl.await().toString()
        }
        val updates = mutableMapOf<String, Any>("name" to name)
        if (imageUrl != null) updates["profileImage"] = imageUrl
        
        db.collection("users").document(user.uid).update(updates).await()
        return getUserProfile()
    }

    // --- Products ---
    fun getProductsStream(): Flow<List<Product>> = callbackFlow {
        val listener = db.collection("products")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    trySend(products)
                }
            }
        awaitClose { listener.remove() }
    }

    // --- Favorites ---
    fun getFavoritesStream(): Flow<List<Product>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            return@callbackFlow
        }
        val listener = db.collection("users").document(uid).collection("favorites")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val products = snapshot.documents.mapNotNull { it.toObject(Product::class.java)?.copy(id = it.id) }
                    trySend(products)
                }
            }
        awaitClose { listener.remove() }
    }

    suspend fun toggleFavorite(product: Product, isFavorite: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val docRef = db.collection("users").document(uid).collection("favorites").document(product.id)
        if (isFavorite) {
            docRef.delete().await()
        } else {
            docRef.set(product).await()
        }
    }

    // --- Gemini Assistant ---
    suspend fun getGeminiResponse(prompt: String): String = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "Error: Gemini API Key is missing. Please add it via the Secrets panel."
        }

        val json = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("thinkingConfig", JSONObject().apply {
                    put("thinkingLevel", "HIGH")
                })
            })
        }

        val requestBody = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.1-pro-preview:generateContent?key=$apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) {
                val resultJson = JSONObject(body)
                val candidates = resultJson.optJSONArray("candidates")
                val content = candidates?.optJSONObject(0)?.optJSONObject("content")
                val parts = content?.optJSONArray("parts")
                return@withContext parts?.optJSONObject(0)?.optString("text") ?: "No response from AI."
            } else {
                return@withContext "API Error: \${response.code}"
            }
        } catch (e: Exception) {
            return@withContext "Network Error: \${e.message}"
        }
    }

    // --- Helper to Seed Fake Data for the UI demonstration ---
    suspend fun seedDatabase() {
        val fakeProducts = listOf(
            Product("p1", "Wireless Headphones", "High quality noise cancelling", 199.99, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=800", "audio"),
            Product("p2", "Smart Watch", "Track your daily activity and fitness", 249.50, "https://images.unsplash.com/photo-1523275335684-37898b6baf30?w=800", "wearables"),
            Product("p3", "Mechanical Keyboard", "RGB backlit mechanical keyboard", 129.00, "https://images.unsplash.com/photo-1595225476474-87563907a212?w=800", "accessories")
        )
        for (p in fakeProducts) {
            db.collection("products").document(p.id).set(p).await()
        }
    }
}
