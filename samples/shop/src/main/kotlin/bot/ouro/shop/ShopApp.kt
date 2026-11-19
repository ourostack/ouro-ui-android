// The app sets each declared action's handler once, when it starts. Every door (a button, an AppFunction) runs it.
package bot.ouro.shop

import android.app.Application
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import bot.ouro.shop.actions.AddShoppingListItem
import bot.ouro.shop.actions.AddShoppingListItemOutput
import bot.ouro.shop.actions.OuroActions
import bot.ouro.shop.actions.PlaceGroceryOrder
import bot.ouro.shop.actions.ShareShoppingList
import bot.ouro.ui.actions.OuroConfirm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One thing on the list. */
data class ListItem(val id: String, val name: String, val quantity: Double?)

/** The list, in memory: synthetic data only. */
object Shop {
    val items = mutableStateListOf(ListItem("1", "Oat milk", 2.0), ListItem("2", "Lemons", 4.0), ListItem("3", "Orzo", null))
    val sharedWith = mutableStateListOf<String>()
    val ordered = mutableStateOf(false)
    private var next = 4

    fun bind() {
        AddShoppingListItem.perform = { input ->
            withContext(Dispatchers.Main) {
                val item = ListItem((next++).toString(), input.name, input.quantity)
                items += item
                AddShoppingListItemOutput(id = item.id)
            }
        }
        ShareShoppingList.perform = { input -> withContext(Dispatchers.Main) { sharedWith += input.with } }
        PlaceGroceryOrder.perform = { withContext(Dispatchers.Main) { ordered.value = true } }
        // ShareShoppingList.confirm stays unset here: OuroConfirm.install (ShopApp.onCreate) gives it the built-in
        // confirmation, which asks the person before an agent's share runs. Set confirm yourself to ask another way.
    }
}

class ShopApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Shop.bind()
        OuroConfirm.install(this, OuroActions.set)
    }
}
