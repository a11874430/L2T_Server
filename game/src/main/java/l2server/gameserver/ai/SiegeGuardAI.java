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

package l2server.gameserver.ai;

import l2server.Config;
import l2server.gameserver.GeoData;
import l2server.gameserver.ThreadPoolManager;
import l2server.gameserver.TimeController;
import l2server.gameserver.model.Abnormal;
import l2server.gameserver.model.Skill;
import l2server.gameserver.model.WorldObject;
import l2server.gameserver.model.actor.*;
import l2server.gameserver.model.actor.instance.DefenderInstance;
import l2server.gameserver.model.actor.instance.DoorInstance;
import l2server.gameserver.model.actor.instance.NpcInstance;
import l2server.gameserver.model.actor.instance.Player;
import l2server.gameserver.templates.skills.SkillType;
import l2server.gameserver.util.Util;
import l2server.util.Rnd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Future;

import static l2server.gameserver.ai.CtrlIntention.*;

/**
 * This class manages AI of Attackable.<BR><BR>
 */
public class SiegeGuardAI extends CreatureAI implements Runnable {
	private static Logger log = LoggerFactory.getLogger(SiegeGuardAI.class.getName());

	//
	private static final int MAX_ATTACK_TIMEOUT = 300; // int ticks, i.e. 30 seconds

	/**
	 * The Attackable AI task executed every 1s (call onEvtThink method)
	 */
	private Future<?> aiTask;

	/**
	 * For attack AI, analysis of mob and its targets
	 */
	private SelfAnalysis selfAnalysis = new SelfAnalysis();
	//private TargetAnalysis mostHatedAnalysis = new TargetAnalysis();

	/**
	 * The delay after which the attacked is stopped
	 */
	private int attackTimeout;

	/**
	 * The Attackable aggro counter
	 */
	private int globalAggro;

	/**
	 * The flag used to indicate that a thinking action is in progress
	 */
	private boolean thinking; // to prevent recursive thinking

	private int attackRange;

	/**
	 * Constructor of AttackableAI.<BR><BR>
	 *
	 */
	public SiegeGuardAI(Creature creature) {
		super(creature);
		selfAnalysis.init();
		attackTimeout = Integer.MAX_VALUE;
		globalAggro = -10; // 10 seconds timeout of ATTACK after respawn
		attackRange = actor.getPhysicalAttackRange();
	}

	@Override
	public void run() {
		// Launch actions corresponding to the Event Think
		onEvtThink();
	}

	/**
	 * Return True if the target is autoattackable (depends on the actor type).<BR><BR>
	 * <p>
	 * <B><U> Actor is a GuardInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The Player target has karma (=PK)</li>
	 * <li>The MonsterInstance target is aggressive</li><BR><BR>
	 * <p>
	 * <B><U> Actor is a L2SiegeGuardInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk or a Door</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>A siege is in progress</li>
	 * <li>The Player target isn't a Defender</li><BR><BR>
	 * <p>
	 * <B><U> Actor is a FriendlyMobInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk, a Door or another NpcInstance</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The Player target has karma (=PK)</li><BR><BR>
	 * <p>
	 * <B><U> Actor is a MonsterInstance</U> :</B><BR><BR>
	 * <li>The target isn't a Folk, a Door or another NpcInstance</li>
	 * <li>The target isn't dead, isn't invulnerable, isn't in silent moving mode AND too far (>100)</li>
	 * <li>The target is in the actor Aggro range and is at the same height</li>
	 * <li>The actor is Aggressive</li><BR><BR>
	 *
	 * @param target The targeted WorldObject
	 */
	private boolean autoAttackCondition(Creature target) {
		// Check if the target isn't another guard, folk or a door
		if (target == null || target instanceof DefenderInstance || target instanceof NpcInstance || target instanceof DoorInstance ||
				target.isAlikeDead()) {
			return false;
		}

		// Check if the target isn't invulnerable
		if (target.isInvul(getActor())) {
			// However EffectInvincible requires to check GMs specially
			if (target instanceof Player && target.isGM()) {
				return false;
			}
			if (target instanceof Summon && ((Summon) target).getOwner().isGM()) {
				return false;
			}
		}

		// Get the owner if the target is a summon
		if (target instanceof Summon) {
			Player owner = ((Summon) target).getOwner();
			if (actor.isInsideRadius(owner, 1000, true, false)) {
				target = owner;
			}
		}

		// Check if the target is a Player
		if (target instanceof Playable) {
			// Check if the target isn't in silent move mode AND too far (>100)
			if (((Playable) target).isSilentMoving() && !actor.isInsideRadius(target, 250, false, false)) {
				return false;
			}
		}
		// Los Check Here
		return actor.isAutoAttackable(target) && GeoData.getInstance().canSeeTarget(actor, target);
	}

