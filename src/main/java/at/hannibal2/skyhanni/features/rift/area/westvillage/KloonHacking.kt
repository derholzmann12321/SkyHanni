package at.hannibal2.skyhanni.features.rift.area.westvillage

import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.GuiContainerEvent
import at.hannibal2.skyhanni.events.InventoryCloseEvent
import at.hannibal2.skyhanni.events.InventoryFullyOpenedEvent
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.events.LorenzRenderWorldEvent
import at.hannibal2.skyhanni.events.LorenzTickEvent
import at.hannibal2.skyhanni.events.LorenzToolTipEvent
import at.hannibal2.skyhanni.features.rift.RiftAPI
import at.hannibal2.skyhanni.test.GriffinUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.getInternalName
import at.hannibal2.skyhanni.utils.ItemUtils.getLore
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzColor
import at.hannibal2.skyhanni.utils.RenderUtils.highlight
import at.hannibal2.skyhanni.utils.StringUtils.matchMatcher
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import io.github.moulberry.notenoughupdates.events.SlotClickEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

class KloonHacking {
    private val config get() = RiftAPI.config.area.westVillageConfig.hacking

    // TODO USE SH-REPO
    val pattern = "You've set the color of this terminal to (?<colour>.*)!".toPattern()

    private var wearingHelmet = false
    private var inTerminalInventory = false
    private var inColourInventory = false
    private val correctButtons = mutableListOf<String>()
    private var nearestTerminal: KloonTerminal? = null

    @SubscribeEvent
    fun onTick(event: LorenzTickEvent) {
        if (!RiftAPI.inRift()) return
        if (event.repeatSeconds(1)) {
            checkHelmet()
        }
    }

    private fun checkHelmet() {
        wearingHelmet = InventoryUtils.getArmor()[3]?.getInternalName()?.equals("RETRO_ENCABULATING_VISOR") ?: false
    }

    @SubscribeEvent
    fun onInventoryOpen(event: InventoryFullyOpenedEvent) {
        inTerminalInventory = false
        inColourInventory = false
        nearestTerminal = null
        if (!RiftAPI.inRift()) return
        if (!config.solver) return
        if (event.inventoryName == "Hacking" || event.inventoryName == "Hacking (As seen on CSI)") {
            inTerminalInventory = true
            correctButtons.clear()
            for ((slot, stack) in event.inventoryItems) {
                if (slot in 2..6) {
                    correctButtons.add(stack.displayName.removeColor())
                }
            }
        }
        if (event.inventoryName == "Hacked Terminal Color Picker") {
            inColourInventory = true
        }
    }

    @SubscribeEvent
    fun onInventoryClose(event: InventoryCloseEvent) {
        inTerminalInventory = false
        inColourInventory = false
    }

    @SubscribeEvent
    fun onBackgroundDrawn(event: GuiContainerEvent.BackgroundDrawnEvent) {
        if (!RiftAPI.inRift()) return
        if (inTerminalInventory) {
            if (!config.solver) return
            var i = 0
            for (slot in InventoryUtils.getItemsInOpenChest()) {
                if (slot.slotIndex == 11 + 10 * i) {
                    val correctButton = slot.stack!!.displayName.removeColor() == correctButtons[i]
                    slot highlight if (correctButton) LorenzColor.GREEN else LorenzColor.RED
                    continue
                }
                if (slot.slotIndex > i * 9 + 8 && slot.slotIndex < i * 9 + 18 && slot.stack!!.displayName.removeColor() == correctButtons[i]) {
                    slot highlight LorenzColor.YELLOW
                }
                if (slot.slotIndex == i * 9 + 17) {
                    i += 1
                }
            }
        }
        if (inColourInventory) {
            if (!config.colour) return
            val targetColour = nearestTerminal ?: getNearestTerminal()
            for (slot in InventoryUtils.getItemsInOpenChest()) {
                if (slot.stack.getLore().any { it.contains(targetColour?.name ?: "") }) {
                    slot highlight LorenzColor.GREEN
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    fun onSlotClick(event: SlotClickEvent) {
        if (!inTerminalInventory || !RiftAPI.inRift()) return
        event.usePickblockInstead()
    }

    @SubscribeEvent
    fun onRenderWorld(event: LorenzRenderWorldEvent) {
        if (!RiftAPI.inRift()) return
        if (!config.waypoints) return
        if (!wearingHelmet) return
        val storage = ProfileStorageData.profileSpecific?.rift ?: return
        for (terminal in KloonTerminal.entries) {
            if (terminal !in storage.completedKloonTerminals) {
                event.drawWaypointFilled(terminal.location, LorenzColor.DARK_RED.toColor(), true, true)
            }
        }
    }

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!RiftAPI.inRift()) return
        if (!wearingHelmet) return
        pattern.matchMatcher(event.message.removeColor()) {
            val storage = ProfileStorageData.profileSpecific?.rift ?: return
            val colour = group("colour")
            val completedTerminal = KloonTerminal.entries.firstOrNull { it.name == colour } ?: return
            if (completedTerminal != nearestTerminal) return
            storage.completedKloonTerminals.add(completedTerminal)
        }
    }

    @SubscribeEvent
    fun onTooltip(event: LorenzToolTipEvent) {
        if (!RiftAPI.inRift()) return
        if (!inTerminalInventory) return
        if (!config.solver) return

        val neededTooltips = listOf(0, 2, 3, 4, 5, 6, 8, 9, 26, 27, 44, 45)
        if (event.slot.slotIndex !in neededTooltips) {
            event.toolTip.clear()
        }
    }

    private fun getNearestTerminal(): KloonTerminal? {
        var closestTerminal: KloonTerminal? = null
        var closestDistance = 8.0

        for (terminal in KloonTerminal.entries) {
            val distance = terminal.location.distanceToPlayer()
            if (distance < closestDistance) {
                closestTerminal = terminal
                closestDistance = distance
            }
        }
        nearestTerminal = closestTerminal
        return closestTerminal
    }
}
