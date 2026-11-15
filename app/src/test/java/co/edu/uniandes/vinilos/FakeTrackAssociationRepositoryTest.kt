package co.edu.uniandes.vinilos

import co.edu.uniandes.vinilos.data.FakeTrackAssociationRepository
import co.edu.uniandes.vinilos.data.TrackAssociationException
import co.edu.uniandes.vinilos.data.TrackAssociationException.Reason
import co.edu.uniandes.vinilos.model.NewTrack
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

/** El doble compartido con #32, #34 y #35 se comporta como el contrato real. */
class FakeTrackAssociationRepositoryTest {
    @Test fun `guarda la cancion solo en el album pedido`() = runTest {
        val fake = FakeTrackAssociationRepository()

        val saved = fake.addTrack(101, NewTrack("Decisiones", "5:05"))

        assertEquals(listOf(101 to NewTrack("Decisiones", "5:05")), fake.requests)
        assertEquals(listOf(saved), fake.getTracks(101))
        assertEquals(emptyList<Any>(), fake.getTracks(100))
    }

    @Test fun `simula un fallo programado y luego responde normal`() = runTest {
        val fake = FakeTrackAssociationRepository()
        fake.failNext(Reason.NETWORK)

        try { fake.addTrack(100, NewTrack("Decisiones", "5:05")); fail() }
        catch (error: TrackAssociationException) { assertEquals(Reason.NETWORK, error.reason) }
        assertEquals("Decisiones", fake.addTrack(100, NewTrack("Decisiones", "5:05")).name)
    }

    @Test fun `informa album inexistente`() = runTest {
        try { FakeTrackAssociationRepository().addTrack(999, NewTrack("x", "1:00")); fail() }
        catch (error: TrackAssociationException) { assertEquals(Reason.ALBUM_NOT_FOUND, error.reason) }
    }

    @Test fun `mantiene pendiente la solicitud hasta liberarla`() = runTest {
        val fake = FakeTrackAssociationRepository()
        val gate = fake.holdNext()

        val pending = async { fake.addTrack(100, NewTrack("Decisiones", "5:05")) }
        runCurrent()
        assertFalse(pending.isCompleted)
        gate.complete(Unit)
        assertEquals("Decisiones", pending.await().name)
    }
}
