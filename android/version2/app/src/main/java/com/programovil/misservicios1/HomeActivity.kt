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

            // Primero intenta buscar en userClients
            db.collection("userClients").document(uid).get()
                .addOnSuccessListener { document ->
                    if (document.exists()) {
                        mostrarDatosCliente(document.getString("username"), document.getString("email"))
                    } else {
                        // Si no existe, intenta buscar en userServices
                        db.collection("userServices").document(uid).get()
                            .addOnSuccessListener { serviceDoc ->
                                if (serviceDoc.exists()) {
                                    mostrarDatosServicio(
                                        serviceDoc.getString("username"),
                                        serviceDoc.getString("email"),
                                        serviceDoc.getString("serviceType"),
                                        serviceDoc.getString("acceptOrders")
                                    )
                                } else {
                                    Toast.makeText(this, "No se encontraron los datos del usuario", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener {
                                Toast.makeText(this, "Error al obtener datos: ${it.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al obtener datos: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(this, "No hay usuario autenticado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun mostrarDatosCliente(username: String?, email: String?) {
        tvUsername.text = "Usuario: ${username ?: "N/A"}"
        tvEmail.text = "Correo: ${email ?: "N/A"}"
        tvUserType.text = "Tipo de usuario: Cliente"
        tvServiceType.visibility = View.GONE
        tvAcceptOrders.visibility = View.GONE
    }

    private fun mostrarDatosServicio(username: String?, email: String?, serviceType: String?, acceptOrders: String?) {
        tvUsername.text = "Usuario: ${username ?: "N/A"}"
        tvEmail.text = "Correo: ${email ?: "N/A"}"
        tvUserType.text = "Tipo de usuario: Servicio"
        tvServiceType.text = "Servicio: ${serviceType ?: "N/A"}"
        tvAcceptOrders.text = "Aceptar pedidos: ${acceptOrders ?: "false"}"
        tvServiceType.visibility = View.VISIBLE
        tvAcceptOrders.visibility = View.VISIBLE
    }
}
