package com.hearu.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.hearu.app.MainActivity
import com.hearu.app.R
import com.hearu.app.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class HearUFirebaseMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var userRepository: UserRepository

    @Inject
    lateinit var auth: FirebaseAuth

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received: $token")
        val currentUid = auth.currentUser?.uid
        if (currentUid != null) {
            serviceScope.launch {
                try {
                    userRepository.updateFcmToken(currentUid, token)
                    Log.d(TAG, "Successfully synced FCM token for user: $currentUid")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync FCM token to Firestore: ${e.message}", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        // Ensure channels are created
        createNotificationChannels(this)

        val data = remoteMessage.data
        val notification = remoteMessage.notification

        val type = data["type"] ?: "general"
        val sessionId = data["sessionId"] ?: ""
        val userId = data["userId"] ?: ""
        val senderName = data["senderName"] ?: "A Peer"

        val title = notification?.title
            ?: data["title"]
            ?: when (type) {
                TYPE_SESSION_REQUEST -> "New Listening Request"
                TYPE_CHAT_MESSAGE -> "New Message from $senderName"
                TYPE_CRISIS_ALERT -> "Emergency Crisis Alert"
                TYPE_SESSION_ENDED -> "Session Concluded"
                else -> "HearU Notification"
            }

        val body = notification?.body
            ?: data["body"]
            ?: data["messageText"]
            ?: when (type) {
                TYPE_SESSION_REQUEST -> "Someone is seeking empathetic support right now."
                TYPE_CHAT_MESSAGE -> "You have received a new compassionate response."
                TYPE_CRISIS_ALERT -> "A peer requires urgent support. Safety resources are available."
                TYPE_SESSION_ENDED -> "Your conversation has concluded. Please take a moment to reflect."
                else -> "You have a new update from HearU."
            }

        showNotification(
            type = type,
            title = title,
            body = body,
            sessionId = sessionId,
            userId = userId
        )
    }

    private fun showNotification(
        type: String,
        title: String,
        body: String,
        sessionId: String,
        userId: String
    ) {
        val channelId = when (type) {
            TYPE_SESSION_REQUEST -> CHANNEL_SESSION_REQUESTS
            TYPE_CRISIS_ALERT -> CHANNEL_SAFETY_ALERTS
            else -> CHANNEL_CHAT_MESSAGES
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, type)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_USER_ID, userId)
        }

        val pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        val notificationId = (System.currentTimeMillis() % 100000).toInt()
        val pendingIntent = PendingIntent.getActivity(this, notificationId, intent, pendingIntentFlags)

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (type == TYPE_CRISIS_ALERT) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )

        if (type == TYPE_CRISIS_ALERT) {
            notificationBuilder.setVibrate(longArrayOf(0, 500, 200, 500))
            notificationBuilder.setLights(Color.RED, 1000, 1000)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "HearUMessagingService"

        const val CHANNEL_CHAT_MESSAGES = "hearu_chat_messages"
        const val CHANNEL_SESSION_REQUESTS = "hearu_session_requests"
        const val CHANNEL_SAFETY_ALERTS = "hearu_safety_alerts"

        const val TYPE_CHAT_MESSAGE = "chat_message"
        const val TYPE_SESSION_REQUEST = "session_request"
        const val TYPE_CRISIS_ALERT = "crisis_alert"
        const val TYPE_SESSION_ENDED = "session_ended"

        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        const val EXTRA_SESSION_ID = "extra_session_id"
        const val EXTRA_USER_ID = "extra_user_id"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                val chatChannel = NotificationChannel(
                    CHANNEL_CHAT_MESSAGES,
                    "Chat Messages",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Real-time peer and companion chat messages"
                    enableLights(true)
                    lightColor = Color.BLUE
                    enableVibration(true)
                }

                val sessionChannel = NotificationChannel(
                    CHANNEL_SESSION_REQUESTS,
                    "Session Requests",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Incoming peer listening and connection requests"
                    enableLights(true)
                    lightColor = Color.GREEN
                    enableVibration(true)
                }

                val safetyChannel = NotificationChannel(
                    CHANNEL_SAFETY_ALERTS,
                    "Safety & Crisis Alerts",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Urgent safety and crisis support escalations"
                    enableLights(true)
                    lightColor = Color.RED
                    enableVibration(true)
                }

                notificationManager.createNotificationChannels(listOf(chatChannel, sessionChannel, safetyChannel))
            }
        }
    }
}
