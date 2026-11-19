// Ouro's composed tree on Android: core's src/composed.mts (ouro-ui-core v0.18.0), ported. What an agent may put on a
// person's screen, whatever format it arrived in: the components a catalog allows, the tree every renderer draws, the
// validator for that tree, the data model its fields write by JSON Pointer, and what a press sends. Every button is a
// declared action whose label, consent and press come from the app's catalog, so no sender can relabel a button or
// loosen its consent. Decisions: core's docs/decisions/2026-11-14-composed-ui-own-tree.md and 2026-11-17-composed-fields.md.
//
// JSON values are Kotlin's: null, Boolean, Long or Double, String, List and Map ([ComposedJson]). A missing value and
// null are both null here; core's rules treat them alike everywhere a renderer reads one.
package bot.ouro.ui.hx.composed

import bot.ouro.ui.actions.OuroConsent

/** The version of Ouro's composed tree this port draws (core's `COMPOSED_VERSION`). */
const val COMPOSED_VERSION = "1"

/** A prop's kind (core's `PropKind`): what the validator checks. [kind] is core's name for it. */
data class PropKind(val kind: String, val values: List<String> = emptyList(), val default: Any? = null) {
    companion object {
        val Text = PropKind("string")
        val Texts = PropKind("strings")
        val Children = PropKind("children")
        val Terms = PropKind("terms")
        val Action = PropKind("action")
        val Input = PropKind("input")
        val Path = PropKind("path")
        fun choice(values: List<String>, default: String) = PropKind("enum", values, default)
        fun flag(default: Boolean) = PropKind("boolean", default = default)
        fun level(default: Int) = PropKind("level", default = default)
    }
}

/** One prop of a component: what it is for an agent, whether the tree must carry it, and its kind. */
data class ComposedProp(val description: String, val type: PropKind, val required: Boolean = false)

/** A catalog component (core's `ComponentSpec`): one line for an agent, and its props in order. */
data class ComposedComponent(val description: String, val props: Map<String, ComposedProp>)

/** What pressing a composed button does (core's `Press`): run it, confirm then run it, or hand the person to the app. */
enum class ComposedPress(val wire: String) {
    Run("run"),
    Confirm("confirm"),
    Handoff("handoff"),
    ;

    companion object {
        /** The press a consent class gives (core's `pressOf`). */
        fun of(consent: OuroConsent): ComposedPress = when (consent) {
            OuroConsent.PersonOnly -> Handoff
            OuroConsent.Consequential -> Confirm
            else -> Run
        }
    }
}

/** One input field of a catalog action: its label for the person, its kind, and the sibling field that names it. */
data class ComposedField(
    val label: String,
    val kind: String,
    val optional: Boolean,
    val values: List<String>? = null,
    /** The sibling input that carries this id's name, shown on a confirm sheet in its place. */
    val shownAs: String? = null,
)

/** Where a person-only action is done, and the typed refusal every agent door answers (core's outcome, as JSON). */
data class ComposedPersonOnly(val how: String, val refusal: Map<String, Any?>) {
    /** The refusal's one-line summary. */
    val summary: String get() = refusal["summary"] as? String ?: ""
}

/** An action as an app's catalog carries it (core's `CatalogAction`). */
data class ComposedAction(
    val consent: OuroConsent,
    val press: ComposedPress,
    val label: String,
    val description: String,
    val input: Map<String, ComposedField>,
    val personOnly: ComposedPersonOnly? = null,
)

/** A problem in a composed tree: a JSON pointer into it, one sentence, and the typed refusal when one applies. */
data class TreeProblem(val path: String, val message: String, val refusal: Map<String, Any?>? = null)

/** What an app's resolver is asked to name: one prepared input of a button that confirms (core's `LabelContext`). */
data class LabelContext(val action: String, val field: String, val value: String, val input: Map<String, Any?>)

/**
 * A node of the composed tree (core's `ComposedNode`): the component, its props with every value resolved and every
 * default filled, and its children. [id] is what a press names as its source.
 */
data class ComposedNode(val id: String, val component: String, val props: Map<String, Any?>, val children: List<ComposedNode>) {
    /** The node as JSON, as core writes it. */
    fun toJson(): Map<String, Any?> = linkedMapOf("id" to id, "component" to component, "props" to props, "children" to children.map { it.toJson() })

    companion object {
        /**
         * The node a JSON tree holds. Check it with [OuroComposed.validateTree] first: this reads its shape and nothing
         * else, and throws [IllegalArgumentException] when a node isn't `{ id, component, props, children }`.
         */
        fun fromJson(json: Any?): ComposedNode {
            val n = json as? Map<*, *>
            val id = n?.get("id") as? String
            val component = n?.get("component") as? String
            val props = n?.get("props") as? Map<*, *>
            val children = n?.get("children") as? List<*>
            require(id != null && component != null && props != null && children != null) { "A node is { id, component, props, children }." }
            @Suppress("UNCHECKED_CAST")
            val typed = props as Map<String, Any?>
            return ComposedNode(id, component, typed, children.map { fromJson(it) })
        }
    }
}

/** What pressing a composed button asks the app to do (core's `PersonPress`): always by the person. */
data class PersonPress(
    /** The action's id, which is also its MCP tool's name. */
    val action: String,
    /** The input the agent prepared, with each field the person filled in read when they pressed. */
    val input: Map<String, Any?>,
    val consent: OuroConsent,
    /** The button's label, as the person read it. */
    val label: String,
    /** True for a consequential action the person confirmed. */
    val confirmed: Boolean = false,
) {
    /** Always "person": a composed button is the person's door, never an agent's. */
    val by: String get() = "person"

    /** The press as core writes it: `{ action, input, consent, label, by, confirmed? }`. */
    fun toJson(): Map<String, Any?> = linkedMapOf<String, Any?>("action" to action, "input" to input, "consent" to consent.wire, "label" to label, "by" to by)
        .apply { if (confirmed) put("confirmed", true) }
}

