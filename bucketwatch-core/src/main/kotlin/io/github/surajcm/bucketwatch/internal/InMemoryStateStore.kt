package io.github.surajcm.bucketwatch.internal

import io.github.surajcm.bucketwatch.ObjectState
import io.github.surajcm.bucketwatch.StateStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Default [StateStore] backed by a [ConcurrentHashMap].
 *
 * State is held in-process and lost on restart. Suitable for most use cases;
 * swap out via the builder when persistence across restarts is required.
 */
internal class InMemoryStateStore : StateStore {

    private val store = ConcurrentHashMap<String, ObjectState>()

    override fun get(key: String): ObjectState? = store[key]

    override fun put(key: String, state: ObjectState) {
        store[key] = state
    }

    override fun remove(key: String): ObjectState? = store.remove(key)

    // Snapshot the key set so callers see a stable view even as the store mutates.
    override fun getAllKeys(): Set<String> = store.keys.toSet()

    override fun clear() = store.clear()
}
