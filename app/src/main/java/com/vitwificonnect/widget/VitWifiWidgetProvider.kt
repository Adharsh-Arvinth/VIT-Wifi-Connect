package com.vitwificonnect.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.vitwificonnect.R
import com.vitwificonnect.data.CredentialStore
import com.vitwificonnect.data.NetworkDetector
import com.vitwificonnect.data.SettingsStore
import com.vitwificonnect.data.VitWifiManager
import com.vitwificonnect.ui.MainActivity
import java.util.concurrent.Executors

class VitWifiWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_WIDGET_CONNECT = "com.vitwificonnect.widget.ACTION_CONNECT"
        const val ACTION_WIDGET_OPEN = "com.vitwificonnect.widget.ACTION_OPEN"

        private val backgroundExecutor = Executors.newSingleThreadExecutor()

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, VitWifiWidgetProvider::class.java)
            val ids = appWidgetManager.getAppWidgetIds(component)
            if (ids.isNotEmpty()) {
                val intent = Intent(context, VitWifiWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
                context.sendBroadcast(intent)
            }
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        CredentialStore.init(context)
        SettingsStore.init(context)

        backgroundExecutor.execute {
            for (widgetId in appWidgetIds) {
                renderWidgetState(context, appWidgetManager, widgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        CredentialStore.init(context)
        SettingsStore.init(context)

        when (intent.action) {
            ACTION_WIDGET_CONNECT -> {
                handleOneTapConnect(context)
            }
            ACTION_WIDGET_OPEN -> {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                context.startActivity(launchIntent)
            }
        }
    }

    private fun handleOneTapConnect(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, VitWifiWidgetProvider::class.java)
        val ids = appWidgetManager.getAppWidgetIds(component)

        if (!CredentialStore.hasCredentials(context)) {
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(launchIntent)
            return
        }

        // If currently online and not explicitly disconnected, ONE-TAP DISCONNECT
        val isOnline = NetworkDetector.isWifiActuallyOnline(context)
        val isExplicitlyDisconnected = SettingsStore.isExplicitlyDisconnected(context)

        if (isOnline && !isExplicitlyDisconnected) {
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_layout)
                views.setTextViewText(R.id.widget_state, "Disconnecting…")
                views.setImageViewResource(R.id.widget_status_dot, R.drawable.status_dot_connecting)
                views.setTextViewText(R.id.widget_connect_button, "…")
                attachIntents(context, views)
                appWidgetManager.updateAppWidget(id, views)
            }

            VitWifiManager.disconnect(context, object : VitWifiManager.LogoutCallback {
                override fun onSuccess(message: String) {
                    for (id in ids) {
                        renderWidgetState(context, appWidgetManager, id)
                    }
                }
                override fun onFailure(message: String) {
                    for (id in ids) {
                        renderWidgetState(context, appWidgetManager, id)
                    }
                }
            })
            return
        }

        // ONE-TAP CONNECT
        SettingsStore.setExplicitlyDisconnected(context, false)

        // Show immediate connecting feedback
        for (id in ids) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            views.setTextViewText(R.id.widget_state, "Connecting…")
            views.setImageViewResource(R.id.widget_status_dot, R.drawable.status_dot_connecting)
            views.setTextViewText(R.id.widget_connect_button, "…")
            attachIntents(context, views)
            appWidgetManager.updateAppWidget(id, views)
        }

        VitWifiManager.connectAndAuthenticate(context, object : VitWifiManager.ConnectionCallback {
            override fun onProgress(message: String) {
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_state, message)
                    views.setImageViewResource(R.id.widget_status_dot, R.drawable.status_dot_connecting)
                    views.setTextViewText(R.id.widget_connect_button, "…")
                    attachIntents(context, views)
                    appWidgetManager.updateAppWidget(id, views)
                }
            }

            override fun onSuccess(message: String) {
                for (id in ids) {
                    renderWidgetState(context, appWidgetManager, id)
                }
            }

            override fun onFailure(message: String, isCredentialsError: Boolean) {
                for (id in ids) {
                    val views = RemoteViews(context.packageName, R.layout.widget_layout)
                    views.setTextViewText(R.id.widget_state, if (isCredentialsError) "Invalid Creds" else "Failed")
                    views.setImageViewResource(R.id.widget_status_dot, R.drawable.status_dot_error)
                    views.setTextViewText(R.id.widget_connect_button, "Retry")
                    attachIntents(context, views)
                    appWidgetManager.updateAppWidget(id, views)
                }
            }
        })
    }

    private fun renderWidgetState(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)

        val isWifi = NetworkDetector.isWifiConnected(context)
        val isOnline = if (isWifi) NetworkDetector.isOnline(context) else false
        val isVit = if (isWifi && !isOnline) NetworkDetector.isVitNetwork(context) else false
        val hasCreds = CredentialStore.hasCredentials(context)

        val stateText: String
        val dotRes: Int
        val buttonText: String

        when {
            SettingsStore.isExplicitlyDisconnected(context) -> {
                stateText = "Disconnected"
                dotRes = R.drawable.status_dot_disconnected
                buttonText = "Connect"
            }
            !isWifi -> {
                stateText = "WiFi disconnected"
                dotRes = R.drawable.status_dot_disconnected
                buttonText = "Connect"
            }
            isOnline -> {
                stateText = "Connected"
                dotRes = R.drawable.status_dot_connected
                buttonText = "Disconnect"
            }
            isVit -> {
                if (hasCreds) {
                    stateText = "Ready to connect"
                    dotRes = R.drawable.status_dot_connecting
                    buttonText = "Connect"
                } else {
                    stateText = "Credentials required"
                    dotRes = R.drawable.status_dot_connecting
                    buttonText = "Setup"
                }
            }
            else -> {
                stateText = "Non-campus WiFi"
                dotRes = R.drawable.status_dot_disconnected
                buttonText = "Connect"
            }
        }

        views.setTextViewText(R.id.widget_state, stateText)
        views.setImageViewResource(R.id.widget_status_dot, dotRes)
        views.setTextViewText(R.id.widget_connect_button, buttonText)

        attachIntents(context, views)
        appWidgetManager.updateAppWidget(widgetId, views)
    }

    private fun attachIntents(context: Context, views: RemoteViews) {
        val connectIntent = Intent(context, VitWifiWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CONNECT
        }
        val connectPendingIntent = PendingIntent.getBroadcast(
            context,
            201,
            connectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_connect_button, connectPendingIntent)

        val openIntent = Intent(context, VitWifiWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_OPEN
        }
        val openPendingIntent = PendingIntent.getBroadcast(
            context,
            202,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)
    }
}
