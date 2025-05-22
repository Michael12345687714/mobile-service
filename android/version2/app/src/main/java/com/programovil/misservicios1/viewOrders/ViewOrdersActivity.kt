package com.programovil.misservicios1.viewOrders

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.programovil.misservicios1.R
import java.text.SimpleDateFormat
import java.util.*

class ViewOrdersActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: OrdersAdapter
    private lateinit var searchEditText: EditText
    private lateinit var statusSpinner: Spinner
    private lateinit var datePickerButton: Button
    private lateinit var clearDateButton: Button

    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var allOrders: List<Map<String, Any>> = listOf()
    private var selectedDate: String? = null
    private var selectedStatus: String = "Todos"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_orders)

        recyclerView = findViewById(R.id.ordersRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = OrdersAdapter()
        recyclerView.adapter = adapter

        searchEditText = findViewById(R.id.searchEditText)
        statusSpinner = findViewById(R.id.statusSpinner)
        datePickerButton = findViewById(R.id.datePickerButton)
        clearDateButton = findViewById(R.id.clearDateButton)

        setupFilters()

        val uid = auth.currentUser?.uid ?: return
        val userClientsRef = db.collection("userClients").document(uid)
        val userServicesRef = db.collection("userServices").document(uid)

        userClientsRef.get().addOnSuccessListener { doc ->
            if (doc.exists()) {
                loadOrders("userClients", uid)
            } else {
                userServicesRef.get().addOnSuccessListener { doc2 ->
                    if (doc2.exists()) {
                        loadOrders("userServices", uid)
                    } else {
                        Toast.makeText(this, "No se encontró el usuario", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }

    private fun setupFilters() {
        val estados = listOf("Todos", "pendiente", "aceptado", "finalizado")
        statusSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, estados)

        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedStatus = estados[position]
                applyFilters()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                applyFilters()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        datePickerButton.setOnClickListener {
            val calendar = Calendar.getInstance()
            DatePickerDialog(this, { _, year, month, day ->
                selectedDate = String.format("%02d/%02d/%04d", day, month + 1, year)
                datePickerButton.text = selectedDate
                applyFilters()
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
        }

        clearDateButton.setOnClickListener {
            selectedDate = null
            datePickerButton.text = "Seleccionar fecha"
            applyFilters()
        }
    }

    private fun applyFilters() {
        val searchQuery = searchEditText.text.toString().lowercase(Locale.getDefault())
        val filtered = allOrders.filter { order ->
            val nombre = (order["nombreOtroUsuario"] as? String)?.lowercase() ?: ""
            val nota = (order["nota"] as? String)?.lowercase() ?: ""
            val estado = order["estado"] as? String ?: ""
            val timestamp = order["timestamp"] as? Timestamp
            val fecha = timestamp?.toDate()?.let {
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(it)
            }

            val matchTexto = nombre.contains(searchQuery) || nota.contains(searchQuery)
            val matchEstado = selectedStatus == "Todos" || estado == selectedStatus
            val matchFecha = selectedDate == null || selectedDate == fecha

            matchTexto && matchEstado && matchFecha
        }

        adapter.setOrders(filtered)
    }

    private fun loadOrders(collection: String, uid: String) {
        val esCliente = collection == "userClients"
        db.collection(collection).document(uid).collection("orders")
            .orderBy("timestamp")
            .get()
            .addOnSuccessListener { result ->
                val orders = mutableListOf<Map<String, Any>>()
                val tasks = mutableListOf<com.google.android.gms.tasks.Task<*>>()

                for (doc in result) {
                    val data = doc.data.toMutableMap()
                    val otroId = if (esCliente) data["proveedorId"] else data["clienteId"]
                    if (otroId is String) {
                        val userCollection = if (esCliente) "userServices" else "userClients"
                        val task = db.collection(userCollection).document(otroId).get()
                            .addOnSuccessListener { userDoc ->
                                val nombre = userDoc.getString("username") ?: "Desconocido"
                                data["nombreOtroUsuario"] = nombre
                                orders.add(data)
                            }
                        tasks.add(task)
                    }
                }

                com.google.android.gms.tasks.Tasks.whenAllComplete(tasks)
                    .addOnSuccessListener {
                        allOrders = orders
                        applyFilters()
                    }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al cargar pedidos", Toast.LENGTH_SHORT).show()
            }
    }
}
