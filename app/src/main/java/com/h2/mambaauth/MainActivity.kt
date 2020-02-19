package com.h2.mambaauth

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity(), View.OnClickListener {

    // For Firebase Auth
    private lateinit var auth: FirebaseAuth

    // For Facebook Auth
    private lateinit var callbackManager: CallbackManager

    // For Google Auth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnFacebookSignIn.setOnClickListener(this)
        btnGoogleSignIn.setOnClickListener(this)
        btnAppleSignIn.setOnClickListener(this)
        btnEmailSignIn.setOnClickListener(this)

        initFirebaseAuth()
        initFacebookAuth()
        initGoogleAuth()
    }

    public override fun onStart() {
        super.onStart()

        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        if (currentUser != null) {
            showDetail(currentUser.providerId)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account!!)

            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
            }

        } else {
            // Pass the activity result back to the Facebook SDK
            callbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun initFirebaseAuth() {
        auth = FirebaseAuth.getInstance()
    }

    private fun initFacebookAuth() {
        callbackManager = CallbackManager.Factory.create()

        LoginManager.getInstance().registerCallback(callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult?) {
                    Log.d(TAG, "facebook:login:onSuccess: $result")

                    // handle token by Firebase
                    if (result != null) {
                        val token = result.accessToken
                        firebaseAuthWithFacebook(token)
                    }

                }

                override fun onCancel() {
                    Log.d(TAG, "facebook:login:onCancel")
                    Snackbar.make(main_layout, "Facebook Login Cancelled", Snackbar.LENGTH_SHORT).show()
                }

                override fun onError(error: FacebookException?) {
                    Log.d(TAG, "facebook:login:onError", error)
                    Snackbar.make(main_layout, "Facebook Login Failed: ${error?.message}", Snackbar.LENGTH_SHORT).show()
                }

            })
    }

    private fun initGoogleAuth() {
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun alertAuthUserCollision(email: String) {
        FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email)
            .addOnSuccessListener(this) { result ->
                val signInMethod = result.signInMethods
                Log.d(TAG, "Supported sign in method: $signInMethod")
                Toast.makeText(baseContext, "Supported sign in method $signInMethod", Toast.LENGTH_LONG).show()
            }
    }

    private fun firebaseAuthWithFacebook(token: AccessToken) {
        Log.d(TAG, "firebaseAuthWithFacebook:facebookId: ${token.userId}\n facebookToken: ${token.token}")

        val credential = FacebookAuthProvider.getCredential(token.token)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")

                    val user = auth.currentUser
                    if (user != null) {
                        showDetail(DetailedActivity.PROVIDER_ID_FACEBOOK)
                    }

                } else {
                    // If sign in fails, display a message to the user.
                    val e = task.exception
                    Log.w(TAG, "signInWithCredential:failure", e)
                    Toast.makeText(baseContext, "Authentication failed: ${e?.message}",
                        Toast.LENGTH_LONG).show()

                    if (e is FirebaseAuthUserCollisionException) {
                        val email = e.email
                        if (email != null) {
                            alertAuthUserCollision(email)
                        }
                    }
                }
            }
    }

    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:googleId: ${acct.id}\n googleToken: ${acct.idToken}")

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")

                    showDetail(DetailedActivity.PROVIDER_ID_GOOGLE)

                } else {
                    // If sign in fails, display a message to the user.
                    val e = task.exception
                    Log.w(TAG, "signInWithCredential:failure: $e")
                    Snackbar.make(main_layout, "Authentication Failed: ${e?.message}", Snackbar.LENGTH_SHORT).show()
                }
            }
    }

    private fun showDetail(loginProvider: String) {
        val intent = DetailedActivity.newIntent(this, loginProvider)
        startActivity(intent)
    }

    private fun emailSignIn() {
//        val email = "conanstar@gmail.com"
//        val password = "123456"
//        auth.signInWithEmailAndPassword(email, password)
//            .addOnCompleteListener(this) { task ->
//                if (task.isSuccessful) {
//                    // Sign in success, update UI with the signed-in user's information
//                    Log.d(TAG, "signInWithEmail:success")
//
//                    showDetail(DetailedActivity.PROVIDER_ID_PASSWORD)
//                } else {
//                    // If sign in fails, display a message to the user.
//                    val e = task.exception
//                    Log.w(TAG, "signInWithEmail:failure: $e")
//                    Toast.makeText(baseContext, "Authentication failed: ${e?.message}",
//                        Toast.LENGTH_SHORT).show()
//                }
//            }

    }

    private fun appleSignIn() {
        Log.d(TAG, "appleSignIn")

        val provider = OAuthProvider.newBuilder("apple.com")
        provider.setScopes(arrayOf("email", "name").toMutableList())
        provider.addCustomParameter("locale", "zh-TW")

        val pending = auth.pendingAuthResult
        if (pending != null) {
            Log.d(TAG, "pending: not null")

            pending.addOnSuccessListener { authResult ->
                Log.d(TAG, "checkPending:onSuccess:${authResult.user}")
                // Get the user profile with authResult.getUser() and
                // authResult.getAdditionalUserInfo(), and the ID
                // token from Apple with authResult.getCredential().
                showDetail(DetailedActivity.PROVIDER_ID_APPLE)

            }.addOnFailureListener { e ->
                val msg = "checkPending:onFailure: $e"
                Log.w(TAG, msg)
                Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
            }

        } else {
            Log.d(TAG, "pending: null")

            auth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener { authResult ->
                    // Sign-in successful!
                    Log.d(TAG, "activitySignIn:onSuccess:${authResult.user}")
                    showDetail(DetailedActivity.PROVIDER_ID_APPLE)
                }
                .addOnFailureListener { e ->
                    val msg = "activitySignIn:onFailure: $e"
                    Log.w(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                }
        }

    }

    private fun facebookSignIn() {
        Log.d(TAG, "facebookSignIn")

        LoginManager.getInstance().logInWithReadPermissions(this, listOf("email", "public_profile"))
    }

    private fun googleSignIn() {
        Log.d(TAG, "googleSignIn")

        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }

    override fun onClick(v: View?) {

        when (v?.id) {
            R.id.btnFacebookSignIn -> facebookSignIn()
            R.id.btnGoogleSignIn -> googleSignIn()
            R.id.btnAppleSignIn -> appleSignIn()
            R.id.btnEmailSignIn -> emailSignIn()
        }
    }

    companion object {
        private const val TAG = "Login"
        private const val RC_GOOGLE_SIGN_IN = 9001
    }
}
