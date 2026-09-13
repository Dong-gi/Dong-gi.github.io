package io.github.donggi.iroiroviewer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.donggi.iroiroviewer.model.SortKey
import io.github.donggi.iroiroviewer.model.SortSpec
import io.github.donggi.iroiroviewer.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 앱 전체에 걸리는 몇 개짜리 설정. **Room 이 아니라 DataStore 인 이유**는
 * 이것들이 키-값 몇 개이고 질의가 필요 없기 때문이다.
 *
 * 반대로 초당 갱신되는 재생 위치는 DataStore 에 넣지 않는다 — 쓸 때마다 파일 전체를
 * 다시 쓰므로 그런 빈도를 감당하지 못한다. 그쪽은 Room 이다.
 */
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class AppPreferences(private val context: Context) {

    private object Keys {
        val sortKey = intPreferencesKey("sort_key")
        val sortAscending = booleanPreferencesKey("sort_ascending")
        val foldersFirst = booleanPreferencesKey("folders_first")
        val viewMode = intPreferencesKey("view_mode")
        val showHidden = booleanPreferencesKey("show_hidden")
    }

    val sortSpec: Flow<SortSpec> = context.dataStore.data.map { p ->
        SortSpec(
            key = SortKey.entries.getOrElse(p[Keys.sortKey] ?: 0) { SortKey.NAME },
            ascending = p[Keys.sortAscending] ?: true,
            foldersFirst = p[Keys.foldersFirst] ?: true,
        )
    }

    val viewMode: Flow<ViewMode> = context.dataStore.data.map { p ->
        ViewMode.entries.getOrElse(p[Keys.viewMode] ?: 0) { ViewMode.LIST }
    }

    val showHidden: Flow<Boolean> = context.dataStore.data.map { p -> p[Keys.showHidden] ?: false }

    suspend fun setSortSpec(spec: SortSpec) {
        context.dataStore.edit { p ->
            p[Keys.sortKey] = spec.key.ordinal
            p[Keys.sortAscending] = spec.ascending
            p[Keys.foldersFirst] = spec.foldersFirst
        }
    }

    suspend fun setViewMode(mode: ViewMode) {
        context.dataStore.edit { it[Keys.viewMode] = mode.ordinal }
    }

    suspend fun setShowHidden(show: Boolean) {
        context.dataStore.edit { it[Keys.showHidden] = show }
    }
}
