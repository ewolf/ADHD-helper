package com.roundtooit.data.remote

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YapiClient @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val PREFS_NAME = "yapi_session"
        private const val KEY_TOKEN = "token"
        private const val KEY_SERVER_URL = "server_url"
        private const val DEFAULT_URL = "https://madyote.com/yapi"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        private set(value) {
            prefs.edit().putString(KEY_TOKEN, value).apply()
        }

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_URL) ?: DEFAULT_URL
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value).apply()
        }

    val isLoggedIn: Boolean get() = token != null

    // ------------------------------------------------------------------
    // Core request
    // ------------------------------------------------------------------

    private suspend fun request(body: JsonObject): YapiResponse = withContext(Dispatchers.IO) {
        val requestBody = body.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(serverUrl)
            .post(requestBody)
            .build()

        val response = client.newCall(request).execute()
        val responseBody = response.body?.string()
            ?: throw YapiException("Empty response")

        val jsonResponse = json.parseToJsonElement(responseBody).jsonObject
        val ok = jsonResponse["ok"]?.jsonPrimitive?.intOrNull == 1

        // Update token if provided
        jsonResponse["token"]?.jsonPrimitive?.contentOrNull?.let { newToken ->
            token = newToken
        }

        if (!ok) {
            val error = jsonResponse["error"]?.jsonPrimitive?.contentOrNull ?: "Unknown error"
            throw YapiException(error)
        }

        YapiResponse(
            ok = true,
            resp = jsonResponse["resp"],
            objects = jsonResponse["objects"]?.jsonObject ?: JsonObject(emptyMap()),
            classes = jsonResponse["classes"]?.jsonObject ?: JsonObject(emptyMap()),
        )
    }

    // ------------------------------------------------------------------
    // Actions
    // ------------------------------------------------------------------

    suspend fun connect(): YapiResponse {
        val body = buildJsonObject {
            put("action", "connect")
            put("app", "roundtooit")
            token?.let { put("token", it) }
        }
        return request(body)
    }

    suspend fun login(handle: String, password: String): YapiResponse {
        val body = buildJsonObject {
            put("action", "login")
            putJsonObject("args") {
                put("handle", handle)
                put("password", password)
            }
        }
        return request(body)
    }

    suspend fun createUser(handle: String, password: String, email: String? = null): YapiResponse {
        val body = buildJsonObject {
            put("action", "createUser")
            putJsonObject("args") {
                put("handle", handle)
                put("password", password)
                email?.let { put("email", it) }
            }
        }
        return request(body)
    }

    suspend fun logout() {
        try {
            val body = buildJsonObject {
                put("action", "logout")
                token?.let { put("token", it) }
            }
            request(body)
        } finally {
            token = null
        }
    }

    suspend fun call(method: String, args: Map<String, Any?> = emptyMap()): YapiResponse {
        val body = buildJsonObject {
            put("action", "call")
            put("app", "roundtooit")
            put("method", method)
            token?.let { put("token", it) }
            putJsonObject("args") {
                for ((key, value) in args) {
                    when (value) {
                        is String -> put(key, "v$value")
                        is Number -> put(key, "v$value")
                        is Boolean -> put(key, "v${if (value) 1 else 0}")
                        is YapiObject -> put(key, "r${value.objId}")
                        null -> put(key, JsonNull)
                        else -> put(key, "v$value")
                    }
                }
            }
        }
        return request(body)
    }

    // ------------------------------------------------------------------
    // Response parsing helpers
    // ------------------------------------------------------------------

    fun parseObjects(response: YapiResponse): Map<String, YapiObject> {
        val result = mutableMapOf<String, YapiObject>()
        for ((objId, objJson) in response.objects) {
            val obj = objJson.jsonObject
            val className = obj["_class"]?.jsonPrimitive?.contentOrNull ?: continue
            val data = obj["data"]?.jsonObject ?: continue

            val fields = mutableMapOf<String, String?>()
            for ((key, value) in data) {
                val str = value.jsonPrimitive.contentOrNull ?: continue
                // Strip v/r prefix
                fields[key] = when {
                    str.startsWith("v") -> str.substring(1)
                    str.startsWith("r") -> str  // keep ref as-is for resolution
                    else -> str
                }
            }
            result[objId] = YapiObject(objId, className, fields)
        }
        return result
    }

    fun decodeValue(element: JsonElement?): String? {
        if (element == null || element is JsonNull) return null
        val str = element.jsonPrimitive.contentOrNull ?: return null
        return if (str.startsWith("v")) str.substring(1) else str
    }
}

data class YapiResponse(
    val ok: Boolean,
    val resp: JsonElement?,
    val objects: JsonObject,
    val classes: JsonObject,
)

data class YapiObject(
    val objId: String,
    val className: String,
    val fields: Map<String, String?>,
)

class YapiException(message: String) : Exception(message)
