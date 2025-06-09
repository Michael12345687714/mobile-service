const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

exports.sendOrderNotification = functions.https.onCall(async (data, context) => {
  try {
    const {proveedorId, clienteId, cantidad, nota} = data;

    // Verificar autenticación
    if (!context.auth) {
      throw new functions.https.HttpsError("failed-precondition",
          "La función debe ser llamada por un usuario autenticado.");
    }

    // Obtener información del proveedor
    const proveedorDoc = await admin.firestore()
        .collection("userServices")
        .doc(proveedorId)
        .get();

    if (!proveedorDoc.exists) {
      throw new functions.https.HttpsError("not-found",
          "Proveedor no encontrado");
    }

    // Obtener información del cliente
    const clienteDoc = await admin.firestore()
        .collection("userClients")
        .doc(clienteId)
        .get();

    if (!clienteDoc.exists) {
      throw new functions.https.HttpsError("not-found",
          "Cliente no encontrado");
    }

    const proveedorData = proveedorDoc.data();
    const clienteData = clienteDoc.data();
    const fcmToken = proveedorData.fcmToken;

    if (!fcmToken) {
      console.log("El proveedor no tiene token FCM registrado");
      return {success: false, error: "No FCM token"};
    }

    const clienteNombre = clienteData.username || "Cliente";
    const serviceType = proveedorData.serviceType || "servicio";

    // Crear mensaje de notificación
    const message = {
      token: fcmToken,
      notification: {
        title: "🔔 Nuevo Pedido Recibido",
        body: `${clienteNombre} solicita ${cantidad} unidades de ${serviceType}`,
      },
      data: {
        orderId: proveedorId,
        clienteId: clienteId,
        cantidad: cantidad.toString(),
        nota: nota || "",
        type: "new_order",
      },
      android: {
        notification: {
          sound: "default",
          priority: "high",
          channelId: "order_notifications",
        },
      },
    };

    // Enviar notificación
    const response = await admin.messaging().send(message);
    console.log("Notificación enviada exitosamente:", response);

    return {success: true, messageId: response};
  } catch (error) {
    console.error("Error al enviar notificación:", error);
    throw new functions.https.HttpsError("internal", error.message);
  }
});