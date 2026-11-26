package co.edu.uniandes.vinilos.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectorAlbumRepositoryTest {
    private class FakeService : CollectorAlbumService {
        var relations = listOf(CollectorAlbumDto(album = CollectorAlbumReferenceDto(7, "Album")))
        var response = CollectorAlbumDto(CollectorAlbumReferenceDto(7), CollectorAlbumReferenceDto(2))
        var calls = 0
        var request: AddCollectorAlbumRequest? = null
        var ids: Pair<Int, Int>? = null
        override suspend fun getAlbums(collectorId: Int) = relations
        override suspend fun addAlbum(collectorId: Int, albumId: Int, request: AddCollectorAlbumRequest): CollectorAlbumDto {
            calls++
            this.request = request
            ids = collectorId to albumId
            return response
        }
    }

    @Test fun readsNestedAlbumIdsAndNames() = runTest {
        val repository = CollectorAlbumRepository(FakeService())
        assertEquals(setOf(7), repository.getAlbumIds(2))
        assertEquals(listOf("Album"), repository.getAlbumNames(2))
    }

    @Test fun rejectsMalformedRelation() = runTest {
        val service = FakeService()
        service.relations = listOf(CollectorAlbumDto())
        assertTrue(runCatching { CollectorAlbumRepository(service).getAlbumIds(2) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test fun sendsCorrectIdsPriceAndStatus() = runTest {
        val service = FakeService()
        val repository = CollectorAlbumRepository(service)
        repository.addAlbum(2, 7, 0, CollectorAlbumStatus.INACTIVE)
        assertEquals(2 to 7, service.ids)
        assertEquals(AddCollectorAlbumRequest(0, "Inactive"), service.request)
        repository.addAlbum(2, 7, 15, CollectorAlbumStatus.ACTIVE)
        assertEquals(AddCollectorAlbumRequest(15, "Active"), service.request)
    }

    @Test fun rejectsInvalidInputWithoutPosting() = runTest {
        val service = FakeService()
        val repository = CollectorAlbumRepository(service)
        for ((collector, album, price) in listOf(Triple(0, 7, 1), Triple(2, 0, 1), Triple(2, 7, -1))) {
            assertTrue(runCatching { repository.addAlbum(collector, album, price, CollectorAlbumStatus.ACTIVE) }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(0, service.calls)
    }

    @Test fun rejectsResponseForDifferentCollectorOrAlbum() = runTest {
        val service = FakeService()
        val repository = CollectorAlbumRepository(service)
        for ((album, collector) in listOf(8 to 2, 7 to 3)) {
            service.response = CollectorAlbumDto(CollectorAlbumReferenceDto(album), CollectorAlbumReferenceDto(collector))
            assertTrue(runCatching { repository.addAlbum(2, 7, 1, CollectorAlbumStatus.ACTIVE) }.exceptionOrNull() is IllegalArgumentException)
        }
    }
}
