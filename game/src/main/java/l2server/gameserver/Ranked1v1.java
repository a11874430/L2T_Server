package l2server.gameserver;

import l2server.DatabasePool;
import l2server.gameserver.communitybbs.Manager.CustomCommunityBoard;
import l2server.gameserver.events.TopRanked;
import l2server.gameserver.model.World;
import l2server.gameserver.model.actor.Npc;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.util.NpcUtil;
import l2server.util.Rnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.concurrent.ScheduledFuture;

/**
 * @author Inia
 * Ajouter : - Une meilleure gestion de restrictions (DONE)
 * -> Verifier la nouvelle restriction
 */
public class Ranked1v1 {
	private static Logger log = LoggerFactory.getLogger(Ranked1v1.class.getName());

	public static enum State {
		INACTIVE,
		REGISTER,
		LOADING,
		FIGHT
	}

	public static State state = State.INACTIVE;
	public static final int timeEach = 2;
	public static Vector<Player> players = new Vector<>();
	public static Map<Player, Long> fighters = new HashMap<Player, Long>();
	public static Vector<Integer> fight = new Vector<>();
	public ScheduledFuture<?> t;

	public void checkRegistered() {
		if (players.size() < 2) {
			return;
		}
		state = State.REGISTER;

		int rnd1 = Rnd.get(players.size());
		int rnd2 = Rnd.get(players.size());

		while (rnd2 == rnd1) {
			rnd2 = Rnd.get(players.size());
		}

		fighters.put(players.get(rnd1), System.currentTimeMillis());
		fighters.put(players.get(rnd2), System.currentTimeMillis());

		for (Map.Entry<Player, Long> sendMessage : fighters.entrySet()) {
			if (sendMessage == null) {
				continue;
			}
			sendMessage.getKey().sendMessage("[RANKED] Match found! You will be teleported in 30 seconds!");
			players.remove(sendMessage.getKey());
		}

		ThreadPoolManager.getInstance().scheduleGeneral(new Teleport(), 30000);
	}

	public void teleportFighters() {
		if (fighters.size() < 2) {
			for (Map.Entry<Player, Long> sendMessage : fighters.entrySet()) {
				if (sendMessage == null) {
					continue;
				}
				sendMessage.getKey().sendMessage("Your opponent left!");
			}
			state = State.INACTIVE;
		}

		state = State.FIGHT;

		Npc bufferOne = NpcUtil.addSpawn(40002, -88900 + 50, -252849, -3330, 0, false, 15000, false, 0);
		Npc bufferTwo = NpcUtil.addSpawn(40002, -87322 + 50, -252849, -3332, 0, false, 15000, false, 0);
		bufferOne.spawnMe();
		bufferTwo.spawnMe();

		int i = 1;

		for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
			if (fighter == null) {
				continue;
			}
			if (i % 2 == 1) {
				fighter.getKey().teleToLocation(-88900, -252849, -3330);
				fighter.getKey().setTeam(1);
			} else {
				fighter.getKey().teleToLocation(-87322, -252849, -3332);
				fighter.getKey().setTeam(2);
			}

			fighter.getKey().setParalyzed(true);
			fighter.getKey().sendMessage("Fight will start in 15 seconds.");
			fighter.getKey().setPvpFlag(0);
			fighter.getKey().heal();
			i++;
		}

