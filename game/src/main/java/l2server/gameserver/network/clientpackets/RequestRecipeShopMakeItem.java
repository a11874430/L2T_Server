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

package l2server.gameserver.network.clientpackets;

import l2server.gameserver.RecipeController;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.instance.Player;

/**
 * @author Administrator
 */
public final class RequestRecipeShopMakeItem extends L2GameClientPacket {
	//
	
	private int id;
	private int recipeId;
	@SuppressWarnings("unused")
	private long unknow;
	
	@Override
	protected void readImpl() {
		id = readD();
		recipeId = readD();
		unknow = readQ();
	}
	
	@Override
	protected void runImpl() {
		Player activeChar = getClient().getActiveChar();
		if (activeChar == null) {
			return;
		}
		
		if (!getClient().getFloodProtectors().getManufacture().tryPerformAction("RecipeShopMake")) {
			return;
		}
		
		Player manufacturer = World.getInstance().getPlayer(id);
		if (manufacturer == null) {
			return;
		}
		
		manufacturer.hasBeenStoreActive();
		
		if (manufacturer.getInstanceId() != activeChar.getInstanceId() && activeChar.getInstanceId() != -1) {
			return;
		}
		
		if (activeChar.getPrivateStoreType() != 0) {
			activeChar.sendMessage("Cannot make items while trading");
			return;
		}
		if (manufacturer.getPrivateStoreType() != 5) {
			//activeChar.sendMessage("Cannot make items while trading");
			return;
		}
		
		if (activeChar.isInCraftMode() || manufacturer.isInCraftMode()) {
			activeChar.sendMessage("Currently in Craft Mode");
			return;
		}
		RecipeController.getInstance().requestManufactureItem(manufacturer, recipeId, activeChar);
	}
}
