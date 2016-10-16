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

package handlers.actionhandlers;

import java.util.Map.Entry;

import l2server.Config;
import l2server.gameserver.datatables.ItemTable;
import l2server.gameserver.handler.IActionHandler;
import l2server.gameserver.model.Elementals;
import l2server.gameserver.model.L2DropCategory;
import l2server.gameserver.model.L2DropData;
import l2server.gameserver.model.L2Object;
import l2server.gameserver.model.L2Object.InstanceType;
import l2server.gameserver.model.actor.L2Attackable;
import l2server.gameserver.model.actor.L2Attackable.AggroInfo;
import l2server.gameserver.model.actor.L2Character;
import l2server.gameserver.model.actor.L2Npc;
import l2server.gameserver.model.actor.instance.L2MerchantInstance;
import l2server.gameserver.model.actor.instance.L2PcInstance;
import l2server.gameserver.network.serverpackets.AbnormalStatusUpdateFromTarget;
import l2server.gameserver.network.serverpackets.MyTargetSelected;
import l2server.gameserver.network.serverpackets.NpcHtmlMessage;
import l2server.gameserver.network.serverpackets.StatusUpdate;
import l2server.gameserver.stats.BaseStats;
import l2server.gameserver.stats.Stats;
import l2server.gameserver.templates.item.L2Item;
import l2server.util.StringUtil;

