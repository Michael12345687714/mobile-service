package com.programovil.misservicios1

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
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
    private val allServiceMarkers = mutableListOf<Pair<Marker, String>>()
    private lateinit var searchCardView: MaterialCardView

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val USER_ICON_SIZE_DP = 50  // Tamaño para el icono del usuario
        private const val PROVIDER_ICON_SIZE_DP = 40  // Tamaño para los iconos de proveedores
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        initViews()
        setupServicesSection()
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }


    private fun initViews() {
        // Inicializar las vistas para la sección de servicios con animaciones
        waterServiceCard = findViewById(R.id.waterServiceCard)
        gasServiceCard = findViewById(R.id.gasServiceCard)
        garbageServiceCard = findViewById(R.id.garbageServiceCard)
        selectionIndicator = findViewById(R.id.selectionIndicator)
        servicesSection = findViewById(R.id.servicesSection)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val menuButton = findViewById<View>(R.id.menuButton)

        // Usar una única referencia para los elementos de búsqueda
        val searchCardView = findViewById<MaterialCardView>(R.id.searchCardView)
        val searchView = findViewById<SearchView>(R.id.searchView)
        val searchButton = findViewById<ImageButton>(R.id.searchButton)

        // Un solo listener para el botón de búsqueda
        searchButton.setOnClickListener {
            // Verificamos la visibilidad del CardView, no del SearchView
            if (searchCardView.visibility == View.VISIBLE) {
                // Ocultar la barra de búsqueda
                searchCardView.visibility = View.GONE
                // Ocultar teclado
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchView.windowToken, 0)
            } else {
                // Mostrar la barra de búsqueda
                searchCardView.visibility = View.VISIBLE
                // Dar foco y mostrar teclado
                searchView.isIconified = false
                searchView.requestFocus()
            }
        }

        // Usar searchCardView en el onCloseListener
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

        // Hacer que el ícono de lupa dentro del SearchView también ejecute la búsqueda
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

    fun setUserOfflineAndStopLocation() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("locations").document(uid)
                .update("isOnline", false)
        }

        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkLocationPermission()
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

            mMap.isMyLocationEnabled = true // Habilitamos el botón azul predeterminado de Google Maps

            val uid = auth.currentUser?.uid

            uid?.let {
                // Primero buscar en userClients
                db.collection("userClients").document(it).get()
                    .addOnSuccessListener { clientDoc ->
                        if (clientDoc.exists()) {
                            // Es Cliente
                            currentUserType = clientDoc.getString("userType")
                            iniciarActualizacionUbicacion()
                        } else {
                            // No está en userClients, buscar en userServices
                            db.collection("userServices").document(it).get()
                                .addOnSuccessListener { serviceDoc ->
                                    if (serviceDoc.exists()) {
                                        currentUserType = serviceDoc.getString("userType")
                                        // Si es servicio, necesitamos obtener el tipo de servicio para mostrar el ícono correcto
                                        currentServiceType = serviceDoc.getString("serviceType")
                                        iniciarActualizacionUbicacion()
                                    }
                                }
                        }
                    }
            }
        }
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

    // Solo los clientes escuchan las ubicaciones de los proveedores de servicio
    private fun listenToServiceProviders() {
        db.collection("locations")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener ubicaciones", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                // Eliminamos los marcadores de servicio anteriores pero mantenemos el indicador de ubicación
                allServiceMarkers.forEach { (marker, _) -> marker.remove() }
                allServiceMarkers.clear() // Limpiar antes de agregar

                snapshots?.forEach { doc ->
                    val userId = doc.id
                    if (userId == auth.currentUser?.uid) return@forEach // Saltamos nuestro propio documento

                    // Solo mostrar usuarios de tipo Servicio
                    db.collection("userServices").document(userId).get()
                        .addOnSuccessListener { serviceDoc ->
                            if (serviceDoc.exists()) {
                                val userType = serviceDoc.getString("userType")
                                if (userType == "Servicio") {
                                    val serviceType = serviceDoc.getString("serviceType") ?: "Desconocido"

                                    // Aplicar el filtro - solo mostrar si no hay filtro o si coincide con el filtro
                                    if (filteredServiceType == null || filteredServiceType == serviceType) {
                                        val username = serviceDoc.getString("username") ?: "Servicio"
                                        val lat = doc.getDouble("latitude") ?: return@addOnSuccessListener
                                        val lng = doc.getDouble("longitude") ?: return@addOnSuccessListener

                                        val icon = when (serviceType) {
                                            "Agua" -> getBitmapDescriptorFromVector(R.drawable.ic_water_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
                                            "GLP" -> getBitmapDescriptorFromVector(R.drawable.ic_gas_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
                                            "Carro de basura" -> getBitmapDescriptorFromVector(R.drawable.ic_garbage_truck, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
                                            else -> getBitmapDescriptorFromVector(R.drawable.ic_filter, PROVIDER_ICON_SIZE_DP, PROVIDER_ICON_SIZE_DP)
                                        }

                                        val marker = mMap.addMarker(
                                            MarkerOptions()
                                                .position(LatLng(lat, lng))
                                                .title("Servicio: $username ($serviceType)")
                                                .icon(icon)
                                        )

                                        if (marker != null) {
                                            allServiceMarkers.add(Pair(marker, username))
                                        }
                                    }
                                }
                            }
                        }
                }
            }
    }

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