package com.programovil.misservicios1

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.functions.FirebaseFunctions

class OrderActivity : AppCompatActivity() {

    private lateinit var serviceImageView: ImageView
    private lateinit var serviceNameTextView: TextView
    private lateinit var serviceTypeTextView: TextView
    private lateinit var serviceEmailTextView: TextView
    private lateinit var quantityEditText: EditText
    private lateinit var noteEditText: EditText
    private lateinit var orderButton: Button
    private lateinit var storage: FirebaseStorage

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var functions: FirebaseFunctions

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_order)

        // Inicializar Firebase Functions
        functions = FirebaseFunctions.getInstance()

        // Inicializamos vistas
        serviceImageView = findViewById(R.id.serviceImage)
        serviceNameTextView = findViewById(R.id.serviceName)
        serviceTypeTextView = findViewById(R.id.serviceType)
        serviceEmailTextView = findViewById(R.id.serviceEmail)
        quantityEditText = findViewById(R.id.quantityEditText)
        noteEditText = findViewById(R.id.noteEditText)
        orderButton = findViewById(R.id.orderButton)
        storage = FirebaseStorage.getInstance()

        val serviceUid = intent.getStringExtra("SERVICE_UID")
        val clientUid = auth.currentUser?.uid

        if (serviceUid == null || clientUid == null) {
            Toast.makeText(this, "Datos faltantes", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db.collection("userServices").document(serviceUid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username") ?: "Sin nombre"
                    val serviceType = document.getString("serviceType") ?: "Sin tipo"
                    val email = document.getString("email") ?: "Sin correo"
                    val acceptOrders = document.getString("acceptOrders") ?: "false"

                    serviceNameTextView.text = "Proveedor: $username"
                    serviceTypeTextView.text = "Servicio: $serviceType"
                    serviceEmailTextView.text = "Correo: $email"

                    val imageRef = storage.reference.child("profileBackgroundImages/$serviceUid.jpg")
                    imageRef.downloadUrl
                        .addOnSuccessListener { uri ->
                            Glide.with(this)
                                .load(uri)
                                .circleCrop()
                                .into(serviceImageView)
                        }
                        .addOnFailureListener {
                            Glide.with(this)
                                .load(R.drawable.default_profile_image)
                                .circleCrop()
                                .into(serviceImageView)
                        }

                    val isAcceptingOrders = acceptOrders == "true"
                    orderButton.isEnabled = isAcceptingOrders
                    orderButton.alpha = if (isAcceptingOrders) 1f else 0.5f

                    if (!isAcceptingOrders) {
                        Toast.makeText(this, "Este proveedor no acepta pedidos", Toast.LENGTH_LONG).show()
                    }

                } else {
                    Toast.makeText(this, "Servicio no disponible", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar datos del servicio", Toast.LENGTH_SHORT).show()
                finish()
            }

        orderButton.setOnClickListener {
            val quantityStr = quantityEditText.text.toString().trim()
            val note = noteEditText.text.toString().trim()

            if (quantityStr.isEmpty() || quantityStr.toIntOrNull() == null || quantityStr.toInt() <= 0) {
                Toast.makeText(this, "Cantidad inválida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (note.length > 300) {
                Toast.makeText(this, "La nota no puede exceder 300 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val quantity = quantityStr.toInt()

            db.collection("locations").document(clientUid).get()
                .addOnSuccessListener { locationDoc ->
                    val lat = locationDoc.getDouble("latitude") ?: 0.0
                    val lon = locationDoc.getDouble("longitude") ?: 0.0

                    val orderData = hashMapOf(
                        "clienteId" to clientUid,
                        "proveedorId" to serviceUid,
                        "cantidad" to quantity,
                        "nota" to note,
                        "estado" to "pendiente",
                        "timestamp" to Timestamp.now(),
                        "ubicacionCliente" to hashMapOf(
                            "latitude" to lat,
                            "longitude" to lon
                        )
                    )

                    //Generar id pedido y guarda
                    val orderId = db.collection("dummy").document().id
                    orderData["orderId"] = orderId

                    val clientOrdersRef = db.collection("userClients").document(clientUid).collection("orders")
                    val serviceOrdersRef = db.collection("userServices").document(serviceUid).collection("orders")

                    // Guardar el pedido en ambas colecciones
                    clientOrdersRef.document(orderId).set(orderData)
                    serviceOrdersRef.document(orderId).set(orderData)
                        .addOnSuccessListener {
                            // Enviar notificación usando Cloud Function
                            enviarNotificacionConCloudFunction(serviceUid, clientUid, quantity, note)

                            Toast.makeText(this, "Pedido realizado exitosamente", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al realizar el pedido", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al obtener ubicación del cliente", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun enviarNotificacionConCloudFunction(serviceUid: String, clientUid: String, cantidad: Int, nota: String) {
        val data = hashMapOf(
            "proveedorId" to serviceUid,
            "clienteId" to clientUid,
            "cantidad" to cantidad,
            "nota" to nota
        )

        functions
            .getHttpsCallable("sendOrderNotification")
            .call(data)
            .addOnSuccessListener { result ->
                println("Notificación enviada exitosamente")
            }
            .addOnFailureListener { e ->
                println("Error al enviar notificación: ${e.message}")
                Toast.makeText(this, "Error al enviar notificación", Toast.LENGTH_SHORT).show()
            }
    }
}