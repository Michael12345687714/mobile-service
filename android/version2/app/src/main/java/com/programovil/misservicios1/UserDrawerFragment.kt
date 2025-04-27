package com.programovil.misservicios1

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class UserDrawerFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user_drawer, container, false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Referencias a las vistas
        val profileImageView = view.findViewById<ImageView>(R.id.profileImageView)
        val usernameText = view.findViewById<TextView>(R.id.usernameText)
        val emailText = view.findViewById<TextView>(R.id.emailText)
        val typeText = view.findViewById<TextView>(R.id.typeText)
        val extraInfoText = view.findViewById<TextView>(R.id.extraInfoText2)
        val editProfileButton = view.findViewById<Button>(R.id.editProfileButton)
        val logoutButton = view.findViewById<Button>(R.id.logoutButton)

        val user = auth.currentUser
        if (user != null) {
            val uid = user.uid

            // Cargar imagen de perfil desde Firebase Storage
            val profileImageRef = storage.reference.child("profileBackgroundImages/$uid.jpg")
            profileImageRef.downloadUrl
                .addOnSuccessListener { uri ->
                    Glide.with(this)
                        .load(uri)
                        .circleCrop()
                        .into(profileImageView)
                }
                .addOnFailureListener {
                    // Si no se encuentra la imagen, puedes mostrar una imagen predeterminada o dejarla en blanco
                    Glide.with(this)
                        .load(R.drawable.default_profile_image)  // Asegúrate de tener una imagen predeterminada
                        .circleCrop()
                        .into(profileImageView)
                }

            // Cargar datos del usuario
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

        // Botón para editar perfil
        editProfileButton.setOnClickListener {
            val intent = Intent(requireContext(), EditarPerfilActivity::class.java)
            startActivity(intent)
        }

        // Botón para cerrar sesión
        logoutButton.setOnClickListener {
            val homeActivity = activity as? HomeActivity
            homeActivity?.setUserOfflineAndStopLocation()

            auth.signOut()
            Toast.makeText(requireContext(), "Sesión cerrada", Toast.LENGTH_SHORT).show()

            val intent = Intent(requireContext(), MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
        }

        return view
    }
}