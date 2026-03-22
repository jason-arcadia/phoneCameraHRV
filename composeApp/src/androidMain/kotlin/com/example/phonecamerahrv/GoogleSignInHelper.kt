package com.example.phonecamerahrv

import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

private const val WEB_CLIENT_ID =
    "755883204511-isna0en1ppfk3ju0d7j1sfo3gtp4ahk2.apps.googleusercontent.com"

class GoogleSignInHelper(
    private val activity: androidx.activity.ComponentActivity,
    private val onSuccess: (email: String, displayName: String) -> Unit,
    private val onFailure: (String) -> Unit
) {
    private val client: GoogleSignInClient

    init {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()
        client = GoogleSignIn.getClient(activity, gso)
    }

    val launcher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account: GoogleSignInAccount = task.getResult(ApiException::class.java)
                val email = account.email ?: ""
                val name  = account.displayName ?: email
                onSuccess(email, name)
            } catch (e: ApiException) {
                onFailure("Google 登入失敗 (${e.statusCode})")
            }
        }

    fun getLastSignedInAccount(): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(activity)

    fun launch() = launcher.launch(client.signInIntent)

    fun signOut() = client.signOut()
}
