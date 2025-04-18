package com.programovil.misservicios1

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentUserMarker: Marker? = null

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val drawerLayout = findViewById<androidx.drawerlayout.widget.DrawerLayout>(R.id.drawerLayout)
        val menuButton = findViewById<View>(R.id.menuButton)

        // Cargar fragmento del drawer
        supportFragmentManager.beginTransaction()
            .replace(R.id.drawerFragmentContainer, UserDrawerFragment())
            .commit()

        menuButton.setOnClickListener {
            drawerLayout.openDrawer(Gravity.START)
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        checkLocationPermission()
        listenToOnlineUsers()
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

            mMap.isMyLocationEnabled = true

            val locationRequest = LocationRequest.create().apply {
                interval = 5000
                fastestInterval = 3000
                priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            }

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation ?: return
                    val latLng = LatLng(location.latitude, location.longitude)

                    if (currentUserMarker == null) {
                        currentUserMarker = mMap.addMarker(
                            MarkerOptions().position(latLng).title("Tu ubicación")
                        )
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    } else {
                        currentUserMarker?.position = latLng
                    }

                    val uid = auth.currentUser?.uid
                    val userLocation = hashMapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude,
                        "isOnline" to true
                    )
                    if (uid != null) {
                        db.collection("locations").document(uid).set(userLocation)
                    }
                }
            }

            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, mainLooper)
        }
    }

    private fun listenToOnlineUsers() {
        db.collection("locations")
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(this, "Error al obtener ubicaciones", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                mMap.clear()

                snapshots?.forEach { doc ->
                    val lat = doc.getDouble("latitude") ?: return@forEach
                    val lng = doc.getDouble("longitude") ?: return@forEach
                    val userId = doc.id

                    // Consultamos si este usuario está en la colección de servicios
                    db.collection("userServices").document(userId).get()
                        .addOnSuccessListener { serviceDoc ->
                            if (serviceDoc.exists()) {
                                val marker = MarkerOptions()
                                    .position(LatLng(lat, lng))
                                    .title("Servicio: ${serviceDoc.getString("username") ?: userId}")
                                mMap.addMarker(marker)
                            }
                        }
                }
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
        val uid = auth.currentUser?.uid
        if (uid != null) {
            db.collection("locations").document(uid).update("isOnline", false)
        }
        if (::locationCallback.isInitialized) {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        }
    }
}
