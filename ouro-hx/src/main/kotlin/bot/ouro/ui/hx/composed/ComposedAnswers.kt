// What the person entered in a composed tree's fields, kept across an agent's new tree or data (core 0.19.0's tree
// updates), as web's ComposedSurface keeps it (ourostack/ouro-ui#2115): the person's answer stays, except where the
// agent changed its value or the list item it sits in, where the agent's value wins.
package bot.ouro.ui.hx.composed

/**
 * The data model as the person fills it in: what the agent last sent ([data]), what the fields hold now ([current]),
 * and each pointer the person wrote, in the order they first wrote it ([edited]).
 */
internal data class ComposedAnswers(val data: Any?, val current: Any?, val edited: Set<String>) {
    /** The person's answer at [pointer]. */
    fun write(pointer: String, value: Any?): ComposedAnswers =
        copy(current = OuroComposed.setPointer(current, pointer, value), edited = LinkedHashSet(edited).apply { add(pointer) })

    /**
     * The fields' data model when the agent sends [next]: the new data, with each answer the person entered kept where
     * the agent left its value as it was, and the list item it is in. Values compare as JSON text, so an object whose
     * keys moved counts as changed, and a value the data doesn't hold differs from null.
     */
    fun carry(next: Any?): ComposedAnswers {
        val fresh: Any = next ?: LinkedHashMap<String, Any?>()
        val before: Any = data ?: LinkedHashMap<String, Any?>()
        fun text(d: Any?, p: String) = OuroComposed.find(d, p).let { if (it === OuroComposed.Absent) null else ComposedJson.stringify(it) }
        val kept = edited.filterTo(LinkedHashSet()) { path -> anchors(before, path).all { text(before, it) == text(fresh, it) } }
        var current: Any? = fresh
        for (path in kept) current = OuroComposed.setPointer(current, path, OuroComposed.getPointer(this.current, path))
        return ComposedAnswers(next, current, kept)
    }

    companion object {
        /** A data model nobody has written to yet. */
        fun of(data: Any?) = ComposedAnswers(data, data ?: LinkedHashMap<String, Any?>(), emptySet())

        /**
         * The pointers whose value an answer at [pointer] depends on: the pointer itself and each list item on the way
         * to it, since a list that loses an item moves the next one into its place. The data model is an object, so
         * its first key is never a list item.
         */
        fun anchors(data: Any?, pointer: String): List<String> {
            val keys = pointer.split("/").drop(1)
            val prefixes = keys.indices.map { i -> "/" + keys.subList(0, i + 1).joinToString("/") }
            return prefixes.filterIndexed { i, _ -> i == keys.lastIndex || (i > 0 && OuroComposed.getPointer(data, prefixes[i - 1]) is List<*>) }
        }
    }
}