public class L2NpcActionShift implements IActionHandler
{
	/**
	 * Manage and Display the GM console to modify the L2NpcInstance (GM only).<BR><BR>
	 * <p>
	 * <B><U> Actions (If the L2PcInstance is a GM only)</U> :</B><BR><BR>
	 * <li>Set the L2NpcInstance as target of the L2PcInstance player (if necessary)</li>
	 * <li>Send a Server->Client packet MyTargetSelected to the L2PcInstance player (display the select window)</li>
	 * <li>If L2NpcInstance is autoAttackable, send a Server->Client packet StatusUpdate to the L2PcInstance in order to update L2NpcInstance HP bar </li>
	 * <li>Send a Server->Client NpcHtmlMessage() containing the GM console about this L2NpcInstance </li><BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : Each group of Server->Client packet must be terminated by a ActionFailed packet in order to avoid
	 * that client wait an other packet</B></FONT><BR><BR>
	 * <p>
	 * <B><U> Example of use </U> :</B><BR><BR>
	 * <li> Client packet : Action</li><BR><BR>
	 */
	@Override
	public boolean action(L2PcInstance activeChar, L2Object target, boolean interact)
	{
		activeChar.setTarget(target);

		// Check if the L2PcInstance is a GM
		if (activeChar.getAccessLevel().isGm())
		{
			// Set the target of the L2PcInstance activeChar
			activeChar.setTarget(target);

			// Send a Server->Client packet MyTargetSelected to the L2PcInstance activeChar
			// The activeChar.getLevel() - getLevel() permit to display the correct color in the select window
			MyTargetSelected my = new MyTargetSelected(target.getObjectId(),
					activeChar.getLevel() - ((L2Character) target).getLevel());
			activeChar.sendPacket(my);
			activeChar.sendPacket(new AbnormalStatusUpdateFromTarget((L2Character) target));

			// Check if the activeChar is attackable (without a forced attack)
			if (target.isAutoAttackable(activeChar))
			{
				// Send a Server->Client packet StatusUpdate of the L2NpcInstance to the L2PcInstance to update its HP bar
				StatusUpdate su = new StatusUpdate(target);
				su.addAttribute(StatusUpdate.CUR_HP, (int) ((L2Character) target).getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, ((L2Character) target).getMaxHp());
				activeChar.sendPacket(su);
			}

			L2Npc targetNpc = (L2Npc) target;
			L2Attackable attTarget = targetNpc instanceof L2Attackable ? (L2Attackable) targetNpc : null;

			NpcHtmlMessage html = new NpcHtmlMessage(0);
			html.setFile(activeChar.getHtmlPrefix(), "admin/npcinfo.htm");

			//System.out.println(targetNpc.getPDef(null));
			//System.out.println(targetNpc.getTemplate().basePDef);
			//System.out.println(targetNpc.getKnownList().getDistanceToWatchObject(targetNpc));

			html.replace("%objid%", String.valueOf(target.getObjectId()));
			html.replace("%class%", target.getClass().getSimpleName());
			html.replace("%id%", String.valueOf(targetNpc.getTemplate().NpcId));
			html.replace("%lvl%", String.valueOf(targetNpc.getTemplate().Level));
			html.replace("%name%", String.valueOf(targetNpc.getTemplate().Name));
			html.replace("%tmplid%", String.valueOf(targetNpc.getTemplate().NpcId));
			html.replace("%aggro%", String.valueOf(attTarget != null ? attTarget.getAggroRange() : 0));
			html.replace("%hp%", String.valueOf((int) ((L2Character) target).getCurrentHp()));
			html.replace("%hpmax%", String.valueOf(((L2Character) target).getMaxHp()));
			html.replace("%mp%", String.valueOf((int) ((L2Character) target).getCurrentMp()));
			html.replace("%mpmax%", String.valueOf(((L2Character) target).getMaxMp()));

			String aggroInfo = "";
			if (attTarget != null)
			{
				aggroInfo += "<table width=100%>";
				for (Entry<L2Character, AggroInfo> i : attTarget.getAggroList().entrySet())
				{
					aggroInfo += "<tr><td>" + i.getKey().getName() + "</td><td>Aggro: " + i.getValue().getDamage() +
							"</td><td>Hate: " + i.getValue().getHate() + "</td></tr>";
				}
				aggroInfo += "</table>";
			}
			html.replace("%aggroInfo%", aggroInfo);

			html.replace("%patk%", String.valueOf(((L2Character) target).getPAtk(null)));
			html.replace("%matk%", String.valueOf(((L2Character) target).getMAtk(null, null)));
			html.replace("%pdef%", String.valueOf(((L2Character) target).getPDef(null)));
			html.replace("%mdef%", String.valueOf(((L2Character) target).getMDef(null, null)));
			html.replace("%accu%", String.valueOf(((L2Character) target).getAccuracy()));
			html.replace("%evas%", String.valueOf(((L2Character) target).getEvasionRate(null)));
			html.replace("%crit%", String.valueOf(((L2Character) target).getCriticalHit(null, null)));
			html.replace("%rspd%", String.valueOf(((L2Character) target).getRunSpeed()));
			html.replace("%aspd%", String.valueOf(((L2Character) target).getPAtkSpd()));
			html.replace("%cspd%", String.valueOf(((L2Character) target).getMAtkSpd()));
			html.replace("%str%", String.valueOf(((L2Character) target).getSTR()));
			html.replace("%dex%", String.valueOf(((L2Character) target).getDEX()));
			html.replace("%con%", String.valueOf(((L2Character) target).getCON()));
			html.replace("%int%", String.valueOf(((L2Character) target).getINT()));
			html.replace("%wit%", String.valueOf(((L2Character) target).getWIT()));
			html.replace("%men%", String.valueOf(((L2Character) target).getMEN()));
			html.replace("%loc%", String.valueOf(target.getX() + " " + target.getY() + " " + target.getZ() + " " +
					((L2Character) target).getHeading()));
			html.replace("%dist%", String.valueOf((int) Math.sqrt(activeChar.getDistanceSq(target))));

			byte attackAttribute = ((L2Character) target).getAttackElement();
			html.replace("%ele_atk%", Elementals.getElementName(attackAttribute));
			html.replace("%ele_atk_value%",
					String.valueOf(((L2Character) target).getAttackElementValue(attackAttribute)));
			html.replace("%ele_dfire%", String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.FIRE)));
			html.replace("%ele_dwater%",
					String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.WATER)));
			html.replace("%ele_dwind%", String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.WIND)));
			html.replace("%ele_dearth%",
					String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.EARTH)));
			html.replace("%ele_dholy%", String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.HOLY)));
			html.replace("%ele_ddark%", String.valueOf(((L2Character) target).getDefenseElementValue(Elementals.DARK)));

			if (targetNpc.getSpawn() != null)
			{
				html.replace("%spawn%", targetNpc.getSpawn().getX() + " " + targetNpc.getSpawn().getY() + " " +
						targetNpc.getSpawn().getZ() + " " + targetNpc.getSpawn().getHeading());
				html.replace("%loc2d%", String.valueOf((int) Math.sqrt(((L2Character) target)
						.getPlanDistanceSq(targetNpc.getSpawn().getX(), targetNpc.getSpawn().getY()))));
				html.replace("%loc3d%", String.valueOf((int) Math.sqrt(((L2Character) target)
						.getDistanceSq(targetNpc.getSpawn().getX(), targetNpc.getSpawn().getY(),
								targetNpc.getSpawn().getZ()))));
				html.replace("%resp%", String.valueOf(targetNpc.getSpawn().getRespawnDelay() / 1000));
			}
			else
			{
				html.replace("%spawn%", "<font color=FF0000>null</font>");
				html.replace("%loc2d%", "<font color=FF0000>--</font>");
				html.replace("%loc3d%", "<font color=FF0000>--</font>");
				html.replace("%resp%", "<font color=FF0000>--</font>");
			}

			if (targetNpc.hasAI())
			{
				html.replace("%ai_intention%",
						"<tr><td><table width=270 border=0 bgcolor=131210><tr><td width=100><font color=FFAA00>Intention:</font></td><td align=right width=170>" +
								String.valueOf(targetNpc.getAI().getIntention().name()) +
								"</td></tr></table></td></tr>");
				html.replace("%ai%",
						"<tr><td><table width=270 border=0><tr><td width=100><font color=FFAA00>AI</font></td><td align=right width=170>" +
								targetNpc.getAI().getClass().getSimpleName() + "</td></tr></table></td></tr>");
				html.replace("%ai_type%",
						"<tr><td><table width=270 border=0 bgcolor=131210><tr><td width=100><font color=FFAA00>AIType</font></td><td align=right width=170>" +
								String.valueOf(targetNpc.getAiType()) + "</td></tr></table></td></tr>");
				html.replace("%ai_clan%",
						"<tr><td><table width=270 border=0><tr><td width=100><font color=FFAA00>Clan & Range:</font></td><td align=right width=170>" +
								String.valueOf(targetNpc.getTemplate().getAIData().getClan()) + " " +
								String.valueOf(targetNpc.getTemplate().getAIData().getClanRange()) +
								"</td></tr></table></td></tr>");
				html.replace("%ai_enemy_clan%",
						"<tr><td><table width=270 border=0 bgcolor=131210><tr><td width=100><font color=FFAA00>Enemy & Range:</font></td><td align=right width=170>" +
								String.valueOf(targetNpc.getTemplate().getAIData().getEnemyClan()) + " " +
								String.valueOf(targetNpc.getTemplate().getAIData().getEnemyRange()) +
								"</td></tr></table></td></tr>");
			}
			else
			{
				html.replace("%ai_intention%", "");
				html.replace("%ai%", "");
				html.replace("%ai_type%", "");
				html.replace("%ai_clan%", "");
				html.replace("%ai_enemy_clan%", "");
			}

			if (target instanceof L2MerchantInstance)
			{
				html.replace("%butt%", "<button value=\"Shop\" action=\"bypass -h admin_showShop " +
						String.valueOf(targetNpc.getTemplate().NpcId) +
						"\" width=60 height=21 back=\"L2UI_ct1.button_df\" fore=\"L2UI_ct1.button_df\">");
			}
			else
			{
				html.replace("%butt%", "");
			}

			activeChar.sendPacket(html);
		}
		else if (Config.ALT_GAME_VIEWNPC)
		{
			// Set the target of the L2PcInstance activeChar
			activeChar.setTarget(target);

			// Send a Server->Client packet MyTargetSelected to the L2PcInstance activeChar
			// The activeChar.getLevel() - getLevel() permit to display the correct color in the select window
			MyTargetSelected my = new MyTargetSelected(target.getObjectId(),
					activeChar.getLevel() - ((L2Character) target).getLevel());
			activeChar.sendPacket(my);

			// Check if the activeChar is attackable (without a forced attack)
			if (target.isAutoAttackable(activeChar))
			{
				// Send a Server->Client packet StatusUpdate of the L2NpcInstance to the L2PcInstance to update its HP bar
				StatusUpdate su = new StatusUpdate(target);
				su.addAttribute(StatusUpdate.CUR_HP, (int) ((L2Character) target).getCurrentHp());
				su.addAttribute(StatusUpdate.MAX_HP, ((L2Character) target).getMaxHp());
				activeChar.sendPacket(su);
			}

			L2Npc targetNpc = (L2Npc) target;

			NpcHtmlMessage html = new NpcHtmlMessage(0);
			int hpMul = Math.round(
					(float) (((L2Character) target).getStat().calcStat(Stats.MAX_HP, 1, (L2Character) target, null) /
							BaseStats.CON.calcBonus((L2Character) target)));
			if (hpMul == 0)
			{
				hpMul = 1;
			}
			final StringBuilder html1 = StringUtil.startAppend(1000,
					"<html><body>" + "<br><center><font color=\"LEVEL\">[Combat Stats]</font></center>" +
							"<table border=0 width=\"100%\">" + "<tr><td>Max.HP</td><td>",
					String.valueOf(((L2Character) target).getMaxHp() / hpMul), "*", String.valueOf(hpMul),
					"</td><td>Max.MP</td><td>", String.valueOf(((L2Character) target).getMaxMp()),
					"</td></tr>" + "<tr><td>P.Atk.</td><td>", String.valueOf(((L2Character) target).getPAtk(null)),
					"</td><td>M.Atk.</td><td>", String.valueOf(((L2Character) target).getMAtk(null, null)),
					"</td></tr>" + "<tr><td>P.Def.</td><td>", String.valueOf(((L2Character) target).getPDef(null)),
					"</td><td>M.Def.</td><td>", String.valueOf(((L2Character) target).getMDef(null, null)),
					"</td></tr>" + "<tr><td>Accuracy</td><td>", String.valueOf(((L2Character) target).getAccuracy()),
					"</td><td>Evasion</td><td>", String.valueOf(((L2Character) target).getEvasionRate(null)),
					"</td></tr>" + "<tr><td>Critical</td><td>",
					String.valueOf(((L2Character) target).getCriticalHit(null, null)), "</td><td>Speed</td><td>",
					String.valueOf(((L2Character) target).getRunSpeed()), "</td></tr>" + "<tr><td>Atk.Speed</td><td>",
					String.valueOf(((L2Character) target).getPAtkSpd()), "</td><td>Cast.Speed</td><td>",
					String.valueOf(((L2Character) target).getMAtkSpd()), "</td></tr>" + "<tr><td>Race</td><td>",
					targetNpc.getTemplate().getRace().toString(), "</td><td></td><td></td></tr>" + "</table>" +
							"<br><center><font color=\"LEVEL\">[Basic Stats]</font></center>" +
							"<table border=0 width=\"100%\">" + "<tr><td>STR</td><td>",
					String.valueOf(((L2Character) target).getSTR()), "</td><td>DEX</td><td>",
					String.valueOf(((L2Character) target).getDEX()), "</td><td>CON</td><td>",
					String.valueOf(((L2Character) target).getCON()), "</td></tr>" + "<tr><td>INT</td><td>",
					String.valueOf(((L2Character) target).getINT()), "</td><td>WIT</td><td>",
					String.valueOf(((L2Character) target).getWIT()), "</td><td>MEN</td><td>",
					String.valueOf(((L2Character) target).getMEN()), "</td></tr>" + "</table>");

			if (!targetNpc.getTemplate().getMultiDropData().isEmpty())
			{
				StringUtil.append(html1, "<br><center><font color=\"LEVEL\">[Drop Info]</font></center>" +
						"<br>Rates legend: <font color=\"ff0000\">50%+</font> <font color=\"00ff00\">30%+</font> <font color=\"0000ff\">less than 30%</font>" +
						"<table border=0 width=\"100%\">");
				for (L2DropData drop : targetNpc.getTemplate().getSpoilData())
				{
					final L2Item item = ItemTable.getInstance().getTemplate(drop.getItemId());
					if (item == null)
					{
						continue;
					}

					final String color;

					if (drop.getChance() >= 50)
					{
						color = "ff0000";
					}
					else if (drop.getChance() >= 30)
					{
						color = "00ff00";
					}
					else
					{
						color = "0000ff";
					}

					StringUtil.append(html1, "<tr><td><font color=\"", color, "\">", item.getName(), "</font></td><td>",
							drop.isQuestDrop() ? "Quest" : "Drop", "</td></tr>");
				}
				for (L2DropData drop : targetNpc.getTemplate().getDropData())
				{
					final L2Item item = ItemTable.getInstance().getTemplate(drop.getItemId());
					if (item == null)
					{
						continue;
					}

					final String color;

					if (drop.getChance() >= 50)
					{
						color = "ff0000";
					}
					else if (drop.getChance() >= 30)
					{
						color = "00ff00";
					}
					else
					{
						color = "0000ff";
					}

					StringUtil.append(html1, "<tr><td><font color=\"", color, "\">", item.getName(), "</font></td><td>",
							drop.isQuestDrop() ? "Quest" : "Drop", "</td></tr>");
				}
				for (L2DropCategory cat : targetNpc.getTemplate().getMultiDropData())
				{
					for (L2DropData drop : cat.getAllDrops())
					{
						final L2Item item = ItemTable.getInstance().getTemplate(drop.getItemId());
						if (item == null)
						{
							continue;
						}

						final String color;

						if (drop.getChance() >= 50)
						{
							color = "ff0000";
						}
						else if (drop.getChance() >= 30)
						{
							color = "00ff00";
						}
						else
						{
							color = "0000ff";
						}

						StringUtil.append(html1, "<tr><td><font color=\"", color, "\">", item.getName(),
								"</font></td><td>", drop.isQuestDrop() ? "Quest" : "Drop", "</td></tr>");
					}
				}
				html1.append("</table>");
			}
			html1.append("</body></html>");

			html.setHtml(html1.toString());
			activeChar.sendPacket(html);
		}
		return true;
	}

	@Override
	public InstanceType getInstanceType()
	{
		return InstanceType.L2Npc;
	}
}
