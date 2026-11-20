// Core's claims, run on Android for every component the kit ships (KIT). Each component spec in vendor/core/components
// names its claims; every one needs a check here, so a claim core adds fails this until Android proves it. The checks
// read Compose's semantics tree (what TalkBack and agents read) under the settings each claim names, the components'
// own reports, and pixels sampled from the rendered image. The table goes to build/ouro/claims.md for CI's summary.
package bot.ouro.ui.hx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import bot.ouro.ui.hx.tokens.OuroThemeName
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h4000dp-xxhdpi")
class ClaimsTest {
    @get:Rule
    val rule = createComposeRule()

    /** The components this kit ships. Core specs more; each joins here when its composable does, with a check per claim. */
    private val kit = listOf("button", "badge", "card", "item", "checkbox", "text-field", "separator", "description-list", "dialog", "sheet", "confirm-dialog", "tabs", "tab-bar", "sidebar", "switch", "select")

    private class Result(val ok: Boolean?, val detail: String)

    private fun all(xs: List<Pair<Boolean, String>>, what: String): Result =
        if (xs.isEmpty()) Result(null, "no $what rendered")
        else Result(xs.all { it.first }, "${xs.count { it.first }}/${xs.size} $what" + xs.filter { !it.first }.take(6).joinToString("; ", prefix = if (xs.any { !it.first }) "; failing: " else "") { it.second })

    private var setting by mutableStateOf(LIGHT)
    private var long by mutableStateOf(false)
    private var only by mutableStateOf<String?>(null)
    private var reopen by mutableIntStateOf(0)

    private fun under(s: Setting, isLong: Boolean = false, just: String? = null) {
        setting = s
        long = isLong
        only = just
        rule.waitForIdle()
    }

    private fun kindIs(kind: String) = SemanticsMatcher("kind=$kind") { n -> n.config.getOrNull(OuroReport)?.let { parseOuroReport(it)["kind"] } == kind }

    private fun nodes(kind: String): List<SemanticsNode> = rule.onAllNodes(kindIs(kind)).fetchSemanticsNodes()

    private fun SemanticsNode.report() = parseOuroReport(config[OuroReport])
    private fun SemanticsNode.tag() = config.getOrNull(SemanticsProperties.TestTag) ?: "#$id"
    private fun SemanticsNode.words() = (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }
    /** What TalkBack names it by: its content description when it has one, else its words. */
    private fun SemanticsNode.name() = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ") ?: words()
    private fun SemanticsNode.within(): List<SemanticsNode> = listOf(this) + children.flatMap { it.within() }

