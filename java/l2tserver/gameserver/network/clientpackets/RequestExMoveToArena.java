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
package l2tserver.gameserver.network.clientpackets;

import l2tserver.gameserver.model.actor.instance.L2PcInstance;
import l2tserver.gameserver.network.serverpackets.ExOlympiadInfoList;

/**
 * @author -Wooden-
 *
 * D0 0F 00 5A 00 77 00 65 00 72 00 67 00 00 00
 *
 */
public final class RequestExMoveToArena extends L2GameClientPacket
{
	//
	private static final String _C__D0_88_REQUESTEXMOVETOARENA = "[C] D0:88 RequestExMoveToArena";
	
	@Override
	protected void readImpl()
	{
	}
	
	/* (non-Javadoc)
	 * @see l2tserver.gameserver.clientpackets.ClientBasePacket#runImpl()
	 */
	@Override
	protected void runImpl()
	{
		L2PcInstance activeChar = getClient().getActiveChar();
		
		if (activeChar != null)
			activeChar.sendPacket(new ExOlympiadInfoList());
	}
	
	/* (non-Javadoc)
	 * @see l2tserver.gameserver.BasePacket#getType()
	 */
	@Override
	public String getType()
	{
		return _C__D0_88_REQUESTEXMOVETOARENA;
	}
	
}