// The A2UI v1.0 adapter, ported from ouro-ui-core's a2ui.mts (vendor/core/ouro-a2ui.mts): it validates a stream of A2UI
// messages against an app's catalog, resolves it into Ouro's composed tree and data model, and wraps a person's press in
// A2UI's action message. Everything that isn't A2UI's (the components, the consent rules, the tree and its validator) is
// Composed.kt's, so the tree an A2UI stream gives is drawn and checked exactly as one an agent sends itself. Problems
// come in A2UI's validation error format, word for word as core writes them, so an agent corrects itself the same way on
// every kit. Decisions: core's docs/decisions/2026-11-14-composed-ui-own-tree.md and 2026-11-17-composed-tree-leads.md.
package bot.ouro.ui.hx.composed

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * A problem in a stream of A2UI messages, in A2UI's validation error format (core's `Problem`): its code, the surface or
 * function call it is about, a JSON pointer into the stream, one sentence, and the typed refusal when one applies.
 */
data class A2UIProblem(
    val code: String,
    val path: String,
    val message: String,
    val surfaceId: String? = null,
    val functionCallId: String? = null,
    val refusal: Map<String, Any?>? = null,
) {
    /** The problem as JSON, as core writes it: hand it to the agent to correct the stream. */
    fun toJson(): Map<String, Any?> = linkedMapOf<String, Any?>("code" to code).apply {
        if (surfaceId != null) put("surfaceId", surfaceId)
        if (functionCallId != null) put("functionCallId", functionCallId)
        put("path", path)
        put("message", message)
        if (refusal != null) put("refusal", refusal)
    }

    /** The same problem as a composed tree's ([TreeProblem]): its path, message and refusal. */
    fun toTreeProblem(): TreeProblem = TreeProblem(path, message, refusal)
}

/**
 * One surface an A2UI stream leaves: its id, its composed tree (null when no component has id `root`, so it shows
 * nothing) and its data model, which the tree's fields start from.
 */
data class A2UISurface(val surfaceId: String, val tree: Map<String, Any?>?, val data: Any?)

/** What [OuroA2UI.resolveMessages] gives: every surface the stream leaves, in order, or only the problems. */
data class A2UIResolved(val surfaces: List<A2UISurface>, val problems: List<A2UIProblem>) {
    /** Whether the stream passed the checks, so its surfaces can be drawn. */
    val valid: Boolean get() = problems.isEmpty()

    /** As core's `resolveMessages` writes it: `{ surfaces: { id: tree or null } }`, or `{ problems }`. */
    fun toJson(): Map<String, Any?> =
        if (!valid) linkedMapOf("problems" to problems.map { it.toJson() })
        else linkedMapOf("surfaces" to LinkedHashMap<String, Any?>().apply { for (s in surfaces) put(s.surfaceId, s.tree) })
}

/**
 * A2UI v1.0 onto Ouro's composed tree (core's `a2ui.mts`): validate a message stream against the app's catalog, resolve
 * it into the tree [OuroComposed.validateTree] checks and [bot.ouro.ui.hx.OuroComposedSurface] draws, and send a press
 * as A2UI's `action` message.
 */
object OuroA2UI {
    /** The A2UI protocol version this adapter reads (core's `A2UI_VERSION`). */
    const val VERSION = "1.0"

    /** Ouro HX's catalog id; an app's catalog, with its actions, takes its own (core's `HX_CATALOG_ID`). */
    const val HX_CATALOG_ID = "https://ui.ouroboros.bot/a2ui/v1/catalog.json"

    /** The action-egress extension key on every A2UI `action` a composed button sends. */
    const val ACTION_EXTENSION = "bot_ouroboros_action"

    /**
     * How many components deep a surface's tree may go. A stream names children by id, so no JSON nesting limit bounds
     * its depth; past this the stream is refused, so no stream can exhaust the stack. Windows' kit refuses the same.
     */
    const val MAX_DEPTH = 24

    /**
     * How many nodes one surface may resolve to. Shared children and templates over lists multiply; past this the
     * stream is refused, so no stream can exhaust memory or hang the screen that draws it.
     */
    const val MAX_NODES = 5000

    private const val FAILED = "VALIDATION_FAILED"
    private const val FUNCTION_CALL = "INVALID_FUNCTION_CALL"
    private const val INTEGRITY = "INTEGRITY_ERROR"
    private val MESSAGE_KEYS = listOf("createSurface", "updateComponents", "updateDataModel", "deleteSurface", "callRendererFunction", "agentFunctionResponse")

