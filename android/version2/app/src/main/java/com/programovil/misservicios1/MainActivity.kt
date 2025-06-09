package com.programovil.misservicios1

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.programovil.misservicios1.usersAccounts.CompleteProfileActivity
import com.programovil.misservicios1.usersAccounts.RegisterActivity




class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            setupFirebaseMessaging() // <- Obtener token si ya está logueado
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        val emailField = findViewById<EditText>(R.id.etEmail)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val googleSignInButton = findViewById<com.google.android.gms.common.SignInButton>(R.id.googleSignInButton)

        for (i in 0 until googleSignInButton.childCount) {
            val view = googleSignInButton.getChildAt(i)
            if (view is TextView) {
                view.text = "Acceder con Google"
                break
            }
        }

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val toast = Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT)
                        val icon = ContextCompat.getDrawable(this, R.drawable.imagen1)
                        val textView = toast.view?.findViewById<TextView>(android.R.id.message)
                        textView?.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                        textView?.compoundDrawablePadding = 16
                        toast.show()

                        setupFirebaseMessaging() // <- Obtener y guardar token

                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        val toast = Toast.makeText(this, "Error al iniciar sesión: Correo o contraseña incorrectos", Toast.LENGTH_SHORT)
                        val icon = ContextCompat.getDrawable(this, R.drawable.imagen2)
                        val textView = toast.view?.findViewById<TextView>(android.R.id.message)
                        textView?.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                        textView?.compoundDrawablePadding = 16
                        toast.show()
                    }
            } else {
                val toast = Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT)
                val icon = ContextCompat.getDrawable(this, R.drawable.imagen2)
                val textView = toast.view?.findViewById<TextView>(android.R.id.message)
                textView?.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
                textView?.compoundDrawablePadding = 16
                toast.show()
            }
        }

        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        googleSignInButton.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, RC_SIGN_IN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                Toast.makeText(this, "Error al iniciar sesión con Google: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val email = user?.email ?: return@addOnCompleteListener
                    val db = FirebaseFirestore.getInstance()

                    val userClientsQuery = db.collection("userClients").whereEqualTo("email", email).get()
                    val userServicesQuery = db.collection("userServices").whereEqualTo("email", email).get()

                    userClientsQuery.addOnSuccessListener { clients ->
                        if (!clients.isEmpty) {
                            Toast.makeText(this, "Sesión iniciada correctamente", Toast.LENGTH_SHORT).show()
                            setupFirebaseMessaging() // <- Guardar token tras login
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        } else {
                            userServicesQuery.addOnSuccessListener { services ->
                                if (!services.isEmpty) {
                                    Toast.makeText(this, "Sesión iniciada correctamente", Toast.LENGTH_SHORT).show()
                                    setupFirebaseMessaging() // <- Guardar token tras login
                                    startActivity(Intent(this, HomeActivity::class.java))
                                    finish()
                                } else {
                                    startActivity(Intent(this, CompleteProfileActivity::class.java))
                                    finish()
                                }
                            }.addOnFailureListener {
                                Toast.makeText(this, "Error al verificar userServices", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }.addOnFailureListener {
                        Toast.makeText(this, "Error al verificar userClients", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Fallo en la autenticación con Google", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // 🔐 FUNCIONES FCM

    private fun setupFirebaseMessaging() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            Log.d("FCM", "FCM Registration Token: $token")

            // Guardar en Firestore
            val auth = FirebaseAuth.getInstance()
            val db = FirebaseFirestore.getInstance()
            val uid = auth.currentUser?.uid

            if (uid != null) {
                db.collection("userServices").document(uid)
                    .update("fcmToken", token)
                    .addOnSuccessListener {
                        Log.d("FCM", "Token guardado exitosamente")
                        Toast.makeText(this, "Token FCM guardado", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        Log.e("FCM", "Error guardando token", e)
                        // Si falla el update, intentar set
                        val data = hashMapOf("fcmToken" to token)
                        db.collection("userServices").document(uid).set(data, com.google.firebase.firestore.SetOptions.merge())
                    }
            }
        }
    }

    private fun saveTokenToFirestore(token: String) {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        val uid = auth.currentUser?.uid ?: return

        db.collection("userServices").document(uid)
            .update("fcmToken", token)
            .addOnFailureListener {
                Log.w("FCM", "No se pudo guardar el token FCM. ¿Quizá es cliente?", it)
            }
    }
}
