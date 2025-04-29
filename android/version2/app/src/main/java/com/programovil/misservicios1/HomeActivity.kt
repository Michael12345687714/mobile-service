package com.programovil.misservicios1

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
import android.widget.SearchView


class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentUserMarker: Marker? = null
    private var isSelected = false
    private var currentUserType: String? = null

    private var currentServiceType: String? = null
    private var isFirstLocationUpdate = true

    // Variable para almacenar el tipo de servicio filtrado
    private var filteredServiceType: String? = null

    // Variable para almacenar el contenedor actualmente seleccionado
    private var selectedServiceContainer: LinearLayout? = null

    // Lista de marcadores y sus nombres
    private val allServiceMarkers = mutableListOf<Pair<Marker, String>>()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
        private const val USER_ICON_SIZE_DP = 50  // Tamaño para el icono del usuario
        private const val PROVIDER_ICON_SIZE_DP = 40  // Tamaño para los iconos de proveedores
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // Inicialización de vistas y listeners
        initViews()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun initViews() {
        val waterContainer = findViewById<LinearLayout>(R.id.waterContainer)
        val gasContainer = findViewById<LinearLayout>(R.id.gasContainer)
        val garbageContainer = findViewById<LinearLayout>(R.id.garbageContainer)
        // val filterButton = findViewById<ImageView>(R.id.filterButton)
        val requestButton = findViewById<Button>(R.id.requestButton)
        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val menuButton = findViewById<View>(R.id.menuButton)

        fun selectService(container: LinearLayout) {
            // Si ya está seleccionado el mismo contenedor, lo deseleccionamos
            if (selectedServiceContainer == container) {
                container.isSelected = false
                selectedServiceContainer = null
                filteredServiceType = null
                Toast.makeText(this, "Mostrando todos los servicios", Toast.LENGTH_SHORT).show()
            } else {
                // Deseleccionamos el contenedor anterior si existe
                selectedServiceContainer?.isSelected = false

                // Seleccionamos el nuevo contenedor
                container.isSelected = true
                selectedServiceContainer = container

                // Actualizar el tipo de servicio filtrado
                filteredServiceType = when (container.id) {
                    R.id.waterContainer -> {
                        Toast.makeText(this, "Servicio de agua seleccionado", Toast.LENGTH_SHORT).show()
                        "Agua"
                    }
                    R.id.gasContainer -> {
                        Toast.makeText(this, "Servicio de gas seleccionado", Toast.LENGTH_SHORT).show()
                        "GLP"
                    }
                    R.id.garbageContainer -> {
                        Toast.makeText(this, "Servicio de basura seleccionado", Toast.LENGTH_SHORT).show()
                        "Carro de basura"
                    }
                    else -> null
                }
            }

            // Actualizar el mapa con el filtro aplicado
            if (currentUserType == "Cliente" && ::mMap.isInitialized) {
                refreshMap()
            }
        }

        waterContainer.setOnClickListener { selectService(waterContainer) }
        gasContainer.setOnClickListener { selectService(gasContainer) }
        garbageContainer.setOnClickListener { selectService(garbageContainer) }


        requestButton.setOnClickListener {
            if (selectedServiceContainer == null) {
                Toast.makeText(this, "Por favor seleccione un servicio", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Solicitar servicio", Toast.LENGTH_SHORT).show()
            }
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.drawerFragmentContainer, UserDrawerFragment())
            .commit()

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        val searchView = findViewById<SearchView>(R.id.searchView)

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                if (!query.isNullOrEmpty()) {
                    searchServiceProvider(query)
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                filterServices(newText.orEmpty())
                return true
            }
        })
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

    // Método para filtrar los servicios mostrados en el mapa
    private fun filterServices(query: String) {
        val lowerCaseQuery = query.lowercase()

        allServiceMarkers.forEach { (marker, serviceName) ->
            marker.isVisible = serviceName.lowercase().contains(lowerCaseQuery)
        }

        // Si no hay ningún marcador visible, mostramos un toast
        if (allServiceMarkers.none { it.first.isVisible }) {
            Toast.makeText(this, "No se encontraron resultados", Toast.LENGTH_SHORT).show()
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