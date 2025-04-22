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
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore


class MainActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val RC_SIGN_IN = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val emailField = findViewById<EditText>(R.id.etEmail)
        val passwordField = findViewById<EditText>(R.id.etPassword)
        val loginButton = findViewById<Button>(R.id.btnLogin)
        val registerButton = findViewById<Button>(R.id.btnRegister)
        val googleSignInButton = findViewById<com.google.android.gms.common.SignInButton>(R.id.googleSignInButton)
        //Cambiar el texto del boton
        for (i in 0 until googleSignInButton.childCount) {
            val view = googleSignInButton.getChildAt(i)
            if (view is android.widget.TextView) {
                view.text = "Acceder con Google"
                break
            }
        }
        // Configurar Google Sign-In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id)) // Asegúrate de tener este string en strings.xml
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Inicio de sesión con email y contraseña
        loginButton.setOnClickListener {
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(this, "Inicio de sesión exitoso", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this, HomeActivity::class.java))
                        finish()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Error al iniciar sesión: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
            } else {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
            }
        }

        // Redirige al registro
        registerButton.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        // Inicio de sesión con Google
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

                    // Verifica en ambas colecciones: userClients y userServices
                    val userClientsQuery = db.collection("userClients").whereEqualTo("email", email).get()
                    val userServicesQuery = db.collection("userServices").whereEqualTo("email", email).get()

                    userClientsQuery.addOnSuccessListener { clients ->
                        if (!clients.isEmpty) {
                            Log.d("AUTH", "Usuario encontrado en userClients. Redirigiendo a HomeActivity.")
                            startActivity(Intent(this, HomeActivity::class.java))
                            finish()
                        } else {
                            userServicesQuery.addOnSuccessListener { services ->
                                if (!services.isEmpty) {
                                    Log.d("AUTH", "Usuario encontrado en userServices. Redirigiendo a HomeActivity.")
                                    startActivity(Intent(this, HomeActivity::class.java))
                                    finish()
                                } else {
                                    Log.d("AUTH", "Correo no registrado. Redirigiendo a CompleteProfileActivity.")
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
}