		ThreadPoolManager.getInstance().scheduleGeneral(new fight(), 15000);
	}

	protected void checkItem() {
		if (state != State.FIGHT) {
			return;
		}

		for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
			if (fighter == null) {
				continue;
			}
			fighter.getKey().setPvpFlag(1);
			fighter.getKey().checkItemRestriction();
			fighter.getKey().broadcastUserInfo();
		}
		return;
	}

	public Long getTime(Player player) {
		if (!fighters.containsKey(player)) {
			return null;
		}
		Long Seconds = (System.currentTimeMillis() - fighters.get(player)) / 1000;
		return Seconds;
	}

	public void onKill2v2(Player killer, Player killed) {

		int killerCurrentPoints;
		int killedCurrentPoints;

		killer.setPvpFlag(0);
		killed.setPvpFlag(0);

		killerCurrentPoints = getRankedPoints(killer);
		killedCurrentPoints = getRankedPoints(killed);

		int totalPoints = ((killedCurrentPoints + 2) / (killerCurrentPoints + 3)) + 2;
		if (totalPoints > 15) {
			totalPoints = 15 + (int) Math.pow(totalPoints - 15, 0.55);
		}

		int amount = totalPoints;
		if (amount > 8) {
			amount = 8 + (int) Math.pow(amount - 8, 0.55);
		}

		setRankedPoints(killer, getRankedPoints(killer) + totalPoints);
		setRankedPoints(killed, getRankedPoints(killed) - ((totalPoints / 2) + 1));
		if (getRankedPoints(killed) < 0) {
			setRankedPoints(killed, 0);
		}

		killer.addItem("", 5899, amount, killer, true);

		killer.sendMessage("Duration : " + getTime(killer));
		killed.sendMessage("Duration : " + getTime(killed));

		killer.sendMessage("You won " + totalPoints + " points!");
		killer.sendMessage("Current points: " + getRankedPoints(killer));

		killed.sendMessage("You lost " + ((totalPoints / 2) - 1) + " points");
		killed.sendMessage("Current points: " + getRankedPoints(killed));

		for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
			if (fighter == null) {
				continue;
			}
			fighter.getKey().setPvpFlag(0);
			if (fighter.getKey().isDead()) {
				fighter.getKey().doRevive();
			}
			fighter.getKey().heal();
			fighter.getKey().teleToLocation(-114435, 253417, -1546);
			fighter.getKey().setTeam(0);
		}

		t.cancel(true);
		killed.doRevive();
		killed.heal();

		fighters.clear();
		state = State.INACTIVE;
	}

	public void runFight() {
		if (fighters.size() < 2) {
			for (Map.Entry<Player, Long> sendMessage : fighters.entrySet()) {
				if (sendMessage == null) {
					continue;
				}
				sendMessage.getKey().sendMessage("Your enemy left.");
			}
			state = State.INACTIVE;
		}

		state = State.FIGHT;

		for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
			if (fighter == null) {
				continue;
			}
			fighter.getKey().setPvpFlag(1);
			fighter.getKey().setParalyzed(false);
			fighter.getKey().sendMessage("[RANKED] Fight!");
			fighter.setValue(System.currentTimeMillis());
		}

		t = ThreadPoolManager.getInstance().scheduleGeneral(new checkLast(), 60000 * 3);

		return;
	}

	public void lastcheck() {
		if (state != State.FIGHT) {
			return;
		}

		int alive = 0;

		for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
			if (fighter == null) {
				continue;
			}
			if (!fighter.getKey().isDead()) {
				alive++;
			}
		}

		if (alive == 2) {
			for (Map.Entry<Player, Long> fighter : fighters.entrySet()) {
				if (fighter == null) {
					continue;
				}
				fighter.getKey().setPvpFlag(0);
				fighter.getKey().heal();
				fighter.getKey().setTeam(0);
				fighter.getKey().teleToLocation(-114435, 253417, -1546);
				fighter.getKey().sendMessage("[RANKED] Tie!");
			}
			fighters.clear();
			state = State.INACTIVE;
		}

		return;
	}

	public void register(Player player) {
		if (player == null) {
			return;
		}
		if (players.contains(player)) {
			player.sendMessage("You're already in the queue.");
			return;
		}
		players.add(player);
		player.sendMessage("You joined the queue !");
		CustomCommunityBoard.getInstance().parseCmd("_bbscustom;ranked;0;0", player);
		return;
	}

	public void unregister(Player player) {
		if (player == null) {
			return;
		}
		if (!players.contains(player)) {
			player.sendMessage("You're not in the queue.");
			return;
		}
		player.sendMessage("You left the queue!");
		players.remove(player);
		CustomCommunityBoard.getInstance().parseCmd("_bbscustom;ranked;0;0", player);
		return;
	}

	public int getRankedPoints(Player player) {
		Connection get = null;

		try {
			get = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = get.prepareStatement("SELECT rankedPoints FROM characters WHERE charId = ?");
			statement.setInt(1, player.getObjectId());
			ResultSet rset = statement.executeQuery();

			if (rset.next()) {
				int currentPoints = rset.getInt("rankedPoints");
				return (currentPoints);
			}
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Couldn't get current ranked points : " + e.getMessage(), e);
		} finally {
			DatabasePool.close(get);
		}
		return 0;
	}

	public void setRankedPoints(Player player, int amount) {
		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();

			PreparedStatement statement = con.prepareStatement("UPDATE characters SET rankedPoints=? WHERE charId=?");
			statement.setInt(1, amount);
			statement.setInt(2, player.getObjectId());

			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Failed updating Ranked Points", e);
		} finally {
			DatabasePool.close(con);
		}
	}

	public void clear(Player player) {

		if (!player.isGM()) {
			return;
		}

		for (Player register : players) {
			if (player == null) {
				continue;
			}
			register.sendMessage("You've been forced unregistered by a GM");
		}

		players.clear();

		player.sendMessage("Queue cleared!");
		CustomCommunityBoard.getInstance().parseCmd("_bbscustom;ranked;0;0", player);
		return;
	}

	public void reduce(int id) {
		Connection get = null;
		int currentPoints = 10;

		try {
			get = DatabasePool.getInstance().getConnection();
			PreparedStatement statement = get.prepareStatement("SELECT rankedPoints FROM characters WHERE charId = ?");
			statement.setInt(1, id);
			ResultSet rset = statement.executeQuery();

			if (rset.next()) {
				currentPoints = rset.getInt("rankedPoints");
			}
			rset.close();
			statement.close();
		} catch (Exception e) {
			log.warn("Couldn't get current ranked points : " + e.getMessage(), e);
		} finally {
			DatabasePool.close(get);
		}

		Connection con = null;
		try {
			con = DatabasePool.getInstance().getConnection();

			PreparedStatement statement = con.prepareStatement("UPDATE characters SET rankedPoints=? WHERE charId=?");
			statement.setInt(1, currentPoints - 10);
			statement.setInt(2, id);

			statement.execute();
			statement.close();
		} catch (Exception e) {
			log.error("Failed updating Ranked Points", e);
		} finally {
			DatabasePool.close(con);
		}
	}

	public void doAll(Player player) {
		player.sendMessage("Adding everyone !");
		for (Map.Entry<Integer, Player> fighter : World.getInstance().getAllPlayers().entrySet()) {
			if (fighter.getValue() == null || fighter.getValue().isInStoreMode() || fighter.getValue().isInCraftMode()) {
				continue;
			}
			Ranked1v1.getInstance().register(fighter.getValue());
		}
		return;
	}

	public void handleEventCommand(Player player, String command) {
		if (player == null) {
			return;
		}

		StringTokenizer st = new StringTokenizer(command, ";");
		st.nextToken();
		st.nextToken();
		st.nextToken();

		switch (String.valueOf(st.nextToken())) {
			case "register":
				register(player);
				break;
			case "unregister":
				unregister(player);
				break;
			case "clear":
				clear(player);
				break;
			case "doall":
				doAll(player);
				break;
			case "reward":
				TopRanked.getInstance().test();
				break;
			case "reduce": {
				int playerId = Integer.valueOf(st.nextToken());
				reduce(playerId);
				break;
			}
		}

		return;
	}

	protected Ranked1v1() {
		ThreadPoolManager.getInstance().scheduleGeneralAtFixedRate(new Event(), 60000 * timeEach, 62000 * timeEach);
	}

	public static Ranked1v1 getInstance() {
		return SingletonHolder.instance;
	}

	private static class SingletonHolder {
		protected static final Ranked1v1 instance = new Ranked1v1();
	}

	protected class Event implements Runnable {
		@Override
		public void run() {
			if (state != State.FIGHT) {
				checkRegistered();
			}
		}
	}

	protected class Item implements Runnable {
		@Override
		public void run() {
			checkItem();
		}
	}

	protected class fight implements Runnable {
		@Override
		public void run() {

			runFight();
		}
	}

	protected class checkLast implements Runnable {
		@Override
		public void run() {

			lastcheck();
		}
	}

	protected class Teleport implements Runnable {
		@Override
		public void run() {

			teleportFighters();
		}
	}
}
