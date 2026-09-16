package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.Product
import com.example.data.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AppViewModel(private val repository: AppRepository) : ViewModel() {

    val onboardingCompleted: StateFlow<Boolean> = repository.onboardingCompletedFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val isUserAuthenticated: StateFlow<Boolean> = repository.authStateFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, FirebaseAuth.getInstance().currentUser != null)

    val isOnline: StateFlow<Boolean> = repository.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val products: StateFlow<List<Product>?> = repository.getProductsStream()
        .combine(_searchQuery) { productList, query ->
            if (query.isBlank()) {
                productList
            } else {
                productList.filter {
                    it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val favorites: StateFlow<List<Product>?> = repository.getFavoritesStream()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _aiResponse = MutableStateFlow<String?>(null)
    val aiResponse: StateFlow<String?> = _aiResponse

    private val _isAiLoading = MutableStateFlow(false)
    val isAiLoading: StateFlow<Boolean> = _isAiLoading

    init {
        viewModelScope.launch {
            repository.authStateFlow.collect { isAuthenticated ->
                if (isAuthenticated) {
                    _userProfile.value = repository.getUserProfile()
                } else {
                    _userProfile.value = null
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setOnboardingCompleted() {
        viewModelScope.launch {
            repository.setOnboardingCompleted(true)
        }
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun signUp(email: String, pass: String, name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                val authResult = FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pass).await()
                val uid = authResult.user?.uid
                if (uid != null) {
                    val profile = UserProfile(uid = uid, name = name, email = email)
                    FirebaseFirestore.getInstance().collection("users").document(uid).set(profile).await()
                    _userProfile.value = profile
                }
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Failed to sign up"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signIn(email: String, pass: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pass).await()
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Failed to sign in"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signInWithGoogle(context: Context) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            val result = repository.signInWithGoogle(context)
            if (result.isSuccess) {
                _userProfile.value = result.getOrNull()
            } else {
                _authError.value = result.exceptionOrNull()?.localizedMessage ?: "Google Sign-In failed"
            }
            _isLoading.value = false
        }
    }

    fun resetPassword(email: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                FirebaseAuth.getInstance().sendPasswordResetEmail(email).await()
                _authError.value = "Password reset email sent!"
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Failed to send reset email"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun signOut() {
        FirebaseAuth.getInstance().signOut()
    }

    fun toggleFavorite(product: Product) {
        viewModelScope.launch {
            val isFavorite = favorites.value?.any { it.id == product.id } == true
            repository.toggleFavorite(product, isFavorite)
        }
    }

    fun updateProfile(name: String, imageUri: Uri?) {
        viewModelScope.launch {
            _isLoading.value = true
            _authError.value = null
            try {
                val updated = repository.updateUserProfile(name, imageUri)
                if (updated != null) {
                    _userProfile.value = updated
                }
            } catch (e: Exception) {
                _authError.value = e.localizedMessage ?: "Failed to update profile"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun seedDatabase() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.seedDatabase()
            _isLoading.value = false
        }
    }

    fun askGemini(prompt: String) {
        viewModelScope.launch {
            _isAiLoading.value = true
            _aiResponse.value = repository.getGeminiResponse(prompt)
            _isAiLoading.value = false
        }
    }
}

class AppViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(AppRepository(context)) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
