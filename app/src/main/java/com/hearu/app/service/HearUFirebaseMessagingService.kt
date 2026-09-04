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

        val data = remoteMessage.data
        val notificationType = data[EXTRA_NOTIFICATION_TYPE] ?: TYPE_CHAT_MESSAGE
        val sessionId = data[EXTRA_SESSION_ID] ?: ""
        val senderId = data[EXTRA_USER_ID] ?: ""

        val title = remoteMessage.notification?.title ?: when (notificationType) {
            TYPE_SESSION_REQUEST -> "New Listener Request"
            TYPE_CRISIS_ALERT -> "Emergency Alert"
            else -> "HearU Message"
        }

        val body = remoteMessage.notification?.body ?: data["message"] ?: "You have a new update in HearU."

        sendNotification(
            title = title,
            body = body,
            notificationType = notificationType,
            sessionId = sessionId,
            userId = senderId
        )
    }

    private fun sendNotification(
        title: String,
        body: String,
        notificationType: String,
        sessionId: String,
        userId: String
    ) {
        val channelId = when (notificationType) {
            TYPE_SESSION_REQUEST -> CHANNEL_SESSION_REQUESTS
            TYPE_CRISIS_ALERT -> CHANNEL_SAFETY_ALERTS
            else -> CHANNEL_CHAT_MESSAGES
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_NOTIFICATION_TYPE, notificationType)
            putExtra(EXTRA_SESSION_ID, sessionId)
            putExtra(EXTRA_USER_ID, userId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(
                if (notificationType == TYPE_CRISIS_ALERT) NotificationCompat.PRIORITY_MAX
                else NotificationCompat.PRIORITY_HIGH
            )

        if (notificationType == TYPE_CRISIS_ALERT) {
            notificationBuilder.setColor(Color.RED)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notificationBuilder.build())
    }

    companion object {
        private const val TAG = "HearUMessagingService"

        const val CHANNEL_CHAT_MESSAGES = HearUNotificationConfig.CHANNEL_CHAT_MESSAGES
        const val CHANNEL_SESSION_REQUESTS = HearUNotificationConfig.CHANNEL_SESSION_REQUESTS
        const val CHANNEL_SAFETY_ALERTS = HearUNotificationConfig.CHANNEL_SAFETY_ALERTS

        const val TYPE_CHAT_MESSAGE = HearUNotificationConfig.TYPE_CHAT_MESSAGE
        const val TYPE_SESSION_REQUEST = HearUNotificationConfig.TYPE_SESSION_REQUEST
        const val TYPE_CRISIS_ALERT = HearUNotificationConfig.TYPE_CRISIS_ALERT
        const val TYPE_SESSION_ENDED = HearUNotificationConfig.TYPE_SESSION_ENDED

        const val EXTRA_NOTIFICATION_TYPE = HearUNotificationConfig.EXTRA_NOTIFICATION_TYPE
        const val EXTRA_SESSION_ID = HearUNotificationConfig.EXTRA_SESSION_ID
        const val EXTRA_USER_ID = HearUNotificationConfig.EXTRA_USER_ID

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
