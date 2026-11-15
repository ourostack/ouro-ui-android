// Core's claims, run on Android for every component the kit ships (KIT). Each component spec in vendor/core/components
// names its claims; every one needs a check here, so a claim core adds fails this until Android proves it. The checks
// read Compose's semantics tree (what TalkBack and agents read) under the settings each claim names, the components'
// own reports, and pixels sampled from the rendered image. The table goes to build/ouro/claims.md for CI's summary.
package bot.ouro.ui.hx

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
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
@Config(qualifiers = "w411dp-h2000dp-xxhdpi")
class ClaimsTest {
    @get:Rule
    val rule = createComposeRule()

    /** The components this kit ships. Core specs more; each joins here when its composable does, with a check per claim. */
    private val kit = listOf("button", "badge", "card", "item")

    private class Result(val ok: Boolean?, val detail: String)

    private fun all(xs: List<Pair<Boolean, String>>, what: String): Result =
        if (xs.isEmpty()) Result(null, "no $what rendered")
        else Result(xs.all { it.first }, "${xs.count { it.first }}/${xs.size} $what" + xs.filter { !it.first }.take(6).joinToString("; ", prefix = if (xs.any { !it.first }) "; failing: " else "") { it.second })

    private var setting by mutableStateOf(LIGHT)
    private var long by mutableStateOf(false)

    private fun under(s: Setting, isLong: Boolean = false) {
        setting = s
        long = isLong
        rule.waitForIdle()
    }

    private fun kindIs(kind: String) = SemanticsMatcher("kind=$kind") { n -> n.config.getOrNull(OuroReport)?.let { parseOuroReport(it)["kind"] } == kind }

    private fun nodes(kind: String): List<SemanticsNode> = rule.onAllNodes(kindIs(kind)).fetchSemanticsNodes()

    private fun SemanticsNode.report() = parseOuroReport(config[OuroReport])
    private fun SemanticsNode.tag() = config.getOrNull(SemanticsProperties.TestTag) ?: "#$id"
    private fun SemanticsNode.words() = (config.getOrNull(SemanticsProperties.Text) ?: emptyList()).joinToString(" ") { it.text }
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
            else -> false
        }
    }

    /** The colour pairs each component's words sit on, from its report. */
    private fun pairs(r: Map<String, String>): List<Triple<String, String, String>> =
        if (r["kind"] == "button" || r["kind"] == "badge") listOf(Triple("label", r["label"]!!, r["behind"]!!))
        else listOf(Triple("title", r["title"]!!, r["surface"]!!), Triple("description", r["description"]!!, r["surface"]!!))

    private fun nameSpoken(kind: String): Result {
        under(LIGHT)
        return all(nodes(kind).map { spoken(kind, it) to "${it.tag()}: role ${it.config.getOrNull(SemanticsProperties.Role)} \"${it.words()}\"" }, "${kind}s named and typed as the spec says")
    }

    private fun targetTouch(kind: String): Result {
        under(LIGHT)
        val d = rule.density.density
        return all(nodes(kind).map { n -> (n.size.width / d >= 48f && n.size.height / d >= 48f) to "${n.tag()} ${(n.size.width / d).toInt()}x${(n.size.height / d).toInt()} dp" }, "${kind}s at least 48x48 dp")
    }

    private fun motionReduced(kind: String): Result {
        under(LIGHT)
        val on = nodes(kind).map { (it.report()["pressScale"]!!.toFloat() < 1f) to "${it.tag()} on: ${it.report()["pressScale"]}" }
        under(NO_ANIMATIONS)
        val off = nodes(kind).map { (it.report()["pressScale"]!!.toFloat() == 1f) to "${it.tag()} off: ${it.report()["pressScale"]}" }
        if (on.isEmpty() || off.isEmpty()) return Result(null, "no ${kind}s with animations on and off")
        return all(on + off, "${kind}s: press scale 0.97 with animations on, 1 with Remove animations")
    }

    private fun textReflows(kind: String): Result {
        under(TEXT_200, isLong = true)
        val roots = nodes(kind)
        val texts = rule.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.GetTextLayoutResult), useUnmergedTree = true).fetchSemanticsNodes()
            .filter { t -> generateSequence(t) { it.parent }.any { p -> roots.any { it.id == p.id } } }
        val checks = texts.map { t ->
            val layouts = mutableListOf<TextLayoutResult>()
            t.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
            val l = layouts.firstOrNull()
            val text = l?.layoutInput?.text?.text ?: ""
            val ok = l != null && !l.hasVisualOverflow && (text != LONG || l.lineCount > 1)
            ok to "\"${text.take(24)}\": ${l?.lineCount} lines, overflow ${l?.hasVisualOverflow}"
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
            for (n in nodes(kind)) for ((what, fg, bg) in pairs(n.report())) {
                val r = OuroColor.contrast(color(fg), color(bg))
                checks += (r >= min - 0.01) to "${s.id} ${n.tag()} $what ${"%.2f".format(r)}:1"
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

    private val checks: Map<String, (String) -> Result> = mapOf(
        "name.spoken" to ::nameSpoken,
        "target.touch" to ::targetTouch,
        "motion.reduced" to ::motionReduced,
        "text.reflows" to ::textReflows,
        "contrast.text" to ::contrastText,
    )

    @Test
    fun every_claim_core_makes_for_the_kits_components_passes_on_android() {
        rule.setContent { Gallery(setting, long = long) }
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
