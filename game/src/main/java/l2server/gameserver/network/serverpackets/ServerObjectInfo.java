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

import l2server.gameserver.model.actor.Creature;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.StatueInstance;

/**
 * @author devScarlet & mrTJO
 */
public final class ServerObjectInfo extends L2GameServerPacket {
	private Npc activeChar;
	private int x, y, z, heading;
	private int idTemplate;
	private boolean isAttackable;
	private double collisionHeight, collisionRadius;
	private String name;
	private int type;
	
	public ServerObjectInfo(Npc activeChar, Creature actor) {
		this.activeChar = activeChar;
		idTemplate = activeChar.getTemplate().TemplateId + 1000000;
		isAttackable = activeChar.isAutoAttackable(actor);
		collisionHeight = activeChar.getCollisionHeight();
		collisionRadius = activeChar.getCollisionRadius();
		x = activeChar.getX();
		y = activeChar.getY();
		z = activeChar.getZ();
		heading = activeChar.getHeading();
		name = activeChar.getTemplate().ServerSideName ? activeChar.getTemplate().Name : "";
		type = 4;
		
		if (activeChar instanceof StatueInstance) {
			idTemplate = 0;
			name = activeChar.getName();
			isAttackable = false;
			collisionHeight = 30;
			collisionRadius = 40;
			type = 7;
		}
	}
	
	/**
	 * @see l2server.gameserver.network.serverpackets.L2GameServerPacket#writeImpl()
	 */
	@Override
	protected final void writeImpl() {
		writeD(activeChar.getObjectId());
		writeD(idTemplate);
		writeS(name); // name
		writeD(isAttackable ? 1 : 0);
		writeD(x);
		writeD(y);
		writeD(z);
		writeD(heading);
		writeF(1.0); // movement multiplier
		writeF(1.0); // attack speed multiplier
		writeF(collisionRadius);
		writeF(collisionHeight);
		writeD((int) (isAttackable ? activeChar.getCurrentHp() : 0));
		writeD(isAttackable ? activeChar.getMaxVisibleHp() : 0);
		writeD(type); // object type
		writeD(0x00); // special effects
		
		if (type == 7) {
			StatueInstance statue = (StatueInstance) activeChar;
			writeD(statue.getRecordId());
			writeD(0x00); // ???
			writeD(statue.getSocialId());
			writeD(statue.getSocialFrame());
			writeD(statue.getClassId());
			writeD(statue.getRace());
			writeD(statue.getSex());
			writeD(statue.getHairStyle());
			writeD(statue.getHairColor());
			writeD(statue.getFace());
			writeD(statue.getNecklace());
			writeD(statue.getHead());
			writeD(statue.getRHand());
			writeD(statue.getLHand());
			writeD(statue.getGloves());
			writeD(statue.getChest());
			writeD(statue.getPants());
			writeD(statue.getBoots());
			writeD(statue.getCloak());
			writeD(statue.getHair1());
			writeD(statue.getHair2());
		}
	}
}
