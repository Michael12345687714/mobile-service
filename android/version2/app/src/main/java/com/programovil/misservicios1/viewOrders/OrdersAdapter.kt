package com.programovil.misservicios1.viewOrders

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.Timestamp
import com.programovil.misservicios1.R
import java.text.SimpleDateFormat
import java.util.*

class OrdersAdapter : RecyclerView.Adapter<OrdersAdapter.OrderViewHolder>() {

    private var orders: List<Map<String, Any>> = listOf()

    fun setOrders(newOrders: List<Map<String, Any>>) {
        orders = newOrders
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_order, parent, false)
        return OrderViewHolder(view)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        val order = orders[position]
        holder.bind(order)
    }

    override fun getItemCount() = orders.size

    class OrderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val cantidadText: TextView = view.findViewById(R.id.cantidadText)
        private val notaText: TextView = view.findViewById(R.id.notaText)
        private val estadoText: TextView = view.findViewById(R.id.estadoText)
        private val nombreText: TextView = view.findViewById(R.id.nombreText)
        private val fechaText: TextView = view.findViewById(R.id.fechaText)

        fun bind(order: Map<String, Any>) {
            cantidadText.text = "Cantidad: ${order["cantidad"]}"
            notaText.text = "Nota: ${order["nota"] ?: "Sin nota"}"
            estadoText.text = "Estado: ${order["estado"]}"

            nombreText.text = "Usuario: ${order["nombreOtroUsuario"] ?: "Desconocido"}"

            val timestamp = order["timestamp"] as? Timestamp
            val fechaFormateada = timestamp?.toDate()?.let {
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(it)
            } ?: "Fecha desconocida"
            fechaText.text = "Fecha: $fechaFormateada"
        }
    }
}