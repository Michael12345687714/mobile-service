package com.programovil.misservicios1

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class CompleteProfileActivity : AppCompatActivity() {

    private lateinit var spinnerUserType: Spinner
    private lateinit var layoutServiceOptions: LinearLayout
    private lateinit var spinnerServiceType: Spinner
    private lateinit var checkBoxAcceptOrders: CheckBox
    private lateinit var btnSave: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_complete_profile)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        spinnerUserType = findViewById(R.id.spinnerUserType)
        layoutServiceOptions = findViewById(R.id.layoutServiceOptions)
        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        checkBoxAcceptOrders = findViewById(R.id.checkAcceptOrders)
        btnSave = findViewById(R.id.btnSave)

        val userTypes = arrayOf("Cliente", "Servicio")
        spinnerUserType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, userTypes)

        val serviceTypes = arrayOf("Agua", "GLP", "Carro de basura")
        spinnerServiceType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serviceTypes)

        spinnerUserType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                layoutServiceOptions.visibility = if (userTypes[position] == "Servicio") View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnSave.setOnClickListener {
            val currentUser = auth.currentUser
            val uid = currentUser?.uid

            if (uid == null) {
                Toast.makeText(this, "No se pudo obtener el usuario", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val username = currentUser.displayName ?: "SinNombre"
            val email = currentUser.email ?: ""

            val userType = spinnerUserType.selectedItem.toString()
            val userData = hashMapOf(
                "uid" to uid,
                "username" to username,
                "email" to email,
                "userType" to userType
            )

            if (userType == "Servicio") {
                val serviceType = spinnerServiceType.selectedItem.toString()
                val acceptOrders = checkBoxAcceptOrders.isChecked.toString()
                userData["serviceType"] = serviceType
                userData["acceptOrders"] = acceptOrders
            }

            val collectionName = if (userType == "Cliente") "userClients" else "userServices"

            db.collection(collectionName).document(uid)
                .set(userData)
                .addOnSuccessListener {
                    Toast.makeText(this, "Perfil completado", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, HomeActivity::class.java))
                    finish()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al guardar el perfil: ${it.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}
