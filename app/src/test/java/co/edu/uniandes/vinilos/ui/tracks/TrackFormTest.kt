package co.edu.uniandes.vinilos.ui.tracks

import org.junit.Assert.*
import org.junit.Test

class TrackFormTest {
    @Test fun requiredFieldsAndDurationBoundaries() {
        assertFalse(TrackFormValidator.validate(TrackForm("  ", "")).valid)
        listOf("0:00", "3:45", "123:59").forEach {
            assertTrue(it, TrackFormValidator.validate(TrackForm("Tema", it)).valid)
        }
        listOf("3:60", "3:5", "-1:00", "3:999", "abc").forEach {
            assertFalse(it, TrackFormValidator.validate(TrackForm("Tema", it)).valid)
        }
    }
    @Test fun draftRetainsInputAndNormalizesOnlyOnValidSubmission() {
        val vm = TrackFormViewModel()
        vm.update("  Tema  ", " 3:45 ")
        assertEquals("  Tema  ", vm.form.name)
        assertEquals(TrackForm("Tema", "3:45"), vm.validated())
        vm.update("Tema", "3:70")
        assertNull(vm.validated())
        assertEquals("3:70", vm.form.duration)
    }
}
