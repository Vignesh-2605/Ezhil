package com.ezhil.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurePrefs @Inject constructor(@ApplicationContext context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "ezhil_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Server address, settable at runtime so one APK works on any network.
     *  Null or blank falls back to the compile-time BuildConfig default. */
    var serverUrl: String?
        get() = prefs.getString("server_url", null)
        set(value) = prefs.edit().putString("server_url", value?.trim()).apply()

    var authToken: String?
        get() = prefs.getString("auth_token", null)
        set(value) = prefs.edit().putString("auth_token", value).apply()

    var teacherId: String?
        get() = prefs.getString("teacher_id", null)
        set(value) = prefs.edit().putString("teacher_id", value).apply()

    var teacherName: String?
        get() = prefs.getString("teacher_name", null)
        set(value) = prefs.edit().putString("teacher_name", value).apply()

    /** Server-side school UUID. Needed to rebuild the school/teacher rows that
     *  the roster's foreign key depends on when sync runs on a device whose
     *  local teacher record is missing. */
    var schoolId: String?
        get() = prefs.getString("school_id", null)
        set(value) = prefs.edit().putString("school_id", value).apply()

    var schoolCode: String?
        get() = prefs.getString("school_code", null)
        set(value) = prefs.edit().putString("school_code", value).apply()

    /** Human-facing teacher code (e.g. "T-0042"); unique-indexed in Room. */
    var teacherCode: String?
        get() = prefs.getString("teacher_code", null)
        set(value) = prefs.edit().putString("teacher_code", value).apply()

    var schoolName: String?
        get() = prefs.getString("school_name", null)
        set(value) = prefs.edit().putString("school_name", value).apply()

    var className: String?
        get() = prefs.getString("class_name", null)
        set(value) = prefs.edit().putString("class_name", value).apply()

    var district: String?
        get() = prefs.getString("district", null)
        set(value) = prefs.edit().putString("district", value).apply()

    var activeStudentId: String?
        get() = prefs.getString("active_student_id", null)
        set(value) = prefs.edit().putString("active_student_id", value).apply()

    var studentTeacherName: String?
        get() = prefs.getString("student_teacher_name", null)
        set(value) = prefs.edit().putString("student_teacher_name", value).apply()

    var studentDob: String?
        get() = prefs.getString("student_dob", null)
        set(value) = prefs.edit().putString("student_dob", value).apply()

    var appLanguage: String
        get() = prefs.getString("app_language", "tamil") ?: "tamil"
        set(value) = prefs.edit().putString("app_language", value).apply()

    var onboardingDone: Boolean
        get() = prefs.getBoolean("onboarding_done", false)
        set(value) = prefs.edit().putBoolean("onboarding_done", value).apply()

    fun clear() = prefs.edit().clear().apply()
}
