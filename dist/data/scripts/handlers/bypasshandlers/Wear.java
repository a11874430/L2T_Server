/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package handlers.bypasshandlers;

import l2server.Config;
import l2server.gameserver.TradeController;
import l2server.gameserver.handler.IBypassHandler;
import l2server.gameserver.model.L2TradeList;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.network.serverpackets.ActionFailed;
import l2server.gameserver.network.serverpackets.ShopPreviewList;

import java.util.StringTokenizer;

public class Wear implements IBypassHandler {
	private static final String[] COMMANDS = {"Wear"};

	@Override
	public boolean useBypass(String command, Player activeChar, Npc target) {
		if (target == null) {
			return false;
		}

		if (!Config.ALLOW_WEAR) {
			return false;
		}

		try {
			StringTokenizer st = new StringTokenizer(command, " ");
			st.nextToken();

			if (st.countTokens() < 1) {
				return false;
			}

			showWearWindow(activeChar, Integer.parseInt(st.nextToken()));
			return true;
		} catch (Exception e) {
			log.info("Exception in " + getClass().getSimpleName());
		}
		return false;
	}

	private static void showWearWindow(Player player, int val) {
		player.tempInventoryDisable();

		if (Config.DEBUG) {
			log.debug("Showing wearlist");
		}

		L2TradeList list = TradeController.INSTANCE.getBuyList(val);

		if (list != null) {
			ShopPreviewList bl = new ShopPreviewList(list, player.getAdena(), player.getExpertiseIndex());
			player.sendPacket(bl);
		} else {
			log.warn("no buylist with id:" + val);
			player.sendPacket(ActionFailed.STATIC_PACKET);
		}
	}

	@Override
	public String[] getBypassList() {
		return COMMANDS;
	}
}
