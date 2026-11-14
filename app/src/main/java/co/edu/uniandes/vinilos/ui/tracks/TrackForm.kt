package co.edu.uniandes.vinilos.ui.tracks

import androidx.lifecycle.ViewModel

data class TrackForm(val name: String = "", val duration: String = "")
data class TrackFormErrors(val name: Boolean = false, val duration: Boolean = false) {
    val valid: Boolean get() = !name && !duration
}

object TrackFormValidator {
    private val durationPattern = Regex("^[0-9]+:[0-5][0-9]$")
    fun validate(form: TrackForm) = TrackFormErrors(
        name = form.name.isBlank(),
        duration = !durationPattern.matches(form.duration.trim()),
    )
}

/** Draft survives configuration changes. Selection and submission belong to the enclosing flow. */
class TrackFormViewModel : ViewModel() {
    var form = TrackForm()
        private set
    fun update(name: String, duration: String) { form = TrackForm(name, duration) }
    var showErrors: Boolean = false
        private set
    fun validate(): TrackFormErrors { showErrors = true; return TrackFormValidator.validate(form) }
    fun validated(): TrackForm? = form.takeIf { TrackFormValidator.validate(it).valid }
        ?.let { TrackForm(it.name.trim(), it.duration.trim()) }
}
