package com.ezhil.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.ezhil.app.data.local.SecurePrefs
import com.ezhil.app.ui.strings.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AppLanguageViewModel @Inject constructor(
    private val prefs: SecurePrefs
) : ViewModel() {

    private val _language = MutableStateFlow(
        if (prefs.appLanguage == "tamil") AppLanguage.TAMIL else AppLanguage.ENGLISH
    )
    val language: StateFlow<AppLanguage> = _language

    fun toggle() {
        val next = if (_language.value == AppLanguage.TAMIL) AppLanguage.ENGLISH else AppLanguage.TAMIL
        _language.value = next
        prefs.appLanguage = if (next == AppLanguage.TAMIL) "tamil" else "english"
    }
}
