package com.programovil.misservicios1

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SearchView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.card.MaterialCardView
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import android.app.ProgressDialog
import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater

import android.widget.*
import android.view.ViewGroup
import java.text.SimpleDateFormat
import java.util.Locale
import com.google.android.gms.maps.model.LatLngBounds
import com.programovil.misservicios1.DirectionsApiService.DirectionsResponse

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.google.maps.android.PolyUtil
import com.google.android.gms.maps.model.PolylineOptions
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import android.text.InputType

import com.google.firebase.firestore.FieldValue

private const val GOOGLE_MAPS_API_BASE_URL = "https://maps.googleapis.com/maps/api/"
private lateinit var directionsApiService: DirectionsApiService
private val YOUR_API_KEY = "AIzaSyCARALN5S5FNKPF1WZQQoVPLSzlPk8_tp0"


class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentUserMarker: Marker? = null
    private var currentUserType: String? = null
    private var currentServiceType: String? = null
    private var isFirstLocationUpdate = true
    private var filteredServiceType: String? = null
    private lateinit var waterServiceCard: CardView
    private lateinit var gasServiceCard: CardView
    private lateinit var garbageServiceCard: CardView
    private lateinit var selectionIndicator: View
    private lateinit var servicesSection: ConstraintLayout
    private var currentSelectedCard: CardView? = null
    private lateinit var searchCardView: MaterialCardView
    private var clienteMarker: Marker? = null
    private val allServiceMarkers = mutableListOf<Triple<Marker, String, String?>>() // Marker, username, serviceType

    private lateinit var notificacionesContainer: ConstraintLayout
    private lateinit var closeNotificacionesButton: TextView

    private lateinit var notificationButton: ImageButton
    private lateinit var contenedorNotificaciones: LinearLayout
    private val notificacionesList = mutableListOf<Notificacion>()

    // clase para manejar los datos de notificaciones
    data class Notificacion(
        val id: String = "",
        val cantidad: Int = 0,
        val clienteId: String = "",
        val estado: String = "pendiente",
        val nota: String = "",
        val proveedorId: String = "",
        val timestamp: com.google.firebase.Timestamp? = null,
        val ubicacionCliente: Map<String, Double>? = null,
        var nombreCliente: String = "",

        var nombreProveedor: String = "",
        var servicioTipo: String = "",
        var orderId: String = "",
        var tipoServicio: String = "",
        val motivoRechazo: String? = null,
        val fechaRechazo: Timestamp? = null
    )

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val USER_ICON_SIZE_DP = 50
        private const val PROVIDER_ICON_SIZE_DP = 40
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        // Inicializaciones FIREBASE
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)


        val retrofit = Retrofit.Builder()
            .baseUrl(GOOGLE_MAPS_API_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        directionsApiService = retrofit.create(DirectionsApiService::class.java)

        val stockCounterText = findViewById<TextView>(R.id.stock_counter)


        cargarStockDisponible(stockCounterText)

        initViews()
        setupServicesSection()

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initViews() {
        waterServiceCard = findViewById(R.id.waterServiceCard)
        gasServiceCard = findViewById(R.id.gasServiceCard)
        garbageServiceCard = findViewById(R.id.garbageServiceCard)
        selectionIndicator = findViewById(R.id.selectionIndicator)
        servicesSection = findViewById(R.id.servicesSection)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val menuButton = findViewById<View>(R.id.menuButton)


        val searchCardView = findViewById<MaterialCardView>(R.id.searchCardView)
        val searchView = findViewById<SearchView>(R.id.searchView)
        val searchButton = findViewById<ImageButton>(R.id.searchButton)

        searchButton.setOnClickListener {

            if (searchCardView.visibility == View.VISIBLE) {

                searchCardView.visibility = View.GONE

                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
            } else {

                searchCardView.visibility = View.VISIBLE

                searchView.isIconified = false
                searchView.requestFocus()
            }
        }


        searchView.setOnCloseListener {
            searchCardView.visibility = View.GONE
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(searchView.windowToken, 0)
            false
        }

        val searchSrcTextId = searchView.context.resources.getIdentifier(
            "android:id/search_src_text", null, null
        )
        val searchSrcText = searchView.findViewById<EditText>(searchSrcTextId)

        val searchMagId = searchView.context.resources.getIdentifier(
            "android:id/search_mag_icon", null, null
        )
        val searchMagIcon = searchView.findViewById<ImageView>(searchMagId)


        searchMagIcon?.setOnClickListener {
            val query = searchSrcText?.text.toString()
            if (query.isNotEmpty()) {
                searchServiceProvider(query)
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                searchCardView.visibility = View.GONE
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.drawerFragmentContainer, UserDrawerFragment())
            .commit()

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        // Actualizar la visibilidad de searchCardView, no searchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchServiceProvider(query)
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchView.windowToken, 0)
                    searchCardView.visibility = View.GONE
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                return true
            }
        })

        // Inicializar componentes de notificaciones
        notificationButton = findViewById(R.id.notificationButton)
        contenedorNotificaciones = findViewById(R.id.contenedorNotificaciones)
        notificacionesContainer = findViewById(R.id.notificacionesContainer)
        closeNotificacionesButton = findViewById(R.id.close_notificaciones_button)

        // Configurar el botón de cierre en el encabezado de notificaciones
        closeNotificacionesButton.setOnClickListener {
            notificacionesContainer.animate()
                .alpha(0f)
                .setDuration(300)
                .withEndAction {
                    notificacionesContainer.visibility = View.GONE
                }
                .start()
        }

        // Configurar el botón de notificaciones
        notificationButton.setOnClickListener {
            if (notificacionesContainer.visibility == View.VISIBLE) {
                // Si está visible, ocultar
                notificacionesContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction {
                        notificacionesContainer.visibility = View.GONE
                    }
                    .start()
            } else {
                // Si está oculto, cargar notificaciones y mostrar
                cargarNotificaciones()
            }
        }
    }

    private fun cargarStockDisponible(stockCounterText: TextView) {
        val currentUser = auth.currentUser ?: return
        val proveedorId = currentUser.uid

        db.collection("userServices")
            .document(proveedorId)
            .collection("register_stock")
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    stockCounterText.text = "Error"
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    var stockTotal = 0
                    for (doc in snapshots) {
                        val cantidad = doc.getLong("cant_stock")?.toInt() ?: 0
                        stockTotal += cantidad
                    }
                    stockCounterText.text = stockTotal.toString()
                }
            }
    }


    private fun cargarNotificaciones() {
        mostrarCargando()
        val stockContainer = findViewById<LinearLayout>(R.id.stockContainer)
        val currentUser = auth.currentUser
        if (currentUser == null) {
            mostrarNoNotificaciones("Debe iniciar sesión para ver notificaciones")
            return
        }

        val userId = currentUser.uid
        Log.d("NotificacionesDebug", "Usuario actual ID: $userId")

        // Primero verificar si es un userClient
        db.collection("userClients")
            .document(userId)
            .get()
            .addOnSuccessListener { clientDocument ->
                if (clientDocument.exists()) {
                    val userType = clientDocument.getString("userType") ?: ""
                    Log.d("NotificacionesDebug", "Usuario encontrado en userClients - Tipo: $userType")

                    if (userType == "Cliente") {
                        Log.d("NotificacionesDebug", "Es un cliente, redirigiendo a cargarNotificacionesCliente()")
                        // Ocultar el stockContainer si es cliente

                        stockContainer?.visibility = View.GONE
                        cargarNotificacionesCliente()
                    } else {
                        Log.w("NotificacionesDebug", "Usuario en userClients pero tipo no es 'Cliente': $userType")
                        mostrarNoNotificaciones("Tipo de usuario no válido para ver notificaciones")
                    }
                } else {
                    Log.d("NotificacionesDebug", "Usuario no encontrado en userClients, verificando userServices")

                    // Si no está en userClients, verificar en userServices
                    db.collection("userServices")
                        .document(userId)
                        .get()
                        .addOnSuccessListener { serviceDocument ->
                            if (serviceDocument.exists()) {
                                val userType = serviceDocument.getString("userType") ?: ""
                                Log.d("NotificacionesDebug", "Usuario encontrado en userServices - Tipo: $userType")

                                if (userType == "Servicio") {
                                    Log.d("NotificacionesDebug", "Es un proveedor de servicios, cargando notificaciones de pedidos")

                                    stockContainer?.visibility = View.VISIBLE
                                    cargarNotificacionesProveedor(userId)


                                } else {
                                    Log.w("NotificacionesDebug", "Usuario en userServices pero tipo no es 'Servicio': $userType")
                                    mostrarNoNotificaciones("Tipo de usuario no válido para ver notificaciones")
                                }
                            } else {
                                Log.e("NotificacionesDebug", "Usuario no encontrado ni en userClients ni en userServices")
                                mostrarNoNotificaciones("Usuario no encontrado en el sistema")
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("NotificacionesDebug", "Error al verificar userServices: ${e.message}")
                            mostrarNoNotificaciones("Error al verificar tipo de usuario: ${e.message}")
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificacionesDebug", "Error al verificar userClients: ${e.message}")
                mostrarNoNotificaciones("Error al verificar tipo de usuario: ${e.message}")
            }
    }


    private fun cargarNotificacionesProveedor(proveedorId: String) {
        Log.d("NotificacionesDebug", "Iniciando carga de notificaciones para proveedor: $proveedorId")

        db.collection("userServices")
            .document(proveedorId)
            .collection("orders")
            .whereIn("estado", listOf("pendiente", "aceptado"))
            .get()
            .addOnSuccessListener { documents ->
                Log.d("FirestoreDebug", "Pedidos pendientes encontrados: ${documents.size()}")

                // Limpiar lista anterior
                notificacionesList.clear()

                if (documents.isEmpty) {
                    Log.d("NotificacionesDebug", "No hay pedidos pendientes")
                    mostrarNoNotificaciones("No hay pedidos pendientes en este momento")
                } else {
                    Log.d("NotificacionesDebug", "Procesando ${documents.size()} notificaciones")

                    // Procesar cada notificación y obtener datos adicionales necesarios
                    var notificacionesProcesadas = 0
                    val totalNotificaciones = documents.size()

                    for (document in documents) {
                        val notificacion = document.toObject(Notificacion::class.java).copy(id = document.id)
                        Log.d("NotificacionesDebug", "Procesando notificación ID: ${document.id}, Cliente ID: ${notificacion.clienteId}")

                        db.collection("userClients")
                            .document(notificacion.clienteId)
                            .get()
                            .addOnSuccessListener { clientDocument ->
                                if (clientDocument.exists()) {
                                    notificacion.nombreCliente = clientDocument.getString("username") ?: "Cliente"
                                    Log.d("NotificacionesDebug", "Cliente encontrado: ${notificacion.nombreCliente}")
                                } else {
                                    notificacion.nombreCliente = "Cliente #${notificacion.clienteId.take(5)}"
                                    Log.w("NotificacionesDebug", "Cliente no encontrado, usando nombre genérico: ${notificacion.nombreCliente}")
                                }

                                notificacionesList.add(notificacion)
                                notificacionesProcesadas++

                                Log.d("NotificacionesDebug", "Notificaciones procesadas: $notificacionesProcesadas de $totalNotificaciones")

                                if (notificacionesProcesadas == totalNotificaciones) {
                                    Log.d("NotificacionesDebug", "Todas las notificaciones procesadas, ordenando y mostrando")
                                    notificacionesList.sortByDescending { it.timestamp }
                                    mostrarNotificaciones()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("NotificacionesDebug", "Error al obtener datos del cliente ${notificacion.clienteId}: ${e.message}")

                                notificacion.nombreCliente = "Cliente #${notificacion.clienteId.take(5)}"
                                notificacionesList.add(notificacion)
                                notificacionesProcesadas++

                                if (notificacionesProcesadas == totalNotificaciones) {
                                    Log.d("NotificacionesDebug", "Todas las notificaciones procesadas (con algunos errores), ordenando y mostrando")
                                    notificacionesList.sortByDescending { it.timestamp }
                                    mostrarNotificaciones()
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificacionesDebug", "Error al cargar pedidos del proveedor: ${e.message}")
                mostrarNoNotificaciones("Error al cargar pedidos: ${e.message}")
            }
    }


    // Método para mostrar un indicador de carga
    private fun mostrarCargando() {
        // Limpiar el contenedor
        contenedorNotificaciones.removeAllViews()
        val progressBar = ProgressBar(this)
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.gravity = Gravity.CENTER
        params.topMargin = 50
        params.bottomMargin = 50
        progressBar.layoutParams = params

        contenedorNotificaciones.addView(progressBar)

        // Hacer visible el contenedor
        notificacionesContainer.alpha = 0f
        notificacionesContainer.visibility = View.VISIBLE
        notificacionesContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    // Método para mostrar que no hay notificaciones
    private fun mostrarNoNotificaciones(mensaje: String = "No hay pedidos pendientes en este momento") {
        // Limpiar el contenedor
        contenedorNotificaciones.removeAllViews()

        // Inflar la vista de no notificaciones
        val noNotificacionesView = LayoutInflater.from(this).inflate(
            R.layout.no_notificaciones_layout, contenedorNotificaciones, false
        )

        val mensajeTextView = noNotificacionesView.findViewById<TextView>(R.id.no_hay_notificaciones_text)


        if (mensajeTextView != null) {
            mensajeTextView.text = mensaje
        } else {

            val allTextViews = ArrayList<TextView>()
            findAllTextViews(noNotificacionesView, allTextViews)
            if (allTextViews.isNotEmpty()) {

                allTextViews[0].text = mensaje
            }
        }


        contenedorNotificaciones.addView(noNotificacionesView)


        if (notificacionesContainer.visibility != View.VISIBLE) {
            notificacionesContainer.alpha = 0f
            notificacionesContainer.visibility = View.VISIBLE
            notificacionesContainer.animate()
                .alpha(1f)
                .setDuration(300)
                .start()
        }
    }

    // Método auxiliar para encontrar todos los TextView en un layout
    private fun findAllTextViews(view: View, textViews: ArrayList<TextView>) {
        if (view is TextView) {
            textViews.add(view)
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAllTextViews(view.getChildAt(i), textViews)
            }
        }
    }



    // Función modificada que verifica el estado y llama a la función correspondiente
    private fun mostrarNotificaciones() {
        // Limpiar el contenedor
        contenedorNotificaciones.removeAllViews()

        for (notificacion in notificacionesList) {
           // val notificacionView = if (notificacion.estado == "finalizar") {
            val notificacionView = if (notificacion.estado == "aceptado") {
                // Si el estado es aceptado, usar la vista con botón "Finalizar"
                crearVistaNotificacionFinalizar(notificacion)
            } else {
                // Para otros estados, usar la vista normal
                crearVistaNotificacion(notificacion)
            }
            contenedorNotificaciones.addView(notificacionView)
        }

        // Agregar una vista espaciadora al final
        val espaciador = View(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (170 * resources.displayMetrics.density).toInt() // 170dp en pixels
        )
        espaciador.layoutParams = layoutParams
        contenedorNotificaciones.addView(espaciador)

        // Hacer visible el contenedor
        notificacionesContainer.alpha = 0f
        notificacionesContainer.visibility = View.VISIBLE
        notificacionesContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        actualizarContadorNotificaciones(notificacionesList.size)
    }

    // Nueva función para crear vista de notificación con botón "Finalizar"
    private fun crearVistaNotificacionFinalizar(notificacion: Notificacion): View {
        // Inflar la vista desde el layout
        val notificacionView = LayoutInflater.from(this).inflate(
            R.layout.item_notificacion, contenedorNotificaciones, false
        )

        // Obtener referencias a las vistas
        val cardView = notificacionView.findViewById<androidx.cardview.widget.CardView>(R.id.card_notificacion)
        val tituloPedido = notificacionView.findViewById<TextView>(R.id.titulo_pedido)
        val cantidadPedido = notificacionView.findViewById<TextView>(R.id.cantidad_pedido)
        val notaPedido = notificacionView.findViewById<TextView>(R.id.nota_pedido)
        val clientePedido = notificacionView.findViewById<TextView>(R.id.cliente_pedido)
        val fechaPedido = notificacionView.findViewById<TextView>(R.id.fecha_pedido)
        val stockWarning = notificacionView.findViewById<TextView>(R.id.stock_warning)
        val btnAceptar = notificacionView.findViewById<Button>(R.id.btn_aceptar)
        val btnRechazar = notificacionView.findViewById<Button>(R.id.btn_rechazar)
        val btnVerRuta = notificacionView.findViewById<Button>(R.id.btn_ver_ruta)

        // Configurar los datos básicos
        tituloPedido.text = "📍 Pedido # ${notificacion.id.take(8)}"
        cantidadPedido.text = " ${notificacion.cantidad}"
        notaPedido.text = " ${notificacion.nota}"
        clientePedido.text = "${notificacion.nombreCliente}"

        // Formatear fecha y hora
        val fechaHora = if (notificacion.timestamp != null) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateFormat.format(notificacion.timestamp.toDate())
        } else {
            "Fecha no disponible"
        }
        fechaPedido.text = "$fechaHora"

        // Ocultar el warning de stock ya que el pedido fue aceptado
        stockWarning.visibility = View.GONE

        // Cambiar el texto del botón "Aceptar" a "Finalizar"
        btnAceptar.text = "Finalizar"
        btnAceptar.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))

        // Ocultar el botón "Rechazar" ya que el pedido ya fue aceptado
        btnRechazar.visibility = View.GONE

        val stockCounterText = findViewById<TextView>(R.id.stock_counter)
        cargarStockDisponible(stockCounterText)

        // Configurar el botón "Finalizar"
        btnAceptar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "finalizado")
        }

        // Configurar el botón "Ver Ruta"
        btnVerRuta.setOnClickListener {
            notificacion.ubicacionCliente?.let { clientLocation ->
                mostrarRuta(LatLng(clientLocation["latitude"]!!, clientLocation["longitude"]!!))
            } ?: run {
                Toast.makeText(this@HomeActivity, "Ubicación del cliente no disponible", Toast.LENGTH_SHORT).show()
            }
        }

        return notificacionView
    }

    // Tu función original se mantiene igual para otros estados
    private fun crearVistaNotificacion(notificacion: Notificacion): View {
        // Inflar la vista desde el layout
        val notificacionView = LayoutInflater.from(this).inflate(
            R.layout.item_notificacion, contenedorNotificaciones, false
        )

        // Obtener referencias a las vistas
        val cardView = notificacionView.findViewById<androidx.cardview.widget.CardView>(R.id.card_notificacion)
        val tituloPedido = notificacionView.findViewById<TextView>(R.id.titulo_pedido)
        val cantidadPedido = notificacionView.findViewById<TextView>(R.id.cantidad_pedido)
        val notaPedido = notificacionView.findViewById<TextView>(R.id.nota_pedido)
        val clientePedido = notificacionView.findViewById<TextView>(R.id.cliente_pedido)
        val fechaPedido = notificacionView.findViewById<TextView>(R.id.fecha_pedido)
        val stockWarning = notificacionView.findViewById<TextView>(R.id.stock_warning)
        val btnAceptar = notificacionView.findViewById<Button>(R.id.btn_aceptar)
        val btnRechazar = notificacionView.findViewById<Button>(R.id.btn_rechazar)
        val btnVerRuta = notificacionView.findViewById<Button>(R.id.btn_ver_ruta)

        // Configurar los datos básicos
        tituloPedido.text = "📍 Pedido # ${notificacion.id.take(8)}"
        cantidadPedido.text = " ${notificacion.cantidad}"
        notaPedido.text = " ${notificacion.nota}"
        clientePedido.text = "${notificacion.nombreCliente}"

        // Formatear fecha y hora
        val fechaHora = if (notificacion.timestamp != null) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateFormat.format(notificacion.timestamp.toDate())
        } else {
            "Fecha no disponible"
        }
        fechaPedido.text = "$fechaHora"

        verificarStockYConfigurarVista(notificacion, cardView, stockWarning, btnAceptar, btnRechazar)

        val stockCounterText = findViewById<TextView>(R.id.stock_counter)
        cargarStockDisponible(stockCounterText)

        // Configurar los botones
        btnAceptar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "aceptado")
        }

        btnRechazar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "rechazado")
        }

        // Configurar el botón "Ver Ruta"
        btnVerRuta.setOnClickListener {
            notificacion.ubicacionCliente?.let { clientLocation ->
                mostrarRuta(LatLng(clientLocation["latitude"]!!, clientLocation["longitude"]!!))
            } ?: run {
                Toast.makeText(this@HomeActivity, "Ubicación del cliente no disponible", Toast.LENGTH_SHORT).show()
            }
        }

        return notificacionView
    }



    private fun cargarNotificacionesCliente() {
        Log.d("NotificacionesDebug", "Iniciando carga de notificaciones para cliente")

        val currentUser = auth.currentUser
        if (currentUser == null) {
            mostrarNoNotificaciones("Debe iniciar sesión para ver notificaciones")
            return
        }

        val clienteId = currentUser.uid
        Log.d("NotificacionesDebug", "Cliente ID: $clienteId")

        // Buscar pedidos del cliente en la colección userClients
        db.collection("userClients")
            .document(clienteId)
            .collection("orders")
            .get()
            .addOnSuccessListener { documents ->
                Log.d("FirestoreDebug", "Pedidos del cliente encontrados: ${documents.size()}")

                // Limpiar lista anterior
                notificacionesList.clear()

                if (documents.isEmpty) {
                    Log.d("NotificacionesDebug", "No hay pedidos realizados")
                    mostrarNoNotificaciones("No has realizado pedidos aún")
                } else {
                    Log.d("NotificacionesDebug", "Procesando ${documents.size()} pedidos")

                    // Procesar cada pedido y obtener datos del proveedor
                    var pedidosProcesados = 0
                    val totalPedidos = documents.size()

                    for (document in documents) {
                        val notificacion = document.toObject(Notificacion::class.java).copy(id = document.id)
                        Log.d("NotificacionesDebug", "Procesando pedido ID: ${document.id}, Proveedor ID: ${notificacion.proveedorId}")

                        // Obtener datos del proveedor desde userServices
                        db.collection("userServices")
                            .document(notificacion.proveedorId)
                            .get()
                            .addOnSuccessListener { proveedorDocument ->
                                if (proveedorDocument.exists()) {
                                    // Extraer username y serviceType del proveedor
                                    notificacion.nombreProveedor = proveedorDocument.getString("username") ?: "Proveedor"
                                    notificacion.tipoServicio = proveedorDocument.getString("serviceType") ?: "Servicio"
                                    Log.d("NotificacionesDebug", "Proveedor encontrado: ${notificacion.nombreProveedor}, Tipo: ${notificacion.tipoServicio}")
                                } else {
                                    notificacion.nombreProveedor = "Proveedor #${notificacion.proveedorId.take(5)}"
                                    notificacion.tipoServicio = "Servicio"
                                    Log.w("NotificacionesDebug", "Proveedor no encontrado, usando nombre genérico: ${notificacion.nombreProveedor}")
                                }

                                notificacionesList.add(notificacion)
                                pedidosProcesados++

                                Log.d("NotificacionesDebug", "Pedidos procesados: $pedidosProcesados de $totalPedidos")

                                if (pedidosProcesados == totalPedidos) {
                                    Log.d("NotificacionesDebug", "Todos los pedidos procesados, ordenando y mostrando")
                                    notificacionesList.sortByDescending { it.timestamp }
                                    mostrarNotificacionesCliente()
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("NotificacionesDebug", "Error al obtener datos del proveedor ${notificacion.proveedorId}: ${e.message}")

                                notificacion.nombreProveedor = "Proveedor #${notificacion.proveedorId.take(5)}"
                                notificacion.tipoServicio = "Servicio"
                                notificacionesList.add(notificacion)
                                pedidosProcesados++

                                if (pedidosProcesados == totalPedidos) {
                                    Log.d("NotificacionesDebug", "Todos los pedidos procesados (con algunos errores), ordenando y mostrando")
                                    notificacionesList.sortByDescending { it.timestamp }
                                    mostrarNotificacionesCliente()
                                }
                            }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.e("NotificacionesDebug", "Error al cargar pedidos del cliente: ${e.message}")
                mostrarNoNotificaciones("Error al cargar pedidos: ${e.message}")
            }
    }

    // Función para mostrar las notificaciones específicas del cliente
    private fun mostrarNotificacionesCliente() {
        // Limpiar el contenedor
        contenedorNotificaciones.removeAllViews()

        for (notificacion in notificacionesList) {
            val notificacionView = crearVistaNotificacionCliente(notificacion)
            contenedorNotificaciones.addView(notificacionView)
        }

        // Agregar una vista espaciadora al final
        val espaciador = View(this)
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (170 * resources.displayMetrics.density).toInt() // 170dp en pixels
        )
        espaciador.layoutParams = layoutParams
        contenedorNotificaciones.addView(espaciador)

        // Hacer visible el contenedor
        notificacionesContainer.alpha = 0f
        notificacionesContainer.visibility = View.VISIBLE
        notificacionesContainer.animate()
            .alpha(1f)
            .setDuration(300)
            .start()

        actualizarContadorNotificaciones(notificacionesList.size)
    }

    // Función para crear la vista específica de notificación para cliente
    // Función para crear la vista específica de notificación para cliente
    private fun crearVistaNotificacionCliente(notificacion: Notificacion): View {
        // Inflar la vista desde el layout
        val notificacionView = LayoutInflater.from(this).inflate(
            R.layout.item_notificacion_cliente, contenedorNotificaciones, false
        )

        // Obtener referencias a las vistas
        val cardView = notificacionView.findViewById<androidx.cardview.widget.CardView>(R.id.card_notificacion_cliente)
        val tituloPedido = notificacionView.findViewById<TextView>(R.id.titulo_pedido_cliente)
        val nombreProveedor = notificacionView.findViewById<TextView>(R.id.nombre_proveedor)
        val tipoServicio = notificacionView.findViewById<TextView>(R.id.tipo_servicio)
        val cantidadPedido = notificacionView.findViewById<TextView>(R.id.cantidad_pedido_cliente)
        val estadoPedido = notificacionView.findViewById<TextView>(R.id.estado_pedido)
        val notaPedido = notificacionView.findViewById<TextView>(R.id.nota_pedido_cliente)
        val fechaPedido = notificacionView.findViewById<TextView>(R.id.fecha_pedido_cliente)
        val motivoRechazo = notificacionView.findViewById<TextView>(R.id.motivo_rechazo_cliente)
        val btnEliminar = notificacionView.findViewById<ImageButton>(R.id.btn_eliminar_notificacion)

        // Configurar los datos
        tituloPedido.text = "📦 Pedido # ${notificacion.id.take(8)}"
        nombreProveedor.text = "Proveedor: ${notificacion.nombreProveedor}"
        tipoServicio.text = "Servicio: ${notificacion.tipoServicio}"
        cantidadPedido.text = "Cantidad: ${notificacion.cantidad}"
        estadoPedido.text = "Estado: ${notificacion.estado}"
        notaPedido.text = "Nota: ${notificacion.nota}"

        // Formatear fecha y hora
        val fechaHora = if (notificacion.timestamp != null) {
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            dateFormat.format(notificacion.timestamp.toDate())
        } else {
            "Fecha no disponible"
        }
        fechaPedido.text = "Fecha: $fechaHora"

        // Configurar color del estado y mostrar motivo de rechazo si corresponde
        when (notificacion.estado) {
            "pendiente" -> {
                estadoPedido.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                motivoRechazo.visibility = View.GONE
            }
            "aceptado" -> {
                estadoPedido.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark))
                motivoRechazo.visibility = View.GONE
            }
            "finalizado" -> {
                estadoPedido.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                motivoRechazo.visibility = View.GONE
            }
            "rechazado" -> {
                estadoPedido.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

                // Mostrar motivo de rechazo si existe
                if (!notificacion.motivoRechazo.isNullOrEmpty()) {
                    motivoRechazo.text = "❌ Motivo de rechazo: ${notificacion.motivoRechazo}"
                    motivoRechazo.visibility = View.VISIBLE
                } else {
                    motivoRechazo.text = "❌ Motivo de rechazo: No especificado"
                    motivoRechazo.visibility = View.VISIBLE
                }
            }
            else -> {
                motivoRechazo.visibility = View.GONE
            }
        }

        // Configurar el botón de eliminar
        btnEliminar.setOnClickListener {
            mostrarDialogoConfirmacionEliminar(notificacion, notificacionView)
        }

        return notificacionView
    }

    // Función para mostrar diálogo de confirmación antes de eliminar
    private fun mostrarDialogoConfirmacionEliminar(notificacion: Notificacion, notificacionView: View) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Eliminar notificación")
        builder.setMessage("¿Estás seguro de que deseas eliminar esta notificación? Esta acción no se puede deshacer.")

        builder.setPositiveButton("Eliminar") { _, _ ->
            eliminarNotificacion(notificacion, notificacionView)
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }

    // Función para eliminar la notificación de Firebase
    private fun eliminarNotificacion(notificacion: Notificacion, notificacionView: View) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val clienteId = currentUser.uid

        // Mostrar progress mientras se elimina
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Eliminando notificación...")
        progressDialog.show()

        // Eliminar de Firebase
        db.collection("userClients")
            .document(clienteId)
            .collection("orders")
            .document(notificacion.id)
            .delete()
            .addOnSuccessListener {
                Log.d("NotificacionesDebug", "Notificación eliminada exitosamente: ${notificacion.id}")

                // Eliminar de la lista local
                val index = notificacionesList.indexOfFirst { it.id == notificacion.id }
                if (index != -1) {
                    notificacionesList.removeAt(index)
                }

                // Animar la eliminación de la vista
                notificacionView.animate()
                    .alpha(0f)
                    .scaleX(0f)
                    .scaleY(0f)
                    .setDuration(300)
                    .withEndAction {
                        // Remover la vista del contenedor
                        contenedorNotificaciones.removeView(notificacionView)

                        // Actualizar el contador
                        actualizarContadorNotificaciones(notificacionesList.size)

                        // Si no quedan notificaciones, mostrar mensaje
                        if (notificacionesList.isEmpty()) {
                            mostrarNoNotificaciones("No tienes notificaciones")
                        }
                    }
                    .start()

                progressDialog.dismiss()
                Toast.makeText(this, "Notificación eliminada", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e("NotificacionesDebug", "Error al eliminar notificación: ${e.message}")
                progressDialog.dismiss()
                Toast.makeText(this, "Error al eliminar: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }




    private fun mostrarRuta(destination: LatLng) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Permisos de ubicación no concedidos", Toast.LENGTH_SHORT).show()
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let { currentLoc ->
                val origin = LatLng(currentLoc.latitude, currentLoc.longitude)
                drawRoute(origin, destination)
            } ?: run {
                Toast.makeText(this, "No se pudo obtener la ubicación actual", Toast.LENGTH_SHORT).show()
            }
        }
    }
    private fun drawRoute(origin: LatLng, destination: LatLng) {
        val originStr = "${origin.latitude},${origin.longitude}"
        val destinationStr = "${destination.latitude},${destination.longitude}"

        directionsApiService.getDirections(originStr, destinationStr, YOUR_API_KEY)
            .enqueue(object : retrofit2.Callback<DirectionsResponse> {
                override fun onResponse(
                    call: retrofit2.Call<DirectionsResponse>,
                    response: retrofit2.Response<DirectionsResponse>
                ) {
                    if (response.isSuccessful) {
                        val routes = response.body()?.routes
                        if (!routes.isNullOrEmpty()) {
                            val points = routes[0].legs[0].steps.flatMap { it.polyline.points.let { p -> PolyUtil.decode(p) } }
                            val polylineOptions = PolylineOptions()
                                .addAll(points)
                                .width(12f)
                                .color(Color.GREEN)
                                .geodesic(true)

                            mMap.addPolyline(polylineOptions)

                            // Zoom to fit the route
                            val builder = LatLngBounds.Builder()
                            builder.include(origin)
                            builder.include(destination)
                            val bounds = builder.build()
                            val padding = 100 // Padding in pixels
                            val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)
                            mMap.animateCamera(cameraUpdate)

                            // Add markers
                            mMap.addMarker(MarkerOptions().position(origin).title("Tu Ubicación"))
                            clienteMarker = mMap.addMarker(MarkerOptions().position(destination).title("Ubicación del Cliente")) // Guardamos el marcador del cliente

                        } else {
                            Toast.makeText(this@HomeActivity, "No se encontraron rutas", Toast.LENGTH_SHORT).show()
                            val builder = LatLngBounds.Builder()
                            builder.include(origin)
                            builder.include(destination)
                            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                            mMap.addMarker(MarkerOptions().position(origin).title("Tu Ubicación"))
                            clienteMarker = mMap.addMarker(MarkerOptions().position(destination).title("Ubicación del Cliente")) // Guardamos el marcador del cliente
                        }
                    } else {
                        Toast.makeText(this@HomeActivity, "Error al obtener la ruta: ${response.code()}", Toast.LENGTH_SHORT).show()
                        val builder = LatLngBounds.Builder()
                        builder.include(origin)
                        builder.include(destination)
                        mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                        mMap.addMarker(MarkerOptions().position(origin).title("Tu Ubicación"))
                        clienteMarker = mMap.addMarker(MarkerOptions().position(destination).title("Ubicación del Cliente")) // Guardamos el marcador del cliente
                    }
                }

                override fun onFailure(call: retrofit2.Call<DirectionsResponse>, t: Throwable) {
                    Toast.makeText(
                        this@HomeActivity,
                        "Fallo al obtener la ruta: ${t.localizedMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                    val origin = LatLng(origin.latitude, origin.longitude)
                    val destination = LatLng(destination.latitude, destination.longitude)
                    val builder = LatLngBounds.Builder()
                    builder.include(origin)
                    builder.include(destination)
                    mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 150))
                    mMap.addMarker(MarkerOptions().position(origin).title("Tu Ubicación"))
                    clienteMarker = mMap.addMarker(MarkerOptions().position(destination).title("Ubicación del Cliente")) // Guardamos el marcador del cliente
                }
            })
    }

    // Método para verificar stock y configurar la vista según disponibilidad

    private fun verificarStockYConfigurarVista(
        notificacion: Notificacion,
        cardView: androidx.cardview.widget.CardView,
        stockWarning: TextView,
        btnAceptar: Button,
        btnRechazar: Button
    ) {
        val currentUser = auth.currentUser
        if (currentUser == null) return

        val proveedorId = currentUser.uid

        db.collection("userServices")
            .document(proveedorId)
            .collection("register_stock")
            .get()
            .addOnSuccessListener { documents ->
                var stockDisponible = 0

                for (document in documents) {
                    val cantStock = document.getLong("cant_stock")?.toInt() ?: 0
                    stockDisponible += cantStock
                }

                if (stockDisponible < notificacion.cantidad) {
                    configurarVistaStockInsuficiente(
                        cardView, stockWarning, btnAceptar, btnRechazar,
                        stockDisponible, notificacion.cantidad, notificacion
                    )
                } else {
                    configurarVistaNormal(
                        cardView, stockWarning, btnAceptar, btnRechazar, notificacion
                    )
                }
            }
            .addOnFailureListener { e ->
                configurarVistaStockInsuficiente(
                    cardView, stockWarning, btnAceptar, btnRechazar,
                    0, notificacion.cantidad, notificacion
                )
            }
    }

    // Configurar vista cuando no hay suficiente stock
    private fun configurarVistaStockInsuficiente(
        cardView: androidx.cardview.widget.CardView,
        stockWarning: TextView,
        btnAceptar: Button,
        btnRechazar: Button,
        stockDisponible: Int,
        cantidadSolicitada: Int,
        notificacion: Notificacion
    ) {
        // Cambiar color de fondo a rojo de advertencia
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_warning_background))

        // Mostrar mensaje de advertencia
        stockWarning.visibility = View.VISIBLE
        stockWarning.text = "⚠️ STOCK INSUFICIENTE\nDisponible: $stockDisponible | Solicitado: $cantidadSolicitada"

        // Deshabilitar botón aceptar
        btnAceptar.isEnabled = false
        btnAceptar.setBackgroundColor(ContextCompat.getColor(this, R.color.button_disabled))
        btnAceptar.text = "SIN STOCK"

        // Mantener botón rechazar habilitado pero con color de advertencia
        btnRechazar.isEnabled = true
        btnRechazar.setBackgroundColor(ContextCompat.getColor(this, R.color.button_reject_warning))
        btnRechazar.text = "RECHAZAR"

        // Configurar solo el listener de rechazar
        btnRechazar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "rechazado")
        }
    }

    // Configurar vista normal cuando hay suficiente stock

    private fun configurarVistaNormal(
        cardView: androidx.cardview.widget.CardView,
        stockWarning: TextView,
        btnAceptar: Button,
        btnRechazar: Button,
        notificacion: Notificacion
    ) {
        // Color normal de la tarjeta
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_normal_background))

        stockWarning.visibility = View.GONE

        btnAceptar.isEnabled = true
        btnAceptar.setBackgroundColor(ContextCompat.getColor(this, R.color.button_accept))
        btnAceptar.text = "ACEPTAR"

        btnRechazar.isEnabled = true
        btnRechazar.setBackgroundColor(ContextCompat.getColor(this, R.color.button_reject))
        btnRechazar.text = "RECHAZAR"

        // Configurar ambos listeners
        btnAceptar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "aceptado")
        }

        btnRechazar.setOnClickListener {
            actualizarEstadoNotificacion(notificacion.id, "rechazado")
        }
    }



    //motivo de rechaso

    private fun actualizarEstadoNotificacion(notificacionId: String, nuevoEstado: String) {
        // Cambiar "aceptado" por "finalizar"
        val estadoFinal = if (nuevoEstado == "aceptado") "finalizar" else nuevoEstado

        if (estadoFinal == "finalizar") {
            verificarStockAntesDeAceptar(notificacionId)
            return
        }

        // Si el estado es "rechazado", mostrar diálogo para ingresar motivo
        if (estadoFinal == "rechazado") {
            mostrarDialogoMotivoRechazo(notificacionId)
            return
        }

        // Para otros estados, continuar con el flujo normal
        ejecutarActualizacionEstado(notificacionId, estadoFinal, null)
    }

    private fun mostrarDialogoMotivoRechazo(notificacionId: String) {
        val builder = AlertDialog.Builder(this)
        val input = EditText(this)

        // Configurar el EditText
        input.hint = "Ingrese el motivo del rechazo"
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        input.maxLines = 3
        input.setLines(2)

        // Agregar padding al EditText
        val padding = resources.getDimensionPixelSize(R.dimen.dialog_padding) // o usar 16.dpToPx()
        input.setPadding(padding, padding, padding, padding)

        builder.setTitle("Motivo del Rechazo")
        builder.setMessage("Por favor, especifique el motivo por el cual está rechazando este pedido:")
        builder.setView(input)

        builder.setPositiveButton("Rechazar") { dialog, _ ->
            val motivo = input.text.toString().trim()
            if (motivo.isEmpty()) {
                Toast.makeText(this, "Debe ingresar un motivo para rechazar", Toast.LENGTH_SHORT).show()
            } else {
                dialog.dismiss()
                ejecutarActualizacionEstado(notificacionId, "rechazado", motivo)
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.cancel()
        }

        val dialog = builder.create()
        dialog.show()

        // Opcional: Enfocar automáticamente el EditText
        input.requestFocus()
    }

    private fun ejecutarActualizacionEstado(notificacionId: String, nuevoEstado: String, motivoRechazo: String?) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show()
            return
        }

        val proveedorId = currentUser.uid
        val ordenRefProveedor = db.collection("userServices")
            .document(proveedorId)
            .collection("orders")
            .document(notificacionId)

        val progressDialog = ProgressDialog(this).apply {
            setMessage("Actualizando estado...")
            setCancelable(false)
            show()
        }

        // Primero actualizamos el estado en userServices y luego buscamos clienteId
        ordenRefProveedor.get().addOnSuccessListener { documento ->
            if (!documento.exists()) {
                progressDialog.dismiss()
                Toast.makeText(this, "Pedido no encontrado", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            val clienteId = documento.getString("clienteId")
            if (clienteId.isNullOrEmpty()) {
                progressDialog.dismiss()
                Toast.makeText(this, "Cliente no especificado en la orden", Toast.LENGTH_SHORT).show()
                return@addOnSuccessListener
            }

            // Creamos una batch para hacer ambas actualizaciones juntas
            val batch = db.batch()

            // Preparar los datos a actualizar
            val updateData = mutableMapOf<String, Any>()
            updateData["estado"] = nuevoEstado

            // Si hay motivo de rechazo, agregarlo a los datos
            if (!motivoRechazo.isNullOrEmpty()) {
                updateData["motivoRechazo"] = motivoRechazo
                updateData["fechaRechazo"] = FieldValue.serverTimestamp()
            }

            // Update en userServices
            batch.update(ordenRefProveedor, updateData)

            // Update en userClients
            val ordenRefCliente = db.collection("userClients")
                .document(clienteId)
                .collection("orders")
                .document(notificacionId)
            batch.update(ordenRefCliente, updateData)

            // Commit del batch
            batch.commit()
                .addOnSuccessListener {
                    progressDialog.dismiss()

                    // Actualizar la lista local primero
                    val index = notificacionesList.indexOfFirst { it.id == notificacionId }
                    if (index != -1) {
                        val notificacion = notificacionesList[index]
                        notificacionesList[index] = notificacion.copy(
                            estado = nuevoEstado,
                            motivoRechazo = motivoRechazo
                        )
                    }

                    // Si el estado es "finalizar", crear la vista especial inmediatamente
                    if (nuevoEstado == "finalizar" && index != -1) {
                        contenedorNotificaciones.removeViewAt(index)
                        val nuevaVista = crearVistaNotificacionFinalizar(notificacionesList[index])
                        contenedorNotificaciones.addView(nuevaVista, index)
                    } else {
                        // Para otros estados, recargar las notificaciones
                        cargarNotificaciones()
                    }

                    val mensaje = when (nuevoEstado) {
                        "finalizar" -> "Pedido marcado para finalizar"
                        "rechazado" -> "Pedido rechazado correctamente"
                        "finalizado" -> "Pedido finalizado correctamente"
                        else -> "Estado actualizado"
                    }

                    Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error al actualizar estado: ${e.message}", Toast.LENGTH_SHORT).show()
                }

        }.addOnFailureListener { e ->
            progressDialog.dismiss()
            Toast.makeText(this, "Error al obtener orden: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Int.dpToPx(): Int {
        return (this * resources.displayMetrics.density).toInt()
    }
    // Verificar stock antes de aceptar definitivamente
    private fun verificarStockAntesDeAceptar(notificacionId: String) {
        val notificacion = notificacionesList.find { it.id == notificacionId }
        if (notificacion == null) return

        val currentUser = auth.currentUser
        if (currentUser == null) return

        val proveedorId = currentUser.uid

        // Mostrar diálogo de progreso
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Verificando stock...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        db.collection("userServices")
            .document(proveedorId)
            .collection("register_stock")
            .get()
            .addOnSuccessListener { documents ->
                var stockDisponible = 0

                for (document in documents) {
                    val cantStock = document.getLong("cant_stock")?.toInt() ?: 0
                    stockDisponible += cantStock
                }

                progressDialog.dismiss()

                if (stockDisponible >= notificacion.cantidad) {
                    // Stock suficiente, proceder con la aceptación
                    procesarActualizacionEstado(notificacionId, "aceptado")
                } else {
                    // Stock insuficiente, mostrar error y recargar vista
                    Toast.makeText(
                        this,
                        "Stock insuficiente. Disponible: $stockDisponible, Solicitado: ${notificacion.cantidad}",
                        Toast.LENGTH_LONG
                    ).show()

                    // Recargar las notificaciones para actualizar la vista
                    cargarNotificaciones()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Error al verificar stock: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }



    private fun procesarActualizacionEstado(notificacionId: String, nuevoEstado: String) {
        val progressDialog = ProgressDialog(this)
        progressDialog.setMessage("Procesando...")
        progressDialog.setCancelable(false)
        progressDialog.show()

        val currentUser = auth.currentUser
        if (currentUser == null) {
            progressDialog.dismiss()
            Toast.makeText(this, "Error: Usuario no identificado", Toast.LENGTH_SHORT).show()
            return
        }

        val proveedorId = currentUser.uid

        // Primero obtener información del pedido para conocer el clienteId y la cantidad
        db.collection("userServices")
            .document(proveedorId)
            .collection("orders")
            .document(notificacionId)
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val clienteId = document.getString("clienteId")
                    val cantidadPedida = document.getLong("cantidad")?.toInt() ?: 0

                    if (clienteId != null) {
                        // Si el estado es "aceptado", también actualizar el stock
                        if (nuevoEstado == "aceptado") {
                            actualizarEstadoYStock(proveedorId, clienteId, notificacionId, nuevoEstado, cantidadPedida, progressDialog)
                        } else {
                            // Si es rechazar o finalizar, no actualizar stock
                            actualizarSoloEstado(proveedorId, clienteId, notificacionId, nuevoEstado, progressDialog)
                        }
                    } else {
                        progressDialog.dismiss()
                        Toast.makeText(this, "Error: No se encontró el ID del cliente", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error: Pedido no encontrado", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Error al obtener información del pedido: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }


    private fun actualizarEstadoYStock(
        proveedorId: String,
        clienteId: String,
        notificacionId: String,
        nuevoEstado: String,
        cantidadPedida: Int,
        progressDialog: ProgressDialog
    ) {
        // Primero obtener el stock actual
        db.collection("userServices").document(proveedorId)
            .collection("register_stock")
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val stockDoc = querySnapshot.documents[0]
                    val stockDocId = stockDoc.id
                    val stockActual = stockDoc.getLong("cant_stock")?.toInt() ?: 0

                    // Verificar que hay suficiente stock
                    if (stockActual >= cantidadPedida) {
                        val nuevoStock = stockActual - cantidadPedida

                        // Usar batch para actualizar todo de forma atómica
                        val batch = db.batch()

                        // Referencias para orders
                        val userServiceRef = db.collection("userServices")
                            .document(proveedorId)
                            .collection("orders")
                            .document(notificacionId)

                        val userClientRef = db.collection("userClients")
                            .document(clienteId)
                            .collection("orders")
                            .document(notificacionId)

                        // Referencia para stock
                        val stockRef = db.collection("userServices")
                            .document(proveedorId)
                            .collection("register_stock")
                            .document(stockDocId)

                        // Actualizar estado en ambas colecciones de orders
                        //batch.update(userServiceRef, "estado", "finalizar")
                        batch.update(userServiceRef, "estado", nuevoEstado)
                        batch.update(userClientRef, "estado", nuevoEstado)

                        // Actualizar stock
                        batch.update(stockRef, "cant_stock", nuevoStock)

                        // Ejecutar el batch
                        batch.commit()
                            .addOnSuccessListener {
                                // Eliminar la notificación de la lista
                                val index = notificacionesList.indexOfFirst { it.id == notificacionId }
                                if (index != -1) {
                                    val notificacion = notificacionesList[index] // Guarda una copia
                                    notificacionesList.removeAt(index)

                                    // Quitar la vista actual si la tienes en un ViewGroup
                                    contenedorNotificaciones.removeViewAt(index)

                                    // Crear la vista finalizada y agregarla al contenedor
                                    val vistaFinal = crearVistaNotificacionFinalizar(notificacion)
                                    contenedorNotificaciones.addView(vistaFinal, index) // Insertarla en la misma posición
                                }


                                progressDialog.dismiss()

                                Toast.makeText(
                                    this,
                                    "Pedido aceptado y stock actualizado correctamente",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // Llamar a crearVistaNotificacionFinalizar


                                if (notificacionesList.isEmpty()) {
                                    mostrarNoNotificaciones()
                                } else {
                                    mostrarNotificaciones()
                                }
                            }

                    } else {
                        progressDialog.dismiss()
                        Toast.makeText(
                            this,
                            "Error: Stock insuficiente. Stock actual: $stockActual, Cantidad pedida: $cantidadPedida",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    progressDialog.dismiss()
                    Toast.makeText(this, "Error: No se encontró el documento de stock", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(this, "Error al acceder al stock: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun actualizarSoloEstado(
        proveedorId: String,
        clienteId: String,
        notificacionId: String,
        nuevoEstado: String,
        progressDialog: ProgressDialog
    ) {
        // Usar batch para actualizar ambas colecciones de forma atómica
        val batch = db.batch()

        // Referencia para userServices
        val userServiceRef = db.collection("userServices")
            .document(proveedorId)
            .collection("orders")
            .document(notificacionId)

        // Referencia para userClients
        val userClientRef = db.collection("userClients")
            .document(clienteId)
            .collection("orders")
            .document(notificacionId)

        // Actualizar estado en ambas colecciones
        batch.update(userServiceRef, "estado", nuevoEstado)
        batch.update(userClientRef, "estado", nuevoEstado)

        // Ejecutar el batch
        batch.commit()
            .addOnSuccessListener {
                // Eliminar la notificación de la lista
                val index = notificacionesList.indexOfFirst { it.id == notificacionId }
                if (index != -1) {
                    notificacionesList.removeAt(index)
                }

                progressDialog.dismiss()

                Toast.makeText(
                    this,
                    "Pedido ${if (nuevoEstado == "aceptado") "aceptado" else "rechazado"} correctamente",
                    Toast.LENGTH_SHORT
                ).show()

                if (notificacionesList.isEmpty()) {
                    mostrarNoNotificaciones()
                } else {
                    mostrarNotificaciones()
                }
            }
            .addOnFailureListener { e ->
                progressDialog.dismiss()
                Toast.makeText(
                    this,
                    "Error al actualizar: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    // Método para actualizar el contador visual de notificaciones
    private fun actualizarContadorNotificaciones(count: Int) {
        // Cambiar el color de la campana según si hay notificaciones o no
        if (count > 0) {
            notificationButton.setColorFilter(Color.parseColor("#FFD600"))  // Amarillo
        } else {
            notificationButton.setColorFilter(Color.GRAY)
        }

        // un contador visual con el  de notificaciones

    }

    // para actualizar las notificaciones cuando el usuario vuelve a la app
    override fun onResume() {
        super.onResume()

        // Verificar si hay usuario logueado
        if (auth.currentUser != null) {
            // Buscar notificaciones pendientes
            db.collection("orders")
                .whereEqualTo("proveedorId", auth.currentUser!!.uid)
                .whereEqualTo("estado", "pendiente")
                .get()
                .addOnSuccessListener { documents ->
                    // Actualizar el indicador visual
                    actualizarContadorNotificaciones(documents.size())
                }
        }


    }

    // Método para configurar la sección de servicios con animaciones
    private fun setupServicesSection() {
        servicesSection.translationY = 200f
        servicesSection.alpha = 0f
        servicesSection.animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(OvershootInterpolator())
            .start()

        // Configurar listeners para cada servicio
        waterServiceCard.setOnClickListener {
            toggleServiceSelection("agua", waterServiceCard)
        }

        gasServiceCard.setOnClickListener {
            toggleServiceSelection("gas", gasServiceCard)
        }

        garbageServiceCard.setOnClickListener {
            toggleServiceSelection("basurero", garbageServiceCard)
        }
    }

    private fun toggleServiceSelection(serviceType: String, cardView: CardView) {
        // Verificar si la tarjeta ya está seleccionada
        if (currentSelectedCard == cardView) {
            cardView.isSelected = false
            cardView.cardElevation = 4f
            currentSelectedCard = null
            filteredServiceType = null
            selectionIndicator.visibility = View.GONE
        } else {
            resetCardsAppearance()
            cardView.isSelected = true
            cardView.cardElevation = 8f
            currentSelectedCard = cardView

            // Efectos de animación para la tarjeta seleccionada
            val scaleX = ObjectAnimator.ofFloat(cardView, View.SCALE_X, 1f, 0.95f, 1f)
            val scaleY = ObjectAnimator.ofFloat(cardView, View.SCALE_Y, 1f, 0.95f, 1f)
            val elevate = ObjectAnimator.ofFloat(cardView, View.TRANSLATION_Z, 4f, 12f)

            val animSet = AnimatorSet().apply {
                playTogether(scaleX, scaleY, elevate)
                duration = 300
                interpolator = OvershootInterpolator()
                start()
            }


            moveSelectionIndicator(cardView)

            // Actualizar el tipo de servicio filtrado
            filteredServiceType = when (serviceType) {
                "agua" -> {
                    "Agua"
                }
                "gas" -> {
                    "GLP"
                }
                "basurero" -> {
                    "Carro de basura"
                }
                else -> null
            }
        }
        if (currentUserType == "Cliente" && ::mMap.isInitialized) {
            refreshMap()
        }
    }


    private fun moveSelectionIndicator(cardView: CardView) {
        // Hacer visible el indicador
        selectionIndicator.visibility = View.VISIBLE

        // Calcular las coordenadas para el indicador
        val params = selectionIndicator.layoutParams as ConstraintLayout.LayoutParams

        // Actualizar las restricciones del indicador para que coincida con la tarjeta seleccionada
        params.startToStart = cardView.id
        params.endToEnd = cardView.id

        // Aplicar los cambios
        selectionIndicator.layoutParams = params

        // Animar el cambio
        selectionIndicator.alpha = 0f
        selectionIndicator.animate()
            .alpha(1f)
            .setDuration(300)
            .start()
    }

    private fun resetCardsAppearance() {
        waterServiceCard.isSelected = false
        gasServiceCard.isSelected = false
        garbageServiceCard.isSelected = false

        waterServiceCard.cardElevation = 4f
        gasServiceCard.cardElevation = 4f
        garbageServiceCard.cardElevation = 4f
    }

    // Método para buscar un proveedor de servicio por su username
    private fun searchServiceProvider(username: String) {
        // Buscar el proveedor de servicio por username
        db.collection("userServices")
            .whereEqualTo("username", username)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(this, "No se encontró ningún proveedor con ese nombre", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val serviceDoc = documents.documents[0]
                val uid = serviceDoc.getString("uid")

                if (uid != null) {
                    // Obtener la ubicación del proveedor
                    db.collection("locations").document(uid).get()
                        .addOnSuccessListener { locationDoc ->
                            if (locationDoc.exists()) {
                                val lat = locationDoc.getDouble("latitude")
                                val lng = locationDoc.getDouble("longitude")

                                if (lat != null && lng != null) {
                                    // Centrar el mapa en la ubicación del proveedor
                                    val providerLocation = LatLng(lat, lng)
                                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(providerLocation, 17f))

                                    // Destacar el marcador (opcional)
                                    allServiceMarkers.forEach { (marker, name) ->
                                        if (name == username) {
                                            marker.showInfoWindow() // Muestra el info window del marcador
                                        }
                                    }

                                    Toast.makeText(this, "Proveedor encontrado: $username", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this, "El proveedor no tiene ubicación disponible", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(this, "El proveedor no está en línea actualmente", Toast.LENGTH_SHORT).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al obtener la ubicación del proveedor", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error en la búsqueda", Toast.LENGTH_SHORT).show()
            }
    }


    // Método para refrescar el mapa aplicando los filtros actuales
    private fun refreshMap() {
        // Eliminamos los marcadores pero mantenemos la configuración del mapa
        if (currentUserMarker != null) {
            currentUserMarker?.remove()
        }

        allServiceMarkers.forEach { (marker, _) -> marker.remove() }
        allServiceMarkers.clear()

        // Si existe la ubicación actual, añadir el marcador del usuario actual
        currentUserMarker?.position?.let { position ->
            currentUserMarker = mMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("Tu ubicación")
            )
        }

        // Volver a cargar los servicios con el filtro aplicado
        listenToServiceProviders()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        mMap.uiSettings.isCompassEnabled = true
        mMap.uiSettings.isRotateGesturesEnabled = true

        mMap.setOnMarkerClickListener { marker ->
            if (marker == clienteMarker) {
                clearRoute()
                return@setOnMarkerClickListener true // Indica que el evento de clic ha sido consumido
            }
            // Si no es el marcador del cliente, devuelve false para que se muestre el info window (si lo hay)
            return@setOnMarkerClickListener false
        }

        checkLocationPermission()
    }
    private fun clearRoute() {
        mMap.clear()
        clienteMarker = null
        // Volver a añadir el marcador de usuario
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let { currentLoc ->
                    val latLng = LatLng(currentLoc.latitude, currentLoc.longitude)
                    val markerOptions = MarkerOptions().position(latLng).title("Tu ubicación")
                    if (currentUserType != "Cliente" && currentServiceType != null) {
                        markerOptions.icon(getServiceIcon(currentServiceType))
                    }
                    currentUserMarker = mMap.addMarker(markerOptions)
                }
            }
        } else {
            Toast.makeText(this, "Permisos de ubicación no concedidos.", Toast.LENGTH_SHORT).show()
        }
        // Volver a añadir los marcadores de servicio (si eres cliente)
        if (currentUserType == "Cliente") {
            allServiceMarkers.forEach { (marker, _, serviceType) ->
                marker?.position?.let { pos ->
                    val serviceMarkerOptions = MarkerOptions()
                        .position(pos)
                        .title(marker.title)
                        .snippet(marker.snippet)
                        .icon(getServiceIcon(serviceType))
                    mMap.addMarker(serviceMarkerOptions)
                }
            }
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            enableMyLocation()
        }
    }



    private fun enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            // mMap.isMyLocationEnabled = true // Habilitamos el botón azul predeterminado de Google Maps

            val uid = auth.currentUser?.uid

            uid?.let {
                // Primero buscar en userClients
                db.collection("userClients").document(it).get()
                    .addOnSuccessListener { clientDoc ->
                        if (clientDoc.exists()) {
                            // Es Cliente
                            currentUserType = clientDoc.getString("userType")
                            iniciarActualizacionUbicacion()
                            // Ocultar botón de stock para clientes
                            updateStockButtonVisibility(false)

                        } else {
                            // No está en userClients, buscar en userServices
                            db.collection("userServices").document(it).get()
                                .addOnSuccessListener { serviceDoc ->
                                    if (serviceDoc.exists()) {
                                        currentUserType = serviceDoc.getString("userType")
                                        // Si es servicio, necesitamos obtener el tipo de servicio para mostrar el ícono correcto
                                        currentServiceType = serviceDoc.getString("serviceType")
                                        iniciarActualizacionUbicacion()
                                        // Mostrar botón de stock para servicios
                                        updateStockButtonVisibility(true)
                                    }
                                }
                        }
                    }
            }
        }
    }

    private fun updateStockButtonVisibility(isService: Boolean) {
        val drawerFragment = supportFragmentManager.findFragmentById(R.id.drawerFragmentContainer) as? UserDrawerFragment
        drawerFragment?.setStockButtonVisibility(isService)
    }



    // Esta es una función separada para iniciar la ubicación una vez que ya sabemos el tipo de usuario
    private fun iniciarActualizacionUbicacion() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)

                // Guardar la ubicación del usuario en Firebase independientemente del tipo
                val uid = auth.currentUser?.uid
                val userLocation = hashMapOf(
                    "latitude" to location.latitude,
                    "longitude" to location.longitude,
                    "isOnline" to true
                )
                uid?.let { db.collection("locations").document(it).set(userLocation) }

                // Añadir marcador según el tipo de usuario (sin limpiar el mapa para mantener el indicador de ubicación)
                // Eliminamos los marcadores anteriores pero mantenemos la configuración del mapa
                if (currentUserMarker != null) {
                    currentUserMarker?.remove()
                }

                if (currentUserType == "Cliente") {
                    // Si es Cliente, mostrar con pin rojo predeterminado
                    currentUserMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Tu ubicación")
                    )
                    // Los clientes pueden ver a los proveedores de servicio
                    listenToServiceProviders()
                } else {
                    // Si es Servicio, mostrar con ícono personalizado según su tipo


                    val serviceIcon = getServiceIcon(currentServiceType)
                    currentUserMarker = mMap.addMarker(
                        MarkerOptions()
                            .position(latLng)
                            .title("Tu ubicación")
                            .icon(serviceIcon)
                    )
                    // Los proveedores de servicio NO ven a otros usuarios
                }

                // Mover la cámara a la ubicación actual (solo si es la primera vez)
                if (isFirstLocationUpdate) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    isFirstLocationUpdate = false
                }
            }
        }

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
    }

    // Función para obtener el ícono según el tipo de servicio
    private fun getServiceIcon(serviceType: String?): BitmapDescriptor {
        return when (serviceType) {
            "Agua" -> getBitmapDescriptorFromVector(R.drawable.ic_water_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
            "GLP" -> getBitmapDescriptorFromVector(R.drawable.ic_gas_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
            "Carro de basura" -> getBitmapDescriptorFromVector(R.drawable.ic_garbage_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
            else -> getBitmapDescriptorFromVector(R.drawable.ic_filter, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
        }
    }

    //-------------------------------------------------------------------------
    // Solo los clientes escuchan las ubicaciones de los proveedores de servicio
    private fun listenToServiceProviders() {
        db.collection("locations")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener ubicaciones", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                allServiceMarkers.forEach { (marker, _) -> marker.remove() }
                allServiceMarkers.clear()

                snapshots?.forEach { doc ->
                    val userId = doc.id
                    if (userId == auth.currentUser?.uid) return@forEach

                    db.collection("userServices").document(userId).get()
                        .addOnSuccessListener { serviceDoc ->
                            if (serviceDoc.exists()) {
                                val userType = serviceDoc.getString("userType")
                                if (userType == "Servicio") {
                                    val serviceType = serviceDoc.getString("serviceType") ?: "Desconocido"
                                    if (filteredServiceType == null || filteredServiceType == serviceType) {
                                        val username = serviceDoc.getString("username") ?: "Servicio"
                                        val lat = doc.getDouble("latitude") ?: return@addOnSuccessListener
                                        val lng = doc.getDouble("longitude") ?: return@addOnSuccessListener
                                        val icon = getServiceIcon(serviceType)

                                        val marker = mMap.addMarker(
                                            MarkerOptions()
                                                .position(LatLng(lat, lng))
                                                .title("Servicio: $username ($serviceType)")
                                                .snippet(serviceType)
                                                .icon(icon)
                                        )

                                        val acceptOrders = serviceDoc.getString("acceptOrders") ?: "false"

                                        if (marker != null) {
                                            allServiceMarkers.add(Triple(marker, username, serviceType))
                                            marker.tag = listOf(userId, username, serviceType, acceptOrders)
                                        }
                                    }
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("ServiceProvider", "Error al obtener el documento del servicio", exception)
                        }
                }

                mMap.setOnMarkerClickListener { clickedMarker ->
                    val tag = clickedMarker.tag
                    if (tag is List<*> && tag.size == 4) {
                        val userId = tag[0] as? String ?: return@setOnMarkerClickListener false
                        val username = tag[1] as? String ?: return@setOnMarkerClickListener false
                        val serviceType = tag[2] as? String ?: return@setOnMarkerClickListener false
                        val acceptOrders = tag[3] as? String ?: "false"

                        showServiceDialog(userId, username, serviceType, acceptOrders)
                        true
                    } else {
                        false
                    }
                }
            }
    }

    // Mostrar ventana emergente al hacer clic en un servicio
    private fun showServiceDialog(userId: String, username: String, serviceType: String, acceptOrders: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Detalles del Servicio")
        builder.setMessage("Proveedor: $username\nTipo de servicio: $serviceType")

        if (acceptOrders == "true") {
            builder.setPositiveButton("Hacer Pedido") { dialog, _ ->
                val intent = Intent(this, OrderActivity::class.java)
                intent.putExtra("username", username)
                intent.putExtra("serviceType", serviceType)
                intent.putExtra("SERVICE_UID", userId)
                startActivity(intent)
                dialog.dismiss()
            }
        }

        builder.setNegativeButton("Cancelar") { dialog, _ ->
            dialog.dismiss()
        }

        builder.show()
    }
    //-------------------------------------------------------------------------
    private fun getBitmapDescriptorFromVector(
        vectorResId: Int,
        widthDp: Int,
        heightDp: Int
    ): BitmapDescriptor {
        return try {
            val vectorDrawable = ContextCompat.getDrawable(this, vectorResId) ?: return BitmapDescriptorFactory.defaultMarker()

            // Convertir dp a píxeles
            val widthPx = (widthDp * resources.displayMetrics.density).toInt()
            val heightPx = (heightDp * resources.displayMetrics.density).toInt()

            val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            vectorDrawable.setBounds(0, 0, canvas.width, canvas.height)
            vectorDrawable.draw(canvas)

            BitmapDescriptorFactory.fromBitmap(bitmap)
        } catch (e: Exception) {
            e.printStackTrace()
            BitmapDescriptorFactory.defaultMarker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        auth.currentUser?.uid?.let { uid ->
            db.collection("locations").document(uid).update("isOnline", false)
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    fun setUserOfflineAndStopLocation() {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("locations").document(uid).update("isOnline", false)
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}