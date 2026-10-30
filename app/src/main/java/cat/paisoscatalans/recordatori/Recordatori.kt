package cat.paisoscatalans.recordatori

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import cat.paisoscatalans.MainActivity
import cat.paisoscatalans.R
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Recordatori diari del joc. Tot és local: una alarma del sistema (sense hora exacta,
 * per no demanar permisos especials) i una notificació. Si avui ja s'ha jugat, no avisa.
 */
object Recordatori {
    private const val CANAL = "recordatori-joc"
    private const val ACCIO = "cat.paisoscatalans.RECORDATORI"
    const val EXTRA_OBRE = "obre"

    private fun intencio(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 1,
        Intent(ctx, ReceptorRecordatori::class.java).setAction(ACCIO),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Programa el proper avís a l'hora indicada (minuts des de mitjanit). */
    fun programa(ctx: Context, minuts: Int) {
        val ara = LocalDateTime.now()
        var quan = LocalDateTime.of(LocalDate.now(), LocalTime.of(minuts / 60, minuts % 60))
        if (!quan.isAfter(ara.plusSeconds(30))) quan = quan.plusDays(1)
        val ms = quan.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.setWindow(AlarmManager.RTC_WAKEUP, ms, 10 * 60 * 1000L, intencio(ctx))
    }

    fun cancella(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java)?.cancel(intencio(ctx))
    }

    fun potNotificar(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun mostra(ctx: Context) {
        if (!potNotificar(ctx)) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CANAL) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CANAL, "Recordatori del joc diari", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Un avís cada dia per jugar al municipi del dia"
                },
            )
        }
        val obre = PendingIntent.getActivity(
            ctx, 2,
            Intent(ctx, MainActivity::class.java)
                .putExtra(EXTRA_OBRE, "joc")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacio)
            .setContentTitle("El municipi del dia t'espera")
            .setContentText("Endevina el municipi d'avui i mantén la ratxa.")
            .setContentIntent(obre)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(1, n)
        } catch (_: SecurityException) {
        }
    }

    /** Llegeix els ajustos directament del fitxer d'estat (el receptor no té ViewModel). */
    internal fun llegeixAjustos(ctx: Context): Triple<Boolean, Int, Boolean> {
        val f = File(ctx.filesDir, "estat.json")
        if (!f.exists()) return Triple(false, 19 * 60, false)
        return try {
            val o = JSONObject(f.readText())
            val avui = LocalDate.now().toEpochDay().toString()
            val jugat = o.optJSONObject("partides")?.optJSONObject(avui)?.optBoolean("acabada") == true
            Triple(o.optBoolean("recordatori"), o.optInt("horaRecordatori", 19 * 60), jugat)
        } catch (_: Exception) {
            Triple(false, 19 * 60, false)
        }
    }
}

class ReceptorRecordatori : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val (actiu, minuts, jugatAvui) = Recordatori.llegeixAjustos(context)
        if (!actiu) return
        if (intent.action == "cat.paisoscatalans.RECORDATORI" && !jugatAvui) Recordatori.mostra(context)
        // Sempre es torna a programar per a la propera vegada (també després de reiniciar)
        Recordatori.programa(context, minuts)
    }
}
