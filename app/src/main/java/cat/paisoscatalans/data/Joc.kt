package cat.paisoscatalans.data

import java.time.LocalDate
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * El joc diari. El municipi de cada dia es calcula al dispositiu, sense connexió:
 * es barregen tots els municipis amb una llavor fixa (SplitMix64, idèntic a tots
 * els telèfons) i cada dia s'agafa el següent de la llista. Així no es repeteix
 * cap municipi fins que s'han jugat tots, i tothom té el mateix repte el mateix dia.
 */
object JocDiari {
    const val MAX_INTENTS = 6
    private const val LLAVOR = 0x50414953_434154L // "PAISCAT"
    private val INICI: LocalDate = LocalDate.of(2026, 1, 1)
    private const val DISTANCIA_MAX_KM = 1000.0

    fun avui(): Long = LocalDate.now().toEpochDay()

    fun numero(dia: Long): Long = dia - INICI.toEpochDay() + 1

    private var cacheGeo: Geo? = null
    private var ordre: List<Municipi> = emptyList()

    @Synchronized
    private fun ordre(geo: Geo): List<Municipi> {
        if (cacheGeo !== geo) {
            val llista = geo.municipis.sortedBy { it.id }.toMutableList()
            var s = LLAVOR
            fun seguent(): Long {
                s += -0x61c8864680b583ebL
                var z = s
                z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
                z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
                return z xor (z ushr 31)
            }
            for (i in llista.size - 1 downTo 1) {
                val j = ((seguent() ushr 1) % (i + 1)).toInt()
                val t = llista[i]; llista[i] = llista[j]; llista[j] = t
            }
            ordre = llista
            cacheGeo = geo
        }
        return ordre
    }

    fun municipiDelDia(geo: Geo, dia: Long): Municipi {
        val o = ordre(geo)
        val idx = Math.floorMod(dia - INICI.toEpochDay(), o.size.toLong()).toInt()
        return o[idx]
    }

    fun distanciaKm(a: Municipi, b: Municipi): Double {
        val r = 6371.0
        val la1 = Math.toRadians(a.lat); val la2 = Math.toRadians(b.lat)
        val dla = la2 - la1
        val dlo = Math.toRadians(b.lon - a.lon)
        val h = sin(dla / 2) * sin(dla / 2) + cos(la1) * cos(la2) * sin(dlo / 2) * sin(dlo / 2)
        return 2 * r * asin(sqrt(h))
    }

    /** Rumb en graus (0 = nord, 90 = est) des de `de` cap a `a`. */
    fun rumb(de: Municipi, a: Municipi): Double {
        val la1 = Math.toRadians(de.lat); val la2 = Math.toRadians(a.lat)
        val dlo = Math.toRadians(a.lon - de.lon)
        val y = sin(dlo) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dlo)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    fun proximitat(km: Double): Int = max(0, (100 * (1 - km / DISTANCIA_MAX_KM)).roundToInt())

    fun nomRumb(graus: Double): String {
        val noms = listOf("nord", "nord-est", "est", "sud-est", "sud", "sud-oest", "oest", "nord-oest")
        return noms[((graus + 22.5) / 45).toInt() % 8]
    }
}

/** Ratxes: dies seguits amb la partida acabada (guanyada o no). */
object Ratxa {
    fun actual(estat: Estat, avui: Long): Int {
        val jugats = estat.partides.filterValues { it.acabada }.keys
        var dia = if (avui in jugats) avui else avui - 1
        var n = 0
        while (dia in jugats) {
            n++
            dia--
        }
        return n
    }

    fun maxima(estat: Estat): Int {
        val dies = estat.partides.filterValues { it.acabada }.keys.sorted()
        var millor = 0
        var actual = 0
        var anterior: Long? = null
        for (d in dies) {
            actual = if (anterior != null && d == anterior + 1) actual + 1 else 1
            if (actual > millor) millor = actual
            anterior = d
        }
        return millor
    }
}
