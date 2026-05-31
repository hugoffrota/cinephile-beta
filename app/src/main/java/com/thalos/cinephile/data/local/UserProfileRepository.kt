package com.thalos.cinephile.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PrefKeys {
    val TMDB_API_KEY = stringPreferencesKey("tmdb_api_key")
    val USER_NAME = stringPreferencesKey("user_name")
    val ONBOARDING_COMPLETE = stringPreferencesKey("onboarding_complete")
    val COUNTRY_CODE = stringPreferencesKey("country_code")
    val HIDE_THEATER_ONLY = booleanPreferencesKey("hide_theater_only")
    val HIDE_OBSCURE = booleanPreferencesKey("hide_obscure")
}

class UserProfileRepository(private val dataStore: DataStore<Preferences>) {
    val apiKey: Flow<String?> = dataStore.data.map { it[PrefKeys.TMDB_API_KEY] }
    val userName: Flow<String?> = dataStore.data.map { it[PrefKeys.USER_NAME] }
    val onboardingComplete: Flow<Boolean> = dataStore.data.map { it[PrefKeys.ONBOARDING_COMPLETE] == "true" }
    val countryCode: Flow<String> = dataStore.data.map { it[PrefKeys.COUNTRY_CODE] ?: "BR" }
    val hideTheaterOnly: Flow<Boolean> = dataStore.data.map { it[PrefKeys.HIDE_THEATER_ONLY] == true }
    val hideObscure: Flow<Boolean> = dataStore.data.map { it[PrefKeys.HIDE_OBSCURE] == true }

    suspend fun setApiKey(key: String) {
        dataStore.edit { it[PrefKeys.TMDB_API_KEY] = key }
    }

    suspend fun setUserName(name: String) {
        dataStore.edit { it[PrefKeys.USER_NAME] = name }
    }

    suspend fun setOnboardingComplete() {
        dataStore.edit { it[PrefKeys.ONBOARDING_COMPLETE] = "true" }
    }

    suspend fun setHideTheaterOnly(hide: Boolean) {
        dataStore.edit { it[PrefKeys.HIDE_THEATER_ONLY] = hide }
    }

    suspend fun setHideObscure(hide: Boolean) {
        dataStore.edit { it[PrefKeys.HIDE_OBSCURE] = hide }
    }

    suspend fun setCountryCode(code: String) {
        dataStore.edit { it[PrefKeys.COUNTRY_CODE] = code.uppercase() }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