/** What a renderer needs from a catalog (core's `Catalog`): its id, its components and what each action's button does. */
data class ComposedCatalog(val catalogId: String, val components: Map<String, ComposedComponent>, val actions: Map<String, ComposedAction>) {
    companion object {
        /** The extension key a catalog carries its actions under. */
        const val EXTENSION = "bot_ouroboros_hx"

        /** Reads a catalog ouro-a2ui wrote, from its JSON text (core's `loadCatalog`). */
        fun parse(text: String): ComposedCatalog = fromJson(ComposedJson.parse(text))

        /**
         * Reads a catalog ouro-a2ui wrote (core's `loadCatalog`). Its component specs are HX's, so a hand-edited catalog
         * can't widen them; an action whose press isn't its consent's is refused too.
         */
        fun fromJson(json: Any?): ComposedCatalog {
            val c = json as? Map<*, *>
            val id = c?.get("catalogId") as? String
            val names = c?.get("components") as? Map<*, *>
            require(id != null && names != null) { "not an A2UI catalog: it has no catalogId or components. Fix: pass a catalog ouro-a2ui wrote." }
            val components = linkedMapOf<String, ComposedComponent>()
            for (name in names.keys.map { it.toString() }) {
                components[name] = (if (name == "ActionButton") OuroComposed.ACTION_BUTTON else OuroComposed.COMPONENTS[name])
                    ?: throw IllegalArgumentException("catalog $id: component $name is not Ouro HX's. Fix: regenerate it with ouro-a2ui catalog.")
            }
            val raw = ((c?.get("metadata") as? Map<*, *>)?.get("extensions") as? Map<*, *>)?.get(EXTENSION) as? Map<*, *>
            val actions = linkedMapOf<String, ComposedAction>()
            for ((k, v) in (raw?.get("actions") as? Map<*, *>).orEmpty()) actions[k.toString()] = action(id, k.toString(), v)
            return ComposedCatalog(id, components, actions)
        }

        private fun action(catalog: String, name: String, json: Any?): ComposedAction {
            val bad = { what: String -> IllegalArgumentException("catalog $catalog: action $name $what. Fix: regenerate it with ouro-a2ui catalog.") }
            val a = json as? Map<*, *> ?: throw bad("is not an object")
            val consent = OuroConsent.entries.firstOrNull { it.wire == a["consent"] } ?: throw bad("has no consent class")
            val press = ComposedPress.entries.firstOrNull { it.wire == a["press"] } ?: throw bad("has no press")
            if (press != ComposedPress.of(consent)) throw bad("presses ${press.wire}, but a ${consent.wire} action presses ${ComposedPress.of(consent).wire}")
            val input = linkedMapOf<String, ComposedField>()
            for ((k, f) in (a["input"] as? Map<*, *>).orEmpty()) {
                val field = f as? Map<*, *> ?: throw bad("has an input field $k that is not an object")
                input[k.toString()] = ComposedField(
                    label = field["label"] as? String ?: k.toString(),
                    kind = field["kind"] as? String ?: "string",
                    optional = field["optional"] == true,
                    values = (field["values"] as? List<*>)?.map { it.toString() },
                    shownAs = field["shownAs"] as? String,
                )
            }
            val po = a["personOnly"] as? Map<*, *>
            @Suppress("UNCHECKED_CAST")
            val personOnly = po?.let { ComposedPersonOnly(it["how"] as? String ?: "", (it["refusal"] as? Map<String, Any?>).orEmpty()) }
            if (consent == OuroConsent.PersonOnly && personOnly == null) throw bad("is person-only but says nowhere how the person does it")
            return ComposedAction(consent, press, a["label"] as? String ?: throw bad("has no label"), a["description"] as? String ?: "", input, personOnly)
        }
    }
}

/** Core's composed.mts: the components, the button rules, the validator, the data model and the press. */
object OuroComposed {
    private val TONES = listOf("neutral", "info", "success", "warning", "danger")

