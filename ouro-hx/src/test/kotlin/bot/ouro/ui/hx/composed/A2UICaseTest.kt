// One test per core conformance case, so each kit's CI result names every case and its outcome (JUnit XML, and the
// per-case table in the job summary): the case resolves to its expect and every tree it gives passes the validator.
package bot.ouro.ui.hx.composed

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File

@RunWith(Parameterized::class)
class A2UICaseTest(private val name: String) {
    companion object {
        private val dir = File("../vendor/core/composed")

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun cases(): List<String> = File(dir, "cases").listFiles { f -> f.name.endsWith(".json") }!!.map { it.name }.sorted()

        private fun read(name: String): Any? = ComposedJson.parse(File(dir, name).readText())
        private val catalogs = mapOf(
            "catalog.json" to ComposedCatalog.fromJson(read("catalog.json")),
            "recipe.catalog.json" to ComposedCatalog.fromJson(read("recipe.catalog.json")),
        )
    }

    @Test
    fun case_resolves_to_its_expect_and_its_trees_validate() {
        val c = ComposedJson.parse(File(dir, "cases/$name").readText()) as Map<*, *>
        val cat = catalogs.getValue(c["catalog"] as String)
        val resolved = OuroA2UI.resolveMessages(cat, c["messages"])
        assertEquals(name, ComposedJson.stringify(c["expect"]), ComposedJson.stringify(resolved.toJson()))
        for (s in resolved.surfaces) {
            val tree = s.tree ?: continue
            assertEquals("$name ${s.surfaceId}", emptyList<TreeProblem>(), OuroComposed.validateTree(cat, tree))
        }
    }
}