	/**
	 * Set the Intention of this CreatureAI and create an  AI Task executed every 1s (call onEvtThink method) for this Attackable.<BR><BR>
	 * <p>
	 * <FONT COLOR=#FF0000><B> <U>Caution</U> : If actor knowPlayer isn't EMPTY, AI_INTENTION_IDLE will be change in AI_INTENTION_ACTIVE</B></FONT><BR><BR>
	 *
	 * @param intention The new Intention to set to the AI
	 * @param arg0      The first parameter of the Intention
	 * @param arg1      The second parameter of the Intention
	 */
	@Override
	synchronized void changeIntention(CtrlIntention intention, Object arg0, Object arg1) {
		if (Config.DEBUG) {
			log.info("L2SiegeAI.changeIntention(" + intention + ", " + arg0 + ", " + arg1 + ")");
		}

		if (intention == AI_INTENTION_IDLE /*|| intention == AI_INTENTION_ACTIVE*/) // active becomes idle if only a summon is present
		{
			// Check if actor is not dead
			if (!actor.isAlikeDead()) {
				Attackable npc = (Attackable) actor;

				// If its knownPlayer isn't empty set the Intention to AI_INTENTION_ACTIVE
				if (!npc.getKnownList().getKnownPlayers().isEmpty()) {
					intention = AI_INTENTION_ACTIVE;
				} else {
					intention = AI_INTENTION_IDLE;
				}
			}

			if (intention == AI_INTENTION_IDLE) {
				// Set the Intention of this AttackableAI to AI_INTENTION_IDLE
				super.changeIntention(AI_INTENTION_IDLE, null, null);

				// Stop AI task and detach AI from NPC
				if (aiTask != null) {
					aiTask.cancel(true);
					aiTask = null;
				}

				// Cancel the AI
				actor.detachAI();

				return;
			}
		}

		// Set the Intention of this AttackableAI to intention
		super.changeIntention(intention, arg0, arg1);

		// If not idle - create an AI task (schedule onEvtThink repeatedly)
		if (aiTask == null) {
			aiTask = ThreadPoolManager.getInstance().scheduleAiAtFixedRate(this, 1000, 1000);
		}
	}

	/**
	 * Manage the Attack Intention : Stop current Attack (if necessary), Calculate attack timeout, Start a new Attack and Launch Think Event.<BR><BR>
	 *
	 * @param target The Creature to attack
	 */
	@Override
	protected void onIntentionAttack(Creature target) {
		// Calculate the attack timeout
		attackTimeout = MAX_ATTACK_TIMEOUT + TimeController.getGameTicks();

		// Manage the Attack Intention : Stop current Attack (if necessary), Start a new Attack and Launch Think Event
		//if (actor.getTarget() != null)
		super.onIntentionAttack(target);
	}

