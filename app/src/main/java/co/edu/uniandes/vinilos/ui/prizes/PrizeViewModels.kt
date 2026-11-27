package co.edu.uniandes.vinilos.ui.prizes

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.edu.uniandes.vinilos.data.ActiveCollector
import co.edu.uniandes.vinilos.data.NewPrize
import co.edu.uniandes.vinilos.data.PrizeRepository
import co.edu.uniandes.vinilos.model.Prize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class PrizeCatalogState(
    val loading: Boolean = true,
    val prizes: List<Prize> = emptyList(),
    val failed: Boolean = false,
    val createdName: String? = null,
)

class PrizeCatalogViewModel(private val repository: PrizeRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(PrizeCatalogState())
    val state = mutableState.asStateFlow()
    private var load: Job? = null
    init { refresh() }

    fun refresh(createdName: String? = state.value.createdName) {
        load?.cancel()
        mutableState.value = state.value.copy(loading = true, failed = false, createdName = createdName)
        load = viewModelScope.launch {
            try {
                mutableState.value = state.value.copy(loading = false, prizes = repository.getPrizes())
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                mutableState.value = state.value.copy(loading = false, failed = true)
            }
        }
    }
}

data class PrizeDraft(val name: String = "", val description: String = "", val organization: String = "") {
    val hasContent get() = name.isNotEmpty() || description.isNotEmpty() || organization.isNotEmpty()
}
enum class PrizeField { NAME, DESCRIPTION, ORGANIZATION }
enum class PrizeSaveError { REJECTED, UNCERTAIN }
data class CreatePrizeState(
    val draft: PrizeDraft = PrizeDraft(),
    val errors: Set<PrizeField> = emptySet(),
    val submitting: Boolean = false,
    val error: PrizeSaveError? = null,
    val needsCollector: Boolean = false,
    val created: Prize? = null,
) {
    val canSubmit get() = !submitting && created == null && error != PrizeSaveError.UNCERTAIN
    val canEdit get() = canSubmit
}

/** Marca la escritura antes de enviarla: una recreación del proceso nunca relanza el POST. */
class CreatePrizeViewModel(
    private val repository: PrizeRepository,
    private val collector: StateFlow<ActiveCollector?>,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private val restored = PrizeDraft(handle[NAME] ?: "", handle[DESCRIPTION] ?: "", handle[ORGANIZATION] ?: "")
    private val mutableState = MutableStateFlow(CreatePrizeState(
        draft = restored,
        error = if (handle.get<Boolean>(WRITING) == true) PrizeSaveError.UNCERTAIN else null,
        created = handle.get<Int>(CREATED_ID)?.let { Prize(it, restored.name.trim(), restored.organization.trim(), restored.description.trim()) },
    ))
    val state = mutableState.asStateFlow()
    private var showErrors = false

    fun updateDraft(draft: PrizeDraft) {
        if (!state.value.canEdit || state.value.draft == draft) return
        handle[NAME] = draft.name; handle[DESCRIPTION] = draft.description; handle[ORGANIZATION] = draft.organization
        mutableState.value = state.value.copy(draft = draft, errors = if (showErrors) validate(draft) else emptySet(), error = null)
    }

    fun submit() {
        val current = state.value
        if (!current.canSubmit) return
        showErrors = true
        val errors = validate(current.draft)
        val needsCollector = collector.value == null
        if (errors.isNotEmpty() || needsCollector) {
            mutableState.value = current.copy(errors = errors, needsCollector = needsCollector, error = null)
            return
        }
        val draft = current.draft
        handle[WRITING] = true
        mutableState.value = current.copy(errors = emptySet(), needsCollector = false, submitting = true, error = null)
        viewModelScope.launch {
            try {
                val created = repository.createPrize(NewPrize(draft.name.trim(), draft.description.trim(), draft.organization.trim()))
                handle[CREATED_ID] = created.id
                handle[WRITING] = false
                mutableState.value = state.value.copy(submitting = false, created = created)
            } catch (cancelled: CancellationException) {
                // El servidor pudo aplicar el POST. Se conserva WRITING para la restauración.
                throw cancelled
            } catch (http: HttpException) {
                val rejected = http.code() in 400..499 && http.code() != 408
                if (rejected) handle[WRITING] = false
                mutableState.value = state.value.copy(submitting = false,
                    error = if (rejected) PrizeSaveError.REJECTED else PrizeSaveError.UNCERTAIN)
            } catch (_: Exception) {
                // Red, 2xx inválido o error desconocido: no hay prueba de que no se haya creado.
                mutableState.value = state.value.copy(submitting = false, error = PrizeSaveError.UNCERTAIN)
            }
        }
    }

    companion object {
        const val NAME = "hu13Name"
        const val DESCRIPTION = "hu13Description"
        const val ORGANIZATION = "hu13Organization"
        const val WRITING = "hu13Writing"
        const val CREATED_ID = "hu13CreatedId"
        fun validate(draft: PrizeDraft): Set<PrizeField> = buildSet {
            if (draft.name.isBlank()) add(PrizeField.NAME)
            if (draft.description.isBlank()) add(PrizeField.DESCRIPTION)
            if (draft.organization.isBlank()) add(PrizeField.ORGANIZATION)
        }
    }
}
