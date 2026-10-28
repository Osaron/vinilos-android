package co.edu.uniandes.vinilos

import android.content.res.Resources
import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.allOf
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Escenarios E2E contra BackVynils real (ESC-07 y ESC-13 de «Sprint 1 ‐ Artefactos de prueba»).
 *
 * A diferencia de las demás pruebas Espresso, aquí la app usa su repositorio real y el resultado
 * esperado se obtiene consultando directamente la API REST. No hay datos fijos: la prueba se
 * adapta a los álbumes que tenga el backend.
 *
 * Solo se ejecuta si se activa de forma explícita, porque requiere el backend encendido:
 *
 *     gradlew connectedDebugAndroidTest
 *       -Pandroid.testInstrumentationRunnerArguments.backendReal=true
 *       -Pandroid.testInstrumentationRunnerArguments.class=co.edu.uniandes.vinilos.BackVynilsRealE2ETest
 *
 * Sin ese argumento (por ejemplo, en la integración continua) las pruebas quedan como omitidas.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class BackVynilsRealE2ETest {

    @Before
    fun requiereBackendReal() {
        val activada = InstrumentationRegistry.getArguments().getString(ARGUMENTO) == "true"
        assumeTrue(
            "Suite contra BackVynils real desactivada. Actívala con " +
                "-Pandroid.testInstrumentationRunnerArguments.$ARGUMENTO=true",
            activada,
        )
    }

    /** ESC-07: el catálogo muestra exactamente los álbumes que entrega GET /albums. */
    @Test
    fun esc07_elCatalogoCoincideConElBackend() {
        val esperados = BackVynils.albums()
        assertTrue(SIN_DATOS, esperados.isNotEmpty())

        ActivityScenario.launch(MainActivity::class.java).use {
            Catalogo.esperarContador(esperados.size)
            esperados.forEach { Catalogo.muestraAlbum(it.nombre) }
        }
    }

    /** ESC-13: el detalle de cada álbum coincide con GET /albums/{id} y Atrás regresa al catálogo. */
    @Test
    fun esc13_elDetalleDeCadaAlbumCoincideConElBackend() {
        val albums = BackVynils.albums()
        assertTrue(SIN_DATOS, albums.isNotEmpty())

        ActivityScenario.launch(MainActivity::class.java).use {
            Catalogo.esperarContador(albums.size)
            albums.forEach { album ->
                val esperado = BackVynils.album(album.id)

                Catalogo.abrirAlbum(album.nombre)
                Detalle.coincideCon(esperado)

                closeSoftKeyboard()
                pressBack()
                Catalogo.esperarContador(albums.size)
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Páginas (Page Object)
    // ---------------------------------------------------------------------------------------

    private object Catalogo {
        fun esperarContador(cantidad: Int) = esperar(TIEMPO_RED_MS) {
            val texto = recursos.getQuantityString(R.plurals.album_count, cantidad, cantidad)
            onView(withId(R.id.album_count)).check(matches(withText(texto)))
        }

        fun muestraAlbum(nombre: String) = esperar {
            onView(withId(R.id.album_list)).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(nombre))),
            )
            onView(allOf(withId(R.id.album_name), withText(nombre))).check(matches(isDisplayed()))
        }

        fun abrirAlbum(nombre: String) = esperar {
            onView(withId(R.id.album_list)).perform(
                RecyclerViewActions.actionOnItem<RecyclerView.ViewHolder>(hasDescendant(withText(nombre)), click()),
            )
        }
    }

    private object Detalle {
        fun coincideCon(album: AlbumEsperado) {
            esperar(TIEMPO_RED_MS) {
                onView(withId(R.id.detail_name)).check(matches(withText(album.nombre)))
            }
            onView(withId(R.id.detail_performers)).check(matches(withText(album.artistasTexto())))
            onView(withId(R.id.detail_release_date)).perform(scrollTo())
                .check(matches(withText(recursos.getString(R.string.release_date_value, album.fechaTexto()))))
            onView(withId(R.id.detail_genre)).perform(scrollTo())
                .check(matches(withText(recursos.getString(R.string.genre_value, album.genero.oNoDisponible()))))
            onView(withId(R.id.detail_record_label)).perform(scrollTo())
                .check(matches(withText(recursos.getString(R.string.record_label_value, album.sello.oNoDisponible()))))
            onView(withId(R.id.detail_description)).perform(scrollTo())
                .check(matches(withText(album.descripcion.ifBlank { recursos.getString(R.string.no_description) })))
            onView(withId(R.id.detail_tracks)).perform(scrollTo())
                .check(matches(withText(album.cancionesTexto())))
        }
    }

    // ---------------------------------------------------------------------------------------
    // Oráculo: lo que responde BackVynils, consultado directamente por HTTP
    // ---------------------------------------------------------------------------------------

    private data class AlbumEsperado(
        val id: Int,
        val nombre: String,
        val artistas: List<String>,
        val fecha: String,
        val genero: String,
        val sello: String,
        val descripcion: String,
        val canciones: List<Pair<String, String>>,
    ) {
        fun artistasTexto() = artistas.joinToString(", ").ifBlank { recursos.getString(R.string.unknown_artist) }

        fun fechaTexto() = fecha.take(10).oNoDisponible()

        fun cancionesTexto() =
            if (canciones.isEmpty()) {
                recursos.getString(R.string.no_tracks)
            } else {
                canciones.joinToString("\n") { (nombre, duracion) ->
                    if (duracion.isBlank()) "• $nombre" else "• $nombre ($duracion)"
                }
            }
    }

    private object BackVynils {
        fun albums(): List<AlbumEsperado> {
            val lista = JSONArray(get("albums"))
            return (0 until lista.length()).map { aAlbum(lista.getJSONObject(it)) }
        }

        fun album(id: Int): AlbumEsperado = aAlbum(JSONObject(get("albums/$id")))

        private fun get(ruta: String): String {
            val url = URL(BuildConfig.API_BASE_URL + ruta)
            val conexion = url.openConnection() as HttpURLConnection
            conexion.connectTimeout = TIEMPO_RED_MS.toInt()
            conexion.readTimeout = TIEMPO_RED_MS.toInt()
            try {
                val codigo = conexion.responseCode
                check(codigo == HttpURLConnection.HTTP_OK) { "GET $url respondió HTTP $codigo" }
                return conexion.inputStream.bufferedReader().use { it.readText() }
            } catch (error: IOException) {
                throw AssertionError(
                    "No se pudo conectar con BackVynils en $url. Verifica que el backend esté encendido " +
                        "(docker compose up -d) y responda en el navegador.",
                    error,
                )
            } finally {
                conexion.disconnect()
            }
        }

        private fun aAlbum(json: JSONObject) = AlbumEsperado(
            id = json.getInt("id"),
            nombre = json.texto("name").trim(),
            artistas = json.nombres("performers"),
            fecha = json.texto("releaseDate"),
            genero = json.texto("genre"),
            sello = json.texto("recordLabel"),
            descripcion = json.texto("description"),
            canciones = json.optJSONArray("tracks")?.let { lista ->
                (0 until lista.length()).mapNotNull { indice ->
                    val cancion = lista.getJSONObject(indice)
                    val nombre = cancion.texto("name").trim()
                    if (nombre.isEmpty()) null else nombre to cancion.texto("duration")
                }
            }.orEmpty(),
        )

        private fun JSONObject.texto(campo: String): String = if (isNull(campo)) "" else optString(campo)

        private fun JSONObject.nombres(campo: String): List<String> =
            optJSONArray(campo)?.let { lista ->
                (0 until lista.length()).mapNotNull { indice ->
                    lista.getJSONObject(indice).texto("name").trim().takeIf(String::isNotEmpty)
                }
            }.orEmpty()
    }

    private companion object {
        const val ARGUMENTO = "backendReal"
        const val TIEMPO_RED_MS = 15_000L
        const val SIN_DATOS = "BackVynils no tiene álbumes. Carga los datos de ejemplo (sql/data.sql)."

        val recursos: Resources
            get() = InstrumentationRegistry.getInstrumentation().targetContext.resources

        fun String.oNoDisponible() = ifBlank { recursos.getString(R.string.not_available) }

        /**
         * Reintenta [verificacion] hasta que pase o se agote el tiempo. Espresso no espera las
         * respuestas de red (Retrofit) ni el cálculo de la lista en segundo plano (ListAdapter).
         */
        fun esperar(tiempoMaximoMs: Long = 5_000, verificacion: () -> Unit) {
            val limite = SystemClock.elapsedRealtime() + tiempoMaximoMs
            while (true) {
                try {
                    verificacion()
                    return
                } catch (error: AssertionError) {
                    if (SystemClock.elapsedRealtime() > limite) throw error
                } catch (error: NoMatchingViewException) {
                    if (SystemClock.elapsedRealtime() > limite) throw error
                } catch (error: PerformException) {
                    if (SystemClock.elapsedRealtime() > limite) throw error
                }
                SystemClock.sleep(200)
            }
        }
    }
}
