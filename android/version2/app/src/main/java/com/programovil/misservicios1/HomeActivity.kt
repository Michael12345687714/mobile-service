package com.programovil.misservicios1

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity() {

    private lateinit var tvUsername: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvUserType: TextView
    private lateinit var tvServiceType: TextView
    private lateinit var tvAcceptOrders: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tvUsername = findViewById(R.id.tvUsername)
        tvEmail = findViewById(R.id.tvEmail)
        tvUserType = findViewById(R.id.tvUserType)
        tvServiceType = findViewById(R.id.tvServiceType)
        tvAcceptOrders = findViewById(R.id.tvAcceptOrders)

        val currentUser = auth.currentUser

        if (currentUser != null) {
            val uid = currentUser.uid

            db.collection("users").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        val username = document.getString("username") ?: ""
                        val email = document.getString("email") ?: ""
                        val userType = document.getString("userType") ?: ""

                        tvUsername.text = "Usuario: $username"
                        tvEmail.text = "Correo: $email"
                        tvUserType.text = "Tipo de usuario: $userType"

                        if (userType == "Servicio") {
                            val serviceType = document.getString("serviceType") ?: "N/A"
                            val acceptOrders = document.getString("acceptOrders") ?: "false"

                            tvServiceType.text = "Servicio: $serviceType"
                            tvAcceptOrders.text = "Aceptar pedidos: $acceptOrders"

                            tvServiceType.visibility = View.VISIBLE
                            tvAcceptOrders.visibility = View.VISIBLE
                        }
                    } else {
                        Toast.makeText(this, "No se encontraron los datos del usuario", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al obtener datos: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
        }
    }
}
