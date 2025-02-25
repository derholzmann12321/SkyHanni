package at.hannibal2.skyhanni.features.misc.items

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.config.ConfigManager
import at.hannibal2.skyhanni.config.ConfigUpdaterMigrator
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.RenderItemTooltipEvent
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalNameOrNull
import at.hannibal2.skyhanni.utils.KeyboardManager.isKeyHeld
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.addAsSingletonList
import at.hannibal2.skyhanni.utils.LorenzUtils.onToggle
import at.hannibal2.skyhanni.utils.NEUInternalName
import at.hannibal2.skyhanni.utils.NEUItems.getItemStackOrNull
import at.hannibal2.skyhanni.utils.NEUItems.manager
import at.hannibal2.skyhanni.utils.NumberUtil
import at.hannibal2.skyhanni.utils.NumberUtil.addSeparators
import at.hannibal2.skyhanni.utils.RenderUtils.renderStringsAndItems
import com.google.gson.reflect.TypeToken
import io.github.moulberry.notenoughupdates.events.RepositoryReloadEvent
import io.github.moulberry.notenoughupdates.profileviewer.GuiProfileViewer
import net.minecraft.client.Minecraft
import net.minecraft.init.Items
import net.minecraft.item.ItemStack
import net.minecraftforge.event.entity.player.ItemTooltipEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import java.io.File
import kotlin.math.roundToLong

object EstimatedItemValue {
    private val config get() = SkyHanniMod.feature.misc.estimatedItemValues
    private var display = emptyList<List<Any>>()
    private val cache = mutableMapOf<ItemStack, List<List<Any>>>()
    private var lastToolTipTime = 0L
    var gemstoneUnlockCosts = HashMap<NEUInternalName, HashMap<String, List<String>>>()
    var currentlyShowing = false

    @SubscribeEvent
    fun onRepoReload(event: RepositoryReloadEvent) {
        val data = manager.getJsonFromFile(File(manager.repoLocation, "constants/gemstonecosts.json"))

        if (data != null)
        // item_internal_names -> gemstone_slots -> ingredients_array
            gemstoneUnlockCosts =
                ConfigManager.gson.fromJson(
                    data,
                    object : TypeToken<HashMap<NEUInternalName, HashMap<String, List<String>>>>() {}.type
                )
        else
            LorenzUtils.error("Gemstone Slot Unlock Costs failed to load")
    }

    @SubscribeEvent
    fun onTooltip(event: ItemTooltipEvent) {
        if (!LorenzUtils.inSkyBlock) return
        if (!config.enabled) return

        if (Minecraft.getMinecraft().currentScreen is GuiProfileViewer) {
            updateItem(event.itemStack)
            if (!blockNextFrame && renderedItems < 2) {
                tryRendering()
            }
            renderedItems++
        }
    }

    // Workaround for NEU Profile Viewer bug where the ItemTooltipEvent gets called for two items when hovering over the border between two items.
    private var renderedItems = 0
    private var blockNextFrame = false

    @SubscribeEvent
    fun onRenderOverlayGui(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        blockNextFrame = renderedItems > 1
        renderedItems = 0
    }

    private fun tryRendering() {
        currentlyShowing = checkCurrentlyVisible()
        if (!currentlyShowing) return

        config.itemPriceDataPos.renderStringsAndItems(display, posLabel = "Estimated Item Value")
    }

    @SubscribeEvent
    fun onRenderOverlay(event: GuiRenderEvent.ChestGuiOverlayRenderEvent) {
        tryRendering()
    }

    private fun checkCurrentlyVisible(): Boolean {
        if (!LorenzUtils.inSkyBlock) return false
        if (!config.enabled) return false
        if (!config.hotkey.isKeyHeld() && !config.alwaysEnabled) return false
        if (System.currentTimeMillis() > lastToolTipTime + 200) return false

        if (display.isEmpty()) return false

        return true
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        cache.clear()
    }

    @SubscribeEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        config.enchantmentsCap.onToggle {
            cache.clear()
        }
    }

    @SubscribeEvent
    fun onRenderItemTooltip(event: RenderItemTooltipEvent) {
        if (!LorenzUtils.inSkyBlock) return
        if (!config.enabled) return

        updateItem(event.stack)
    }

    private fun updateItem(item: ItemStack) {
        val oldData = cache[item]
        if (oldData != null) {
            display = oldData
            lastToolTipTime = System.currentTimeMillis()
            return
        }

        val newDisplay = try {
            draw(item)
        } catch (e: Exception) {
            LorenzUtils.debug("Estimated Item Value error: ${e.message}")
            e.printStackTrace()
            listOf()
        }

        cache[item] = newDisplay
        display = newDisplay
        lastToolTipTime = System.currentTimeMillis()
    }

    private fun draw(stack: ItemStack): List<List<Any>> {
        val internalName = stack.getInternalNameOrNull() ?: return listOf()

        // FIX neu item list
        if (internalName.startsWith("ULTIMATE_ULTIMATE_")) return listOf()
        // We don't need this feature to work on books at all
        if (stack.item == Items.enchanted_book) return listOf()
        // Block catacombs items in mort inventory
        if (internalName.startsWith("CATACOMBS_PASS_") || internalName.startsWith("MASTER_CATACOMBS_PASS_")) return listOf()
        // Blocks the dungeon map
        if (internalName.startsWith("MAP-")) return listOf()
        // Hides the rune item
        if (internalName.contains("_RUNE;")) return listOf()
        if (internalName.contains("UNIQUE_RUNE")) return listOf()


        if (internalName.getItemStackOrNull() == null) {
            LorenzUtils.debug("Estimated Item Value is null for: '$internalName'")
            return listOf()
        }

        val list = mutableListOf<String>()
        list.add("§aEstimated Item Value:")
        val pair = EstimatedItemValueCalculator.calculate(stack, list)
        val (totalPrice, basePrice) = pair

        if (basePrice == totalPrice) return listOf()

        val numberFormat = if (config.exactPrice) {
            totalPrice.roundToLong().addSeparators()
        } else {
            NumberUtil.format(totalPrice)
        }
        list.add("§aTotal: §6§l$numberFormat")

        val newDisplay = mutableListOf<List<Any>>()
        for (line in list) {
            newDisplay.addAsSingletonList(line)
        }
        return newDisplay
    }

    @SubscribeEvent
    fun onConfigFix(event: ConfigUpdaterMigrator.ConfigFixEvent) {
        event.move(3, "misc.estimatedIemValueEnabled", "misc.estimatedItemValues.enabled")
        event.move(3, "misc.estimatedItemValueHotkey", "misc.estimatedItemValues.hotkey")
        event.move(3, "misc.estimatedIemValueAlwaysEnabled", "misc.estimatedItemValues.alwaysEnabled")
        event.move(3, "misc.estimatedIemValueEnchantmentsCap", "misc.estimatedItemValues.enchantmentsCap")
        event.move(3, "misc.estimatedIemValueExactPrice", "misc.estimatedItemValues.exactPrice")
        event.move(3, "misc.itemPriceDataPos", "misc.estimatedItemValues.itemPriceDataPos")
    }
}
