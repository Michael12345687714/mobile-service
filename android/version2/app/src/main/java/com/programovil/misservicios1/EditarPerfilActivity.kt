package com.programovil.misservicios1

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage

class EditarPerfilActivity : AppCompatActivity() {

    private lateinit var edtUsername: EditText
    private lateinit var edtPassword: EditText
    private lateinit var edtConfirmPassword: EditText
    private lateinit var tvEmail: TextView
    private lateinit var tvUserType: TextView
    private lateinit var spinnerServiceType: Spinner
    private lateinit var checkboxAcceptOrders: CheckBox
    private lateinit var layoutServiceFields: LinearLayout
    private lateinit var btnSaveChanges: Button
    private lateinit var btnSelectImage: Button
    private lateinit var profileBackgroundImage: ImageView

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private var userType: String = ""
    private var hasPassword: Boolean = false
    private var collectionName: String = ""
    private var imageUri: Uri? = null

    companion object {
        private const val REQUEST_IMAGE_PICK = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_editar_perfil)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        edtUsername = findViewById(R.id.etUsername)
        edtPassword = findViewById(R.id.etPassword)
        edtConfirmPassword = findViewById(R.id.etConfirmPassword)
        tvEmail = findViewById(R.id.tvEmail)
        tvUserType = findViewById(R.id.tvUserType)
        spinnerServiceType = findViewById(R.id.spinnerServiceType)
        checkboxAcceptOrders = findViewById(R.id.checkboxAcceptOrders)
        layoutServiceFields = findViewById(R.id.layoutServiceFields)
        btnSaveChanges = findViewById(R.id.btnSaveChanges)
        btnSelectImage = findViewById(R.id.btnSelectImage)
        profileBackgroundImage = findViewById(R.id.profileBackgroundImage)

        val serviceTypes = arrayOf("Agua", "GLP", "Carro de basura")
        spinnerServiceType.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, serviceTypes)

        loadUserData()

        btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        btnSelectImage.setOnClickListener {
            selectImage()
        }
    }

    private fun loadUserData() {
        val currentUser = auth.currentUser ?: return
        val uid = currentUser.uid

        hasPassword = currentUser.providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
        if (!hasPassword) {
            edtPassword.visibility = View.GONE
            edtConfirmPassword.visibility = View.GONE
        }

        db.collection("userClients").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    collectionName = "userClients"
                    fillUserData(document.getString("username"), document.getString("email"), document.getString("userType")!!)
                } else {
                    db.collection("userServices").document(uid).get()
                        .addOnSuccessListener { serviceDocument ->
                            if (serviceDocument.exists()) {
                                collectionName = "userServices"
                                fillUserData(
                                    serviceDocument.getString("username"),
                                    serviceDocument.getString("email"),
                                    serviceDocument.getString("userType")!!
                                )
                                layoutServiceFields.visibility = View.VISIBLE
                                val serviceType = serviceDocument.getString("serviceType")
                                val acceptOrders = serviceDocument.getString("acceptOrders")?.toBoolean() ?: false

                                spinnerServiceType.setSelection(
                                    (spinnerServiceType.adapter as ArrayAdapter<String>).getPosition(serviceType)
                                )
                                checkboxAcceptOrders.isChecked = acceptOrders
                            }
                        }
                }
            }

        // Cargar imagen de fondo si existe
        val storageRef = FirebaseStorage.getInstance().reference.child("profileBackgroundImages/$uid.jpg")
        storageRef.downloadUrl.addOnSuccessListener { uri ->
            Glide.with(this)
                .load(uri)
                .circleCrop()
                .into(profileBackgroundImage)
        }.addOnFailureListener {
            // Si no tiene imagen aún, no hacemos nada
        }
    }

    private fun fillUserData(username: String?, email: String?, userType: String) {
        edtUsername.setText(username)
        tvEmail.text = email
        tvUserType.text = userType
        this.userType = userType
    }

    private fun selectImage() {
        val intent = Intent(Intent.ACTION_PICK)
        intent.type = "image/*"
        startActivityForResult(intent, REQUEST_IMAGE_PICK)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_PICK && resultCode == RESULT_OK) {
            imageUri = data?.data
            Glide.with(this)
                .load(imageUri)
                .circleCrop()
                .into(profileBackgroundImage)
        }
    }

    private fun saveChanges() {
        val username = edtUsername.text.toString().trim()
        val password = edtPassword.text.toString().trim()
        val confirmPassword = edtConfirmPassword.text.toString().trim()

        val updates = mutableMapOf<String, Any>()
        var passwordChange = false

        // Validaciones
        if (username.isEmpty()) {
            Toast.makeText(this, "El nombre de usuario no puede estar vacío", Toast.LENGTH_SHORT).show()
            return
        }

        if (username.length < 3 || username.length > 60) {
            Toast.makeText(this, "El nombre de usuario debe tener entre 3 y 60 caracteres", Toast.LENGTH_SHORT).show()
            return
        }

        if (username != edtUsername.hint.toString()) {
            updates["username"] = username
        }

        if (userType == "Servicio") {
            val serviceType = spinnerServiceType.selectedItem.toString()
            val acceptOrders = checkboxAcceptOrders.isChecked.toString()
            updates["serviceType"] = serviceType
            updates["acceptOrders"] = acceptOrders
        }

        if (hasPassword && (password.isNotEmpty() || confirmPassword.isNotEmpty())) {
            if (password != confirmPassword) {
                Toast.makeText(this, "Las contraseñas no coinciden", Toast.LENGTH_SHORT).show()
                return
            }

            if (password.length < 8 || password.length > 20) {
                Toast.makeText(this, "La contraseña debe tener entre 8 y 20 caracteres", Toast.LENGTH_SHORT).show()
                return
            }

            val specialCharsRegex = ".*[\\W_áéíóúüñÁÉÍÓÚÜÑ].*".toRegex()
            if (!password.matches(specialCharsRegex)) {
                Toast.makeText(this, "La contraseña debe contener al menos un carácter especial (símbolo o acento)", Toast.LENGTH_SHORT).show()
                return
            }

            passwordChange = true
        }

        if (updates.isEmpty() && !passwordChange && imageUri == null) {
            Toast.makeText(this, "No hay cambios para guardar", Toast.LENGTH_SHORT).show()
            return
        }

        val uid = auth.currentUser?.uid ?: return

        db.collection(collectionName).document(uid)
            .update(updates)
            .addOnSuccessListener {
                if (passwordChange) {
                    auth.currentUser?.updatePassword(password)
                        ?.addOnSuccessListener {
                            uploadImageAndGoHome()
                        }
                        ?.addOnFailureListener {
                            Toast.makeText(this, "Error al actualizar contraseña: ${it.message}", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    uploadImageAndGoHome()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error al actualizar datos: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadImageAndGoHome() {
        val uid = auth.currentUser?.uid ?: return

        if (imageUri != null) {
            val storageRef = FirebaseStorage.getInstance().reference.child("profileBackgroundImages/$uid.jpg")

            storageRef.putFile(imageUri!!)
                .addOnSuccessListener {
                    Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Error al subir imagen: ${it.message}", Toast.LENGTH_SHORT).show()
                    goToHome()
                }
        } else {
            Toast.makeText(this, "Perfil actualizado correctamente", Toast.LENGTH_SHORT).show()
            goToHome()
        }
    }

    private fun goToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finish()
    }
}