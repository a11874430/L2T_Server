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

package l2server.gameserver.network.serverpackets;

import l2server.gameserver.datatables.PlayerClassTable;

/**
 * @author Pere
 */
public final class ExCallToChangeClass extends L2GameServerPacket {
	private int awakeningId;
	private boolean show;
	
	public ExCallToChangeClass(int classId, boolean show) {
		awakeningId = PlayerClassTable.getInstance().getAwakening(classId);
		this.show = show;
	}
	
	@Override
	protected final void writeImpl() {
		writeD(awakeningId);
		writeD(show ? 1 : 0);
		writeD(0x01);
	}
}