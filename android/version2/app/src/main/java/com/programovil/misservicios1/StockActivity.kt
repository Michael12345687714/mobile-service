package com.programovil.misservicios1

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class StockActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var productName: TextView
    private lateinit var currentStock: TextView
    private lateinit var quantityInput: EditText
    private lateinit var updateButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stock)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas
        initViews()

        // Cargar datos actuales
        loadCurrentStock()

        // Configurar botón de actualizar
        setupUpdateButton()
    }

    private fun initViews() {
        productName = findViewById(R.id.productName)
        currentStock = findViewById(R.id.currentStock)
        quantityInput = findViewById(R.id.quantityInput)
        updateButton = findViewById(R.id.updateButton)
    }

    private fun loadCurrentStock() {
        val userId = auth.currentUser?.uid ?: return

        db.collection("userServices").document(userId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val username = document.getString("username") ?: "Producto"
                    val serviceType = document.getString("serviceType") ?: "Servicio"

                    // Ahora accede a la subcolección register_stock
                    db.collection("userServices").document(userId)
                        .collection("register_stock")
                        .get()
                        .addOnSuccessListener { querySnapshot ->
                            if (!querySnapshot.isEmpty) {
                                val stockDoc = querySnapshot.documents[0]
                                val stockActual = stockDoc.getLong("cant_stock")?.toInt() ?: 0

                                productName.text = "$serviceType - $username"
                                currentStock.text = "Stock: $stockActual unidades"
                            } else {
                                currentStock.text = "Stock: N/A"
                            }
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al cargar stock: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "No se encontró el documento del usuario.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al obtener el documento: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }


    private fun setupUpdateButton() {
        updateButton.setOnClickListener {
            val newQuantityText = quantityInput.text.toString().trim()

            if (newQuantityText.isEmpty()) {
                Toast.makeText(this, "Por favor ingresa una cantidad", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newQuantity = newQuantityText.toIntOrNull()
            if (newQuantity == null || newQuantity < 0) {
                Toast.makeText(this, "Por favor ingresa una cantidad válida", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            updateStock(newQuantity)
        }
    }

    private fun updateStock(newQuantity: Int) {
        val userId = auth.currentUser?.uid ?: return

        updateButton.isEnabled = false

        val stockCollection = db.collection("userServices").document(userId).collection("register_stock")

        stockCollection.get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    // Documento ya existe, lo actualizamos
                    val stockDoc = querySnapshot.documents[0]
                    val stockDocId = stockDoc.id

                    stockCollection.document(stockDocId)
                        .update("cant_stock", newQuantity)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Stock actualizado correctamente", Toast.LENGTH_SHORT).show()
                            currentStock.text = "Stock: $newQuantity unidades"
                            quantityInput.text.clear()
                            updateButton.isEnabled = true
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al actualizar stock: ${e.message}", Toast.LENGTH_SHORT).show()
                            updateButton.isEnabled = true
                        }

                } else {
                    // No hay documento, lo creamos con un ID generado automáticamente
                    val newStock = hashMapOf("cant_stock" to newQuantity)
                    stockCollection.add(newStock)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Stock creado y actualizado correctamente", Toast.LENGTH_SHORT).show()
                            currentStock.text = "Stock: $newQuantity unidades"
                            quantityInput.text.clear()
                            updateButton.isEnabled = true
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Error al crear stock: ${e.message}", Toast.LENGTH_SHORT).show()
                            updateButton.isEnabled = true
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error al acceder al stock: ${e.message}", Toast.LENGTH_SHORT).show()
                updateButton.isEnabled = true
            }
    }
}