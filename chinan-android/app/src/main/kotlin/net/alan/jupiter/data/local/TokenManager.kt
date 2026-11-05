package net.alan.jupiter.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TokenManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_ROLE = stringPreferencesKey("role")
        private val KEY_NAME = stringPreferencesKey("name")
        private val KEY_PHONE = stringPreferencesKey("phone")
        private val KEY_STUDENT_NO = stringPreferencesKey("student_no")
        private val KEY_CLASS_NAME = stringPreferencesKey("class_name")
        private val KEY_DEPARTMENT_NAME = stringPreferencesKey("department_name")
    }

    suspend fun getToken(): String? = dataStore.data.first()[KEY_TOKEN]
    suspend fun getUsername(): String? = dataStore.data.first()[KEY_USERNAME]
    suspend fun getRole(): String? = dataStore.data.first()[KEY_ROLE]
    suspend fun getName(): String? = dataStore.data.first()[KEY_NAME]
    suspend fun getPhone(): String? = dataStore.data.first()[KEY_PHONE]
    suspend fun getStudentNo(): String? = dataStore.data.first()[KEY_STUDENT_NO]
    suspend fun getClassName(): String? = dataStore.data.first()[KEY_CLASS_NAME]
    suspend fun getDepartmentName(): String? = dataStore.data.first()[KEY_DEPARTMENT_NAME]
    suspend fun hasToken(): Boolean = !getToken().isNullOrBlank()

    suspend fun saveLoginInfo(token: String, username: String, role: String,
                               name: String, phone: String, studentNo: String,
                               className: String? = null, departmentName: String? = null) {
        dataStore.edit {
            it[KEY_TOKEN] = token
            it[KEY_USERNAME] = username
            it[KEY_ROLE] = role
            it[KEY_NAME] = name
            it[KEY_PHONE] = phone
            it[KEY_STUDENT_NO] = studentNo
            className?.let { cn -> it[KEY_CLASS_NAME] = cn }
            departmentName?.let { dn -> it[KEY_DEPARTMENT_NAME] = dn }
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}