	/**
	 * Manage AI standard thinks of a Attackable (called by onEvtThink).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Update every 1s the globalAggro counter to come close to 0</li>
	 * <li>If the actor is Aggressive and can attack, add all autoAttackable Creature in its Aggro Range to its aggroList, chose a target and order to attack it</li>
	 * <li>If the actor  can't attack, order to it to return to its home location</li>
	 */
	private void thinkActive() {
		Attackable npc = (Attackable) actor;

		// Update every 1s the globalAggro counter to come close to 0
		if (globalAggro != 0) {
			if (globalAggro < 0) {
				globalAggro++;
			} else {
				globalAggro--;
			}
		}

		// Add all autoAttackable Creature in Attackable Aggro Range to its aggroList with 0 damage and 1 hate
		// A Attackable isn't aggressive during 10s after its spawn because globalAggro is set to -10
		if (globalAggro >= 0) {
			for (Creature target : npc.getKnownList().getKnownCharactersInRadius(attackRange)) {
				if (target == null) {
					continue;
				}
				if (autoAttackCondition(target)) // check aggression
				{
					// Get the hate level of the Attackable against this Creature target contained in aggroList
					int hating = npc.getHating(target);

					// Add the attacker to the Attackable aggroList with 0 damage and 1 hate
					if (hating == 0) {
						npc.addDamageHate(target, 0, 1);
					}
				}
			}

			// Chose a target from its aggroList
			Creature hated;
			if (actor.isConfused()) {
				hated = getAttackTarget(); // Force mobs to attack anybody if confused
			} else {
				hated = npc.getMostHated();
			}
			//mostHatedAnalysis.Update(hated);

			// Order to the Attackable to attack the target
			if (hated != null) {
				// Get the hate level of the Attackable against this Creature target contained in aggroList
				int aggro = npc.getHating(hated);

				if (aggro + globalAggro > 0) {
					// Set the Creature movement type to run and send Server->Client packet ChangeMoveType to all others Player
					if (!actor.isRunning()) {
						actor.setRunning();
					}

					// Set the AI Intention to AI_INTENTION_ATTACK
					setIntention(CtrlIntention.AI_INTENTION_ATTACK, hated, null);
				}

				return;
			}
		}
		// Order to the DefenderInstance to return to its home location because there's no target to attack
		((DefenderInstance) actor).returnHome();
	}

	/**
	 * Manage AI attack thinks of a Attackable (called by onEvtThink).<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Update the attack timeout if actor is running</li>
	 * <li>If target is dead or timeout is expired, stop this attack and set the Intention to AI_INTENTION_ACTIVE</li>
	 * <li>Call all WorldObject of its Faction inside the Faction Range</li>
	 * <li>Chose a target and order to attack it with magic skill or physical attack</li><BR><BR>
	 * <p>
	 * TODO: Manage casting Rule to healer mobs (like Ant Nurses)
	 */
	private void thinkAttack() {
		if (Config.DEBUG) {
			log.info("SiegeGuardAI.thinkAttack(); timeout=" + (attackTimeout - TimeController.getGameTicks()));
		}

		if (attackTimeout < TimeController.getGameTicks()) {
			// Check if the actor is running
			if (actor.isRunning()) {
				// Set the actor movement type to walk and send Server->Client packet ChangeMoveType to all others Player
				actor.setWalking();

				// Calculate a new attack timeout
				attackTimeout = MAX_ATTACK_TIMEOUT + TimeController.getGameTicks();
			}
		}

		Creature attackTarget = getAttackTarget();
		// Check if target is dead or if timeout is expired to stop this attack
		if (attackTarget == null || attackTarget.isAlikeDead() || attackTimeout < TimeController.getGameTicks()) {
			// Stop hating this target after the attack timeout or if target is dead
			if (attackTarget != null) {
				Attackable npc = (Attackable) actor;
				npc.stopHating(attackTarget);
			}

			// Cancel target and timeout
			attackTimeout = Integer.MAX_VALUE;
			setAttackTarget(null);

			// Set the AI Intention to AI_INTENTION_ACTIVE
			setIntention(AI_INTENTION_ACTIVE, null, null);

			actor.setWalking();
			return;
		}

		factionNotifyAndSupport();
		attackPrepare();
	}

