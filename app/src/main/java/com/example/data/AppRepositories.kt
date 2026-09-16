package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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
    private val client = OkHttpClient()

    private val ONBOARDING_KEY = booleanPreferencesKey("onboarding_completed")

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

    suspend fun getUserProfile(): UserProfile? {
        val uid = auth.currentUser?.uid ?: return null
        return try {
            val snapshot = db.collection("users").document(uid).get().await()
            snapshot.toObject(UserProfile::class.java)
        } catch (e: Exception) {
            null
        }
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