    /** Ouro HX's catalog components, named as on web (core's `COMPONENTS`). */
    val COMPONENTS: Map<String, ComposedComponent> = linkedMapOf(
        "Card" to ComposedComponent(
            "A raised surface that groups one thing's content; its children stack vertically.",
            linkedMapOf("children" to ComposedProp("What the card holds, top to bottom.", PropKind.Children, required = true)),
        ),
        "Stack" to ComposedComponent(
            "Lays children out in a column or a row with HX spacing.",
            linkedMapOf(
                "children" to ComposedProp("What the stack holds, in order.", PropKind.Children, required = true),
                "direction" to ComposedProp("vertical stacks top to bottom; horizontal lays out a row that wraps.", PropKind.choice(listOf("vertical", "horizontal"), "vertical")),
                "gap" to ComposedProp("Space between children, from HX's spacing scale.", PropKind.choice(listOf("none", "small", "medium", "large"), "medium")),
            ),
        ),
        "Heading" to ComposedComponent(
            "A title. Level 1 is a card's or page's title, 2 a section, 3 a subsection.",
            linkedMapOf(
                "text" to ComposedProp("The heading's words.", PropKind.Text, required = true),
                "level" to ComposedProp("1, 2 or 3.", PropKind.level(2)),
            ),
        ),
        "Text" to ComposedComponent(
            "A paragraph of plain text.",
            linkedMapOf(
                "text" to ComposedProp("The words, plain text without Markdown.", PropKind.Text, required = true),
                "tone" to ComposedProp("muted for secondary text such as a byline or a note.", PropKind.choice(listOf("default", "muted"), "default")),
            ),
        ),
        "Image" to ComposedComponent(
            "A picture with a description for people who can't see it.",
            linkedMapOf(
                "url" to ComposedProp("The image's https URL.", PropKind.Text, required = true),
                "description" to ComposedProp("What the picture shows, for a screen reader; never \"image of\".", PropKind.Text, required = true),
                "aspect" to ComposedProp("The frame's shape; the picture fills it.", PropKind.choice(listOf("landscape", "square", "portrait", "wide"), "landscape")),
            ),
        ),
        "Badge" to ComposedComponent(
            "A short label for a status or a category, such as \"Vegetarian\".",
            linkedMapOf(
                "text" to ComposedProp("One to three words.", PropKind.Text, required = true),
                "tone" to ComposedProp("neutral unless the label is a status.", PropKind.choice(TONES, "neutral")),
            ),
        ),
        "List" to ComposedComponent(
            "A list of short plain-text items, such as ingredients or steps.",
            linkedMapOf(
                "items" to ComposedProp("The items, in order.", PropKind.Texts, required = true),
                "ordered" to ComposedProp("true numbers the items, for steps that happen in order.", PropKind.flag(false)),
            ),
        ),
        "DescriptionList" to ComposedComponent(
            "Pairs of a term and its detail, such as \"Time\" and \"25 min\".",
            linkedMapOf("items" to ComposedProp("The pairs, each { term, detail }.", PropKind.Terms, required = true)),
        ),
        "Separator" to ComposedComponent("A hairline between groups of content.", linkedMapOf()),
        "Alert" to ComposedComponent(
            "A message the person should notice: a warning, an error, a success or information.",
            linkedMapOf(
                "title" to ComposedProp("One line saying what happened.", PropKind.Text, required = true),
                "text" to ComposedProp("What to do about it, if anything.", PropKind.Text),
                "tone" to ComposedProp("How serious it is.", PropKind.choice(listOf("info", "success", "warning", "danger"), "info")),
            ),
        ),
        "TextField" to ComposedComponent(
            "A field the person types an answer into. It starts with what the data model holds at value and writes their answer there; bind an ActionButton's input to the same path, and pressing it sends what they typed.",
            linkedMapOf(
                "label" to ComposedProp("What the field asks for, in a few words, such as \"Servings\".", PropKind.Text, required = true),
                "value" to ComposedProp("Where the answer goes, as a JSON pointer into the data model, such as /servings.", PropKind.Path, required = true),
                "hint" to ComposedProp("One line under the field saying what to enter, when the label isn't enough.", PropKind.Text),
                "type" to ComposedProp("text for words; number when the answer is a number, which the field writes as one.", PropKind.choice(listOf("text", "number"), "text")),
                "multiline" to ComposedProp("true for a longer answer over several lines, such as a note.", PropKind.flag(false)),
            ),
        ),
        "Checkbox" to ComposedComponent(
            "A yes-or-no choice the person ticks. It starts with what the data model holds at value and writes true or false there.",
            linkedMapOf(
                "label" to ComposedProp("The choice, said so that ticking it means yes, such as \"Make it vegetarian\".", PropKind.Text, required = true),
                "value" to ComposedProp("Where the answer goes, as a JSON pointer into the data model, such as /vegetarian.", PropKind.Path, required = true),
                "hint" to ComposedProp("One line under the choice, when the label isn't enough.", PropKind.Text),
            ),
        ),
        "Select" to ComposedComponent(
            "One choice from a short list. It starts with what the data model holds at value and writes the chosen option there, as text.",
            linkedMapOf(
                "label" to ComposedProp("What the person is choosing, in a few words, such as \"Oven\".", PropKind.Text, required = true),
                "value" to ComposedProp("Where the choice goes, as a JSON pointer into the data model, such as /oven.", PropKind.Path, required = true),
                "options" to ComposedProp("The choices, in order, as the person reads them.", PropKind.Texts, required = true),
                "hint" to ComposedProp("One line under the list, when the label isn't enough.", PropKind.Text),
            ),
        ),
    )

    /** The components a person answers with: each writes to the data model at its `value`. */
    val INPUT_COMPONENTS: List<String> = listOf("TextField", "Checkbox", "Select")

    /** A button for one of the app's declared actions (core's `ACTION_BUTTON`). */
    val ACTION_BUTTON = ComposedComponent(
        "A button for one of the app's declared actions. Its label and what pressing it does come from the action, so you choose only the action, the input you prepared and its emphasis.",
        linkedMapOf(
            "action" to ComposedProp("The action's id.", PropKind.Action, required = true),
            "input" to ComposedProp("The input you prepared, by field name; a value may bind to the data model with {\"@path\"}, and one bound to a field the person fills in is read when they press. Never give input to a person-only action.", PropKind.Input),
            "emphasis" to ComposedProp("primary for the one main action on a surface; secondary for the rest.", PropKind.choice(listOf("primary", "secondary"), "secondary")),
        ),
    )

    /** How deep a tree may nest: far past any screen, short of the stack. */
    const val MAX_DEPTH = 100

    private val BUTTON_PROPS = listOf("action", "label", "consent", "press", "input", "confirm", "handoff", "emphasis", "@label")

    /** HX's components, plus ActionButton when the catalog has actions (core's `componentsFor`). */
    fun componentsFor(actions: Map<String, ComposedAction>): Map<String, ComposedComponent> =
        if (actions.isEmpty()) COMPONENTS else COMPONENTS + ("ActionButton" to ACTION_BUTTON)

    // -------------------------------------------------------------------------------------------------------------
    // The data model
    // -------------------------------------------------------------------------------------------------------------

    /** A value bound to the data model: `{ "@path": "/servings" }`. */
    fun isBinding(v: Any?): Boolean = v is Map<*, *> && v.size == 1 && v["@path"] is String

    private fun unescape(key: String) = key.replace("~1", "/").replace("~0", "~")

    /**
     * Pointer segments that would reach a JavaScript object's prototype instead of the data. Kotlin's maps have none,
     * but every kit refuses the same pointers core does (ouro-ui-core#91), so a tree reads the same everywhere.
     */
    private val UNSAFE_KEYS = setOf("__proto__", "constructor", "prototype")

    private fun segments(pointer: String) = pointer.removePrefix("/").split("/").map(::unescape)

    /** Whether no segment of a pointer, absolute or relative, names an object's prototype (core's `isSafePointer`). */
    fun isSafePointer(pointer: String): Boolean = segments(pointer).none { it in UNSAFE_KEYS }

    /** Whether a pointer stays in the data: absolute, and safe (core's `isDataPointer`). */
    fun isDataPointer(pointer: String): Boolean = pointer.startsWith("/") && isSafePointer(pointer)

    /** An array index as core reads one: ASCII digits only (`/^\d+$/`), or null. */
    private fun index(key: String): Int? = if (key.isNotEmpty() && key.all { it in '0'..'9' }) key.toIntOrNull() else null