	private void factionNotifyAndSupport() {
		Creature target = getAttackTarget();
		// Call all WorldObject of its Faction inside the Faction Range
		if (((Npc) actor).getFactionId() == null || target == null) {
			return;
		}

		if (target.isInvul(actor)) {
			return; // speeding it up for siege guards
		}

		String faction_id = ((Npc) actor).getFactionId();

		// Go through all Creature that belong to its faction
		//for (Creature cha : actor.getKnownList().getKnownCharactersInRadius(((NpcInstance) actor).getFactionRange()+actor.getTemplate().collisionRadius))
		for (Creature cha : actor.getKnownList().getKnownCharactersInRadius(1000)) {
			if (cha == null) {
				continue;
			}

			if (!(cha instanceof Npc)) {
				if (selfAnalysis.hasHealOrResurrect && cha instanceof Player &&
						((Npc) actor).getCastle().getSiege().checkIsDefender(((Player) cha).getClan())) {
					// heal friends
					if (!actor.isAttackingDisabled() && cha.getCurrentHp() < cha.getMaxHp() * 0.6 && actor.getCurrentHp() > actor.getMaxHp() / 2 &&
							actor.getCurrentMp() > actor.getMaxMp() / 2 && cha.isInCombat()) {
						for (Skill sk : selfAnalysis.healSkills) {
							if (actor.getCurrentMp() < sk.getMpConsume()) {
								continue;
							}
							if (actor.isSkillDisabled(sk)) {
								continue;
							}
							if (!Util.checkIfInRange(sk.getCastRange(), actor, cha, true)) {
								continue;
							}

							int chance = 5;
							if (chance >= Rnd.get(100)) // chance
							{
								continue;
							}
							if (!GeoData.getInstance().canSeeTarget(actor, cha)) {
								break;
							}

							WorldObject OldTarget = actor.getTarget();
							actor.setTarget(cha);
							clientStopMoving(null);
							actor.doCast(sk, false);
							actor.setTarget(OldTarget);
							return;
						}
					}
				}
				continue;
			}

			Npc npc = (Npc) cha;

			if (!faction_id.equals(npc.getFactionId())) {
				continue;
			}

			if (npc.getAI() != null) // TODO: possibly check not needed
			{
				if (!npc.isDead() && Math.abs(target.getZ() - npc.getZ()) < 600
						//&& actor.getAttackByList().contains(getAttackTarget())
						&& (npc.getAI().intention == CtrlIntention.AI_INTENTION_IDLE || npc.getAI().intention == CtrlIntention.AI_INTENTION_ACTIVE)
						//limiting aggro for siege guards
						&& target.isInsideRadius(npc, 1500, true, false) && GeoData.getInstance().canSeeTarget(npc, target)) {
					// Notify the WorldObject AI with EVT_AGGRESSION
					npc.getAI().notifyEvent(CtrlEvent.EVT_AGGRESSION, getAttackTarget(), 1);
					return;
				}
				// heal friends
				if (selfAnalysis.hasHealOrResurrect && !actor.isAttackingDisabled() && npc.getCurrentHp() < npc.getMaxHp() * 0.6 &&
						actor.getCurrentHp() > actor.getMaxHp() / 2 && actor.getCurrentMp() > actor.getMaxMp() / 2 && npc.isInCombat()) {
					for (Skill sk : selfAnalysis.healSkills) {
						if (actor.getCurrentMp() < sk.getMpConsume()) {
							continue;
						}
						if (actor.isSkillDisabled(sk)) {
							continue;
						}
						if (!Util.checkIfInRange(sk.getCastRange(), actor, npc, true)) {
							continue;
						}

						int chance = 4;
						if (chance >= Rnd.get(100)) // chance
						{
							continue;
						}
						if (!GeoData.getInstance().canSeeTarget(actor, npc)) {
							break;
						}

						WorldObject OldTarget = actor.getTarget();
						actor.setTarget(npc);
						clientStopMoving(null);
						actor.doCast(sk, false);
						actor.setTarget(OldTarget);
						return;
					}
				}
			}
		}
	}