    /** The controls a component draws, which a person presses or types into: the node itself, or those inside it. */
    private fun controls(kind: String, n: SemanticsNode): List<SemanticsNode> = when (kind) {
        "select" -> n.within().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.DropdownList }
        "checkbox", "switch" -> n.within().filter { it.config.contains(SemanticsProperties.ToggleableState) }
        "text-field" -> n.within().filter { it.config.contains(SemanticsActions.SetText) || it.config.contains(SemanticsActions.OnClick) }
        "dialog", "sheet", "confirm-dialog" -> n.within().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button && it.config.contains(SemanticsActions.OnClick) }
        "tabs" -> n.within().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
        "tab-bar", "sidebar" -> n.within().filter { it.config.contains(SemanticsActions.OnClick) }
        else -> listOf(n)
    }

    /** Ends with ", required" exactly when its report says it is required. */
    private fun requiredSpoken(root: SemanticsNode, control: SemanticsNode) = (root.report()["required"] == "true") == control.name().endsWith(", required")
    private fun color(hex: String) = Color(hex.removePrefix("#").toLong(16).toInt())

    /** What TalkBack and agents must read for each component (its spec's accessibility.android). */
    private fun spoken(kind: String, n: SemanticsNode): Boolean {
        val c = n.config
        val role = c.getOrNull(SemanticsProperties.Role)
        val clickable = c.contains(SemanticsActions.OnClick)
        val focusable = c.contains(SemanticsProperties.Focused)
        return when (kind) {
            "button" -> role == Role.Button && clickable && focusable && n.words().isNotBlank()
            "badge" -> role == null && !clickable && !focusable && n.words().isNotBlank()
            "card" -> c.getOrNull(SemanticsProperties.IsTraversalGroup) == true &&
                rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading), useUnmergedTree = true).fetchSemanticsNodes()
                    .any { h -> h.words().isNotBlank() && generateSequence(h.parent) { it.parent }.any { it.id == n.id } }
            "item" -> if (n.report()["pressable"] == "true") role == Role.Button && clickable && focusable && n.words().isNotBlank()
            else !clickable && !focusable && n.words().isNotBlank()
            "checkbox" -> controls(kind, n).let { cs ->
                cs.size == 1 && cs.all { x ->
                    x.config.getOrNull(SemanticsProperties.Role) == Role.Checkbox && x.config.contains(SemanticsActions.OnClick) &&
                        x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank() && requiredSpoken(n, x) &&
                        (n.report()["hasError"] != "true" || x.config.contains(SemanticsProperties.Error))
                }
            }
            "switch" -> controls(kind, n).let { cs ->
                cs.size == 1 && cs.all { x ->
                    x.config.getOrNull(SemanticsProperties.Role) == Role.Switch && x.config.contains(SemanticsActions.OnClick) &&
                        x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank() && requiredSpoken(n, x) &&
                        // The state stays On or Off: an error is its own semantics, never the state.
                        x.config.getOrNull(SemanticsProperties.ToggleableState).let { it == ToggleableState.On || it == ToggleableState.Off } &&
                        (n.report()["hasError"] != "true" || x.config.contains(SemanticsProperties.Error))
                }
            }
            // A dropdown named by its label, with the expand action and its chosen words, error and description as its state.
            "select" -> controls(kind, n).let { cs ->
                cs.size == 1 && cs.all { x ->
                    x.config.contains(SemanticsActions.Expand) && x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank() && requiredSpoken(n, x) &&
                        (n.report()["hasError"] != "true" || x.config.contains(SemanticsProperties.Error)) &&
                        (n.report()["hasNotes"] != "true" || x.config.contains(SemanticsProperties.StateDescription))
                }
            }
            "text-field" -> n.within().filter { it.config.contains(SemanticsActions.SetText) }.let { fields ->
                fields.size == 1 && fields.all { x ->
                    x.config.contains(SemanticsProperties.EditableText) && x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank() && requiredSpoken(n, x) &&
                        (n.report()["hasError"] != "true" || x.config.contains(SemanticsProperties.Error)) &&
                        (n.report()["hasNotes"] != "true" || x.config.contains(SemanticsProperties.StateDescription))
                } && n.within().filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Button }.all { it.name() in listOf("Show password", "Clear") }
            }
            // A line is nothing to TalkBack; a labelled one is its label, as text.
            "separator" -> role == null && !clickable && !focusable &&
                if (n.report()["labelled"] == "true") n.words().isNotBlank() else n.name().isBlank()
            // Each pair is one element, its term then its details.
            "description-list" -> c.getOrNull(SemanticsProperties.IsTraversalGroup) == true &&
                n.within().filter { it.config.isMergingSemanticsOfDescendants }.let { pairs ->
                    pairs.size.toString() == n.report()["pairs"] && pairs.all { (it.config.getOrNull(SemanticsProperties.Text)?.size ?: 0) == 2 && !it.config.contains(SemanticsActions.OnClick) }
                }
            "dialog", "sheet" -> overlaySpoken(n) && n.within().any { b ->
                b.config.getOrNull(SemanticsProperties.Role) == Role.Button && b.config.contains(SemanticsActions.OnClick) &&
                    b.config.contains(SemanticsProperties.Focused) && b.name() == "Close"
            } && c.contains(SemanticsActions.Dismiss)
            "confirm-dialog" -> overlaySpoken(n) && controls(kind, n).map { it.name() }.let { names -> names.size == 2 && names[0] == "Cancel" && names[1].isNotBlank() }
            // One group named by its label, of Tab nodes: each named by its words, clickable and focusable, one of them selected.
            "tabs" -> c.contains(SemanticsProperties.SelectableGroup) && !c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                controls(kind, n).let { tabs ->
                    tabs.isNotEmpty() && tabs.size == n.children.size && tabs.all { x ->
                        x.config.contains(SemanticsActions.OnClick) && (x.config.contains(SemanticsProperties.Focused) || x.config.contains(SemanticsProperties.Disabled)) && x.name().isNotBlank() && x.config.contains(SemanticsProperties.Selected)
                    } && tabs.count { it.config.getOrNull(SemanticsProperties.Selected) == true } == 1
                }
            // One group named by its label: a Tab per item, named by its label, one of them selected, then the accessory, a button named by its label.
            "tab-bar" -> c.contains(SemanticsProperties.SelectableGroup) && !c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                controls(kind, n).let { items ->
                    val tabs = items.filter { it.config.getOrNull(SemanticsProperties.Role) == Role.Tab }
                    val rest = items - tabs.toSet()
                    tabs.size in 2..5 && items.size == n.children.size && rest.size <= 1 && tabs.all { x ->
                        x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank() && x.config.contains(SemanticsProperties.Selected)
                    } && rest.all { x -> x.config.getOrNull(SemanticsProperties.Role) == Role.Button && x.name().isNotBlank() && x.config.contains(SemanticsProperties.Focused) } &&
                        tabs.count { it.config.getOrNull(SemanticsProperties.Selected) == true } <= 1
                }
            // A list named by its label, of Role.Button rows each named by its label and badge, at most one selected, headings with words.
            "sidebar" -> c.contains(SemanticsProperties.CollectionInfo) && !c.getOrNull(SemanticsProperties.ContentDescription).isNullOrEmpty() &&
                controls(kind, n).let { rows ->
                    rows.isNotEmpty() && rows.all { x ->
                        x.config.getOrNull(SemanticsProperties.Role) == Role.Button && x.config.contains(SemanticsProperties.Focused) && x.name().isNotBlank()
                    } && rows.count { it.config.getOrNull(SemanticsProperties.Selected) == true } <= 1 &&
                        n.within().filter { it.config.contains(SemanticsProperties.Heading) }.all { it.words().isNotBlank() }
                }
            else -> false
        }
    }

    /** A pane title, and a heading with words unless the title is hidden. */
    private fun overlaySpoken(n: SemanticsNode): Boolean {
        val headings = n.within().filter { it.config.contains(SemanticsProperties.Heading) }
        val hidden = n.report()["hideTitle"] == "true"
        return !n.config.getOrNull(SemanticsProperties.PaneTitle).isNullOrBlank() && (hidden || headings.any { it.words().isNotBlank() })
    }

    /** The colour pairs each component's words sit on, from its report. */
    private fun pairs(r: Map<String, String>): List<Triple<String, String, String>> {
        val behind = r["behind"] ?: r["surface"]!!
        return listOf("label", "title", "text", "description", "error", "term", "details", "selected", "unselected", "section").mapNotNull { k -> r[k]?.let { Triple(k, it, behind) } } +
            listOfNotNull(r["current"]?.let { c -> r["wash"]?.let { Triple("current", c, it) } }) +
            listOfNotNull(r["badge"]?.let { c -> r["badgeWash"]?.let { Triple("badge", c, it) } })
    }

    private fun nameSpoken(kind: String): Result {
        under(LIGHT)
        return all(nodes(kind).map { n -> spoken(kind, n) to "${n.tag()}: " + controls(kind, n).joinToString { "role ${it.config.getOrNull(SemanticsProperties.Role)} \"${it.name()}\"" } }, "${kind}s named and typed as the spec says")
    }

    private fun targetTouch(kind: String): Result {
        under(LIGHT)
        val d = rule.density.density
        return all(nodes(kind).flatMap { root -> controls(kind, root).map { n -> (n.size.width / d >= 48f && n.size.height / d >= 48f) to "${root.tag()} ${n.name()} ${(n.size.width / d).toInt()}x${(n.size.height / d).toInt()} dp at y=${(n.positionInRoot.y / d).toInt()}" } }, "$kind controls at least 48x48 dp")
    }

    private fun motionReduced(kind: String): Result {
        // A select takes a fill while pressed and never scales, so there is nothing to remove: it holds 1 with animations on and off.
        if (kind == "select") {
            val checked = listOf(LIGHT, NO_ANIMATIONS).flatMap { s ->
                under(s)
                nodes(kind).map { (it.report()["pressScale"]!!.toFloat() == 1f && it.report()["pressFill"] == "true") to "${it.tag()} ${s.id}: scale ${it.report()["pressScale"]}, fill ${it.report()["pressFill"]}" }
            }
            return all(checked, "${kind}s never scale: they fill while pressed, with animations on and off")
        }
        under(LIGHT)
        val on = nodes(kind).map { (it.report()["pressScale"]!!.toFloat() < 1f) to "${it.tag()} on: ${it.report()["pressScale"]}" }
        under(NO_ANIMATIONS)
        val off = nodes(kind).map { (it.report()["pressScale"]!!.toFloat() == 1f) to "${it.tag()} off: ${it.report()["pressScale"]}" }
        if (on.isEmpty() || off.isEmpty()) return Result(null, "no ${kind}s with animations on and off")
        return all(on + off, "${kind}s: press scale 0.97 with animations on, 1 with Remove animations")
    }

    private fun textReflows(kind: String): Result {
        under(TEXT_200, isLong = true, just = kind)
        val roots = nodes(kind)
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { t -> generateSequence(t) { it.parent }.any { p -> roots.any { it.id == p.id } } }
        // Clipped means a glyph falls outside the box the text is given. Compose's hasVisualOverflow can't say that
        // here: the layout the semantics hand back is measured at the column's full width, so a short line it then
        // shrinks to fit reads as overflowing. Each line's right edge and the last line's bottom are compared with the
        // text's own box, and that box with its component's.
        val checks = texts.map { t ->
            val layouts = mutableListOf<TextLayoutResult>()
            t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val l = layouts.firstOrNull()
            val text = l?.layoutInput?.text?.text ?: ""
            val root = roots.first { r -> generateSequence(t) { it.parent }.any { it.id == r.id } }
            val widest = l?.let { (0 until it.lineCount).maxOf { i -> it.getLineRight(i) - it.getLineLeft(i) } } ?: 0f
            val inBox = l != null && widest <= t.size.width + 1 && l.getLineBottom(l.lineCount - 1) <= t.size.height + 1
            val b = t.boundsInRoot
            val rb = root.boundsInRoot
            val inComponent = b.left >= rb.left - 1 && b.right <= rb.right + 1 && b.top >= rb.top - 1 && b.bottom <= rb.bottom + 1
            val ok = inBox && inComponent && (text != LONG || l!!.lineCount > 1)
            ok to "\"${text.take(24)}\": ${l?.lineCount} lines, widest ${"%.0f".format(widest)} of ${t.size.width} px, inside its ${kind} $inComponent"
        }
        under(LIGHT)
        return all(checks, "$kind texts at 200% in a 240 dp column wrap, none clipped")
    }

    private fun contrastText(kind: String): Result {
        val checks = mutableListOf<Pair<Boolean, String>>()
        for (theme in OuroThemeName.entries) for (base in listOf(LIGHT, DARK, CONTRAST, CONTRAST_DARK)) {
            val s = base.copy(id = "${theme.id}-${base.id}", theme = theme)
            under(s)
            val min = if (s.increasedContrast) OuroColor.INCREASED_TEXT_CONTRAST else OuroColor.TEXT_CONTRAST
            for (n in nodes(kind)) {
                for ((what, fg, bg) in pairs(n.report())) {
                    val r = OuroColor.contrast(color(fg), color(bg))
                    checks += (r >= min - 0.01) to "${s.id} ${n.tag()} $what ${"%.2f".format(r)}:1"
                }
                // A box's or a field's edge is how it is found: 3:1 on what is behind it.
                n.report()["edge"]?.let { edge ->
                    val r = OuroColor.contrast(color(edge), color(n.report()["behind"]!!))
                    checks += (r >= OuroColor.INCREASED_LINE_CONTRAST - 0.01) to "${s.id} ${n.tag()} edge ${"%.2f".format(r)}:1"
                }
            }
        }
        // The pixels under the words: in Pearl, light, dark and increased contrast, the strongest pixel inside each
        // button and badge is its reported label colour, at the contrast it reports.
        if (kind == "button" || kind == "badge") for (s in listOf(LIGHT, DARK, CONTRAST)) {
            under(s)
            val min = if (s.increasedContrast) OuroColor.INCREASED_TEXT_CONTRAST else OuroColor.TEXT_CONTRAST
            val matched = rule.onAllNodes(kindIs(kind))
            for ((i, n) in nodes(kind).withIndex()) {
                val r = n.report()
                val behind = color(r["behind"]!!)
                val label = color(r["label"]!!)
                val px = matched[i].captureToImage().toPixelMap()
                var best = behind
                var bestRatio = 1.0
                for (x in (px.width * 0.2).toInt() until (px.width * 0.8).toInt()) for (y in (px.height * 0.25).toInt() until (px.height * 0.75).toInt()) {
                    val p = px[x, y]
                    val ratio = OuroColor.contrast(p, behind)
                    if (ratio > bestRatio) { bestRatio = ratio; best = p }
                }
                val near = listOf(best.red - label.red, best.green - label.green, best.blue - label.blue).all { kotlin.math.abs(it) <= 12f / 255f }
                checks += (near && bestRatio >= min - 0.25) to "pixels ${s.id} ${n.tag()}: strongest ${OuroColor.hex(best)} vs label ${r["label"]}, ${"%.2f".format(bestRatio)}:1"
            }
        }
        under(LIGHT)
        return all(checks, "$kind word pairs at 4.5:1 (7:1 with increased contrast) in all 6 themes, light and dark, and pixels that match")
    }

    /** Pressing Close takes the panel away, and the panel offers the dismiss action to a screen reader. */
    private fun dismissReachable(kind: String): Result {
        under(LIGHT, just = kind)
        val out = mutableListOf<Pair<Boolean, String>>()
        val tags = nodes(kind).map { it.tag() }
        for (tag in tags) {
            val root = nodes(kind).first { it.tag() == tag }
            val closeId = root.within().first { it.config.getOrNull(SemanticsProperties.TestTag) == "$kind.close" }.id
            val offers = root.config.contains(SemanticsActions.Dismiss)
            rule.onNode(SemanticsMatcher("id=$closeId") { it.id == closeId }).performClick()
            rule.waitForIdle()
            val gone = nodes(kind).none { it.tag() == tag }
            out += (offers && gone) to "$tag: dismiss action $offers, gone after Close $gone"
            reopen++
            rule.waitForIdle()
        }
        under(LIGHT)
        return all(out, "$kind panels close from their Close button and offer the dismiss action")
    }

    private val checks: Map<String, (String) -> Result> = mapOf(
        "name.spoken" to ::nameSpoken,
        "target.touch" to ::targetTouch,
        "motion.reduced" to ::motionReduced,
        "text.reflows" to ::textReflows,
        "contrast.text" to ::contrastText,
        "dismiss.reachable" to ::dismissReachable,
    )

    @Test
    fun every_claim_core_makes_for_the_kits_components_passes_on_android() {
        rule.setContent { Gallery(setting, only = only, long = long, reopen = reopen) }
        val specs = File("../vendor/core/components").listFiles { f -> f.name.endsWith(".spec.json") }!!
            .map { JSONObject(it.readText()) }.filter { it.getString("name") in kit }
        assertTrue("no vendored spec for ${kit - specs.map { it.getString("name") }.toSet()}. Fix: node scripts/provenance.mjs update <core tag>.", specs.size == kit.size)
        val rows = mutableListOf<Triple<String, String, Result>>()
        for (spec in specs.sortedBy { kit.indexOf(it.getString("name")) }) {
            val kind = spec.getString("name")
            val claims = spec.getJSONArray("claims")
            for (i in 0 until claims.length()) {
                val claim = claims.getString(i)
                val check = checks[claim]
                rows += Triple(kind, claim, check?.invoke(kind) ?: Result(false, "no Android check for this claim. Fix: add one to ClaimsTest."))
            }
        }
        val mark = { r: Result -> when (r.ok) { true -> "pass"; false -> "FAIL"; null -> "not run" } }
        val md = buildString {
            appendLine("### Core's claims on Android (Robolectric, Compose semantics and pixels)")
            appendLine()
            appendLine("| Component | Claim | Result | Detail |")
            appendLine("| --- | --- | --- | --- |")
            for ((k, c, r) in rows) appendLine("| $k | `$c` | ${mark(r)} | ${r.detail.replace("|", "\\|")} |")
        }
        File("build/ouro").mkdirs()
        File("build/ouro/claims.md").writeText(md)
        println(md)
        val bad = rows.filter { it.third.ok != true }
        assertTrue("claims failing or not run:\n" + bad.joinToString("\n") { "${it.first} ${it.second}: ${it.third.detail}" }, bad.isEmpty())
    }
}
