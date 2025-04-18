package com.programovil.misservicios1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class UserDrawerFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_drawer, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val usernameText = view.findViewById<TextView>(R.id.usernameText)
        val emailText = view.findViewById<TextView>(R.id.emailText)
        val typeText = view.findViewById<TextView>(R.id.typeText)
        val extraInfoText = view.findViewById<TextView>(R.id.extraInfoText2)

        val user = auth.currentUser
        if (user != null) {
            val uid = user.uid
            db.collection("userClients").document(uid).get()
                .addOnSuccessListener { clientDoc ->
                    if (clientDoc.exists()) {
                        val username = clientDoc.getString("username") ?: ""
                        val email = clientDoc.getString("email") ?: ""

                        usernameText.text = "Usuario: $username"
                        emailText.text = "Correo: $email"
                        typeText.text = "Tipo: Cliente"
                        extraInfoText.visibility = View.GONE
                    } else {
                        db.collection("userServices").document(uid).get()
                            .addOnSuccessListener { serviceDoc ->
                                if (serviceDoc.exists()) {
                                    val username = serviceDoc.getString("username") ?: ""
                                    val email = serviceDoc.getString("email") ?: ""
                                    val tipoServicio = serviceDoc.getString("serviceType") ?: "No especificado"
                                    val aceptaPedidos = serviceDoc.getString("acceptOrders") ?: "false"

                                    usernameText.text = "Usuario: $username"
                                    emailText.text = "Correo: $email"
                                    typeText.text = "Tipo: Servicio"
                                    extraInfoText.text = "Servicio: $tipoServicio\nAcepta pedidos: ${if (aceptaPedidos == "true") "Sí" else "No"}"
                                    extraInfoText.visibility = View.VISIBLE
                                } else {
                                    Toast.makeText(context, "No se encontraron los datos del usuario", Toast.LENGTH_SHORT).show()
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(context, "Error al obtener datos: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }

        return view
    }
}
