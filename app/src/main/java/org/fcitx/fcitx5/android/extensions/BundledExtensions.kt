/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fcitx.fcitx5.android.extensions

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.fcitx.fcitx5.android.FcitxApplication
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.SubtypeManager
import org.fcitx.fcitx5.android.daemon.FcitxConnection
import org.fcitx.fcitx5.android.daemon.FcitxDaemon
import org.fcitx.fcitx5.android.data.prefs.AppPrefs
import org.fcitx.fcitx5.android.utils.appContext
import timber.log.Timber

/** Owns native switches and profile updates together; user dictionaries are never removed. */
object BundledExtensions {
    data class Entry(val id: String, val title: Int, val description: Int, val badge: String, val domain: String? = null)
    val entries = listOf(
        Entry("rime", R.string.extension_rime, R.string.extension_rime_desc, "中", "fcitx5-rime"),
        Entry("jyutping", R.string.extension_jyutping, R.string.extension_jyutping_desc, "粤", "fcitx5-jyutping"),
        Entry("chewing", R.string.extension_chewing, R.string.extension_chewing_desc, "注", "fcitx5-chewing"),
        Entry("anthy", R.string.extension_anthy, R.string.extension_anthy_desc, "日", "fcitx5-anthy"),
        Entry("hangul", R.string.extension_hangul, R.string.extension_hangul_desc, "韩", "fcitx5-hangul"),
        Entry("unikey", R.string.extension_unikey, R.string.extension_unikey_desc, "越", "fcitx5-unikey"),
        Entry("sayura", R.string.extension_sayura, R.string.extension_sayura_desc, "僧", "fcitx5-sayura"),
        Entry("clipboard_filter", R.string.extension_clearurls, R.string.extension_clearurls_desc, "↗")
    )
    val ids = entries.map { it.id }.toSet()
    val domains = entries.mapNotNull { it.domain }
    private val mutex = Mutex()
    private val preferences by lazy { appContext.getSharedPreferences("bundled-extensions", Context.MODE_PRIVATE) }
    data class Operation(val busy: String? = null, val failed: String? = null, val target: Boolean = false, val revision: Int = 0)
    private val mutableOperation = MutableStateFlow(Operation())
    val operation = mutableOperation.asStateFlow()

    suspend fun states(connection: FcitxConnection): Map<String, Boolean> = connection.runOnReady {
        val addons = addons().associate { it.uniqueName to it.enabled }
        val active = enabledIme().map { it.addon }.toSet()
        entries.associate { it.id to if (it.id == "clipboard_filter")
            AppPrefs.getInstance().internal.bundledClearUrls.getValue()
        else addons[it.id] == true && it.id in active }
    }

    /** A process-owned operation survives leaving/rotating the settings screen. */
    @Synchronized
    fun request(id: String, enabled: Boolean) {
        require(id in ids)
        if (mutableOperation.value.busy != null) return
        val revision = mutableOperation.value.revision
        mutableOperation.value = Operation(busy = id, target = enabled, revision = revision)
        FcitxApplication.getInstance().coroutineScope.launch(Dispatchers.Default) {
            val owner = "bundled-extension-operation"
            var connected = false
            try {
                // Journal only the explicit intent. If the process dies, the page can finish it.
                check(preferences.edit().putString("pending_id", id).putBoolean("pending_enabled", enabled).commit())
                val connection = FcitxDaemon.connect(owner)
                connected = true
                change(connection, id, enabled)
                preferences.edit { remove("pending_id") }
                mutableOperation.value = Operation(revision = revision + 1)
            } catch (e: Exception) {
                Timber.w(e, "Bundled extension change failed: %s", id)
                mutableOperation.value = Operation(failed = id, target = enabled, revision = revision + 1)
            } finally {
                if (connected) FcitxDaemon.disconnect(owner)
            }
        }
    }

    fun recoverPending() {
        if (operation.value.busy != null || operation.value.failed != null) return
        preferences.getString("pending_id", null)?.takeIf { it in ids }?.let {
            request(it, preferences.getBoolean("pending_enabled", false))
        }
    }