    /** Reads a JSON pointer (RFC 6901). A missing value is null, and only what the data itself holds is read. */
    fun getPointer(data: Any?, pointer: String): Any? = find(data, pointer).takeUnless { it === Absent }

    /** What a pointer finds: the value, JSON null included, or [Absent] where the data holds nothing (core's undefined). */
    internal object Absent

    internal fun find(data: Any?, pointer: String): Any? {
        if (pointer == "" || pointer == "/") return data
        var cur: Any? = data
        for (raw in pointer.removePrefix("/").split("/")) {
            val key = unescape(raw)
            val here = cur
            cur = when (here) {
                is List<*> -> {
                    val i = index(key)
                    if (i == null || i >= here.size) return Absent
                    here[i]
                }
                is Map<*, *> -> if (here.containsKey(key)) here[key] else return Absent
                else -> return Absent
            }
        }
        return cur
    }

    /**
     * Writes a JSON pointer, creating objects on the way; null deletes. It returns a copy and leaves [data] as it was. A
     * pointer with a segment `__proto__`, `constructor` or `prototype` writes nothing.
     */
    @Suppress("UNCHECKED_CAST")
    fun setPointer(data: Any?, pointer: String, value: Any?): Any? {
        if (pointer == "" || pointer == "/") return value ?: LinkedHashMap<String, Any?>()
        val keys = segments(pointer)
        // A segment that names an object's prototype writes nothing, as in core.
        if (keys.any { it in UNSAFE_KEYS }) return data
        val root: Any = if (data is Map<*, *> || data is List<*>) copy(data)!! else LinkedHashMap<String, Any?>()
        var cur: Any = root
        keys.forEachIndexed { i, k ->
            val last = i == keys.lastIndex
            when (cur) {
                is MutableList<*> -> {
                    val list = cur as MutableList<Any?>
                    val index = index(k)
                    // JavaScript sets a named property on an array, which JSON never shows, and an index past the end
                    // leaves holes. Write nowhere for either, so no pointer can make the app fill a huge list.
                    if (index == null || index > list.size) {
                        cur = LinkedHashMap<String, Any?>()
                        return@forEachIndexed
                    }
                    if (last) {
                        if (value == null) {
                            if (index < list.size) list.removeAt(index)
                        } else if (index == list.size) {
                            list += value
                        } else {
                            list[index] = value
                        }
                        return@forEachIndexed
                    }
                    if (index == list.size) list += null
                    val next = list[index]
                    if (next !is Map<*, *> && next !is List<*>) list[index] = LinkedHashMap<String, Any?>()
                    cur = list[index]!!
                }
                else -> {
                    val map = cur as MutableMap<String, Any?>
                    if (last) {
                        if (value == null) map.remove(k) else map[k] = value
                        return@forEachIndexed
                    }
                    val next = map[k]
                    if (next !is Map<*, *> && next !is List<*>) map[k] = LinkedHashMap<String, Any?>()
                    cur = map[k]!!
                }
            }
        }
        return root
    }

    /** A deep, writable copy of a JSON value. */
    private fun copy(v: Any?): Any? = when (v) {
        is Map<*, *> -> LinkedHashMap<String, Any?>().also { m -> for ((k, x) in v) m[k.toString()] = copy(x) }
        is List<*> -> ArrayList<Any?>().also { l -> for (x in v) l += copy(x) }
        else -> v
    }

    /** A value as text: numbers and booleans as written, null as "", others as JSON (core's `asText`). */
    fun asText(v: Any?): String = when (v) {
        null -> ""
        is String -> v
        is Number -> ComposedJson.number(v)
        is Boolean -> v.toString()
        else -> ComposedJson.stringify(v)
    }

    // -------------------------------------------------------------------------------------------------------------
    // The button rules
    // -------------------------------------------------------------------------------------------------------------

    /**
     * The rows of a confirm sheet: each prepared input with the person's label. An id shows its name (the app's
     * resolver, else the sibling `shownAs` names, else the value itself), with the id as `raw` when the name replaced
     * it; a name field that stands in for an id has no row of its own.
     */
    /** The input fields a confirm sheet has a row for, in order: each prepared one, less a name that stands in for an id. */
    private fun confirmKeys(a: ComposedAction, input: Map<*, *>): List<String> {
        val names = a.input.filter { (k, f) -> input.containsKey(k) && f.shownAs != null && a.input.containsKey(f.shownAs) }.map { it.value.shownAs!! }.toSet()
        return a.input.keys.filter { input.containsKey(it) && it !in names }
    }

    private fun confirmFields(action: String, a: ComposedAction, input: Map<String, Any?>, label: ((LabelContext) -> String?)?): List<Map<String, Any?>> {
        return confirmKeys(a, input).map { k ->
            val f = a.input.getValue(k)
            val bound = input[k]
            if (isBinding(bound)) return@map linkedMapOf<String, Any?>("label" to f.label, "value" to "", "bind" to (bound as Map<*, *>)["@path"])
            val raw = asText(bound)
            val sibling = f.shownAs?.let { input[it] }
            val prepared = (sibling as? String)?.trim()?.takeIf { it.isNotEmpty() }
            // Only an id field (one with shownAs) shows a name; every other row shows exactly what will be sent.
            val value = (if (f.shownAs != null) label?.invoke(LabelContext(action, k, raw, input)) ?: prepared else null) ?: raw
            if (value == raw) linkedMapOf<String, Any?>("label" to f.label, "value" to value) else linkedMapOf<String, Any?>("label" to f.label, "value" to value, "raw" to raw)
        }
    }