	private void attackPrepare() {
		// Get all information needed to choose between physical or magical attack
		Skill[] skills = null;
		double dist_2 = 0;
		int range = 0;
		DefenderInstance sGuard = (DefenderInstance) actor;
		Creature attackTarget = getAttackTarget();

		try {
			actor.setTarget(attackTarget);
			skills = actor.getAllSkills();
			dist_2 = actor.getPlanDistanceSq(attackTarget.getX(), attackTarget.getY());
			range = actor.getPhysicalAttackRange() + actor.getTemplate().getCollisionRadius() + attackTarget.getTemplate().getCollisionRadius();
			if (attackTarget.isMoving()) {
				range += 50;
			}
		} catch (NullPointerException e) {
			//Logozo.warning("AttackableAI: Attack target is NULL.");
			actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}

		// never attack defenders
		if (attackTarget instanceof Player && sGuard.getCastle().getSiege().checkIsDefender(((Player) attackTarget).getClan())) {
			// Cancel the target
			sGuard.stopHating(attackTarget);
			actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}

		if (!GeoData.getInstance().canSeeTarget(actor, attackTarget)) {
			// Siege guards differ from normal mobs currently:
			// If target cannot seen, don't attack any more
			sGuard.stopHating(attackTarget);
			actor.setTarget(null);
			setIntention(AI_INTENTION_IDLE, null, null);
			return;
		}

		// Check if the actor isn't muted and if it is far from target
		if (!actor.isMuted() && dist_2 > range * range) {
			// check for long ranged skills and heal/buff skills
			for (Skill sk : skills) {
				int castRange = sk.getCastRange();

				if (dist_2 <= castRange * castRange && castRange > 70 && !actor.isSkillDisabled(sk) &&
						actor.getCurrentMp() >= actor.getStat().getMpConsume(sk) && !sk.isPassive()) {

					WorldObject OldTarget = actor.getTarget();
					if (sk.getSkillType() == SkillType.BUFF || sk.getSkillType() == SkillType.HEAL) {
						boolean useSkillSelf = true;
						if (sk.getSkillType() == SkillType.HEAL && actor.getCurrentHp() > (int) (actor.getMaxHp() / 1.5)) {
							useSkillSelf = false;
							break;
						}
						if (sk.getSkillType() == SkillType.BUFF) {
							Abnormal[] effects = actor.getAllEffects();
							for (int i = 0; effects != null && i < effects.length; i++) {
								Abnormal effect = effects[i];
								if (effect.getSkill() == sk) {
									useSkillSelf = false;
									break;
								}
							}
						}
						if (useSkillSelf) {
							actor.setTarget(actor);
						}
					}

					clientStopMoving(null);
					actor.doCast(sk, false);
					actor.setTarget(OldTarget);
					return;
				}
			}

			// Check if the L2SiegeGuardInstance is attacking, knows the target and can't run
			if (!actor.isAttackingNow() && actor.getRunSpeed() == 0 && actor.getKnownList().knowsObject(attackTarget)) {
				// Cancel the target
				actor.getKnownList().removeKnownObject(attackTarget);
				actor.setTarget(null);
				setIntention(AI_INTENTION_IDLE, null, null);
			} else {
				double dx = actor.getX() - attackTarget.getX();
				double dy = actor.getY() - attackTarget.getY();
				double dz = actor.getZ() - attackTarget.getZ();
				double homeX = attackTarget.getX() - sGuard.getSpawn().getX();
				double homeY = attackTarget.getY() - sGuard.getSpawn().getY();

				// Check if the L2SiegeGuardInstance isn't too far from it's home location
				if (dx * dx + dy * dy > 10000 && homeX * homeX + homeY * homeY > 3240000 // 1800 * 1800
						&& actor.getKnownList().knowsObject(attackTarget)) {
					// Cancel the target
					actor.getKnownList().removeKnownObject(attackTarget);
					actor.setTarget(null);
					setIntention(AI_INTENTION_IDLE, null, null);
				} else
				// Move the actor to Pawn server side AND client side by sending Server->Client packet MoveToPawn (broadcast)
				{
					// Temporary hack for preventing guards jumping off towers,
					// before replacing this with effective geodata checks and AI modification
					if (dz * dz < 170 * 170) // normally 130 if guard z coordinates correct
					{
						if (selfAnalysis.isHealer) {
							return;
						}
						if (selfAnalysis.isMage) {
							range = selfAnalysis.maxCastRange - 50;
						}
						if (attackTarget.isMoving()) {
							moveToPawn(attackTarget, range - 70);
						} else {
							moveToPawn(attackTarget, range);
						}
					}
				}
			}
		}
		// Else, if the actor is muted and far from target, just "move to pawn"
		else if (actor.isMuted() && dist_2 > range * range && !selfAnalysis.isHealer) {
			// Temporary hack for preventing guards jumping off towers,
			// before replacing this with effective geodata checks and AI modification
			double dz = actor.getZ() - attackTarget.getZ();
			if (dz * dz < 170 * 170) // normally 130 if guard z coordinates correct
			{
				if (selfAnalysis.isMage) {
					range = selfAnalysis.maxCastRange - 50;
				}
				if (attackTarget.isMoving()) {
					moveToPawn(attackTarget, range - 70);
				} else {
					moveToPawn(attackTarget, range);
				}
			}
		}
		// Else, if this is close enough to attack
		else if (dist_2 <= range * range) {
			// Force mobs to attack anybody if confused
			Creature hated = null;
			if (actor.isConfused()) {
				hated = attackTarget;
			} else {
				hated = ((Attackable) actor).getMostHated();
			}

			if (hated == null) {
				setIntention(AI_INTENTION_ACTIVE, null, null);
				return;
			}
			if (hated != attackTarget) {
				attackTarget = hated;
			}

			attackTimeout = MAX_ATTACK_TIMEOUT + TimeController.getGameTicks();

			// check for close combat skills && heal/buff skills
			if (!actor.isMuted() && Rnd.nextInt(100) <= 5) {
				for (Skill sk : skills) {
					int castRange = sk.getCastRange();

					if (castRange * castRange >= dist_2 && !sk.isPassive() && actor.getCurrentMp() >= actor.getStat().getMpConsume(sk) &&
							!actor.isSkillDisabled(sk)) {
						WorldObject OldTarget = actor.getTarget();
						if (sk.getSkillType() == SkillType.BUFF || sk.getSkillType() == SkillType.HEAL) {
							boolean useSkillSelf = true;
							if (sk.getSkillType() == SkillType.HEAL && actor.getCurrentHp() > (int) (actor.getMaxHp() / 1.5)) {
								useSkillSelf = false;
								break;
							}
							if (sk.getSkillType() == SkillType.BUFF) {
								Abnormal[] effects = actor.getAllEffects();
								for (int i = 0; effects != null && i < effects.length; i++) {
									Abnormal effect = effects[i];
									if (effect.getSkill() == sk) {
										useSkillSelf = false;
										break;
									}
								}
							}
							if (useSkillSelf) {
								actor.setTarget(actor);
							}
						}

						clientStopMoving(null);
						actor.doCast(sk, false);
						actor.setTarget(OldTarget);
						return;
					}
				}
			}
			// Finally, do the physical attack itself
			if (!selfAnalysis.isHealer) {
				actor.doAttack(attackTarget);
			}
		}
	}

