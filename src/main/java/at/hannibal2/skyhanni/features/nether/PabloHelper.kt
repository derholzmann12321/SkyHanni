package at.hannibal2.skyhanni.features.nether

import at.hannibal2.skyhanni.SkyHanniMod
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.LorenzChatEvent
import at.hannibal2.skyhanni.utils.InventoryUtils
import at.hannibal2.skyhanni.utils.ItemUtils.name
import at.hannibal2.skyhanni.utils.LorenzUtils
import at.hannibal2.skyhanni.utils.LorenzUtils.isInIsland
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.StringUtils.matchMatcher
import at.hannibal2.skyhanni.utils.StringUtils.removeColor
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import kotlin.time.Duration.Companion.minutes

// https://wiki.hypixel.net/Pablo
class PabloHelper {
    private val config get() = SkyHanniMod.feature.crimsonIsle

    // There is a different message if the player asks Pablo with an item in their hand, but I don't think it's necessary
    // I'll add it if requested ~NetheriteMiner
    private val pabloMessagePattern = "\\[NPC] Pablo: Could you bring me an (?<flower>[\\w ]+).*".toPattern()
    private var lastSentMessage = SimpleTimeMark.farPast()

    @SubscribeEvent
    fun onChat(event: LorenzChatEvent) {
        if (!isEnabled()) return
        if (lastSentMessage.passedSince() < 5.minutes) return
        val itemName = pabloMessagePattern.matchMatcher(event.message.removeColor()) {
            group("flower")
        } ?: return

        if (InventoryUtils.countItemsInLowerInventory { it.name?.contains(itemName) == true } > 0) return

        LorenzUtils.clickableChat("§e[SkyHanni] Click here to grab an $itemName from sacks!", "gfs $itemName 1")
        lastSentMessage = SimpleTimeMark.now()
    }

    fun isEnabled() = IslandType.CRIMSON_ISLE.isInIsland() && config.pabloHelper
}