    /**
     * The props of an ActionButton for an action and the input an agent prepared: everything else comes from the
     * catalog (core's `actionButtonProps`). [label] is the app's own word for an id, which wins over the agent's.
     */
    fun actionButtonProps(cat: ComposedCatalog, action: String, prepared: Map<String, Any?>, label: ((LabelContext) -> String?)? = null): Map<String, Any?> {
        val a = cat.actions[action] ?: throw IllegalArgumentException("$action is not one of the app's actions: ${cat.actions.keys.joinToString(", ")}.")
        val input = if (a.personOnly != null) emptyMap() else prepared
        val props = linkedMapOf<String, Any?>("action" to action, "label" to a.label, "consent" to a.consent.wire, "press" to a.press.wire, "input" to input)
        if (a.press == ComposedPress.Confirm) props["confirm"] = linkedMapOf<String, Any?>("title" to a.label, "description" to a.description, "fields" to confirmFields(action, a, input, label))
        if (a.personOnly != null) props["handoff"] = a.personOnly.how
        return props
    }

    /** Why an agent can't press a button through the screen (core's `agentPressProblem`): agents use their own doors. */
    fun agentPressProblem(cat: ComposedCatalog, name: String?): TreeProblem {
        val a = name?.let { cat.actions[it] }
        val po = a?.personOnly
        if (po != null) return TreeProblem("", po.summary, po.refusal)
        if (a != null) return TreeProblem("", "$name is an action, and agents run actions through their own tool (MCP $name), not through the person's screen.")
        return TreeProblem("", "This catalog has no functions an agent may call${if (name != null) ", so $name is not one" else ""}.")
    }

    // -------------------------------------------------------------------------------------------------------------
    // The validator
    // -------------------------------------------------------------------------------------------------------------

    private fun json(v: Any?) = ComposedJson.stringify(v)

    private fun checkProp(problems: MutableList<TreeProblem>, key: String, p: ComposedProp, v: Any?, at: String) {
        val bad = { expected: String -> problems += TreeProblem(at, "$key is $expected, got ${json(v)}.") }
        when (p.type.kind) {
            "string" -> if (v !is String) bad("text")
            "strings" -> if (v !is List<*> || !v.all { it is String }) bad("a list of text")
            "boolean" -> if (v !is Boolean) bad("true or false")
            "enum" -> if (v !is String || v !in p.type.values) bad("one of ${p.type.values.joinToString(", ")}")
            "level" -> if (!(v is Number && v.toDouble() in listOf(1.0, 2.0, 3.0))) bad("1, 2 or 3")
            "path" -> if (v !is String || !isDataPointer(v)) bad("a JSON pointer into the data model, such as /servings")
            "terms" -> if (v !is List<*> || !v.all { it is Map<*, *> && it.size == 2 && it["term"] is String && it["detail"] is String }) bad("a list of { term, detail } text")
        }
    }

    /**
     * Every problem in a composed tree against a catalog (core's `validateTree`): each component is the catalog's, each
     * prop is resolved and of its kind, and each ActionButton is exactly what the catalog says its action's button is,
     * so no adapter and no sender can relabel a button or loosen its consent. An empty list means it renders.
     */
    fun validateTree(cat: ComposedCatalog, node: Any?, path: String = ""): List<TreeProblem> {
        val problems = mutableListOf<TreeProblem>()
        fun visit(n: Any?, at: String, depth: Int) {
            // No screen nests this deep; a tree that does is refused before it can exhaust the stack.
            if (depth > MAX_DEPTH) {
                problems += TreeProblem(at, "The tree nests more than $MAX_DEPTH components deep.")
                return
            }
            val m = n as? Map<*, *>
            if (m == null || m["id"] !is String || m["component"] !is String || m["props"] !is Map<*, *> || m["children"] !is List<*>) {
                problems += TreeProblem(at, "A node is { id, component, props, children }.")
                return
            }
            val component = m["component"] as String
            val spec = cat.components[component]
            if (spec == null) {
                problems += TreeProblem("$at/component", "${json(component)} is not in this catalog; use one of ${cat.components.keys.joinToString(", ")}.")
                return
            }
            val props = m["props"] as Map<*, *>
            val children = m["children"] as List<*>
            val allowed = if (component == "ActionButton") BUTTON_PROPS else spec.props.filter { it.value.type.kind != "children" }.keys + "@label"
            for (key in props.keys.map { it.toString() }) if (key !in allowed) problems += TreeProblem("$at/props/$key", "$component has no $key.")
            if (props.containsKey("@label") && props["@label"] !is String) problems += TreeProblem("$at/props/@label", "@label is text.")
            for ((key, p) in spec.props) {
                if (p.type.kind == "children" || p.type.kind == "action" || p.type.kind == "input") continue
                if (!props.containsKey(key)) {
                    if (p.required) problems += TreeProblem("$at/props/$key", "$component needs $key: ${p.description}")
                    continue
                }
                checkProp(problems, key, p, props[key], "$at/props/$key")
            }
            if (component == "ActionButton") button(cat, problems, props, at)
            if (!spec.props.containsKey("children") && children.isNotEmpty()) problems += TreeProblem("$at/children", "$component has no children.")
            children.forEachIndexed { i, c -> visit(c, "$at/children/$i", depth + 1) }
        }
        visit(node, path, 1)
        return problems
    }

    /** A node's ComposedNode, checked: [validateTree] on its JSON. */
    fun validateTree(cat: ComposedCatalog, node: ComposedNode): List<TreeProblem> = validateTree(cat, node.toJson())

    /**
     * A confirm sheet's rows against the rows the catalog gives for the prepared input (core 0.18.1): the same fields, in
     * order, with the catalog's labels, and each row showing what will be sent (its `raw`, or its `value` when it has
     * none) or, for a bound input, the same binding. Only the words shown for an id may differ, since the app's own
     * resolver names it.
     */
    private fun confirmRowProblems(problems: MutableList<TreeProblem>, id: String, a: ComposedAction, input: Map<String, Any?>, rows: List<*>, at: String) {
        val expected = confirmFields(id, a, input, null)
        val keys = confirmKeys(a, input)
        val bad = { problems += TreeProblem(at, "$id's confirm sheet shows a row for each prepared input, with its label and the value that will be sent; the catalog's rows are ${json(expected)}.") }
        if (rows.size != expected.size) return bad()
        rows.forEachIndexed { i, r ->
            val want = expected[i]
            val row = r as? Map<*, *>
            if (row == null || row["label"] != want["label"]) return@forEachIndexed bad()
            if (want.containsKey("bind")) {
                if (row["bind"] != want["bind"] || row.containsKey("raw")) bad()
            } else if (row.containsKey("bind") || row["value"] !is String) {
                bad()
            } else if (a.input.getValue(keys[i]).shownAs != null) {
                // An id field may show a name, with the id beside it.
                if ((row["raw"] ?: row["value"]) != (want["raw"] ?: want["value"])) bad()
            } else if (row["value"] != want["value"] || row.containsKey("raw")) {
                // Any other row shows exactly what will be sent.
                bad()
            }
        }
    }

