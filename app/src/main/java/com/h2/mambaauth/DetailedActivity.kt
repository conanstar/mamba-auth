package com.h2.mambaauth

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import kotlinx.android.synthetic.main.detailed_layout.*

class DetailedActivity : Activity(), View.OnClickListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var loginProvider: String

    // For Facebook Auth
    private lateinit var callbackManager: CallbackManager

    // For Google Auth
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.detailed_layout)

        loginProvider = intent.getStringExtra(INTENT_LOGIN_PROVIDER) ?: throw IllegalStateException("field $INTENT_LOGIN_PROVIDER missing in Intent")

        auth = FirebaseAuth.getInstance()

        btnSignOut.setOnClickListener(this)
    }

    override fun onStart() {
        super.onStart()

        val currentUser = auth.currentUser
        updateUI(currentUser)
        checkCurrentConnectivity(currentUser)

        registerSwichClickListener()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_GOOGLE_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)

            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account?.idToken, null)
                linkWithCredential(PROVIDER_ID_GOOGLE, credential)

            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                revertSwitchStatus(PROVIDER_ID_GOOGLE)
            }
        } else {
            // Callback from Facebook
            callbackManager.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun registerSwichClickListener() {
        Log.d(TAG, "registerSwichClickListener")

        switchFacebook.setOnClickListener(this)
        switchGoogle.setOnClickListener(this)
        switchApple.setOnClickListener(this)
        switchEmail.setOnClickListener(this)
    }

    private fun updateUI(user: FirebaseUser?) {
        Log.d(TAG, "updateUI")
        if (user != null) {
            textTitle.text = getString(R.string.fmt_title, loginProvider)
            textUID.text = getString(R.string.fmt_uid, user.uid)
            textDisplayName.text = getString(R.string.fmt_display_name, user.displayName)
            textEmail.text = getString(R.string.fmt_email, user.email)
            textPhoneNumber.text = getString(R.string.fmt_phone_number, user.phoneNumber)

        } else {
            textTitle.text = getString(R.string.fmt_title, null)
            textUID.text = getString(R.string.fmt_uid, null)
            textDisplayName.text = getString(R.string.fmt_display_name, null)
            textEmail.text = getString(R.string.fmt_email, null)
            textPhoneNumber.text = getString(R.string.fmt_phone_number, null)
        }
    }

    private fun checkCurrentConnectivity(user: FirebaseUser?) {
        Log.d(TAG, "checkCurrentConnectivity")
        if (user != null) {
            for (provider in user.providerData) {
                when (provider.providerId) {
                    PROVIDER_ID_FACEBOOK -> switchFacebook.isChecked = true
                    PROVIDER_ID_GOOGLE -> switchGoogle.isChecked = true
                    PROVIDER_ID_APPLE -> switchApple.isChecked = true
                    PROVIDER_ID_PASSWORD -> switchEmail.isChecked = true
                }
            }
        }
    }

    private fun signOut() {
        auth.signOut()
        finish()

        if (AccessToken.getCurrentAccessToken() != null) {
            LoginManager.getInstance().logOut()
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.btnSignOut -> signOut()
            R.id.switchFacebook -> handleFacebookConnect()
            R.id.switchGoogle -> handleGoogleConnect()
            R.id.switchApple -> handleAppleConnect()
            R.id.switchEmail -> handleEmailConnect()
        }
    }

    private fun unlink(providerId: String) {
        auth.currentUser!!.unlink(providerId)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "unlink:success:$providerId")
                }
            }
    }

    private fun revertSwitchStatus(providerId: String) {
        when (providerId) {
            PROVIDER_ID_FACEBOOK -> switchFacebook.isChecked = !switchFacebook.isChecked
            PROVIDER_ID_GOOGLE -> switchGoogle.isChecked = !switchGoogle.isChecked
            PROVIDER_ID_APPLE -> switchApple.isChecked = !switchApple.isChecked
            PROVIDER_ID_PASSWORD -> switchEmail.isChecked = !switchEmail.isChecked
        }
    }

    private fun alertUnlinkDialog(providerName: String, providerId: String) {
        val builder =  AlertDialog.Builder(this)
        builder.setTitle("Unlink $providerName")
        builder.setMessage("Are you sure you want to unlink $providerName account?")
        builder.setPositiveButton(android.R.string.yes) { _, _ ->
            unlink(providerId)
        }
        builder.setNegativeButton(android.R.string.no) { _, _ ->
            revertSwitchStatus(providerId)
        }
        builder.show()
    }

    private fun linkEmail() {

    }

    private fun handleEmailConnect() {
        if (switchEmail.isChecked) {
            Log.d(TAG, "link:email")
            linkEmail()

        } else {
            Log.d(TAG, "unlink:email")
            alertUnlinkDialog("Email", PROVIDER_ID_PASSWORD)
        }
    }

    private fun linkApple() {
        // Initialize
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
                val credential = authResult.credential
                linkWithCredential(PROVIDER_ID_APPLE, credential!!)

            }.addOnFailureListener { e ->
                val msg = "checkPending:onFailure: $e"
                Log.w(TAG, msg)

                revertSwitchStatus(PROVIDER_ID_APPLE)
            }

        } else {
            Log.d(TAG, "pending: null")

            auth.startActivityForSignInWithProvider(this, provider.build())
                .addOnSuccessListener { authResult ->
                    // Sign-in successful!
                    val credential = authResult.credential
                    linkWithCredential(PROVIDER_ID_APPLE, credential!!)
                }
                .addOnFailureListener { e ->
                    val msg = "activitySignIn:onFailure: $e"
                    Log.w(TAG, msg)
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()

                    revertSwitchStatus(PROVIDER_ID_APPLE)
                }
        }
    }

    private fun handleAppleConnect() {
        if (switchApple.isChecked) {
            Log.d(TAG, "link:apple")
            linkApple()

        } else {
            Log.d(TAG, "unlink:apple")
            alertUnlinkDialog("Apple", PROVIDER_ID_APPLE)
        }
    }

    private fun linkGoogle() {
        // Initialize
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .requestProfile()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Login
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_GOOGLE_SIGN_IN)
    }

    private fun handleGoogleConnect() {
        if (switchGoogle.isChecked) {
            Log.d(TAG, "link:google")
            linkGoogle()

        } else {
            Log.d(TAG, "unlink:google")
            alertUnlinkDialog("Google", PROVIDER_ID_GOOGLE)
        }
    }

    private fun linkFacebook() {
        // Initialize
        callbackManager = CallbackManager.Factory.create()
        LoginManager.getInstance().registerCallback(callbackManager,
            object : FacebookCallback<LoginResult> {
                override fun onSuccess(result: LoginResult?) {
                    Log.d(TAG, "linkFacebook:onSuccess")

                    val accessToken = result?.accessToken
                    if (accessToken != null) {
                        val credential = FacebookAuthProvider.getCredential(accessToken.token)
                        linkWithCredential(PROVIDER_ID_FACEBOOK, credential)
                    }
                }

                override fun onCancel() {
                    Log.d(TAG, "linkFacebook:onCancel")
                    revertSwitchStatus(PROVIDER_ID_FACEBOOK)
                }

                override fun onError(error: FacebookException?) {
                    Log.d(TAG, "linkFacebook:onError: $error")
                    revertSwitchStatus(PROVIDER_ID_FACEBOOK)
                }
            }
        )

        // Login
        LoginManager.getInstance().logInWithReadPermissions(this, listOf("email", "public_profile"))
    }

    private fun handleFacebookConnect() {
        if (switchFacebook.isChecked) {
            Log.d(TAG, "link:facebook")
            linkFacebook()

        } else {
            Log.d(TAG, "unlink:facebook")
            alertUnlinkDialog("Facebook", PROVIDER_ID_FACEBOOK)
        }
    }

    private fun linkWithCredential(providerId: String, credential: AuthCredential) {
        auth.currentUser?.linkWithCredential(credential)
            ?.addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "linkWithCredential:success")
                } else {
                    Log.d(TAG, "linkWithCredential:fail")
                    revertSwitchStatus(providerId)
                }
            }
    }

    companion object {
        private const val TAG = "DETAIL"
        private const val INTENT_LOGIN_PROVIDER = "login_provider"
        private const val RC_GOOGLE_SIGN_IN = 9001

//        const val PROVIDER_ID_FIREBASE = "firebase"
        const val PROVIDER_ID_FACEBOOK = "facebook.com"
        const val PROVIDER_ID_GOOGLE   = "google.com"
        const val PROVIDER_ID_PASSWORD = "password"
        const val PROVIDER_ID_APPLE    = "apple.com"

        fun newIntent(context: Context, loginProvider: String): Intent {
            val intent = Intent(context, DetailedActivity::class.java)
            intent.putExtra(INTENT_LOGIN_PROVIDER, loginProvider)
            return intent
        }
    }
}