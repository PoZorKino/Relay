package app.relay.chat.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.relay.chat.ui.theme.ThemeId
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API keys are encrypted with an AES-GCM key that lives in the Android Keystore
 * (hardware-backed where available) and never leaves it.
 */
private object KeyVault {
    private const val ALIAS = "relay.apikeys"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val out = c.iv + c.doFinal(plain.toByteArray())
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(blob: String): String {
        if (blob.isEmpty()) return ""
        return runCatching {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, 12))
            String(c.doFinal(bytes, 12, bytes.size - 12))
        }.getOrDefault("")
    }
}

class ConnectionStore(context: Context) {
    private val prefs = context.getSharedPreferences("relay", Context.MODE_PRIVATE)

    fun loadConnections(): List<Connection> {
        val arr = JSONArray(prefs.getString("connections", "[]"))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val models = o.optJSONArray("models") ?: JSONArray()
            Connection(
                id = o.getString("id"),
                name = o.getString("name"),
                format = runCatching { ApiFormat.valueOf(o.getString("format")) }.getOrDefault(ApiFormat.OpenAI),
                baseUrl = o.getString("baseUrl"),
                apiKey = KeyVault.decrypt(o.optString("key")),
                headers = o.optString("headers"),
                timeoutSeconds = o.optInt("timeout", 60),
                manualModels = o.optString("manualModels"),
                models = (0 until models.length()).map { models.getString(it) },
                lastError = o.optString("lastError").ifEmpty { null },
                plan = o.optString("plan").takeIf { it.isNotEmpty() }?.let { p -> Plan.entries.find { it.name == p } },
            )
        }
    }

    fun saveConnections(list: List<Connection>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("format", c.format.name)
                    .put("baseUrl", c.baseUrl)
                    .put("key", KeyVault.encrypt(c.apiKey))
                    .put("headers", c.headers)
                    .put("timeout", c.timeoutSeconds)
                    .put("manualModels", c.manualModels)
                    .put("models", JSONArray(c.models))
                    .put("lastError", c.lastError ?: "")
                    .put("plan", c.plan?.name ?: "")
            )
        }
        prefs.edit().putString("connections", arr.toString()).apply()
    }

    var defaultConnectionId: String?
        get() = prefs.getString("defaultId", null)
        set(v) = prefs.edit().putString("defaultId", v).apply()

    var theme: ThemeId
        get() = runCatching { ThemeId.valueOf(prefs.getString("theme", null) ?: "") }.getOrDefault(ThemeId.Classic)
        set(v) = prefs.edit().putString("theme", v.name).apply()

    var toolsEnabled: Boolean
        get() = prefs.getBoolean("tools", true)
        set(v) = prefs.edit().putBoolean("tools", v).apply()

    var selected: ModelRef?
        get() {
            val id = prefs.getString("selConn", null) ?: return null
            val model = prefs.getString("selModel", null) ?: return null
            return ModelRef(id, model)
        }
        set(v) = prefs.edit().putString("selConn", v?.connectionId).putString("selModel", v?.model).apply()
}
