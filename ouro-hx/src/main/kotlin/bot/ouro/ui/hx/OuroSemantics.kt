// What the components tell tests and the X-ray, beside what they tell people's assistive technology. These keys are
// not read by TalkBack; they are the Compose counterpart of the Windows kit's self-report and automation ids.
package bot.ouro.ui.hx

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.semantics

/** A component's report of what it drew: `kind=button;tone=primary;label=#FF..;behind=#FF..;pressScale=0.97`. */
val OuroReport = SemanticsPropertyKey<String>("OuroReport")
var SemanticsPropertyReceiver.ouroReport by OuroReport

/** The declared action a control runs: `id;consent`, such as `add_shopping_list_item;write`. */
val OuroActionKey = SemanticsPropertyKey<String>("OuroAction")
var SemanticsPropertyReceiver.ouroAction by OuroActionKey

/** Marks a control as running a declared action, so the X-ray can check what people press against what agents call. */
fun Modifier.ouroAction(id: String, consent: String): Modifier = semantics { ouroAction = "$id;$consent" }

/** The report's fields, parsed. */
fun parseOuroReport(report: String): Map<String, String> =
    report.split(";").filter { it.contains("=") }.associate { it.substringBefore("=") to it.substringAfter("=") }

internal fun report(vararg fields: Pair<String, Any>): String = fields.joinToString(";") { (k, v) -> "$k=$v" }
