package pl.planlekcji

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL = "zmiany_planu"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, "Zmiany w planie", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Powiadomienia o zmianach w planie lekcji (sala, nauczyciel, zastępstwa)"
            ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun canNotify(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    }

    fun notifyChanges(ctx: Context, changes: List<Change>) {
        if (changes.isEmpty() || !canNotify(ctx)) return
        ensureChannel(ctx)
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val lines = changes.take(7).map { "${it.title()}: ${it.text}" }
        val style = NotificationCompat.InboxStyle()
        lines.forEach { style.addLine(it) }
        if (changes.size > lines.size) style.setSummaryText("+${changes.size - lines.size} więcej")

        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(
                if (changes.size == 1) "Zmiana w planie lekcji" else "Zmiany w planie lekcji (${changes.size})"
            )
            .setContentText(lines.first())
            .setStyle(style)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % 1_000_000_000L).toInt(), n)
        } catch (e: SecurityException) {
            // brak zgody na powiadomienia – pomijamy
        }
    }
}
