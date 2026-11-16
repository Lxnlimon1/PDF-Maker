package com.example.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.UserPreferences
import com.example.data.model.PageSizeOption
import com.example.data.model.PdfDocumentUi
import com.example.data.model.ThemeMode
import com.example.data.repository.PdfLibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class HomeUiState(
    val documents: List<PdfDocumentUi> = emptyList(),
    val searchQuery: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val highResScan: Boolean = true,
    val defaultPageSize: PageSizeOption = PageSizeOption.A4
)

class HomeViewModel(
    private val repository: PdfLibraryRepository,
    private val preferences: UserPreferences
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HomeUiState> = combine(
        repository.documentsFlow,
        searchQuery,
        isBusy,
        statusMessage,
        combine(preferences.themeMode, preferences.highResScan, preferences.defaultPageSize) { t, h, p ->
            Triple(t, h, p)
        }
    ) { docs, query, busy, msg, prefs ->
        val filtered = if (query.isBlank()) docs else {
            docs.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.sourceTag.contains(query, ignoreCase = true)
            }
        }
        HomeUiState(
            documents = filtered,
            searchQuery = query,
            isBusy = busy,
            statusMessage = msg,
            themeMode = prefs.first,
            highResScan = prefs.second,
            defaultPageSize = prefs.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun clearStatus() {
        statusMessage.value = null
    }

    fun importPdf(uri: Uri, onImported: (PdfDocumentUi) -> Unit) {
        viewModelScope.launch {
            isBusy.value = true
            val doc = repository.importPdfUri(uri)
            isBusy.value = false
            if (doc != null) {
                statusMessage.value = "Imported \"${doc.title}\""
                onImported(doc)
            } else {
                statusMessage.value = "Could not import selected PDF"
            }
        }
    }

    fun createBlankPdf(title: String, onCreated: (PdfDocumentUi) -> Unit) {
        viewModelScope.launch {
            isBusy.value = true
            val doc = repository.createBlankDocument(title.ifBlank { "Untitled Document" })
            isBusy.value = false
            if (doc != null) {
                onCreated(doc)
            } else {
                statusMessage.value = "Failed to create PDF"
            }
        }
    }

    fun createSampleStarter(onCreated: (PdfDocumentUi) -> Unit) {
        viewModelScope.launch {
            isBusy.value = true
            val doc = repository.createSampleStarterDocument()
            isBusy.value = false
            if (doc != null) {
                statusMessage.value = "Created sample editable PDF"
                onCreated(doc)
            }
        }
    }

    fun renameDocument(id: String, newTitle: String) {
        viewModelScope.launch {
            repository.renameDocument(id, newTitle)
            statusMessage.value = "Renamed document"
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            repository.deleteDocument(id)
            statusMessage.value = "Document deleted"
        }
    }

    fun exportDocumentToUri(context: Context, doc: PdfDocumentUi, destUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isBusy.value = true
            val ok = runCatching {
                context.contentResolver.openOutputStream(destUri)?.use { out ->
                    File(doc.filePath).inputStream().use { input ->
                        input.copyTo(out)
                    }
                }
                true
            }.getOrDefault(false)
            isBusy.value = false
            statusMessage.value = if (ok) "Saved copy of \"${doc.title}\"" else "Failed to export PDF"
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setHighResScan(enabled: Boolean) {
        viewModelScope.launch { preferences.setHighResScan(enabled) }
    }

    fun setDefaultPageSize(size: PageSizeOption) {
        viewModelScope.launch { preferences.setDefaultPageSize(size) }
    }

    companion object {
        fun factory(
            repository: PdfLibraryRepository,
            preferences: UserPreferences
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(repository, preferences) as T
            }
        }
    }
}