    private fun button(cat: ComposedCatalog, problems: MutableList<TreeProblem>, props: Map<*, *>, at: String) {
        val id = props["action"]
        val a = (id as? String)?.let { cat.actions[it] }
        if (a == null || id !is String) {
            problems += TreeProblem("$at/props/action", "${if (props.containsKey("action")) json(id) else "undefined"} is not one of the app's actions: ${cat.actions.keys.joinToString(", ")}.")
            return
        }
        val input = props["input"]
        if (input !is Map<*, *>) {
            problems += TreeProblem("$at/props/input", "input is an object of field values.")
            return
        }
        if (a.personOnly != null && input.isNotEmpty()) {
            problems += TreeProblem("$at/props/input", a.personOnly.summary, a.personOnly.refusal)
            return
        }
        for ((k, v) in input) {
            // A field the catalog doesn't carry would be sent without a row on the confirm sheet.
            if (!a.input.containsKey(k)) problems += TreeProblem("$at/props/input/$k", "$id has no input $k; its fields are ${a.input.keys.joinToString(", ").ifEmpty { "none" }}.")
            if (v is Map<*, *> && v.containsKey("@path") && !(isBinding(v) && isDataPointer(v["@path"] as String))) {
                problems += TreeProblem("$at/props/input/$k", "$k is a value, or {\"@path\": \"/...\"} bound to the data model.")
            }
        }
        @Suppress("UNCHECKED_CAST")
        val expected = actionButtonProps(cat, id, input as Map<String, Any?>)
        for (key in listOf("label", "consent", "press", "handoff")) {
            val sent = if (props.containsKey(key)) json(props[key]) else null
            val want = if (expected.containsKey(key)) json(expected[key]) else null
            if (sent != want) problems += TreeProblem("$at/props/$key", "$id's $key is the catalog's ${json(expected[key])}, never the sender's.")
        }
        if (a.press == ComposedPress.Confirm) {
            val c = props["confirm"] as? Map<*, *>
            if (c == null || c["title"] != a.label || c["description"] != a.description || c["fields"] !is List<*>) {
                problems += TreeProblem("$at/props/confirm", "$id confirms, with the catalog's title and description and a row per prepared input.")
            } else {
                @Suppress("UNCHECKED_CAST")
                confirmRowProblems(problems, id, a, input as Map<String, Any?>, c["fields"] as List<*>, "$at/props/confirm/fields")
            }
        } else if (props.containsKey("confirm")) {
            problems += TreeProblem("$at/props/confirm", "$id doesn't confirm; only a consequential action does.")
        }
    }

    // -------------------------------------------------------------------------------------------------------------
    // The press
    // -------------------------------------------------------------------------------------------------------------

    /**
     * A button as it stands when the person presses it (core's `resolveButton`): each input bound to the data model
     * replaced by what [data] holds there now (null when nothing), and each bound row of its confirm sheet showing that
     * value. A button with nothing bound comes back as it is.
     */
    fun resolveButton(button: ComposedNode, data: Any?): ComposedNode {
        val p = button.props
        val input = p["input"] as? Map<*, *>
        if (input == null || input.values.none(::isBinding)) return button
        val resolved = LinkedHashMap<String, Any?>()
        for ((k, v) in input) resolved[k.toString()] = if (isBinding(v)) getPointer(data, (v as Map<*, *>)["@path"] as String) else v
        val props = LinkedHashMap(p)
        props["input"] = resolved
        val confirm = p["confirm"] as? Map<*, *>
        val fields = confirm?.get("fields") as? List<*>
        if (confirm != null && fields != null) {
            val rows = fields.map { row ->
                val m = row as? Map<*, *>
                val bind = m?.get("bind")
                if (m != null && bind is String) linkedMapOf<String, Any?>("label" to m["label"], "value" to asText(getPointer(data, bind))) else row
            }
            props["confirm"] = LinkedHashMap<String, Any?>().apply {
                for ((k, v) in confirm) put(k.toString(), v)
                put("fields", rows)
            }
        }
        return button.copy(props = props)
    }

    /** A confirm sheet: the action's label and description, and a row per prepared input. */
    data class ConfirmSheet(val title: String, val description: String, val rows: List<ConfirmRow>)

    /** One row of a confirm sheet: the field's label and the value, with the id as [raw] when a name replaced it. */
    data class ConfirmRow(val label: String, val value: String, val raw: String? = null)

    /**
     * The confirm sheet a consequential button shows when the person presses it, or null for a button that doesn't
     * confirm. Its rows are built from the catalog and the button's input, never read from the tree. [validateTree]
     * already refuses a tree whose rows differ from what it sends (core 0.18.1); building them here as well means a tree
     * that was never validated still shows the person what is sent. Rows bound to a field read [data]; [label] is the
     * app's own word for an id field.
     */
    fun confirmSheet(cat: ComposedCatalog, button: ComposedNode, data: Any?, label: ((LabelContext) -> String?)? = null): ConfirmSheet? {
        val action = button.props["action"] as? String ?: return null
        val a = cat.actions[action] ?: return null
        if (a.press != ComposedPress.Confirm) return null
        @Suppress("UNCHECKED_CAST")
        val input = (button.props["input"] as? Map<String, Any?>).orEmpty()
        val rebuilt = resolveButton(button.copy(props = actionButtonProps(cat, action, input, label)), data)
        val confirm = rebuilt.props["confirm"] as Map<*, *>
        val rows = (confirm["fields"] as List<*>).map { row ->
            val r = row as Map<*, *>
            ConfirmRow(r["label"].toString(), asText(r["value"]), r["raw"] as? String)
        }
        return ConfirmSheet(a.label, a.description, rows)
    }