    // ---------------------------------------------------------------------------------------------------------------
    // A tool result
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The A2UI messages a tool result carries in `structuredContent.a2ui.messages` (core's `mcpAppToolResult`), or
     * [NoMessages] when it carries none. Pass the whole result, its `structuredContent`, or the `a2ui` object itself.
     */
    fun messagesOf(toolResult: Any?): Any? {
        val r = toolResult as? Map<*, *> ?: return NoMessages
        val structured = r["structuredContent"] as? Map<*, *> ?: r
        val a2ui = structured["a2ui"] as? Map<*, *> ?: structured.takeIf { it.containsKey("messages") && it.containsKey("version") } ?: return NoMessages
        return if (a2ui.containsKey("messages")) a2ui["messages"] else NoMessages
    }

    /** What [messagesOf] gives for a tool result that carries no A2UI messages. */
    object NoMessages

    /**
     * A tool result carrying A2UI messages (core's `mcpAppToolResult`): `content` is the text an agent and a text-only
     * host read, and `structuredContent.a2ui` the surface a view draws. Throws [IllegalArgumentException], with the
     * problems, when the messages don't validate against [catalog].
     */
    fun mcpAppToolResult(text: String, catalog: ComposedCatalog, messages: List<Any?>): Map<String, Any?> {
        val problems = validateMessages(catalog, messages)
        require(problems.isEmpty()) { "the A2UI messages don't validate: ${problems.joinToString("; ") { "${it.path} ${it.message}" }}" }
        return linkedMapOf(
            "content" to listOf(linkedMapOf("type" to "text", "text" to text)),
            "structuredContent" to linkedMapOf("a2ui" to linkedMapOf("version" to VERSION, "catalogId" to catalog.catalogId, "messages" to messages)),
        )
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The validator
    // ---------------------------------------------------------------------------------------------------------------

    /** Why an agent can't press a button through the screen: the problem for a `callRendererFunction` (core's `functionCallProblem`). */
    fun functionCallProblem(catalog: ComposedCatalog, name: Any?): TreeProblem = OuroComposed.agentPressProblem(catalog, name as? String)

    private class Walk(val cat: ComposedCatalog, val problems: MutableList<A2UIProblem>, val surfaceId: String) {
        fun add(path: String, message: String, refusal: Map<String, Any?>? = null) {
            problems += A2UIProblem(FAILED, path, message, surfaceId, null, refusal)
        }
    }

    private class Entry(val comp: Map<*, *>, val path: String)

    /**
     * Every problem in a stream of A2UI messages against a catalog (core's `validateMessages`): the envelope, each
     * component and its props, every child id resolving, one root per surface, no cycles, no pointer through
     * `__proto__`, `constructor` or `prototype`, and the action rules. An empty list means it can be drawn.
     */
    fun validateMessages(catalog: ComposedCatalog, messages: Any?): List<A2UIProblem> {
        if (messages !is List<*>) return listOf(A2UIProblem(FAILED, "", "Send a list of A2UI messages."))
        val problems = mutableListOf<A2UIProblem>()
        val surfaces = LinkedHashMap<String, LinkedHashMap<String, Entry>>()
        messages.forEachIndexed { i, m ->
            val at = "/$i"
            if (m !is Map<*, *> || m["version"] != VERSION) {
                problems += A2UIProblem(FAILED, "$at/version", "Every message has \"version\": \"$VERSION\".")
                return@forEachIndexed
            }
            val keys = m.keys.map { it.toString() }.filter { it != "version" }
            if (keys.size != 1 || keys[0] !in MESSAGE_KEYS) {
                problems += A2UIProblem(FAILED, at, "A message has exactly one of ${MESSAGE_KEYS.joinToString(", ")}.")
                return@forEachIndexed
            }
            val key = keys[0]
            val body = m[key]
            if (body !is Map<*, *>) {
                problems += A2UIProblem(FAILED, "$at/$key", "$key is an object.")
                return@forEachIndexed
            }
            if (key == "callRendererFunction") {
                val p = functionCallProblem(catalog, (body["callFunction"] as? Map<*, *>)?.get("@call"))
                problems += A2UIProblem(FUNCTION_CALL, "$at/callRendererFunction/callFunction", p.message, null, body["functionCallId"] as? String ?: "", p.refusal)
                return@forEachIndexed
            }
            if (key == "agentFunctionResponse") {
                problems += A2UIProblem(FUNCTION_CALL, "$at/agentFunctionResponse", "This catalog's renderers call no agent functions, so there is nothing to answer.")
                return@forEachIndexed
            }
            val surfaceId = body["surfaceId"] as? String
            if (surfaceId.isNullOrEmpty()) {
                problems += A2UIProblem(FAILED, "$at/$key/surfaceId", "surfaceId is a non-empty string.")
                return@forEachIndexed
            }
            val w = Walk(catalog, problems, surfaceId)
            if (key == "createSurface") {
                if (surfaces.containsKey(surfaceId)) problems += A2UIProblem(INTEGRITY, "$at/createSurface/surfaceId", "Surface $surfaceId already exists; delete it first or use another id.", surfaceId)
                if (body["catalogId"] != catalog.catalogId) w.add("$at/createSurface/catalogId", "catalogId is ${catalog.catalogId}.")
                if (body.containsKey("theme")) w.add("$at/createSurface/theme", "A2UI v1.0 has no theme; the app's HX theme styles the surface.")
                if (body.containsKey("dataModel") && body["dataModel"] !is Map<*, *>) w.add("$at/createSurface/dataModel", "dataModel is an object.")
                surfaces[surfaceId] = LinkedHashMap()
            } else if (!surfaces.containsKey(surfaceId)) {
                w.add("$at/$key/surfaceId", "Create surface $surfaceId before $key.")
                return@forEachIndexed
            }
            if (key == "deleteSurface") {
                surfaces.remove(surfaceId)
                return@forEachIndexed
            }
            if (key == "updateDataModel") {
                if (body.containsKey("path")) {
                    val path = body["path"]
                    if (path !is String || (path != "" && !OuroComposed.isDataPointer(path))) w.add("$at/updateDataModel/path", "path is a JSON pointer starting with /.")
                }
                if (!body.containsKey("value")) w.add("$at/updateDataModel/value", "updateDataModel needs value (null deletes).")
                return@forEachIndexed
            }
            if (!body.containsKey("components") && key == "createSurface") return@forEachIndexed
            val list = body["components"]
            if (list !is List<*>) {
                w.add("$at/$key/components", "components is a list.")
                return@forEachIndexed
            }
            val byId = surfaces.getValue(surfaceId)
            list.forEachIndexed { j, c ->
                val cpath = "$at/$key/components/$j"
                checkComponent(w, c, cpath)
                val id = (c as? Map<*, *>)?.get("id")
                if (c is Map<*, *> && id is String) byId[id] = Entry(c, cpath)
            }
        }
        // The tree each surface ends with: every child resolves, one root, no cycles.
        for ((surfaceId, byId) in surfaces) {
            if (byId.isEmpty()) continue
            if (!byId.containsKey("root")) problems += A2UIProblem(FAILED, "", "One component must have id root; it is what the surface shows.", surfaceId)
            for (entry in byId.values) for ((child, at) in childRefs(entry.comp, entry.path)) {
                if (!byId.containsKey(child)) problems += A2UIProblem(FAILED, at, "No component has id $child.", surfaceId)
                if (child == "root") problems += A2UIProblem(FAILED, at, "root can't be a child.", surfaceId)
            }
            if (byId.containsKey("root")) loops(problems, surfaceId, byId)
        }
        return problems
    }

    // Core's depth-first walk for loops, from root, with its own stack instead of the call stack, and the depth limit.
    private fun loops(problems: MutableList<A2UIProblem>, surfaceId: String, byId: Map<String, Entry>) {
        class Frame(val id: String, val children: List<Pair<String, String>>, var next: Int = 0)
        val seen = HashSet<String>()
        val stack = ArrayList<String>()
        val frames = ArrayDeque<Frame>()
        var deep = false
        fun enter(id: String) {
            if (id in stack) {
                problems += A2UIProblem(FAILED, byId.getValue(id).path, "The tree loops: ${(stack + id).joinToString(" > ")}.", surfaceId)
                return
            }
            if (id in seen || !byId.containsKey(id)) return
            if (stack.size >= MAX_DEPTH) {
                if (!deep) problems += A2UIProblem(FAILED, byId.getValue(id).path, "The tree is more than $MAX_DEPTH components deep at $id; nest less.", surfaceId)
                deep = true
                return
            }
            stack += id
            frames.addLast(Frame(id, childRefs(byId.getValue(id).comp, "")))
        }
        enter("root")
        while (frames.isNotEmpty()) {
            val frame = frames.last()
            if (frame.next < frame.children.size) {
                enter(frame.children[frame.next++].first)
                continue
            }
            frames.removeLast()
            seen += frame.id
            stack.removeAt(stack.lastIndex)
        }
    }

    private fun childRefs(comp: Map<*, *>, path: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        when (val v = comp["children"]) {
            is List<*> -> v.forEachIndexed { i, c -> if (c is String) out += c to "$path/children/$i" }
            is Map<*, *> -> (v["componentId"] as? String)?.let { out += it to "$path/children/componentId" }
        }
        return out
    }

    private fun isCall(v: Any?) = v is Map<*, *> && v.containsKey("@call")

    private enum class Dynamic { Text, Texts, Flag, Value }

    private fun checkDynamic(w: Walk, v: Any?, kind: Dynamic, path: String) {
        if (isCall(v)) {
            w.add(path, "This catalog has no functions; bind to the data model with {\"@path\"} or give the value.")
            return
        }
        if (OuroComposed.isBinding(v)) return
        val ok = when (kind) {
            Dynamic.Text -> v is String
            Dynamic.Flag -> v is Boolean
            Dynamic.Texts -> v is List<*> && v.all { it is String }
            Dynamic.Value -> !(v is Map<*, *> && v.keys.any { it.toString().startsWith("@") })
        }
        if (!ok) {
            val expected = when (kind) {
                Dynamic.Texts -> "a list of strings"
                Dynamic.Value -> "a value"
                Dynamic.Flag -> "a boolean"
                Dynamic.Text -> "a string"
            }
            w.add(path, "Expected $expected or {\"@path\"}, got ${json(v)}.")
        }
    }

    private fun isInteger(v: Any?) = v is Long || v is Int || (v is Double && v.isFinite() && kotlin.math.floor(v) == v)

    private fun checkInput(w: Walk, action: ComposedAction, id: String, given: Boolean, input: Any?, path: String) {
        val po = action.personOnly
        if (po != null) {
            if (given && !(input is Map<*, *> && input.isEmpty())) w.add(path, po.summary, po.refusal)
            return
        }
        if (given && input !is Map<*, *>) {
            w.add(path, "input is an object of field values.")
            return
        }
        val values = input as? Map<*, *> ?: emptyMap<String, Any?>()
        for ((name, v) in jsEntries(values)) {
            val at = "$path/$name"
            val f = action.input[name]
            if (f == null) {
                w.add(at, "$id has no input $name; its fields are ${action.input.keys.joinToString(", ").ifEmpty { "none" }}.")
                continue
            }
            if (isCall(v)) checkDynamic(w, v, Dynamic.Value, at)
            if (OuroComposed.isBinding(v) || isCall(v)) continue
            val ok = when (f.kind) {
                "string" -> v is String
                "boolean" -> v is Boolean
                "enum" -> v is String && v in f.values.orEmpty()
                else -> v is Number && (f.kind == "number" || isInteger(v))
            }
            if (!ok) {
                val kind = when (f.kind) {
                    "enum" -> "one of ${f.values.orEmpty().joinToString(", ")}"
                    "integer" -> "an integer"
                    else -> "a ${f.kind}"
                }
                w.add(at, "${f.label} is $kind, got ${json(v)}.")
            }
        }
        for ((name, f) in action.input) if (!f.optional && !values.containsKey(name)) w.add(path, "$id needs $name (${f.label}) prepared before the person can press it.")
    }

    // Every pointer a component names (a binding, or a template's path) that would reach an object's prototype.
    private fun unsafePointers(w: Walk, v: Any?, path: String) {
        if (v is List<*>) {
            v.forEachIndexed { i, x -> unsafePointers(w, x, "$path/$i") }
            return
        }
        if (v !is Map<*, *>) return
        val pointer = if (OuroComposed.isBinding(v)) v["@path"] as String else if (v["componentId"] is String && v["path"] is String) v["path"] as String else null
        if (pointer != null && !OuroComposed.isSafePointer(pointer)) w.add(path, "${json(pointer)} points into the data model, never through __proto__, constructor or prototype.")
        for ((k, x) in jsEntries(v)) unsafePointers(w, x, "$path/$k")
    }

    private fun checkComponent(w: Walk, c: Any?, path: String) {
        unsafePointers(w, c, path)
        if (c !is Map<*, *>) {
            w.add(path, "A component is an object with id and component.")
            return
        }
        val id = c["id"]
        if (id !is String || !isIdentifier(id)) w.add("$path/id", "id ${if (c.containsKey("id")) json(id) else "undefined"} is not an identifier: letters, digits and _, not starting with a digit (user_card, not user-card).")
        val component = c["component"]
        val spec = (component as? String)?.let { w.cat.components[it] }
        if (spec == null) {
            w.add("$path/component", "${if (c.containsKey("component")) json(component) else "undefined"} is not in this catalog; use one of ${w.cat.components.keys.joinToString(", ")}.")
            return
        }
        if (c.containsKey("@catalogId") && c["@catalogId"] != w.cat.catalogId) w.add("$path/@catalogId", "This renderer has only the catalog ${w.cat.catalogId}.")
        for ((key, _) in jsEntries(c)) {
            if (key == "id" || key == "component" || key == "@catalogId" || key == "@metadata" || key == "@accessibility") continue
            if (!spec.props.containsKey(key)) w.add("$path/$key", "$component has no $key${if (spec.props.isNotEmpty()) "; its props are ${spec.props.keys.joinToString(", ")}" else ""}.")
        }
        val accessibility = c["@accessibility"]
        if (accessibility is Map<*, *>) for ((k, v) in jsEntries(accessibility)) {
            if (k == "label" || k == "description") checkDynamic(w, v, Dynamic.Text, "$path/@accessibility/$k")
            else if (k == "hidden") checkDynamic(w, v, Dynamic.Flag, "$path/@accessibility/$k")
        }
        for ((key, p) in spec.props) {
            val at = "$path/$key"
            if (!c.containsKey(key)) {
                if (p.required) w.add(at, "$component needs $key: ${p.description}")
                continue
            }
            val v = c[key]
            when (p.type.kind) {
                "string" -> checkDynamic(w, v, Dynamic.Text, at)
                "strings" -> checkDynamic(w, v, Dynamic.Texts, at)
                "boolean" -> checkDynamic(w, v, Dynamic.Flag, at)
                "enum" -> if (v !is String || v !in p.type.values) w.add(at, "$key is one of ${p.type.values.joinToString(", ")}, got ${json(v)}.")
                "level" -> if (!(v is Number && v.toDouble() in listOf(1.0, 2.0, 3.0))) w.add(at, "level is 1, 2 or 3, got ${json(v)}.")
                "children" -> when {
                    v is List<*> -> v.forEachIndexed { i, x -> if (x !is String) w.add("$at/$i", "A child is a component id.") }
                    v is Map<*, *> && v.size == 2 && v["componentId"] is String && v["path"] is String -> Unit
                    else -> w.add(at, "children is a list of component ids, or a template { componentId, path }.")
                }
                "terms" -> when {
                    OuroComposed.isBinding(v) -> Unit
                    v !is List<*> -> w.add(at, "items is a list of { term, detail }, or {\"@path\"} to one.")
                    else -> v.forEachIndexed { i, it ->
                        if (it !is Map<*, *> || it.keys.any { k -> k != "term" && k != "detail" }) {
                            w.add("$at/$i", "Each item is { term, detail }.")
                        } else {
                            for (k in listOf("term", "detail")) {
                                if (!it.containsKey(k)) w.add("$at/$i/$k", "Each item needs $k.") else checkDynamic(w, it[k], Dynamic.Text, "$at/$i/$k")
                            }
                        }
                    }
                }
                "path" -> if (!OuroComposed.isBinding(v)) w.add(at, "$key is {\"@path\": \"/...\"}: where the person's answer goes in the data model.")
                "action" -> if (v !is String || !w.cat.actions.containsKey(v)) w.add(at, "${json(v)} is not one of the app's actions: ${w.cat.actions.keys.joinToString(", ")}.")
            }
        }
        val action = c["action"]
        if (component == "ActionButton" && action is String) {
            val a = w.cat.actions[action]
            if (a != null) checkInput(w, a, action, c.containsKey("input"), c["input"], "$path/input")
        }
    }

    // An A2UI component id: Unicode's identifier rule, as core's /^[\p{XID_Start}_][\p{XID_Continue}]*$/u. Java's own
    // identifier classes are close to ID_Start and ID_Continue; this keeps Unicode's general categories and its
    // Other_ID_Start and Other_ID_Continue code points, less those XID drops because NFKC turns them into something that
    // isn't one (UAX #31). The JVM's Unicode tables are its own, so a letter new to a later version may read differently
    // than in a JavaScript engine.
    private fun isIdentifier(s: String): Boolean {
        if (s.isEmpty()) return false
        var i = 0
        var first = true
        while (i < s.length) {
            val v = s.codePointAt(i)
            i += Character.charCount(v)
            val type = Character.getType(v)
            val letter = type == Character.UPPERCASE_LETTER.toInt() || type == Character.LOWERCASE_LETTER.toInt() || type == Character.TITLECASE_LETTER.toInt() ||
                type == Character.MODIFIER_LETTER.toInt() || type == Character.OTHER_LETTER.toInt() || type == Character.LETTER_NUMBER.toInt() ||
                v == 0x1885 || v == 0x1886 || v == 0x2118 || v == 0x212E
            val go = letter || type == Character.NON_SPACING_MARK.toInt() || type == Character.COMBINING_SPACING_MARK.toInt() ||
                type == Character.DECIMAL_DIGIT_NUMBER.toInt() || type == Character.CONNECTOR_PUNCTUATION.toInt() ||
                v == 0x00B7 || v == 0x0387 || v in 0x1369..0x1371 || v == 0x19DA || v == 0x200C || v == 0x200D
            // Neither XID_Start nor XID_Continue: Pattern_Syntax, and the NFKC exceptions.
            val never = v == 0x2E2F || v == 0x037A || v == 0x309B || v == 0x309C || v in 0xFC5E..0xFC63 || v == 0xFDFA || v == 0xFDFB ||
                (v in 0xFE70..0xFE7E && v % 2 == 0)
            // XID_Continue, but not XID_Start.
            val notFirst = v == 0x0E33 || v == 0x0EB3 || v == 0xFF9E || v == 0xFF9F
            if (never || (if (first) !(letter || v == '_'.code) || notFirst else !go)) return false
            first = false
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The resolver: what every renderer must show
    // ---------------------------------------------------------------------------------------------------------------

    private class Surface(var data: Any?) {
        val components = LinkedHashMap<String, Map<*, *>>()
    }

    // Applies a stream the validator accepted, in order: surfaces created, their components set by id, their data
    // written by JSON pointer, and deleted (core's applyMessages).
    private fun applyMessages(messages: List<*>): LinkedHashMap<String, Surface> {
        val surfaces = LinkedHashMap<String, Surface>()
        for (m in messages) {
            val msg = m as? Map<*, *> ?: continue
            val create = msg["createSurface"] as? Map<*, *>
            val update = msg["updateComponents"] as? Map<*, *>
            val write = msg["updateDataModel"] as? Map<*, *>
            val delete = msg["deleteSurface"] as? Map<*, *>
            when {
                create != null -> {
                    val s = Surface(create["dataModel"] ?: LinkedHashMap<String, Any?>())
                    for (c in (create["components"] as? List<*>).orEmpty()) if (c is Map<*, *>) s.components[c["id"] as String] = c
                    surfaces[create["surfaceId"] as String] = s
                }
                update != null -> {
                    val s = surfaces.getValue(update["surfaceId"] as String)
                    for (c in (update["components"] as? List<*>).orEmpty()) if (c is Map<*, *>) s.components[c["id"] as String] = c
                }
                write != null -> {
                    val s = surfaces.getValue(write["surfaceId"] as String)
                    s.data = OuroComposed.setPointer(s.data, write["path"] as? String ?: "/", write["value"])
                }
                delete != null -> surfaces.remove(delete["surfaceId"] as String)
            }
        }
        return surfaces
    }

    // A pointer in a scope: absolute from the root, relative from the template item's path.
    private fun scoped(scope: String, p: String) = if (p.startsWith("/")) p else if (p == "") scope else "$scope/$p"

    // A pointer that starts at the data model's root.
    private fun absolute(p: String) = if (p.startsWith("/")) p else "/$p"

    private fun pathOf(binding: Any?) = (binding as Map<*, *>)["@path"] as String

    private fun value(data: Any?, scope: String, v: Any?): Any? = if (OuroComposed.isBinding(v)) OuroComposed.getPointer(data, scoped(scope, pathOf(v))) else v

    private class TooManyNodes : RuntimeException()

    /**
     * Every surface a stream leaves, resolved into Ouro's composed tree (core's `resolveMessages`), or only the
     * validator's problems when it has any. [label] is the app's own name for an id a confirm sheet shows, as in
     * [OuroComposed.actionButtonProps].
     */
    fun resolveMessages(catalog: ComposedCatalog, messages: Any?, label: ((LabelContext) -> String?)? = null): A2UIResolved {
        val problems = validateMessages(catalog, messages)
        if (problems.isNotEmpty()) return A2UIResolved(emptyList(), problems)
        val surfaces = mutableListOf<A2UISurface>()
        // In the order JavaScript lists a record's keys, as core's resolveMessages gives them: index-like ids first.
        for ((id, s) in jsEntries(applyMessages(messages as List<*>))) {
            val surface = s as Surface
            val tree = try {
                resolveSurface(catalog, surface, label)
            } catch (_: TooManyNodes) {
                return A2UIResolved(emptyList(), listOf(A2UIProblem(FAILED, "", "Surface $id resolves to more than $MAX_NODES components; show fewer, or fewer list items.", id)))
            }
            surfaces += A2UISurface(id, tree, surface.data)
        }
        return A2UIResolved(surfaces, emptyList())
    }

    private fun resolveSurface(cat: ComposedCatalog, surface: Surface, label: ((LabelContext) -> String?)?): Map<String, Any?>? {
        val root = surface.components["root"] ?: return null
        val data = surface.data
        var nodes = 0

        fun node(comp: Map<*, *>, scope: String): LinkedHashMap<String, Any?> {
            if (++nodes > MAX_NODES) throw TooManyNodes()
            val name = comp["component"] as String
            val spec = cat.components.getValue(name)
            val props = LinkedHashMap<String, Any?>()
            val children = ArrayList<Any?>()
            for ((key, p) in spec.props) {
                val given = comp.containsKey(key)
                val raw = comp[key]
                when (p.type.kind) {
                    "string" -> if (given || p.required) props[key] = text(if (given) value(data, scope, raw) else null)
                    "strings" -> props[key] = (value(data, scope, raw) as? List<*>)?.map { text(it) } ?: emptyList<String>()
                    "boolean" -> props[key] = if (given) value(data, scope, raw) == true else p.type.default == true
                    "enum" -> props[key] = raw as? String ?: p.type.default
                    "level" -> props[key] = if (raw is Number) jsCopy(raw) else p.type.default
                    "terms" -> {
                        val bound = OuroComposed.isBinding(raw)
                        val base = if (bound) scoped(scope, pathOf(raw)) else scope
                        props[key] = (value(data, scope, raw) as? List<*>)?.mapIndexed { i, it ->
                            val at = if (bound) "$base/$i" else scope
                            linkedMapOf<String, Any?>("term" to text(value(data, at, (it as? Map<*, *>)?.get("term"))), "detail" to text(value(data, at, (it as? Map<*, *>)?.get("detail"))))
                        } ?: emptyList<Any?>()
                    }
                    "children" -> when (raw) {
                        is List<*> -> for (id in raw) surface.components[OuroComposed.asText(id)]?.let { children += node(it, scope) }
                        is Map<*, *> -> {
                            val base = scoped(scope, OuroComposed.asText(raw["path"]))
                            val list = OuroComposed.getPointer(data, base)
                            val tpl = surface.components[OuroComposed.asText(raw["componentId"])]
                            if (list is List<*> && tpl != null) for (i in list.indices) children += node(tpl, "$base/$i")
                        }
                    }
                    "path" -> props[key] = if (OuroComposed.isBinding(raw)) absolute(scoped(scope, pathOf(raw))) else ""
                }
            }
            if (name == "ActionButton") {
                // Bindings stay bindings here; once the tree is whole, live() reads those no field writes and adds the catalog's props.
                val id = comp["action"] as String
                val input = LinkedHashMap<String, Any?>()
                val prepared = comp["input"]
                if (cat.actions.getValue(id).personOnly == null && prepared is Map<*, *>) {
                    for ((k, v) in jsEntries(prepared)) input[k] = if (OuroComposed.isBinding(v)) linkedMapOf<String, Any?>("@path" to absolute(scoped(scope, pathOf(v)))) else jsCopy(v)
                }
                props["action"] = id
                props["input"] = input
            }
            val accessibility = comp["@accessibility"]
            if (accessibility is Map<*, *> && accessibility.containsKey("label")) props["@label"] = text(value(data, scope, accessibility["label"]))
            return linkedMapOf<String, Any?>("id" to OuroComposed.asText(comp["id"]), "component" to name, "props" to props, "children" to children)
        }

        return live(cat, node(root, ""), data, label)
    }

    /**
     * The tree with each button input bound to a path no field writes read from the data now, as before fields existed;
     * one bound to a field the person fills in stays bound, so a press sends their answer ([OuroComposed.resolveButton]).
     */
    @Suppress("UNCHECKED_CAST")
    private fun live(cat: ComposedCatalog, tree: LinkedHashMap<String, Any?>, data: Any?, label: ((LabelContext) -> String?)?): Map<String, Any?> {
        val written = HashSet<String>()
        fun collect(n: Map<String, Any?>) {
            val value = (n["props"] as Map<String, Any?>)["value"]
            if ((n["component"] as String) in OuroComposed.INPUT_COMPONENTS && value is String) written += value
            for (c in n["children"] as List<*>) collect(c as Map<String, Any?>)
        }
        collect(tree)
        // A value read from the data is a value: one shaped like a binding is sent as its text, never followed.
        fun read(p: String): Any? {
            val v = OuroComposed.getPointer(data, p)
            return if (OuroComposed.isBinding(v)) text(v) else jsCopy(v)
        }
        fun fix(n: LinkedHashMap<String, Any?>) {
            for (c in n["children"] as List<*>) fix(c as LinkedHashMap<String, Any?>)
            val props = n["props"] as LinkedHashMap<String, Any?>
            val prepared = props["input"]
            if (n["component"] != "ActionButton" || prepared !is Map<*, *>) return
            val input = LinkedHashMap<String, Any?>()
            for ((k, v) in prepared) input[k.toString()] = if (OuroComposed.isBinding(v) && pathOf(v) !in written) read(pathOf(v)) else v
            // The catalog's props, computed once per button, so the app's label resolver is asked once per field; the
            // sender's @label goes last.
            val next = LinkedHashMap<String, Any?>()
            for ((k, v) in props) if (k != "action" && k != "input" && k != "@label") next[k] = v
            next.putAll(OuroComposed.actionButtonProps(cat, props["action"] as String, input, label))
            if (props.containsKey("@label")) next["@label"] = props["@label"]
            n["props"] = next
        }
        fix(tree)
        return tree
    }

    // ---------------------------------------------------------------------------------------------------------------
    // A press, as A2UI's action message
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The A2UI `action` message a person's press sends (core's `actionMessage`): the action's id, the input with each
     * field's answer read at the press, the label the person read, and that the person pressed it (and confirmed, for a
     * consequential action). [sourceComponentId] is the pressed button's id.
     */
    fun actionMessage(surfaceId: String, sourceComponentId: String, press: PersonPress, timestamp: String): Map<String, Any?> {
        val consent = linkedMapOf<String, Any?>("consent" to press.consent.wire, "by" to press.by)
        if (press.confirmed) consent["confirmed"] = true
        return linkedMapOf(
            "version" to VERSION,
            "action" to linkedMapOf(
                "name" to press.action,
                "surfaceId" to surfaceId,
                "sourceComponentId" to sourceComponentId,
                "timestamp" to timestamp,
                "userMessage" to press.label,
                "context" to press.input,
                "metadata" to linkedMapOf("extensions" to linkedMapOf(ACTION_EXTENSION to consent)),
            ),
        )
    }

    /**
     * The action message for a pressed [button], with each bound input read from [data]. Throws
     * [OuroComposed.PressRefused] for a person-only button, and for a consequential one the person hasn't confirmed.
     */
    fun actionMessage(surfaceId: String, button: ComposedNode, timestamp: String, confirmed: Boolean = false, data: Any? = null): Map<String, Any?> =
        actionMessage(surfaceId, button.id, OuroComposed.personPress(button, confirmed, data), timestamp)

    /** A time as A2UI's action message carries it, and as JavaScript's `toISOString` writes it. */
    fun timestamp(millis: Long = System.currentTimeMillis()): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(millis))

    // ---------------------------------------------------------------------------------------------------------------
    // Values, as JavaScript holds them
    // ---------------------------------------------------------------------------------------------------------------

    // A value as text (core's asText): numbers and booleans as written, null as "", others as JSON.
    private fun text(v: Any?): String = if (v is Map<*, *> || v is List<*>) json(v) else OuroComposed.asText(v)

    // JSON.stringify, so a problem or a value's text reads the same on every kit.
    private fun json(v: Any?): String = ComposedJson.stringify(jsCopy(v))

    // A JavaScript object lists its keys that are array indexes first, in ascending order, then the rest as they were
    // added; JSON.parse, Object.keys and JSON.stringify all follow it.
    private fun isIndex(key: String) = key == "0" || (key.length in 1..10 && key[0] in '1'..'9' && key.all { it in '0'..'9' } && key.toLong() < 4294967295L)

    private fun jsEntries(m: Map<*, *>): List<Pair<String, Any?>> {
        val all = m.entries.map { (k, v) -> k.toString() to v }
        return all.filter { isIndex(it.first) }.sortedBy { it.first.toLong() } + all.filter { !isIndex(it.first) }
    }

    // A value as JavaScript holds it once parsed: its keys in JavaScript's order, so a tree reads byte for byte as core's.
    private fun jsCopy(v: Any?): Any? = when (v) {
        is Map<*, *> -> LinkedHashMap<String, Any?>().also { m -> for ((k, x) in jsEntries(v)) m[k] = jsCopy(x) }
        is List<*> -> v.map { jsCopy(it) }
        else -> v
    }
}