	/**
	 * Manage AI thinking actions of a Attackable.<BR><BR>
	 */
	@Override
	protected void onEvtThink() {
		//	  if (getIntention() != AI_INTENTION_IDLE && (!actor.isVisible() || !actor.hasAI() || !actor.isKnownPlayers()))
		//		  setIntention(AI_INTENTION_IDLE);

		// Check if the thinking action is already in progress
		if (thinking || actor.isCastingNow() || actor.isAllSkillsDisabled()) {
			return;
		}

		// Start thinking action
		thinking = true;

		try {
			// Manage AI thinks of a Attackable
			if (getIntention() == AI_INTENTION_ACTIVE) {
				thinkActive();
			} else if (getIntention() == AI_INTENTION_ATTACK) {
				thinkAttack();
			}
		} finally {
			// Stop thinking action
			thinking = false;
		}
	}

	/**
	 * Launch actions corresponding to the Event Attacked.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Init the attack : Calculate the attack timeout, Set the globalAggro to 0, Add the attacker to the actor aggroList</li>
	 * <li>Set the Creature movement type to run and send Server->Client packet ChangeMoveType to all others Player</li>
	 * <li>Set the Intention to AI_INTENTION_ATTACK</li><BR><BR>
	 *
	 * @param attacker The Creature that attacks the actor
	 */
	@Override
	protected void onEvtAttacked(Creature attacker) {
		// Calculate the attack timeout
		attackTimeout = MAX_ATTACK_TIMEOUT + TimeController.getGameTicks();

		// Set the globalAggro to 0 to permit attack even just after spawn
		if (globalAggro < 0) {
			globalAggro = 0;
		}

		// Add the attacker to the aggroList of the actor
		((Attackable) actor).addDamageHate(attacker, 0, 1);

		// Set the Creature movement type to run and send Server->Client packet ChangeMoveType to all others Player
		if (!actor.isRunning()) {
			actor.setRunning();
		}

		// Set the Intention to AI_INTENTION_ATTACK
		if (getIntention() != AI_INTENTION_ATTACK) {
			setIntention(CtrlIntention.AI_INTENTION_ATTACK, attacker, null);
		}

		super.onEvtAttacked(attacker);
	}