    /** What [personPress] refuses: a person-only button's press, or a consequential one's before the person confirms. */
    class PressRefused(message: String) : IllegalStateException(message)

    /**
     * What pressing a composed button asks the app to do (core's `personPress`), after confirming for a consequential
     * one. A person-only button never asks: it hands the person to the app's own screen, and this throws
     * [PressRefused]. With [data], an input bound to a field the person filled in is read from it.
     */
    fun personPress(button: ComposedNode, confirmed: Boolean = false, data: Any? = null): PersonPress {
        val p = (if (data == null) button else resolveButton(button, data)).props
        val action = p["action"].toString()
        if (p["press"] == ComposedPress.Handoff.wire) throw PressRefused("$action is person-only; its button hands the person to the app, so it sends no action.")
        if (p["press"] == ComposedPress.Confirm.wire && !confirmed) throw PressRefused("$action is consequential; send its action only after the person confirms.")
        @Suppress("UNCHECKED_CAST")
        val input = (p["input"] as? Map<String, Any?>).orEmpty()
        return PersonPress(
            action = action,
            input = input,
            consent = OuroConsent.entries.first { it.wire == p["consent"] },
            label = p["label"] as String,
            confirmed = p["press"] == ComposedPress.Confirm.wire,
        )
    }

    // -------------------------------------------------------------------------------------------------------------
    // Updating a tree a renderer already draws (core 0.19.0)
    // -------------------------------------------------------------------------------------------------------------

    /** What [applyTreeUpdate] gives: the new tree and data model, or every problem and no change. */
    sealed interface Applied {
        data class Updated(val tree: ComposedNode, val data: Map<String, Any?>?) : Applied

        data class Problems(val problems: List<TreeProblem>) : Applied
    }

    private const val UPDATE_SHAPE = "An update is { nodes?, data? }: nodes replace the tree's nodes by id, and data sets values by JSON pointer."

    /**
     * Every problem in an update that needs no tree (core's `treeUpdateProblems`): its shape, each node against the
     * catalog ([validateTree], at `/nodes/<i>`) and each data path, which is absolute and never names an object's
     * prototype ([isDataPointer]). An update is `{ nodes?, data? }`, as core's `TreeUpdate`.
     */
    fun treeUpdateProblems(cat: ComposedCatalog, update: Any?): List<TreeProblem> {
        if (update !is Map<*, *>) return listOf(TreeProblem("", UPDATE_SHAPE))
        nonJsonAt(update, "", java.util.Collections.newSetFromMap(java.util.IdentityHashMap()))?.let {
            return listOf(TreeProblem(it, "An update is JSON: text, finite numbers, true, false, null, lists and objects."))
        }
        val problems = mutableListOf<TreeProblem>()
        for (key in update.keys.map { it.toString() }) if (key != "nodes" && key != "data") problems += TreeProblem("/$key", "An update has no $key. $UPDATE_SHAPE")
        if (update.containsKey("nodes")) {
            val nodes = update["nodes"]
            if (nodes !is List<*>) problems += TreeProblem("/nodes", "nodes is a list of nodes, each replacing the tree's node with its id.")
            else nodes.forEachIndexed { i, n -> problems += validateTree(cat, n, "/nodes/$i") }
        }
        if (update.containsKey("data")) {
            val data = update["data"]
            if (data !is List<*>) problems += TreeProblem("/data", "data is a list of { path, value }, each setting the data model at a JSON pointer.")
            else data.forEachIndexed { i, d ->
                if (d !is Map<*, *> || !d.containsKey("value") || d.keys.any { it != "path" && it != "value" }) problems += TreeProblem("/data/$i", "A data entry is { path, value }: value is JSON, and null deletes.")
                else if (d["path"] !is String || !isDataPointer(d["path"] as String)) problems += TreeProblem("/data/$i/path", "path is a JSON pointer into the data model, such as /servings, and never names __proto__, constructor or prototype.")
            }
        }
        return problems
    }

    /** Where a value first stops being JSON, or null when it is JSON all the way down. */
    private fun nonJsonAt(v: Any?, at: String, seen: MutableSet<Any>): String? {
        when (v) {
            null, is String, is Boolean, is Long, is Int, is Short, is Byte -> return null
            is Double -> return if (v.isFinite()) null else at
            is Float -> return if (v.isFinite()) null else at
            is Map<*, *>, is List<*> -> Unit
            else -> return at
        }
        if (!seen.add(v)) return at
        val entries: List<Pair<String, Any?>> = if (v is List<*>) v.mapIndexed { i, x -> i.toString() to x } else (v as Map<*, *>).entries.map { (k, x) -> (k as? String ?: return at) to x }
        for ((k, x) in entries) nonJsonAt(x, "$at/${k.replace("~", "~0").replace("/", "~1")}", seen)?.let { return it }
        seen.remove(v)
        return null
    }

    /** Why setting [pointer] in [data] would not give JSON: a list is written only at an index from 0 to its length. */
    private fun listIndexProblem(data: Any?, pointer: String): String? {
        var cur: Any? = data
        for (k in segments(pointer)) {
            val here = cur
            when (here) {
                is List<*> -> {
                    val i = if (k.isNotEmpty() && k.all { it in '0'..'9' }) k.toBigInteger() else null
                    if (i == null || i > here.size.toBigInteger()) return "$pointer names ${json(k)} in a list of ${here.size}; in a list, a segment is an index from 0 to ${here.size}."
                    cur = here.getOrNull(i.toInt())
                }
                is Map<*, *> -> cur = here[k]
                else -> return null
            }
        }
        return null
    }

    private fun countId(n: ComposedNode, id: String): Int = (if (n.id == id) 1 else 0) + n.children.sumOf { countId(it, id) }

    private fun replaceNode(n: ComposedNode, by: ComposedNode): ComposedNode = if (n.id == by.id) by else n.copy(children = n.children.map { replaceNode(it, by) })