    /** Shared by UI and instrumentation; serializes changes and restores the old state on failure. */
    suspend fun change(connection: FcitxConnection, id: String, enabled: Boolean) = mutex.withLock {
        require(id in ids)
        if (id == "clipboard_filter") {
            AppPrefs.getInstance().internal.bundledClearUrls.setValue(enabled)
            return@withLock
        }
        val previous = connection.runOnReady {
            val all = addons().associateBy { it.uniqueName }
            val affected = if (enabled) requiredNames(id, all) else setOf(id)
            all.filterKeys { it in affected }.mapValues { it.value.enabled } to enabledIme().map { it.uniqueName }
        }
        try {
            applyEngine(connection, id, enabled)
        } catch (failure: Exception) {
            withContext(NonCancellable) {
                runCatching {
                    connection.runOnReady { setAddonState(previous.first.keys.toTypedArray(), previous.first.values.toBooleanArray()) }
                    restart(connection)
                    connection.runOnReady { setEnabledIme(previous.second.toTypedArray()); save() }
                    syncSubtypes(connection)
                    preferences.edit { remove("pending_id") }
                }.onFailure { failure.addSuppressed(it) }
            }
            throw failure
        }
    }

    private suspend fun applyEngine(connection: FcitxConnection, id: String, enabled: Boolean) {
        val before = connection.runOnReady { enabledIme().toList() }
        val allAddons = connection.runOnReady { addons().associateBy { it.uniqueName } }
        val required = if (enabled) requiredNames(id, allAddons) else setOf(id)
        if (!enabled) {
            // Move away from an active engine before unloading it. Keep every unrelated language.
            before.filter { it.addon == id }.map { it.uniqueName }.toSet().takeIf { it.isNotEmpty() }?.let {
                preferences.edit { putStringSet("last_$id", it) }
            }
            connection.runOnReady {
                val remaining = before.filterNot { it.addon == id }.map { it.uniqueName }.toMutableList()
                if (remaining.isEmpty()) {
                    val fallback = availableIme().firstOrNull { it.uniqueName == "pinyin" }
                        ?: availableIme().first { it.uniqueName == "keyboard-us" }
                    remaining += fallback.uniqueName
                }
                reset()
                setEnabledIme(remaining.toTypedArray())
                save()
            }
        }
        if (required.any { allAddons.getValue(it).enabled != enabled }) {
            connection.runOnReady { setAddonState(required.toTypedArray(), BooleanArray(required.size) { enabled }) }
            restart(connection)
        }
        if (enabled) connection.runOnReady {
            val available = availableIme().filter { it.addon == id }
            check(available.isNotEmpty()) { "Bundled engine has no available input method: $id" }
            val remembered = preferences.getStringSet("last_$id", emptySet()).orEmpty()
            val selected = available.filter { it.uniqueName in remembered }.ifEmpty {
                listOf(available.firstOrNull { it.uniqueName == id } ?: available.first())
            }
            val current = enabledIme().map { it.uniqueName }
            setEnabledIme((current + selected.map { it.uniqueName }).distinct().toTypedArray())
            save()
        }
        syncSubtypes(connection)
        check(states(connection).getValue(id) == enabled) { "Engine state did not apply: $id" }
    }

    private fun requiredNames(id: String, all: Map<String, org.fcitx.fcitx5.android.core.AddonInfo>): Set<String> {
        val result = linkedSetOf<String>()
        fun visit(name: String) {
            if (!result.add(name)) return
            all[name]?.dependencies?.filter { it in all }?.forEach(::visit)
        }
        visit(id)
        return result
    }

    private suspend fun restart(connection: FcitxConnection) {
        withContext(Dispatchers.Default) { FcitxDaemon.restartFcitx() }
        withTimeout(30_000) { connection.runOnReady { Unit } }
    }

    private suspend fun syncSubtypes(connection: FcitxConnection) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            connection.runOnReady { SubtypeManager.syncWith(enabledIme()) }
        }
    }
}
