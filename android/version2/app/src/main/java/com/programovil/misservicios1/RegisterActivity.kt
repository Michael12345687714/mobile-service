package com.programovil.misservicios1

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import android.content.Intent

class RegisterActivity : AppCompatActivity() {
    private lateinit var edtUsername: EditText
    private lateinit var edtEmail: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPassword: EditText
    private lateinit var spinnerUserType: Spinner
    private lateinit var layoutServiceOptions: LinearLayout
    private lateinit var spinnerServiceType: Spinner
    private lateinit var checkBoxAcceptOrders: CheckBox
    private lateinit var btnRegister: Button

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // Inicializar vistas
        edtUsername = findViewById(R.id.etUsername)
        edtEmail = findViewById(R.id.etEmail)
        edtPassword = findViewById(R.id.etPassword)
        edtConfirmPassword = findViewById(R.id.etConfirmPassword)
        spinnerUserType = findViewById(R.id.spinnerUserType)
        layoutServiceOptions = findViewById(R.id.layoutServiceOptions)
        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        checkBoxAcceptOrders = findViewById(R.id.checkAcceptOrders)
        btnRegister = findViewById(R.id.btnRegister)

        // Configurar spinners
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

        // Registro
        btnRegister.setOnClickListener {
            val username = edtUsername.text.toString().trim()
            val email = edtEmail.text.toString().trim()
            val password = edtPassword.text.toString().trim()
            val confirmPassword = edtConfirmPassword.text.toString().trim()
            val userType = spinnerUserType.selectedItem.toString()

            // Validaciones
            if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor llena todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (username.length < 3 || username.length > 60) {
                Toast.makeText(this, "El nombre de usuario debe tener entre 3 y 60 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val emailPattern = "^[a-zA-Z0-9._%+-]+@(gmail\\.com|hotmail\\.com|est\\.umss\\.edu)$"
            if (!email.matches(Regex(emailPattern))) {
                Toast.makeText(this, "El correo debe ser válido y pertenecer a uno de los dominios permitidos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 8 || password.length > 20) {
                Toast.makeText(this, "La contraseña debe tener entre 8 y 20 caracteres", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar carácter especial o acento
            val specialCharsRegex = ".*[\\W_áéíóúüñÁÉÍÓÚÜÑ].*".toRegex()
            if (!password.matches(specialCharsRegex)) {
                Toast.makeText(this, "La contraseña debe contener al menos un carácter especial (símbolo o acento)", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Intentar crear el usuario en Firebase
            auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener { authResult ->
                    val uid = authResult.user?.uid

                    val userData = hashMapOf(
                        "uid" to uid,
                        "username" to username,
                        "email" to email,
                        "userType" to userType
                    )

                    if (userType == "Servicio") {
                        userData["serviceType"] = spinnerServiceType.selectedItem.toString()
                        userData["acceptOrders"] = checkBoxAcceptOrders.isChecked.toString()
                    }

                    val collectionName = if (userType == "Cliente") "userClients" else "userServices"

                    db.collection(collectionName).document(uid!!)
                        .set(userData)
                        .addOnSuccessListener {
                            Toast.makeText(this, "Usuario registrado exitosamente", Toast.LENGTH_SHORT).show()
                            val intent = Intent(this, MainActivity::class.java)
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(intent)
                            finish()
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error al guardar datos: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    if (it.message?.contains("The email address is already in use") == true) {
                        Toast.makeText(this, "Error: El correo ya se encuentra registrado", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error al registrar: ${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }
}