    /**
     * A tree and its data model after an update (core's `applyTreeUpdate`), or every problem and no change: never a
     * tree partly updated. Each of the update's `nodes` replaces the tree's one node with its id, children and all; an
     * id the tree lacks, or one a template's instances share, is a problem. Each of its `data` sets the value at a JSON
     * pointer ([setPointer]; null deletes, `/` is the whole data model, which stays an object, and a list is written
     * only at an index from 0 to its length). The result passes [validateTree], so an update can't relabel a button or
     * loosen its consent any more than a whole tree can. It changes neither input and shares no object with them.
     */
    fun applyTreeUpdate(cat: ComposedCatalog, tree: Any?, data: Map<String, Any?>?, update: Any?): Applied {
        val shape = treeUpdateProblems(cat, update)
        if (shape.isNotEmpty()) return Applied.Problems(shape)
        val given = validateTree(cat, if (tree is ComposedNode) tree.toJson() else tree)
        if (given.isNotEmpty()) return Applied.Problems(given)
        val u = update as Map<*, *>
        val problems = mutableListOf<TreeProblem>()
        var next = ComposedNode.fromJson(copy(if (tree is ComposedNode) tree.toJson() else tree))
        (u["nodes"] as? List<*>).orEmpty().forEachIndexed { i, raw ->
            val n = ComposedNode.fromJson(copy(raw))
            when (val found = countId(next, n.id)) {
                0 -> problems += TreeProblem("/nodes/$i/id", "${json(n.id)} is not in the tree; replace a node it has, or the node that should hold this one.")
                1 -> next = replaceNode(next, n)
                else -> problems += TreeProblem("/nodes/$i/id", "${json(n.id)} names $found nodes, a template's instances; replace the node that holds them, by its own id.")
            }
        }
        var model: Any? = copy(data)
        (u["data"] as? List<*>).orEmpty().forEachIndexed { i, raw ->
            val d = raw as Map<*, *>
            val path = d["path"] as String
            val base: Any = model ?: LinkedHashMap<String, Any?>()
            val index = listIndexProblem(base, path)
            if (index != null) {
                problems += TreeProblem("/data/$i/path", index)
                return@forEachIndexed
            }
            val written = setPointer(base, path, copy(d["value"]))
            if (written !is Map<*, *>) {
                problems += TreeProblem("/data/$i/value", "The data model is an object; set / only to an object.")
                return@forEachIndexed
            }
            model = written
        }
        if (problems.isNotEmpty()) return Applied.Problems(problems)
        val invalid = validateTree(cat, next)
        @Suppress("UNCHECKED_CAST")
        return if (invalid.isNotEmpty()) Applied.Problems(invalid) else Applied.Updated(next, model as Map<String, Any?>?)
    }

    // -------------------------------------------------------------------------------------------------------------
    // What a tool result carries
    // -------------------------------------------------------------------------------------------------------------

    /** A tool result's composed tree and its data model, or why it can't be drawn. */
    sealed interface Read {
        /** The tree validated against the catalog, and the data model its fields start from. */
        data class Ready(val tree: ComposedNode, val data: Map<String, Any?>) : Read

        /** Why it can't be drawn: each problem says where and why. */
        data class Refused(val problems: List<TreeProblem>) : Read

        /**
         * An update to the tree the view already draws (core's `mcpAppTreeUpdateResult`), its shape, nodes and paths
         * checked; apply it with [applyTreeUpdate], or hand it to [bot.ouro.ui.hx.OuroComposedState.update].
         */
        data class Update(val update: Map<String, Any?>) : Read
    }

    /**
     * Reads an MCP tool result's `structuredContent.ouro` (core's mcp-app.mts: `{ version, catalogId, tree, data? }`, or
     * `{ version, catalogId, update }` for the tree a view already draws) and checks it against [cat]. Pass the whole
     * result, its `structuredContent`, or the `ouro` object itself. A result that carries A2UI messages instead is
     * refused here: [OuroA2UI] resolves those, and [bot.ouro.ui.hx.OuroComposedState.receive] takes either.
     */
    fun read(cat: ComposedCatalog, result: Any?): Read {
        val r = result as? Map<*, *>
        val structured = (r?.get("structuredContent") as? Map<*, *>) ?: r
        val ouro = (structured?.get("ouro") as? Map<*, *>) ?: structured?.takeIf { (it.containsKey("tree") || it.containsKey("update")) && it.containsKey("version") }
        if (ouro == null) {
            val a2ui = structured?.containsKey("a2ui") == true
            return Read.Refused(listOf(TreeProblem("/structuredContent", if (a2ui) "This result carries A2UI messages, not Ouro's tree. Fix: resolve them with OuroA2UI.resolveMessages, or pass the result to OuroComposedState.receive." else "This result carries no structuredContent.ouro. Fix: send the tree (core's mcpAppTreeResult).")))
        }
        if (ouro["version"] != COMPOSED_VERSION) return Read.Refused(listOf(TreeProblem("/structuredContent/ouro/version", "This renderer draws version $COMPOSED_VERSION of Ouro's tree, got ${json(ouro["version"])}.")))
        if (ouro["catalogId"] != cat.catalogId) return Read.Refused(listOf(TreeProblem("/structuredContent/ouro/catalogId", "This renderer has only the catalog ${cat.catalogId}.")))
        if (!ouro.containsKey("tree") && ouro.containsKey("update")) {
            val problems = treeUpdateProblems(cat, ouro["update"]).map { it.copy(path = "/structuredContent/ouro/update${it.path}") }
            @Suppress("UNCHECKED_CAST")
            return if (problems.isNotEmpty()) Read.Refused(problems) else Read.Update(ouro["update"] as Map<String, Any?>)
        }
        val data = ouro["data"]
        if (data != null && data !is Map<*, *>) return Read.Refused(listOf(TreeProblem("/structuredContent/ouro/data", "data is the data model, an object.")))
        val problems = validateTree(cat, ouro["tree"], "/structuredContent/ouro/tree")
        if (problems.isNotEmpty()) return Read.Refused(problems)
        @Suppress("UNCHECKED_CAST")
        val model = (data as? Map<String, Any?>).orEmpty()
        return Read.Ready(ComposedNode.fromJson(ouro["tree"]), model)
    }
}