	/**
	 * Launch actions corresponding to the Event Aggression.<BR><BR>
	 * <p>
	 * <B><U> Actions</U> :</B><BR><BR>
	 * <li>Add the target to the actor aggroList or update hate if already present </li>
	 * <li>Set the actor Intention to AI_INTENTION_ATTACK (if actor is GuardInstance check if it isn't too far from its home location)</li><BR><BR>
	 *
	 * @param aggro The value of hate to add to the actor against the target
	 */
	@Override
	protected void onEvtAggression(Creature target, int aggro) {
		if (actor == null) {
			return;
		}
		Attackable me = (Attackable) actor;

		if (target != null) {
			// Add the target to the actor aggroList or update hate if already present
			me.addDamageHate(target, 0, aggro);

			// Get the hate of the actor against the target
			aggro = me.getHating(target);

			if (aggro <= 0) {
				if (me.getMostHated() == null) {
					globalAggro = -25;
					me.clearAggroList();
					setIntention(AI_INTENTION_IDLE, null, null);
				}
				return;
			}

			// Set the actor AI Intention to AI_INTENTION_ATTACK
			if (getIntention() != CtrlIntention.AI_INTENTION_ATTACK) {
				// Set the Creature movement type to run and send Server->Client packet ChangeMoveType to all others Player
				if (!actor.isRunning()) {
					actor.setRunning();
				}

				DefenderInstance sGuard = (DefenderInstance) actor;
				double homeX = target.getX() - sGuard.getSpawn().getX();
				double homeY = target.getY() - sGuard.getSpawn().getY();

				// Check if the L2SiegeGuardInstance is not too far from its home location
				if (homeX * homeX + homeY * homeY < 3240000) // 1800 * 1800
				{
					setIntention(CtrlIntention.AI_INTENTION_ATTACK, target, null);
				}
			}
		} else {
			// currently only for setting lower general aggro
			if (aggro >= 0) {
				return;
			}

			Creature mostHated = me.getMostHated();
			if (mostHated == null) {
				globalAggro = -25;
				return;
			} else {
				for (Creature aggroed : me.getAggroList().keySet()) {
					me.addDamageHate(aggroed, 0, aggro);
				}
			}

			aggro = me.getHating(mostHated);
			if (aggro <= 0) {
				globalAggro = -25;
				me.clearAggroList();
				setIntention(AI_INTENTION_IDLE, null, null);
			}
		}
	}

	@Override
	public void stopAITask() {
		if (aiTask != null) {
			aiTask.cancel(false);
			aiTask = null;
		}
		actor.detachAI();
		super.stopAITask();
	}
}
