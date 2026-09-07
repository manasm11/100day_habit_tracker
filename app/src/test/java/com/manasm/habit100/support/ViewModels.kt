package com.manasm.habit100.support

import androidx.lifecycle.ViewModel

/**
 * Cancels a ViewModel's `viewModelScope` from a test's `@After`.
 *
 * The production ViewModels keep long-lived Room collectors alive via
 * `stateIn(viewModelScope, WhileSubscribed(...))` and `init {}` blocks. Without cancelling
 * them before the in-memory database is closed, a trailing re-query races the close and
 * Robolectric's CloseGuard dumps a spurious "resource never released" stack trace into the
 * suite output. There is no public API for this, hence the internal-method reflection.
 */
fun ViewModel.clearForTest() {
    runCatching {
        ViewModel::class.java
            .getDeclaredMethod("clear\$lifecycle_viewmodel_release")
            .apply { isAccessible = true }
            .invoke(this)
    }.onFailure {
        error("ViewModels.clearForTest needs updating for this androidx.lifecycle version: ${it.message}")
    }
}
