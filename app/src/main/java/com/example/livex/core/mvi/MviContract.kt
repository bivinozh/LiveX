package com.example.livex.core.mvi

/** Immutable snapshot of everything a screen renders. */
interface UiState

/** A user action or event sent from the UI to the ViewModel. */
interface UiIntent

/** A one-off event (navigation, toast, etc.) that must be consumed exactly once. */
interface UiEffect
