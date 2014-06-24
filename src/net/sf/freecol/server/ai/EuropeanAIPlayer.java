/**
 *  Copyright (C) 2002-2013   The FreeCol Team
 *
 *  This file is part of FreeCol.
 *
 *  FreeCol is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  FreeCol is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with FreeCol.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.sf.freecol.server.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.stream.XMLStreamException;

import net.sf.freecol.common.debug.FreeColDebugger;
import net.sf.freecol.common.io.FreeColXMLReader;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.Building;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.ColonyTile;
import net.sf.freecol.common.model.ColonyTradeItem;
import net.sf.freecol.common.model.DiplomaticTrade;
import net.sf.freecol.common.model.DiplomaticTrade.TradeStatus;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.FeatureContainer;
import net.sf.freecol.common.model.FoundingFather;
import net.sf.freecol.common.model.FreeColGameObject;
import net.sf.freecol.common.model.Game;
import net.sf.freecol.common.model.GameOptions;
import net.sf.freecol.common.model.GoldTradeItem;
import net.sf.freecol.common.model.Goods;
import net.sf.freecol.common.model.GoodsTradeItem;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.HistoryEvent;
import net.sf.freecol.common.model.InciteTradeItem;
import net.sf.freecol.common.model.IndianSettlement;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Map;
import net.sf.freecol.common.model.Market;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.NationSummary;
import net.sf.freecol.common.model.PathNode;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Player.PlayerType;
import net.sf.freecol.common.model.Player.Stance;
import net.sf.freecol.common.model.Role;
import net.sf.freecol.common.model.Settlement;
import net.sf.freecol.common.model.Specification;
import net.sf.freecol.common.model.StanceTradeItem;
import net.sf.freecol.common.model.Tension;
import net.sf.freecol.common.model.Tile;
import net.sf.freecol.common.model.TradeItem;
import net.sf.freecol.common.model.Turn;
import net.sf.freecol.common.model.Unit;
import net.sf.freecol.common.model.Unit.UnitState;
import net.sf.freecol.common.model.UnitTradeItem;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.model.WorkLocation;
import net.sf.freecol.common.model.pathfinding.CostDeciders;
import net.sf.freecol.common.model.pathfinding.GoalDeciders;
import net.sf.freecol.common.networking.NetworkConstants;
import net.sf.freecol.common.option.OptionGroup;
import net.sf.freecol.common.util.RandomChoice;
import net.sf.freecol.common.util.Utils;
import net.sf.freecol.server.ai.mission.BuildColonyMission;
import net.sf.freecol.server.ai.mission.CashInTreasureTrainMission;
import net.sf.freecol.server.ai.mission.DefendSettlementMission;
import net.sf.freecol.server.ai.mission.IdleAtSettlementMission;
import net.sf.freecol.server.ai.mission.Mission;
import net.sf.freecol.server.ai.mission.MissionaryMission;
import net.sf.freecol.server.ai.mission.PioneeringMission;
import net.sf.freecol.server.ai.mission.PrivateerMission;
import net.sf.freecol.server.ai.mission.ScoutingMission;
import net.sf.freecol.server.ai.mission.TransportMission;
import net.sf.freecol.server.ai.mission.TransportMission.Cargo;
import net.sf.freecol.server.ai.mission.UnitSeekAndDestroyMission;
import net.sf.freecol.server.ai.mission.UnitWanderHostileMission;
import net.sf.freecol.server.ai.mission.WishRealizationMission;
import net.sf.freecol.server.ai.mission.WorkInsideColonyMission;
import net.sf.freecol.server.model.ServerPlayer;


/**
 * Objects of this class contains AI-information for a single European
 * {@link Player} and is used for controlling this player.
 *
 * The method {@link #startWorking} gets called by the
 * {@link AIInGameInputHandler} when it is this player's turn.
 */
public class EuropeanAIPlayer extends AIPlayer {

    private static final Logger logger = Logger.getLogger(EuropeanAIPlayer.class.getName());

    /** Maximum number of turns to travel to a building site. */
    private static final int buildingRange = 5;

    /** Maximum number of turns to travel to a cash in location. */
    private static final int cashInRange = 20;

    /** Maximum number of turns to travel to a missionary target. */
    private static final int missionaryRange = 20;

    /**
     * Maximum number of turns to travel to make progress on
     * pioneering.  This is low-ish because it is usually more
     * efficient to ship the tools where they are needed and either
     * create a new pioneer on site or send a hardy pioneer on
     * horseback.  The AI is probably smart enough to do the former
     * already, and one day the latter.
     */
    private static final int pioneeringRange = 10;

    /** Maximum number of turns to travel to a scouting target. */
    private static final int scoutingRange = 20;


    /**
     * A comparator to sort units by suitability for a BuildColonyMission.
     *
     * Favours unequipped freeColonists, and other unskilled over experts.
     * Also favour units on the map.
     */
    private static final Comparator<AIUnit> builderComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                if (a == null || (unit = a.getUnit()) == null
                    || BuildColonyMission.invalidReason(a) != null)
                    return -1000;
                int base = (!unit.getEquipment().isEmpty()) ? 0
                    : (unit.getSkillLevel() > 0) ? 100
                    : 500 + 100 * unit.getSkillLevel();
                if (unit.hasTile()) base += 50;
                return base;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    /**
     * A comparator to sort units by suitability for a ScoutingMission.
     *
     * Favours existing scouts (especially if on the map), then dismounted
     * experts, then units that can become scouts.
     *
     * We do not check if a unit is near to a colony that can provide horses,
     * as that is likely to be too expensive.  TODO: revise
     */
    private static final Comparator<AIUnit> scoutComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                if (a == null || (unit = a.getUnit()) == null
                    || unit.getLocation() == null
                    || !unit.isColonist()) {
                    return -1000;
                } else if (unit.hasAbility(Ability.SPEAK_WITH_CHIEF)) {
                    return 900 + ((unit.hasTile()) ? 100 : 0);
                } else if (unit.hasAbility(Ability.EXPERT_SCOUT)) {
                    return 600;
                }
                List<AbstractGoods> roleEquipment = unit.getSpecification()
                .getRole("model.role.scout").getRequiredGoods();
                int base = (unit.isInEurope()) ? 500
                    : (unit.getLocation().getColony() != null
                        && unit.getLocation().getColony()
                        .canProvideGoods(roleEquipment)) ? 400
                    : -1000;
                if (!unit.getEquipment().isEmpty()) {
                    base -= 400;
                } else if (unit.getSkillLevel() > 0) {
                    base -= 200;
                }
                // Do not penalize criminals or servants.
                return base;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    /**
     * A comparator to sort units by suitability for a PioneeringMission.
     *
     * Favours existing pioneers (especially if on the map), then experts
     * missing tools, then units that can become pioneers.
     *
     * We do not check if a unit is near to a colony that can provide tools,
     * as that is likely to be too expensive.  TODO: revise
     */
    private static final Comparator<AIUnit> pioneerComparator
        = new Comparator<AIUnit>() {
            private int score(AIUnit a) {
                Unit unit;
                if (a == null || (unit = a.getUnit()) == null
                    || unit.getLocation() == null
                    || !unit.isColonist()) {
                    return -1000;
                } else if (unit.hasAbility(Ability.IMPROVE_TERRAIN)) {
                    return 900 + ((unit.hasTile()) ? 100 : 0);
                } else if (unit.hasAbility(Ability.EXPERT_PIONEER)) {
                    return 600;
                }
                List<AbstractGoods> roleEquipment = unit.getSpecification()
                .getRole("model.role.pioneer").getRequiredGoods();
                int base = (unit.isInEurope()) ? 500
                    : (unit.getLocation().getColony() != null
                        && unit.getLocation().getColony()
                        .canProvideGoods(roleEquipment)) ? 400
                    : -1000;
                if (!unit.getEquipment().isEmpty()) {
                    base -= 400;
                } else if (unit.getSkillLevel() > 0) {
                    base -= 200;
                } else {
                    base += unit.getSkillLevel() * 150;
                }
                return base;
            }

            public int compare(AIUnit a1, AIUnit a2) {
                return score(a2) - score(a1);
            }
        };

    /**
     * A cached map of Tile to best TileImprovementPlan.
     * Used to choose a tile improvement for a pioneer to work on.
     * Do not serialize.
     */
    private final java.util.Map<Tile, TileImprovementPlan> tipMap
        = new HashMap<Tile, TileImprovementPlan>();

    /**
     * A cached map of destination Location to Wishes awaiting transport.
     *
     * Do not serialize.
     */
    private final java.util.Map<Location, List<Wish>> transportDemand
        = new HashMap<Location, List<Wish>>();

    /**
     * A cached map of source Location to Transportables awaiting transport.
     *
     * Do not serialize.
     */
    private final java.util.Map<Location, List<Transportable>> transportSupply
        = new HashMap<Location, List<Transportable>>();

    /**
     * A mapping of goods type to the goods wishes where a colony has
     * requested that goods type.  Used to retarget goods that have
     * gone astray.
     * Do not serialize.
     */
    private final java.util.Map<GoodsType, List<GoodsWish>> goodsWishes
        = new HashMap<GoodsType, List<GoodsWish>>();

    /**
     * A mapping of unit type to the worker wishes for that type.
     * Used to allocate WishRealizationMissions for units.
     * Do not serialize.
     */
    private final java.util.Map<UnitType, List<WorkerWish>> workerWishes
        = new HashMap<UnitType, List<WorkerWish>>();

    /**
     * A mapping of contiguity number to number of wagons needed in
     * that landmass.
     */
    private final java.util.Map<Integer, Integer> wagonsNeeded
        = new HashMap<Integer, Integer>();

    /**
     * Stores temporary information for sessions (trading with another player
     * etc).  Do not serialize.
     */
    private final java.util.Map<String, Integer> sessionRegister
        = new HashMap<String, Integer>();

    /**
     * Debug helper to keep track of why/what the units are doing.
     * Do not serialize.
     */
    private final java.util.Map<Unit, String> reasons
        = new HashMap<Unit, String>();

    /**
     * Current estimate of the number of new <code>BuildColonyMission</code>s
     * to create.
     */
    private int nBuilders = 0;
    /**
     * Current estimate of the number of new <code>PioneeringMission</code>s
     * to create.
     */
    private int nPioneers = 0;
    /**
     * Current estimate of the number of new <code>ScoutingMission</code>s
     * to create.
     */
    private int nScouts = 0;
    /**
     * Count of the number of transports needing a naval unit.
     */
    private int nNavalCarrier = 0;


    /**
     * Creates a new <code>EuropeanAIPlayer</code>.
     *
     * @param aiMain The main AI-class.
     * @param player The player that should be associated with this
     *            <code>AIPlayer</code>.
     */
    public EuropeanAIPlayer(AIMain aiMain, ServerPlayer player) {
        super(aiMain, player);

        uninitialized = getPlayer() == null;
    }

    /**
     * Creates a new <code>AIPlayer</code>.
     *
     * @param aiMain The main AI-object.
     * @param xr The input stream containing the XML.
     * @throws XMLStreamException if a problem was encountered during parsing.
     */
    public EuropeanAIPlayer(AIMain aiMain,
                            FreeColXMLReader xr) throws XMLStreamException {
        super(aiMain, xr);

        uninitialized = getPlayer() == null;
    }

    /**
     * Rebuilds a map of locations to TileImprovementPlans.
     * Called by startWorking at the start of every turn.
     * Public for the test suite.
     */
    public void buildTipMap() {
        tipMap.clear();
        for (AIColony aic : getAIColonies()) {
            for (TileImprovementPlan tip : aic.getTileImprovementPlans()) {
                if (tip == null || tip.isComplete()) {
                    aic.removeTileImprovementPlan(tip);
                } else if (tip.getPioneer() != null) {
                    ; // Do nothing, remove when complete
                } else if (!tip.validate()) {
                    aic.removeTileImprovementPlan(tip);
                    tip.dispose();
                } else {
                    TileImprovementPlan other = tipMap.get(tip.getTarget());
                    if (other == null || other.getValue() < tip.getValue()) {
                        tipMap.put(tip.getTarget(), tip);
                    }
                }
            }
        }
    }

    /**
     * Gets the best plan for a tile from the tipMap.
     *
     * @param tile The <code>Tile</code> to lookup.
     * @return The best plan for a tile.
     */
    public TileImprovementPlan getBestPlan(Tile tile) {
        return (tipMap == null) ? null : tipMap.get(tile);
    }

    /**
     * Gets the best plan for a colony from the tipMap.
     *
     * @param colony The <code>Colony</code> to check.
     * @return The tile with the best plan for a colony, or null if none found.
     */
    public Tile getBestPlanTile(Colony colony) {
        TileImprovementPlan best = null;
        int bestValue = Integer.MIN_VALUE;
        for (Tile t : colony.getOwnedTiles()) {
            TileImprovementPlan tip = tipMap.get(t);
            if (tip != null && tip.getValue() > bestValue) {
                bestValue = tip.getValue();
                best = tip;
            }
        }
        return (best == null) ? null : best.getTarget();
    }

    /**
     * Remove a <code>TileImprovementPlan</code> from the relevant colony.
     */
    public void removeTileImprovementPlan(TileImprovementPlan plan) {
        for (AIColony aic : getAIColonies()) {
            if (aic.removeTileImprovementPlan(plan)) break;
        }
    }

    /**
     * Checks if a transportable needs transport.
     *
     * @param t The <code>Transportable</code> to check.
     * @return True if no transport is already present or the
     *     transportable is already aboard a carrier, and there is a
     *     well defined source and destination location.
     */
    private boolean requestsTransport(Transportable t) {
        return t.getTransport() == null
            && t.getTransportDestination() != null
            && t.getTransportSource() != null
            && !(t.getTransportLocatable().getLocation() instanceof Unit);
    }

    /**
     * Checks that the carrier assigned to a transportable is has a
     * transport mission and the transport is queued thereon.
     *
     * @param t The <code>Transportable</code> to check.
     * @return True if all is well.
     */
    private boolean checkTransport(Transportable t) {
        AIUnit aiCarrier = t.getTransport();
        if (aiCarrier != null) {
            Mission m = aiCarrier.getMission();
            if (m instanceof TransportMission) {
                if (((TransportMission)m).isTransporting(t)) return true;
                t.setTransport(null, "mission dropped");
                return false;
            }
            t.setTransport(null, "no carrier transport mission");
            return false;
        }
        return true;
    }

    /**
     * Gets the needed wagons for a tile/contiguity.
     *
     * @param tile The <code>Tile</code> to derive the contiguity from.
     * @return The number of wagons needed.
     */
    public int getNeededWagons(Tile tile) {
        if (tile != null) {
            int contig = tile.getContiguity();
            if (contig > 0) {
                Integer i = wagonsNeeded.get(contig);
                if (i != null) return i.intValue();
            }
        }
        return 0;
    }

    /**
     * Changes the needed wagons map for a specified tile/contiguity.
     * If the change is zero, that is a special flag that a connected
     * port is available, and thus that the map should be initialized
     * for that contiguity.
     *
     * @param tile The <code>Tile</code> to derive the contiguity from.
     * @param amount The change to make.
     */
    private void changeNeedWagon(Tile tile, int amount) {
        if (tile == null) return;
        int contig = tile.getContiguity();
        if (contig > 0) {
            Integer i = wagonsNeeded.get(contig);
            if (i == null) {
                if (amount == 0) wagonsNeeded.put(contig, new Integer(0));
            } else {
                wagonsNeeded.put(contig, new Integer(i.intValue() + amount));
            }
        }
    }

    /**
     * Rebuild the transport maps.
     * Count the number of transports requiring naval/land carriers.
     */
    private void buildTransportMaps() {
        transportDemand.clear();
        transportSupply.clear();
        wagonsNeeded.clear();
        nNavalCarrier = 0;

        // Prime the wagonsNeeded map with contiguities with a connected port
        for (AIColony aic : getAIColonies()) {
            Colony colony = aic.getColony();
            if (colony.isConnectedPort()) changeNeedWagon(colony.getTile(), 0);
        }

        for (AIUnit aiu : getAIUnits()) {
            if (aiu.hasMission() && !aiu.getMission().isValid()) continue;
            Unit u = aiu.getUnit();
            if (u.isCarrier()) {
                if (u.isNaval()) {
                    nNavalCarrier--;
                } else {
                    changeNeedWagon(u.getTile(), -1);
                }
            } else {
                checkTransport(aiu);
                if (requestsTransport(aiu)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aiu.getTransportSource()), aiu);
                    aiu.increaseTransportPriority();
                    nNavalCarrier++;
                }
            }
        }

        for (AIColony aic : getAIColonies()) {
            for (AIGoods aig : aic.getAIGoods()) {
                checkTransport(aig);
                if (requestsTransport(aig)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aig.getTransportSource()), aig);
                    aig.increaseTransportPriority();
                    Location src = aig.getTransportSource();
                    Location dst = aig.getTransportDestination();
                    if (!Map.isSameContiguity(src, dst)) {
                        nNavalCarrier++;
                    }
                }
            }
            Colony colony = aic.getColony();
            if (!colony.isConnectedPort()) {
                changeNeedWagon(colony.getTile(), 1);
            }
        }

        for (Wish w : getWishes()) {
            Transportable t = w.getTransportable();
            if (t != null && t.getTransport() == null
                && t.getTransportDestination() != null) {
                Location loc = upLoc(t.getTransportDestination());
                Utils.appendToMapList(transportDemand, loc, w);
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            StringBuilder sb = new StringBuilder("Supply:");
            for (Location ls : transportSupply.keySet()) {
                sb.append(" ");
                sb.append(((FreeColGameObject)ls).toString());
                sb.append("[");
                for (Transportable t : transportSupply.get(ls)) {
                    sb.append(" ");
                    sb.append(t.toString());
                }
                sb.append(" ]");
            }
            sb.append("\nDemand:");
            for (Location ld : transportDemand.keySet()) {
                sb.append(" ");
                sb.append(((FreeColGameObject)ld).toString());
                sb.append("[");
                for (Wish w : transportDemand.get(ld)) {
                    sb.append(" ");
                    sb.append(w.toString());
                }
                sb.append(" ]");
            }
            logger.finest(sb.toString());
        }
    }

    /**
     * Gets the most urgent transportables.
     *
     * @return The most urgent 10% of the available transportables.
     */
    public List<Transportable> getUrgentTransportables() {
        List<Transportable> urgent = new ArrayList<Transportable>();
        for (Location l : transportSupply.keySet()) {
            urgent.addAll(transportSupply.get(l));
        }
        // Do not let the list exceed 10% of all transports
        Collections.sort(urgent, Transportable.transportableComparator);
        int urge = urgent.size();
        urge = Math.max(2, (urge + 5) / 10);
        while (urgent.size() > urge) urgent.remove(urge);

        return urgent;
    }

    /**
     * Gets a list of the transportables that need transport from a
     * given location.
     *
     * @param loc The <code>Location</code> to transport from.
     * @return A list of transportables.
     */
    public List<Transportable> getTransportablesAt(Location loc) {
        List<Transportable> supply = transportSupply.get(upLoc(loc));
        return (supply == null) ? supply : new ArrayList<Transportable>(supply);
    }

    /**
     * Allows a TransportMission to signal that it has taken responsibility
     * for a Transportable.
     *
     * @param t The <code>Transportable</code> being claimed.
     * @return True if the transportable was claimed from the supply map.
     */
    public boolean claimTransportable(Transportable t) {
        return claimTransportable(t, upLoc(t.getTransportSource()));
    }

    /**
     * Allows a TransportMission to signal that it has taken responsibility
     * for a Transportable.
     *
     * @param t The <code>Transportable</code> being claimed.
     * @param loc The <code>Location</code> to claim from.
     * @return True if the transportable was claimed from the supply map.
     */
    public boolean claimTransportable(Transportable t, Location loc) {
        List<Transportable> tl = transportSupply.get(upLoc(loc));
        return tl != null && tl.remove(t);
    }

    /**
     * Gets the best worker wish for a carrier unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param wishes A list of <code>WorkerWish</code>es to choose from.
     * @return The best worker wish for the unit.
     */
    private WorkerWish getBestWorkerWish(AIUnit aiUnit,
                                         List<WorkerWish> wishes) {
        if (wishes == null) return null;
        final Unit carrier = aiUnit.getUnit();
        WorkerWish nonTransported = null;
        WorkerWish transported = null;
        float bestNonTransportedValue = -1.0f;
        float bestTransportedValue = -1.0f;
        for (WorkerWish w : wishes) {
            int turns;
            try {
                turns = carrier.getTurnsToReach(w.getDestination());
            } catch (Exception e) {
                logger.log(Level.WARNING, "Bogus wish destination: "
                    + w.getDestination() + " for wish: " + w.toString(), e);
                continue;
            }
            if (turns == INFINITY) {
                if (bestTransportedValue < w.getValue()) {
                    bestTransportedValue = w.getValue();
                    transported = w;
                }
            } else {
                if (bestNonTransportedValue < (float)w.getValue() / turns) {
                    bestNonTransportedValue = (float)w.getValue() / turns;
                    nonTransported = w;
                }
            }
        }
        return (nonTransported != null) ? nonTransported
            : (transported != null) ? transported
            : null;
    }

    /**
     * Gets the best goods wish for a carrier unit.
     *
     * @param aiUnit The <code>AIUnit</code> to use as the carrier.
     * @param start The <code>Location</code> to start searches from.
     * @param wishes A list of <code>GoodsWish</code>es to choose from.
     * @return The best goods wish for the unit.
     */
    private GoodsWish getBestGoodsWish(AIUnit aiUnit, Location start,
                                       List<GoodsWish> wishes) {
        if (wishes == null) return null;
        final Unit carrier = aiUnit.getUnit();
        float bestValue = 0.0f;
        GoodsWish best = null;
        for (GoodsWish w : wishes) {
            int turns = carrier.getTurnsToReach(start, w.getDestination());
            if (turns == INFINITY) continue;
            float value = (float)w.getValue() / turns;
            if (bestValue > value) {
                bestValue = value;
                best = w;
            }
        }
        return best;
    }

    /**
     * Find a good place to send a transportable currently on board a
     * carrier yet without a meaningful transport destination.
     *
     * Called from TransportMission.
     *
     * @param t The <code>Transportable</code> to retarget.
     * @param aiCarrier The <code>AIUnit</code> carrier.
     * @param cargoes A list of <code>Cargo</code>s the carrier is
     *     already scheduled to work on.
     * @return True if the transportable should now have a valid
     *     transport destination.
     */
    public boolean retargetCargo(Transportable t, AIUnit aiCarrier,
                                 List<Cargo> cargoes) {
        final Unit carrier = aiCarrier.getUnit();
        final AIUnit aiu = (t instanceof AIUnit) ? (AIUnit)t : null;
        final AIGoods aig = (t instanceof AIGoods) ? (AIGoods)t : null;
        Unit u = (t instanceof AIUnit) ? aiu.getUnit() : null;

        Location dst = t.getTransportDestination();
        if (dst == null) {
            if (aiu != null && aiu.getMission() != null
                && aiu.getMission().isValid()) {
                return false; // Mission ok, just does not need carrier
            }
        } else {
            if (((u == null) ? carrier.getTurnsToReach(dst)
                    : u.getTurnsToReach(u.getLocation(), dst, carrier, null))
                != INFINITY) {
                return true; // Existing target is good
            }
        }

        // First, try to take the transportable to one of the
        // scheduled locations that actually wants it.
        for (Cargo cargo : cargoes) {
            Location loc = cargo.getTarget();
            List<Wish> wl = transportDemand.get(loc);
            if (wl == null) continue;
            Wish found = null;
            for (Wish w : wl) {
                if (aiu != null && w instanceof WorkerWish) {
                    WorkerWish ww = (WorkerWish)w;
                    if (aiu.getUnit().getType() == ww.getUnitType()) {
                        aiu.setMission(consumeWorkerWish(aiu, ww));
                        logger.finest("RetargetCargo succeeded on course: "
                            + aiu.getMission());
                        found = w;
                        break;
                    }
                } else if (aig != null && w instanceof GoodsWish) {
                    GoodsWish gw = (GoodsWish)w;
                    if (aig.getGoods().getType() == gw.getGoodsType()) {
                        aig.setTransportDestination(loc);
                        int a = aig.getGoods().getAmount();
                        if (a >= gw.getGoodsAmount()) {
                            goodsWishes.get(gw.getGoodsType()).remove(gw);
                        } else {
                            gw.setGoodsAmount(gw.getGoodsAmount() - a);
                        }
                        logger.finest("RetargetCargo succeeded on course: "
                            + aig);
                        found = w;
                        break;
                    }
                }
            }
            if (found != null) {
                transportDemand.get(loc).remove(found);
                return true;
            }
        }

        if (t instanceof AIUnit) {
            // Try giving the unit a new mission.
            Mission m = null;
            if (nBuilders > 0 && (m = getBuildColonyMission(aiu)) != null) {
                nBuilders--;
            } else if (nPioneers > 0 && (m = getPioneeringMission(aiu))!=null) {
                nPioneers--;
            } else if (nScouts > 0 && (m = getScoutingMission(aiu)) != null) {
                nScouts--;
            } else if ((m = getSimpleMission(aiu)) != null) {
                if (m.getTransportDestination() == null) m = null;
            }
            if (m != null) {
                aiu.setMission(m);
                logger.finest("RetargetCargo succeeded with new mission: "
                              + aiu.getMission());
                return true;
            }
            // TODO: perhaps we need a `Get onto land' mission?

        } else if (t instanceof AIGoods) {
            // Try another existing goods wish.
            List<GoodsWish> wishList = goodsWishes.get(aig.getGoodsType());
            GoodsWish gw = getBestGoodsWish(aiCarrier, carrier.getLocation(),
                                            wishList);
            if (gw != null) {
                wishList.remove(gw);
                aig.setTransportDestination(gw.getDestination());
                logger.finest("RetargetCargo succeeded with new destination: "
                              + aig);
                return true;
            }
            // Look for a suitable colony to unload the goods.
            Location best = null;
            int bestValue = INFINITY;
            for (AIColony aic : getAIColonies()) {
                Colony colony = aic.getColony();
                if (colony.getImportAmount(aig.getGoodsType())
                    >= aig.getGoodsAmount()) {
                    int value = carrier.getTurnsToReach(colony);
                    if (bestValue > value) {
                        bestValue = value;
                        best = colony;
                    }
                }
            }
            Europe europe = getPlayer().getEurope();
            if (europe != null && getPlayer().canTrade(aig.getGoodsType())
                && carrier.getTurnsToReach(europe) < bestValue) {
                best = europe;
            }
            if (best != null) {
                aig.setTransportDestination(best);
                logger.finest("RetargetCargo reluctantly unloading: " + aig);
                return false;
            }
        }

        return false;
    }

    /**
     * Rebuilds the goods and worker wishes maps.
     */
    private void buildWishMaps() {
        for (UnitType unitType : getSpecification().getUnitTypeList()) {
            List<WorkerWish> wl = workerWishes.get(unitType);
            if (wl == null) {
                workerWishes.put(unitType, new ArrayList<WorkerWish>());
            } else {
                wl.clear();
            }
        }
        for (GoodsType goodsType : getSpecification().getGoodsTypeList()) {
            if (!goodsType.isStorable()) continue;
            List<GoodsWish> gl = goodsWishes.get(goodsType);
            if (gl == null) {
                goodsWishes.put(goodsType, new ArrayList<GoodsWish>());
            } else {
                gl.clear();
            }
        }

        for (Wish w : getWishes()) {
            if (w instanceof WorkerWish) {
                WorkerWish ww = (WorkerWish)w;
                if (ww.getTransportable() == null) {
                    Utils.appendToMapList(workerWishes, ww.getUnitType(), ww);
                }
            } else if (w instanceof GoodsWish) {
                GoodsWish gw = (GoodsWish)w;
                if (gw.getDestination() instanceof Colony) {
                    Utils.appendToMapList(goodsWishes, gw.getGoodsType(), gw);
                }
            }
        }

        if (logger.isLoggable(Level.FINEST)) {
            String logMe = "Wishes (workers) ";
            for (UnitType ut : workerWishes.keySet()) {
                List<WorkerWish> wl = workerWishes.get(ut);
                if (!wl.isEmpty()) {
                    logMe += "[";
                    for (WorkerWish ww : wl) logMe += " " + ww.toString();
                    logMe += " ]";
                }
            }
            logMe += " (goods) ";
            for (GoodsType gt : goodsWishes.keySet()) {
                List<GoodsWish> gl = goodsWishes.get(gt);
                if (!gl.isEmpty()) {
                    logMe += "[";
                    for (GoodsWish gw : gl) logMe += " " + gw.toString();
                    logMe += " ]";
                }
            }
            logger.finest(logMe);
        }
    }

    /**
     * Notify that a wish has been completed.  Called from AIColony.
     *
     * @param w The <code>Wish</code> to complete.
     */
    public void completeWish(Wish w) {
        if (w instanceof WorkerWish) {
            WorkerWish ww = (WorkerWish)w;
            List<WorkerWish> wl = workerWishes.get(ww.getUnitType());
            if (wl != null) wl.remove(ww);
        } else if (w instanceof GoodsWish) {
            GoodsWish gw = (GoodsWish)w;
            List<GoodsWish> gl = goodsWishes.get(gw.getGoodsType());
            if (gl != null) gl.remove(gw);
        } else {
            throw new IllegalStateException("Bogus wish: " + w);
        }
    }

    /**
     * Gets the number of units that should build a colony.
     *
     * @return The desired number of colony builders for this player.
     */
    private int buildersNeeded() {
        Player player = getPlayer();
        if (!player.canBuildColonies()) return 0;

        int nColonies = 0, nPorts = 0, nWorkers = 0;
        for (Settlement settlement : player.getSettlements()) {
            nColonies++;
            if (settlement.isConnectedPort()) nPorts++;
            for (Unit u : settlement.getUnitList()) {
                if (u.isPerson()) nWorkers++;
            }
        }

        // If would be good to have at least two colonies, and at least
        // one port.  After that, determine the ratio of workers to colonies
        // (which should be the average colony size), and if that is above
        // a threshold, send out another colonist.
        // The threshold probably should be configurable.  2 is too
        // low IMHO as it makes a lot of brittle colonies, 3 is too
        // high at least initially as it slows expansion.  For now,
        // arbitrarily choose e.
        int result = (nColonies == 0 || nPorts == 0) ? 2
            : ((nPorts == 1) && nWorkers >= 3) ? 1
            : ((double)nWorkers / nColonies > Math.E) ? 1
            : 0;
        return result;
    }

    /**
     * How many pioneers should we have?
     *
     * @return The desired number of pioneers for this player.
     */
    public int pioneersNeeded() {
        return tipMap.size() / 2;
    }

    /**
     * How many scouts should we have?
     *
     * Current scheme for European AIs is to use up to three scouts in
     * the early part of the game, then one.
     *
     * @return The desired number of scouts for this player.
     */
    public int scoutsNeeded() {
        return (getGame().getTurn().getAge() <= 1) ? 3 : 1;
    }

    /**
     * Asks the server to recruit a unit in Europe on behalf of the AIPlayer.
     *
     * TODO: Move this to a specialized Handler class (AIEurope?)
     * TODO: Give protected access?
     *
     * @param index The index of the unit to recruit in the recruitables list,
     *     (if not a valid index, recruit a random unit).
     * @return The new AIUnit created by this action or null on failure.
     */
    public AIUnit recruitAIUnitInEurope(int index) {
        AIUnit aiUnit = null;
        Europe europe = getPlayer().getEurope();
        if (europe == null) return null;
        int n = europe.getUnitCount();
        final String selectAbility = Ability.SELECT_RECRUIT;
        int slot = (index >= 0 && index < Europe.RECRUIT_COUNT
            && getPlayer().hasAbility(selectAbility)) ? (index + 1) : 0;
        if (AIMessage.askEmigrate(this, slot)
            && europe.getUnitCount() == n+1) {
            aiUnit = getAIUnit(europe.getUnitList().get(n));
        }
        return aiUnit;
    }

    /**
     * Helper function for server communication - Ask the server
     * to train a unit in Europe on behalf of the AIGetPlayer().
     *
     * TODO: Move this to a specialized Handler class (AIEurope?)
     * TODO: Give protected access?
     *
     * @return the new AIUnit created by this action. May be null.
     */
    public AIUnit trainAIUnitInEurope(UnitType unitType) {
        if (unitType==null) {
            throw new IllegalArgumentException("Invalid UnitType.");
        }

        AIUnit aiUnit = null;
        Europe europe = getPlayer().getEurope();
        if (europe == null) return null;
        int n = europe.getUnitCount();

        if (AIMessage.askTrainUnitInEurope(this, unitType)
            && europe.getUnitCount() == n+1) {
            aiUnit = getAIUnit(europe.getUnitList().get(n));
        }
        return aiUnit;
    }

    /**
     * Gets the wishes for all this player's colonies, sorted by the
     * {@link Wish#getValue value}.
     *
     * @return A list of wishes.
     */
    public List<Wish> getWishes() {
        List<Wish> wishes = new ArrayList<Wish>();
        for (AIColony aic : getAIColonies()) {
            wishes.addAll(aic.getWishes());
        }
        Collections.sort(wishes);
        return wishes;
    }

    /**
     * Is this player lagging in naval strength?  Calculate the ratio
     * of its naval strength to the average strength of other European
     * colonial powers.
     *
     * @return The naval strength ratio, or negative if there are no other
     *     European colonial nations.
     */
    private float getNavalStrengthRatio() {
        final Player player = getPlayer();
        float navalAverage = 0;
        float navalStrength = 0;
        int nPlayers = 0;
        for (Player p : getGame().getLiveEuropeanPlayers(null)) {
            if (p.isREF()) continue;
            if (p == player) {
                navalStrength = AIMessage.askGetNationSummary(this, p)
                    .getNavalStrength();
            } else {
                int ns = AIMessage.askGetNationSummary(this, p)
                    .getNavalStrength();
                if (ns >= 0) navalAverage += ns;
                nPlayers++;
            }
        }
        if (nPlayers <= 0 || navalStrength < 0) return -1.0f;
        navalAverage /= nPlayers;
        return navalStrength / navalAverage;
    }

    /**
     * Cheats for the AI.  Please try to centralize cheats here.
     *
     * TODO: Remove when the AI is good enough.
     */
    private void cheat() {
        final AIMain aiMain = getAIMain();
        if (!aiMain.getFreeColServer().isSinglePlayer()) return;

        final Player player = getPlayer();
        if (player.getPlayerType() != PlayerType.COLONIAL) return;

        final Specification spec = getSpecification();
        final Game game = getGame();
        final Market market = player.getMarket();
        final Europe europe = player.getEurope();
        final Random air = getAIRandom();
        final int liftBoycottCheatPercent
            = spec.getInteger(GameOptions.LIFT_BOYCOTT_CHEAT);
        final int equipScoutCheatPercent
            = spec.getInteger(GameOptions.EQUIP_SCOUT_CHEAT);
        final int landUnitCheatPercent
            = spec.getInteger(GameOptions.LAND_UNIT_CHEAT);
        final int offensiveLandUnitCheatPercent
            = spec.getInteger(GameOptions.OFFENSIVE_LAND_UNIT_CHEAT);
        final int offensiveNavalUnitCheatPercent
            = spec.getInteger(GameOptions.OFFENSIVE_NAVAL_UNIT_CHEAT);
        final int transportNavalUnitCheatPercent
            = spec.getInteger(GameOptions.TRANSPORT_NAVAL_UNIT_CHEAT);

        for (GoodsType goodsType : spec.getGoodsTypeList()) {
            if (market.getArrears(goodsType) > 0
                && Utils.randomInt(logger, "Lift Boycott?", air, 100)
                < liftBoycottCheatPercent) {
                market.setArrears(goodsType, 0);
                // Just remove one goods party modifier (we can not
                // currently identify which modifier applies to which
                // goods type, but that is not worth fixing for the
                // benefit of `temporary' cheat code).  If we do not
                // do this, AI colonies accumulate heaps of party
                // modifiers because of the cheat boycott removal.
                findOne: for (Colony c : player.getColonies()) {
                    for (Modifier m : c.getModifierSet()) {
                        if (Modifier.COLONY_GOODS_PARTY.equals(m.getSource())) {
                            c.removeModifier(m);
                            player.logCheat("lift-boycott at " + c.getName());
                            break findOne;
                        }
                    }
                }
            }
        }

        if (!europe.isEmpty()
            && scoutsNeeded() > 0
            && Utils.randomInt(logger, "Equip Scout?", air, 100)
            < equipScoutCheatPercent) {
            for (Unit u : europe.getUnitList()) {
                if (u.hasDefaultRole()
                    && u.hasAbility(Ability.CAN_BE_EQUIPPED)
                    && getAIUnit(u).equipForRole("model.role.scout", true)) {
                    player.logCheat("equipped scout " + u);
                    break;
                }
            }
        }

        if (Utils.randomInt(logger, "Recruit Land Unit?", air, 100)
            < landUnitCheatPercent) {
            WorkerWish bestWish = null;
            int bestValue = Integer.MIN_VALUE;
            for (UnitType ut : workerWishes.keySet()) {
                List<WorkerWish> wl = workerWishes.get(ut);
                if (wl == null
                    || wl.isEmpty()
                    || ut == null
                    || !ut.isAvailableTo(player)
                    || europe.getUnitPrice(ut) == UNDEFINED) continue;
                WorkerWish ww = wl.get(0);
                if (bestValue < ww.getValue()) {
                    bestValue = ww.getValue();
                    bestWish = ww;
                }
            }

            UnitType unitType;
            int cost;
            if (bestWish != null) {
                unitType = bestWish.getUnitType();
                cost = europe.getUnitPrice(unitType);
            } else if (player.getImmigration()
                < player.getImmigrationRequired() / 2) {
                unitType = null;
                cost = player.getRecruitPrice();
            } else {
                unitType = null;
                cost = INFINITY;
                for (UnitType ut : spec.getUnitTypesTrainedInEurope()) {
                    int price = europe.getUnitPrice(ut);
                    if (cost > price) {
                        cost = price;
                        unitType = ut;
                    }
                }
            }
            if (cost != INFINITY) {
                if (cost > 0 && !player.checkGold(cost)) {
                    player.modifyGold(cost);
                }
                AIUnit aiUnit = (unitType == null) ? recruitAIUnitInEurope(-1)
                    : trainAIUnitInEurope(unitType);
                if (aiUnit != null) {
                    if (bestWish != null) {
                        aiUnit.setMission(consumeWorkerWish(aiUnit, bestWish));
                    } else {
                        Mission m = getSimpleMission(aiUnit);
                        if (m != null) aiUnit.setMission(m);
                    }
                    player.logCheat((unitType == null)
                        ? " recruit " + aiUnit.getUnit().getType().toString()
                        : " train " + unitType.toString());
                }
            }
        }

        if (game.getTurn().getAge() >= 2
            && player.isAtWar()
            && Utils.randomInt(logger, "Recruit Offensive Land Unit?", air, 100)
            < offensiveLandUnitCheatPercent) {
            // Find a target to attack.
            Location target = null;
            // - collect enemies, prefer not to antagonize the strong or
            //   crush the weak
            List<Player> enemies = new ArrayList<Player>();
            List<Player> preferred = new ArrayList<Player>();
            for (Player p : game.getLivePlayers(player)) {
                if (player.atWarWith(p)) {
                    enemies.add(p);
                    double strength = getStrengthRatio(p);
                    if (strength < 3.0/2.0 && strength > 2.0/3.0) {
                        preferred.add(p);
                    }
                }
            }
            if (!preferred.isEmpty()) {
                enemies.clear();
                enemies.addAll(preferred);
            }
            List<Colony> colonies = player.getColonies();
            // Few colonies?  Attack the weakest European port
            if (colonies.size() < 3) {
                List<Colony> targets = new ArrayList<Colony>();
                for (Player p : enemies) {
                    if (p.isEuropean()) targets.addAll(p.getColonies());
                }
                double targetScore = -1;
                for (Colony c : targets) {
                    if (c.isConnectedPort()) {
                        double score = 100000.0 / c.getUnitCount();
                        Building stockade = c.getStockade();
                        score /= (stockade == null) ? 1.0
                            : (stockade.getLevel() + 1.5);
                        if (targetScore < score) {
                            targetScore = score;
                            target = c;
                        }
                    }
                }
            }
            // Otherwise attack something near a weak colony
            if (target == null && !colonies.isEmpty()) {
                List<AIColony> bad = new ArrayList<AIColony>();
                for (AIColony aic : getAIColonies()) {
                    if (aic.isBadlyDefended()) bad.add(aic);
                }
                if (bad.isEmpty()) bad.addAll(getAIColonies());
                AIColony defend = Utils.getRandomMember(logger,
                    "AIColony to defend", bad, air);
                Tile center = defend.getColony().getTile();
                Tile t = game.getMap().searchCircle(center,
                    GoalDeciders.getEnemySettlementGoalDecider(enemies),
                    30);
                if (t != null) target = t.getSettlement();
            }
            if (target == null) {
                logger.warning("Failed to find offensive land unit cheat target");
            } else {
                List<Unit> mercs = ((ServerPlayer)player)
                    .createUnits(player.getMonarch().getMercenaries(air),
                                 europe);
                for (Unit u : mercs) {
                    AIUnit aiu = getAIUnit(u);
                    if (aiu == null) {
                        logger.warning("AIUNIT FAIL " + u);
                        aiu = new AIUnit(aiMain, u);
                    }
                    aiu.setMission(new UnitSeekAndDestroyMission(aiMain,
                            aiu, target));
                }
                logger.info("AI offensive land unit cheat ("
                    + mercs.size() + ") targeted: " + target);
            }
        }
            
        // Always cheat a new armed ship if the navy is destroyed,
        // otherwise if the navy is below average the chance to cheat
        // is proportional to how badly below average.
        float naval = getNavalStrengthRatio();
        int nNaval = (naval == 0.0f) ? 100
            : (0.0f < naval && naval < 0.5f)
            ? (int)(naval * offensiveNavalUnitCheatPercent)
            : -1;
        List<RandomChoice<UnitType>> rc
            = new ArrayList<RandomChoice<UnitType>>();
        if (Utils.randomInt(logger, "Build Offensive Naval Unit?", air, 100)
            < nNaval) {
            rc.clear();
            List<UnitType> navalUnits = new ArrayList<UnitType>();
            for (UnitType unitType : spec.getUnitTypeList()) {
                if (unitType.hasAbility(Ability.NAVAL_UNIT)
                    && unitType.isAvailableTo(player)
                    && unitType.hasPrice()
                    && unitType.isOffensive()) {
                    navalUnits.add(unitType);
                    int weight = unitType.getOffence()
                        * 100000 / europe.getUnitPrice(unitType);
                    rc.add(new RandomChoice<UnitType>(unitType, weight));
                }
            }
            cheatUnit(rc);
        }
        // Only cheat carriers if they have work to do.
        int nCarrier = (nNavalCarrier > 0) ? transportNavalUnitCheatPercent
            : -1;
        if (Utils.randomInt(logger, "Build Transport Naval Unit?", air, 100)
            < nCarrier) {
            rc.clear();
            List<UnitType> navalUnits = new ArrayList<UnitType>();
            for (UnitType unitType : spec.getUnitTypeList()) {
                if (unitType.hasAbility(Ability.NAVAL_UNIT)
                    && unitType.isAvailableTo(player)
                    && unitType.hasPrice()
                    && unitType.getSpace() > 0) {
                    navalUnits.add(unitType);
                    int weight = unitType.getSpace()
                        * 100000 / europe.getUnitPrice(unitType);
                    rc.add(new RandomChoice<UnitType>(unitType, weight));
                }
            }
            cheatUnit(rc);
        }
    }

    /**
     * Cheat-build a unit in Europe.
     *
     * @param rc A list of random choices to choose from.
     * @return The <code>AIUnit</code> built.
     */
    private AIUnit cheatUnit(List<RandomChoice<UnitType>> rc) {
        final Random random = getAIRandom();
        UnitType unitToPurchase
            = RandomChoice.getWeightedRandom(logger, "Cheat which unit",
                                             rc, random);
        return cheatUnit(unitToPurchase);
    }

    /**
     * Cheat-build a unit in Europe.
     *
     * @param unitType The <code>UnitType</code> to build.
     * @return The <code>AIUnit</code> built.
     */
    private AIUnit cheatUnit(UnitType unitType) {
        final Player player = getPlayer();
        final Europe europe = player.getEurope();
        int cost = europe.getUnitPrice(unitType);
        if (cost > 0 && !player.checkGold(cost)) player.modifyGold(cost);
        AIUnit aiUnit = trainAIUnitInEurope(unitType);
        if (aiUnit != null) player.logCheat("build " + unitType);
        return aiUnit;
    }

    /**
     * Ensures all units have a mission.
     */
    protected void giveNormalMissions() {
        final AIMain aiMain = getAIMain();
        final Player player = getPlayer();
        final int turnNumber = getGame().getTurn().getNumber();
        reasons.clear();
        List<TransportMission> transportMissions
            = new ArrayList<TransportMission>();
        nBuilders = buildersNeeded();
        nPioneers = pioneersNeeded();
        nScouts = scoutsNeeded();

        // For all units, check if it is a candidate for a new
        // mission.  If it is not a candidate remove it from the
        // aiUnits list (reporting why not).  Adjust the
        // Build/Pioneer/Scout counts according to the existing valid
        // missions.
        List<AIUnit> aiUnits = getAIUnits();
        List<AIUnit> navalUnits = new ArrayList<AIUnit>();
        int allUnits = aiUnits.size(), i = 0;
        while (i < aiUnits.size()) {
            final AIUnit aiUnit = aiUnits.get(i);
            final Unit unit = aiUnit.getUnit();
            Mission m = aiUnit.getMission();

            if (unit.isUninitialized() || unit.isDisposed()) {
                putReason(aiUnit, "Invalid");

            } else if (unit.getState() == UnitState.IN_COLONY
                && unit.getColony().getUnitCount() <= 1) {
                // The unit has its hand full keeping the colony alive.
                if (!(aiUnit.getMission() instanceof WorkInsideColonyMission)){
                    logger.warning(aiUnit + " should WorkInsideColony at "
                        + unit.getColony().getName());
                    m = new WorkInsideColonyMission(aiMain, aiUnit,
                        aiMain.getAIColony(unit.getColony()));
                }
                putReason(aiUnit,
                    "Vital-to-" + unit.getSettlement().getName());

            } else if (unit.isInMission()) {
                putReason(aiUnit, "In-Mission");

            } else if (m != null && m.isValid() && !m.isOneTime()) {
                if (m instanceof BuildColonyMission) {
                    nBuilders--;
                } else if (m instanceof PioneeringMission) {
                    nPioneers--;
                } else if (m instanceof ScoutingMission) {
                    nScouts--;
                } else if (m instanceof TransportMission) {
                    TransportMission tm = (TransportMission)m;
                    if (tm.destinationCapacity() > 0) {
                        transportMissions.add(tm);
                    }
                }
                putReason(aiUnit, "Valid");

            } else if (unit.isAtSea()) { // Wait for it to emerge
                putReason(aiUnit, "At-Sea");

            } else if (unit.isNaval()) {
                navalUnits.add(aiUnit);

            } else { // Unit needs a mission
                if (m != null) { // Abort invalid missions now
                    String reason = m.invalidReason();
                    if (reason != null) aiUnit.abortMission(reason);
                }
                i++;
                continue;
            }
            aiUnits.remove(i);
        }
        StringBuffer sb = null;
        if (logger.isLoggable(Level.FINE)) {
            sb = new StringBuffer(256);
            sb.append(Utils.lastPart(getPlayer().getNationId(), "."))
                .append(".giveNormalMissions(turn=").append(turnNumber)
                .append(" colonies=").append(getPlayer().getNumberOfSettlements())
                .append(" all-units=").append(allUnits)
                .append(" free-land-units=").append(aiUnits.size())
                .append(" free-naval-units=").append(navalUnits.size())
                .append(" builders=").append(nBuilders)
                .append(" pioneers=").append(nPioneers)
                .append(" scouts=").append(nScouts)
                .append(" naval-carriers=").append(nNavalCarrier)
                .append(")");
        }

        // First try to satisfy the demand for missions with a defined quota.
        if (nBuilders > 0) {
            Collections.sort(aiUnits, builderComparator);
            while (!aiUnits.isEmpty()) {
                AIUnit aiUnit = aiUnits.get(0);
                Mission m = getBuildColonyMission(aiUnit);
                if (m == null) break; // Reached the unsuitable units
                aiUnits.remove(0);
                aiUnit.setMission(m);
                if (requestsTransport(aiUnit)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aiUnit.getTransportSource()), aiUnit);
                }
                putReason(aiUnit, "New");
                if (--nBuilders <= 0) break;
            }
        }
        if (nScouts > 0) {
            Collections.sort(aiUnits, scoutComparator);
            while (!aiUnits.isEmpty()) {
                AIUnit aiUnit = aiUnits.get(0);
                Mission m = getScoutingMission(aiUnit);
                if (m == null) break; // Reached the unsuitable units
                aiUnits.remove(0);
                aiUnit.setMission(m);
                if (requestsTransport(aiUnit)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aiUnit.getTransportSource()), aiUnit);
                }
                putReason(aiUnit, "New");
                if (--nScouts <= 0) break;
            }
        }
        if (nPioneers > 0) {
            Collections.sort(aiUnits, pioneerComparator);
            while (!aiUnits.isEmpty()) {
                AIUnit aiUnit = aiUnits.get(0);
                Mission m = getPioneeringMission(aiUnit);
                if (m == null) break; // Reached the unsuitable units
                aiUnits.remove(0);
                aiUnit.setMission(m);
                if (requestsTransport(aiUnit)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aiUnit.getTransportSource()), aiUnit);
                }
                putReason(aiUnit, "New");
                if (--nPioneers <= 0) break;
            }
        }

        // Give the remaining land units a valid mission.
        i = 0;
        while (i < aiUnits.size()) {
            final AIUnit aiUnit = aiUnits.get(i);
            final Unit unit = aiUnit.getUnit();
            Mission m = getSimpleMission(aiUnit);

            if (m != null) {
                aiUnit.setMission(m);
                if (requestsTransport(aiUnit)) {
                    Utils.appendToMapList(transportSupply,
                        upLoc(aiUnit.getTransportSource()), aiUnit);
                }
                putReason(aiUnit, "New");
                aiUnits.remove(i);
            } else {
                i++;
            }
        }

        // Process the free naval units, possibly adding to the usable
        // transport missions.
        i = 0;
        while (i < navalUnits.size()) {
            final AIUnit aiUnit = navalUnits.get(i);
            Mission m = getSimpleMission(aiUnit);

            if (m != null) {
                aiUnit.setMission(m);
                putReason(aiUnit, "New");
                navalUnits.remove(i);
                if (m instanceof TransportMission) {
                    TransportMission tm = (TransportMission)m;
                    if (tm.destinationCapacity() > 0) {
                        transportMissions.add(tm);
                    }
                    // A new transport mission might have retargeted
                    // its passengers into new valid missions.
                    for (Unit u : aiUnit.getUnit().getUnitList()) {
                        AIUnit aiu = getAIUnit(u);
                        Mission um = aiu.getMission();
                        if (um != null && um.isValid()
                            && aiUnits.contains(aiu)) {
                            aiUnits.remove(aiu);
                            putReason(aiu, "New");
                        }
                    }
                }
            } else {
                i++;
            }
        }

        // Now see if transport can be found
        allocateTransportables(transportMissions);

        // Give remaining units the fallback mission.
        aiUnits.addAll(navalUnits);
        for (AIUnit aiUnit : aiUnits) {
            Mission m = aiUnit.getMission();
            if (m != null && m.isValid() && !m.isOneTime()) {
                // Might have picked up a reason in allocateTransportables
                continue;
            }

            if (aiUnit.getMission() instanceof IdleAtSettlementMission) {
                m = aiUnit.getMission();
            } else {
                m = new IdleAtSettlementMission(aiMain, aiUnit);
                aiUnit.setMission(m);
            }
            putReason(aiUnit, "UNUSED");
        }

        // We are done.  Report.
        if (logger.isLoggable(Level.FINE)) {
            for (AIUnit aiu : getAIUnits()) {
                Unit u = aiu.getUnit();
                String reason = reasons.get(u);
                if (reason == null) reason = "OMITTED";
                Mission m = aiu.getMission();
                sb.append("\n  ").append(u.getLocation())
                    .append(" ").append(reason)
                    .append("-").append((m == null)
                        ? "NoMission"
                        : (m instanceof TransportMission)
                        ? ((TransportMission)m).toFullString()
                        : m.toString());
            }
            logger.fine(sb.toString());
        }
    }

    private void putReason(AIUnit aiUnit, String reason) {
        final Unit unit = aiUnit.getUnit();
        final Mission mission = aiUnit.getMission();
        reasons.put(unit, reason);
    }

    /**
     * Choose a mission for an AIUnit.
     *
     * @param aiUnit The <code>AIUnit</code> to choose for.
     * @return A suitable <code>Mission</code>, or null if none found.
     */
    private Mission getSimpleMission(AIUnit aiUnit) {
        final Unit unit = aiUnit.getUnit();
        Mission m;

        if (unit.isNaval()) {
            if ((m = getPrivateerMission(aiUnit)) != null
                || (m = getTransportMission(aiUnit)) != null
                || (m = getSeekAndDestroyMission(aiUnit, 8)) != null
                || (m = getWanderHostileMission(aiUnit)) != null
                ) return m;

        } else if (unit.isCarrier()) {
            return getTransportMission(aiUnit);

        } else {
            // CashIn missions are obvious
            if ((m = getCashInTreasureTrainMission(aiUnit)) != null

                // Try to maintain defence
                || (unit.isDefensiveUnit()
                    && (m = getDefendSettlementMission(aiUnit, false)) != null)

                // Favour wish realization for expert units
                || (unit.isColonist() && unit.getSkillLevel() > 0
                    && (m = getWishRealizationMission(aiUnit)) != null)

                // Try nearby offence
                || (unit.isOffensiveUnit()
                    && (m = getSeekAndDestroyMission(aiUnit, 8)) != null)

                // Missionary missions are only available to some units
                || (m = getMissionaryMission(aiUnit)) != null

                // Try to satisfy any remaining wishes, such as population
                || ((m = getWishRealizationMission(aiUnit)) != null)

                // Another try to defend, with relaxed cost decider
                || (unit.isDefensiveUnit()
                    && (m = getDefendSettlementMission(aiUnit, true)) != null)

                // Another try to attack, at longer range
                || (unit.isOffensiveUnit()
                    && (m = getSeekAndDestroyMission(aiUnit, 16)) != null)

                // Leftover offensive units should go out looking for trouble
                || (unit.isOffensiveUnit()
                    && (m = getWanderHostileMission(aiUnit)) != null)
                ) return m;
        }
        return null;
    }

    /**
     * What is the best transportable for a carrier to collect?
     *
     * @param carrier The carrier <code>Unit</code> to consider.
     * @return The best transportable, or null if none found.
     */
    public Transportable getBestTransportable(Unit carrier) {
        final Location src = (carrier.isAtSea()) ? carrier.resolveDestination()
            : carrier.getLocation();
        Transportable best = null;
        float bestValue = 0.0f;
        boolean present = false;
        for (Location loc : transportSupply.keySet()) {
            List<Transportable> tl = transportSupply.get(loc);
            if (tl.isEmpty()) continue;
            Collections.sort(tl, Transportable.transportableComparator);
            for (Transportable t : tl) {
                if (t.isDisposed()) continue;
                if (upLoc(t.getTransportSource()) != loc) {
                    logger.warning("Transportable " + t
                        + " should have been claimed from " + loc
                        + " now at " + t.getTransportLocatable().getLocation());
                    continue;
                }
                if (!t.carriableBy(carrier)) continue;
                if (Map.isSameLocation(src, loc)) {
                    best = t;
                } else {
                    int turns = (t instanceof AIUnit)
                        ? ((AIUnit)t).getUnit().getTurnsToReach(src, loc,
                                                                carrier, null)
                        : carrier.getTurnsToReach(src, loc);
                    if (turns != INFINITY) {
                        float value = t.getTransportPriority() / (turns + 1);
                        if (bestValue < value) {
                            bestValue = value;
                            best = t;
                        }
                    }
                }
                break; // Only consider the first carriable transportable
            }
        }
        return best;
    }

    /**
     * Assign transportable units and goods to available carriers.
     *
     * These supply driven assignments supplement the demand driven
     * calls inside TransportMission.
     *
     * @param missions A list of <code>TransportMission</code>s to potentially
     *     assign more transportables to.
     */
    private void allocateTransportables(List<TransportMission> missions) {
        if (missions.isEmpty()) return;

        StringBuffer sb = new StringBuffer(64);
        sb.append("allocateTransportables(").append(missions.size())
            .append("):");
        List<Transportable> urgent = getUrgentTransportables();
        for (Transportable t : urgent) sb.append(" ").append(t.toString());
        logger.info(sb.toString());

        int i = 0;
        outer: while (i < urgent.size()) {
            if (missions.isEmpty()) break;
            Transportable t = urgent.get(i);
            TransportMission best = null;
            float bestValue = 0.0f;
            boolean present = false;
            for (TransportMission tm : missions) {
                if (!tm.spaceAvailable(t)) continue;
                Cargo cargo = tm.makeCargo(t);
                if (cargo == null) { // Serious problem with this cargo
                    urgent.remove(i);
                    continue outer;
                }
                int turns = cargo.getTurns();
                float value;
                if (turns == 0) {
                    value = tm.destinationCapacity();
                    if (!present) {
                        bestValue = 0.0f;
                        present = true;
                    }
                } else if (present) {
                    continue;
                } else {
                    value = (float)t.getTransportPriority() / turns;
                }
                if (bestValue < value) {
                    bestValue = value;
                    best = tm;
                }
            }
            if (best != null) {
                if (best.queueTransportable(t, false)) {
                    logger.finest("Queued " + t + " to " + best);
                    claimTransportable(t);
                    if (best.destinationCapacity() <= 0) {
                        missions.remove(best);
                    }
                } else {
                    logger.warning("Failed to queue " + t + " to " + best);
                    missions.remove(best);
                }
            }
            i++;
        }
    }

    /**
     * Gets a new BuildColonyMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getBuildColonyMission(AIUnit aiUnit) {
        String reason = BuildColonyMission.invalidReason(aiUnit);
        if (reason != null) return null;
        final Unit unit = aiUnit.getUnit();
        Location loc = BuildColonyMission.findTarget(aiUnit, buildingRange,
                                                     unit.isInEurope());
        return (loc == null) ? null
            : new BuildColonyMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new CashInTreasureTrainMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getCashInTreasureTrainMission(AIUnit aiUnit) {
        String reason = CashInTreasureTrainMission.invalidReason(aiUnit);
        if (reason != null) return null;
        final Unit unit = aiUnit.getUnit();
        Location loc = CashInTreasureTrainMission.findTarget(aiUnit,
            cashInRange, unit.isInEurope());
        return (loc == null) ? null
            : new CashInTreasureTrainMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new DefendSettlementMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param relaxed Use a relaxed cost decider to choose the target.
     * @return A new mission, or null if impossible.
     */
    private Mission getDefendSettlementMission(AIUnit aiUnit, boolean relaxed) {
        String reason = DefendSettlementMission.invalidReason(aiUnit);
        if (reason != null) return null;
        final Unit unit = aiUnit.getUnit();
        final Location loc = unit.getLocation();
        double worstValue = 1000000.0;
        Colony worstColony = null;
        for (AIColony aic : getAIColonies()) {
            Colony colony = aic.getColony();
            if (aic.isBadlyDefended()) {
                if (unit.isAtLocation(colony.getTile())) {
                    worstColony = colony;
                    break;
                }
                double value = colony.getDefenceRatio() * 100.0
                    / unit.getTurnsToReach(loc, colony.getTile(),
                        unit.getCarrier(),
                        ((relaxed) ? CostDeciders.numberOfTiles() : null));
                if (worstValue > value) {
                    worstValue = value;
                    worstColony = colony;
                }
            }
        }
        if (worstColony == null) return null;
        return new DefendSettlementMission(getAIMain(), aiUnit, worstColony);
    }

    /**
     * Gets a new MissionaryMission for a unit.
     *
     * Public for AIColony.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getMissionaryMission(AIUnit aiUnit) {
        String reason = MissionaryMission.prepare(aiUnit);
        if (reason != null) return null;
        Location loc = MissionaryMission.findTarget(aiUnit, missionaryRange,
                                                    true);
        return (loc == null) ? null
            : new MissionaryMission(getAIMain(), aiUnit);
    }

    /**
     * Gets a new PioneeringMission for a unit.
     * TODO: pioneers to make roads between colonies
     *
     * Public for AIColony.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getPioneeringMission(AIUnit aiUnit) {
        String reason = PioneeringMission.prepare(aiUnit);
        if (reason != null) return null;
        Location loc = PioneeringMission.findTarget(aiUnit, pioneeringRange,
                                                    true);
        return (loc == null) ? null
            : new PioneeringMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new PrivateerMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getPrivateerMission(AIUnit aiUnit) {
        String reason = PrivateerMission.invalidReason(aiUnit);
        if (reason != null) return null;
        return new PrivateerMission(getAIMain(), aiUnit);
    }

    /**
     * Gets a new ScoutingMission for a unit.
     *
     * Public for AIColony.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    public Mission getScoutingMission(AIUnit aiUnit) {
        String reason = ScoutingMission.prepare(aiUnit);
        if (reason != null) return null;
        Location loc = ScoutingMission.findTarget(aiUnit, scoutingRange, true);
        return (loc == null) ? null
            : new ScoutingMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a UnitSeekAndDestroyMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param range A maximum range to search for a target within.
     * @return A new mission, or null if impossible.
     */
    public Mission getSeekAndDestroyMission(AIUnit aiUnit, int range) {
        String reason = UnitSeekAndDestroyMission.invalidReason(aiUnit);
        if (reason != null) return null;
        Location loc = UnitSeekAndDestroyMission.findTarget(aiUnit, range,
                                                            false);
        return (loc == null) ? null
            : new UnitSeekAndDestroyMission(getAIMain(), aiUnit, loc);
    }

    /**
     * Gets a new TransportMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getTransportMission(AIUnit aiUnit) {
        String reason = TransportMission.invalidReason(aiUnit);
        if (reason != null) return null;
        return new TransportMission(getAIMain(), aiUnit);
    }

    /**
     * Gets a new UnitWanderHostileMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getWanderHostileMission(AIUnit aiUnit) {
        String reason = UnitWanderHostileMission.invalidReason(aiUnit);
        if (reason != null) return null;
        return new UnitWanderHostileMission(getAIMain(), aiUnit);
    }

    /**
     * Gets a new WishRealizationMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @return A new mission, or null if impossible.
     */
    private Mission getWishRealizationMission(AIUnit aiUnit) {
        final Unit unit = aiUnit.getUnit();
        List<WorkerWish> wwL = workerWishes.get(unit.getType());
        WorkerWish best = getBestWorkerWish(aiUnit, wwL);
        return (best == null) ? null : consumeWorkerWish(aiUnit, best);
    }

    /**
     * Consume a WorkerWish, yielding a WishRealizationMission for a unit.
     *
     * @param aiUnit The <code>AIUnit</code> to check.
     * @param ww The <code>WorkerWish</code> to consume.
     * @return A new <code>WishRealizationMission</code>.
     */
    private Mission consumeWorkerWish(AIUnit aiUnit, WorkerWish ww) {
        final Unit unit = aiUnit.getUnit();
        List<WorkerWish> wwL = workerWishes.get(unit.getType());
        wwL.remove(ww);
        List<Wish> wl = transportDemand.get(ww.getDestination());
        if (wl != null) wl.remove(ww);
        ww.setTransportable(aiUnit);
        return new WishRealizationMission(getAIMain(), aiUnit, ww);
    }

    /**
     * Brings gifts to nice players with nearby colonies.
     *
     * TODO: European players can also bring gifts! However,
     * this might be folded into a trade mission, since
     * European gifts are just a special case of trading.
     */
    private void bringGifts() {
        return;
    }

    /**
     * Demands goods from players with nearby colonies.
     *
     * TODO: European players can also demand tribute!
     */
    private void demandTribute() {
        return;
    }

    /**
     * Simple initialization of AI missions given that we know the starting
     * conditions.
     */
    private void initializeMissions() {
        List<AIUnit> aiUnits = getAIUnits();
        final AIMain aiMain = getAIMain();

        // Find all the carriers with potential colony builders on board,
        // give them missions.
        List<Unit> carriers = new ArrayList<Unit>();
        Location target;
        carrier: for (AIUnit aiCarrier : aiUnits) {
            if (aiCarrier.hasMission()) continue;
            Unit carrier = aiCarrier.getUnit();
            if (!carrier.isNaval()) continue;
            target = null;
            for (Unit u : carrier.getUnitList()) {
                AIUnit aiu = aiMain.getAIUnit(u);
                if (target == null) {
                    target = BuildColonyMission.findTarget(aiu,
                        buildingRange*3, false);
                    if (target == null) continue carrier;
                }
                aiu.setMission(new BuildColonyMission(aiMain, aiu, target));
            }
            if (target != null) {
                aiCarrier.setMission(new TransportMission(aiMain, aiCarrier));
            }
        }

        // Put in some backup missions.
        for (AIUnit aiu : aiUnits) {
            if (aiu.hasMission()) continue;
            Mission m = getSimpleMission(aiu);
            if (m != null) aiu.setMission(m);
        }
    }

    /**
     * See if a recent peace treaty still has force.
     *
     * @param p The <code>Player</code> to check for a peace treaty with.
     * @return True if peace gets another chance.
     */
    private boolean peaceHolds(Player p) {
        final Player player = getPlayer();
        final Turn turn = getGame().getTurn();
        final double peaceProb = getSpecification()
            .getInteger(GameOptions.PEACE_PROBABILITY) / 100.0;

        int peaceTurn = -1;
        for (HistoryEvent h : player.getHistory()) {
            if (p.getId().equals(h.getPlayerId())
                && h.getTurn().getNumber() > peaceTurn) {
                switch (h.getEventType()) {
                case MAKE_PEACE: case FORM_ALLIANCE:
                    peaceTurn = h.getTurn().getNumber();
                    break;
                case DECLARE_WAR:
                    peaceTurn = -1;
                    break;
                default:
                    break;
                }
            }
        }
        if (peaceTurn < 0) return false;

        int n = turn.getNumber() - peaceTurn;
        float prob = (float)Math.pow(peaceProb, n);
        // Apply Franklin's modifier
        prob = FeatureContainer.applyModifierSet(prob, turn,
            p.getModifierSet(Modifier.PEACE_TREATY));
        return prob > 0.0f
            && Utils.randomInt(logger, "Peace holds?", 
                               getAIRandom(), 100) < (int)(100.0f * prob);
    }

    /**
     * Determines the stances towards each player.
     */
    private void determineStances() {
        final Player player = getPlayer();

        for (Player p : getGame().getLivePlayers(player)) {
            Stance newStance = determineStance(p);
            if (newStance != player.getStance(p)) {
                if (newStance == Stance.WAR && peaceHolds(p)) {
                    ; // Peace treaty holds for now
                } else {
                    getAIMain().getFreeColServer().getInGameController()
                        .changeStance(player, newStance, p, true);
                }
            }
        }
    }


    // Diplomacy support

    protected double getStrengthRatio(Player other) {
        NationSummary ns = AIMessage.askGetNationSummary(this, other);
        int strength = getPlayer().calculateStrength(false);
        return (double)strength / (strength + ns.getMilitaryStrength());
    }

    private int evaluateColony(Colony colony) {
        if (getPlayer().getEurope() == null) return Integer.MIN_VALUE;

        int result = 0, v;
        for (WorkLocation wl : colony.getAvailableWorkLocations()) {
            for (Unit u : wl.getUnitList()) {
                result += evaluateUnit(u);
            }
            if (wl instanceof Building) {
                for (AbstractGoods ag : ((Building)wl).getType()
                         .getRequiredGoods()) {
                    result += evaluateGoods(ag);
                }
            } else if (wl instanceof ColonyTile) {
                for (AbstractGoods ag : ((ColonyTile)wl).getProductionInfo().getProduction()) {
                    result += evaluateGoods(ag);
                }
            }
        }
        for (Unit u : colony.getTile().getUnitList()) {
            result += evaluateUnit(u);
        }
        for (Goods g : colony.getCompactGoods()) {
            result += evaluateGoods(g);
        }
        return result;
    }

    private int evaluateUnit(Unit unit) {
        final Europe europe = getPlayer().getEurope();

        return (europe == null) ? Integer.MIN_VALUE
            : europe.getUnitPrice(unit.getType());
    }

    private int evaluateGoods(AbstractGoods ag) {
        final Market market = getPlayer().getMarket();

        return (market == null) ? Integer.MIN_VALUE
            : market.getSalePrice(ag.getType(), ag.getAmount());
    }

    /**
     * Reject a trade agreement, except if a Franklin-derived stance
     * is supplied.
     *
     * @param stance An optional stance <code>TradeItem</code>.
     * @param agreement The <code>DiplomaticTrade</code> to reset.
     * @return The <code>TradeStatus</code> for the agreement.
     */
    private TradeStatus rejectAgreement(TradeItem stance,
                                        DiplomaticTrade agreement) {
        if (stance == null) return TradeStatus.REJECT_TRADE;
        
        agreement.clear();
        agreement.add(stance);
        return TradeStatus.PROPOSE_TRADE;
    }


    // AIPlayer interface

    /**
     * {@inheritDoc}
     */
    public void startWorking() {
        Player player = getPlayer();
        Turn turn = getGame().getTurn();
        logger.finest(getClass().getName() + " in " + turn
            + " declare=" + (player.checkDeclareIndependence() == null)
            + " strength=" + player.getRebelStrengthRatio()
            + ": " + Utils.lastPart(player.getNationId(), "."));
        sessionRegister.clear();
        clearAIUnits();
        determineStances();
        if (turn.isFirstTurn()) initializeMissions();
        buildTipMap();

        StringBuffer sb = new StringBuffer(64);
        for (AIColony aic : getAIColonies()) {
            aic.rearrangeWorkers();
            aic.updateAIGoods();
            if (aic.isBadlyDefended()) {
                sb.append(" ").append(aic.getColony().getName());
            }
        }
        if (logger.isLoggable(Level.FINEST) && sb.length() > 0) {
            sb.insert(0, "Badly defended colonies:");
            logger.finest(sb.toString());
        }
        
        buildTransportMaps();
        buildWishMaps();
        cheat();

        giveNormalMissions();
        bringGifts();
        demandTribute();
        doMissions();

        for (AIColony aic : getAIColonies()) aic.rearrangeWorkers();
        buildTransportMaps();
        buildWishMaps();
        giveNormalMissions();
        doMissions();

        for (AIColony aic : getAIColonies()) aic.rearrangeWorkers();
        clearAIUnits();
        tipMap.clear();
        transportDemand.clear();
        transportSupply.clear();
        wagonsNeeded.clear();
        goodsWishes.clear();
        workerWishes.clear();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void doMissions() {
        StringBuffer sb = new StringBuffer(64);
        sb.append("doMissions, normal: ");
        List<AIUnit> aiUnits = getAIUnits();
        for (AIUnit aiu : aiUnits) {
            sb.append(aiu);
            if (aiu.getMission() instanceof TransportMission) continue;
            try {
                aiu.doMission();
            } catch (Exception e) {
                logger.log(Level.WARNING, "doMissions failed for: " + aiu, e);
            }
        }
        sb.append(", transport: ");
        for (AIUnit aiu : aiUnits) {
            sb.append(aiu);
            if (!(aiu.getMission() instanceof TransportMission)) continue;
            try {
                aiu.doMission();
            } catch (Exception e) {
                logger.log(Level.WARNING, "doMissions failed for: " + aiu, e);
            }
        }
        if (logger.isLoggable(Level.FINEST)) logger.finest(sb.toString());
    }

    /**
     * {@inheritDoc}
     */
    public int adjustMission(AIUnit aiUnit, PathNode path, Class type,
                             int value) {
        if (value > 0) {
            if (type == DefendSettlementMission.class) {
                // Reduce value in proportion to the number of defenders.
                Location loc = DefendSettlementMission.extractTarget(aiUnit, path);
                if (!(loc instanceof Colony)) {
                    throw new IllegalStateException("European players defend colonies: " + loc);
                }
                Colony colony = (Colony)loc;
                int defenders = getSettlementDefenders(colony);
                value -= 25 * defenders;
                // Reduce value according to the stockade level.
                if (colony.hasStockade()) {
                    if (defenders > colony.getStockade().getLevel() + 1) {
                        value -= 100 * colony.getStockade().getLevel();
                    } else {
                        value -= 20 * colony.getStockade().getLevel();
                    }
                }
            }
        }
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean indianDemand(Unit unit, Colony colony,
                                GoodsType goods, int gold) {
        // TODO: make a better choice, check whether the colony is
        // well defended
        return !"conquest".equals(getAIAdvantage());
    }

    /**
     * {@inheritDoc}
     */
    public TradeStatus acceptDiplomaticTrade(DiplomaticTrade agreement) {
        final Player player = getPlayer();
        final Player other = agreement.getOtherPlayer(player);
        final Market market = player.getMarket();
        final boolean franklin
            = other.hasAbility(Ability.ALWAYS_OFFERED_PEACE);
        final java.util.Map<TradeItem, Integer> scores
            = new HashMap<TradeItem, Integer>();
        TradeItem peace = null;
        TradeItem cash = null;

        int unacceptable = 0;
        for (TradeItem item : agreement.getTradeItems()) {
            int value;
            if (item instanceof GoldTradeItem) {
                cash = item;
                int gold = ((GoldTradeItem)item).getGold();
                if (item.getSource() == player) {
                    value = -gold;
                } else {
                    value = gold;
                }

            } else if (item instanceof ColonyTradeItem) {
                if (item.getSource() == player) {
                    if (player.getNumberOfSettlements() < 5) {
                        value = Integer.MIN_VALUE;
                    } else {
                        value = -evaluateColony(item.getColony());
                    }
                } else {
                    value = evaluateColony(item.getColony());
                }

            } else if (item instanceof GoodsTradeItem) {
                Goods goods = ((GoodsTradeItem)item).getGoods();
                if (item.getSource() == player) {
                    value = -market.getBidPrice(goods.getType(),
                                                goods.getAmount());
                } else {
                    value = market.getSalePrice(goods.getType(),
                                                goods.getAmount());
                }

            } else if (item instanceof InciteTradeItem) {
                // TODO, rebalance this
                Player victim = item.getVictim();
                switch (player.getStance(victim)) {
                case ALLIANCE:
                    value = -1;
                    break;
                case WAR: // Not invalid, other player may not know our stance
                    value = 0;
                    break;
                default:
                    double ratio = getStrengthRatio(victim);
                    if (item.getSource() == player) {
                        value = -(int)Math.round(30 * ratio);
                    } else {
                        value = (int)Math.round(30 * ratio);
                    }
                    break;
                }

            } else if (item instanceof StanceTradeItem) {
                double ratio = getStrengthRatio(other);
                // TODO: evaluate whether we want this stance change
                Stance stance = ((StanceTradeItem)item).getStance();
                switch (stance) {
                case WAR:
                    if (ratio < 0.33) {
                        value = Integer.MIN_VALUE;
                    } else if (ratio < 0.5) {
                        value = -(int)Math.round(100 * ratio);
                    } else {
                        value = (int)Math.round(100 * ratio);
                    }
                    break;
                case PEACE:
                    if (agreement.getContext() == DiplomaticTrade.TradeContext.CONTACT) {
                        peace = item;
                        value = 0;
                        break;
                    }
                    // Fall through
                case CEASE_FIRE: case ALLIANCE:
                    if (franklin) {
                        peace = item;
                        value = 0;
                    } else if (ratio > 0.66) {
                        value = Integer.MIN_VALUE;
                    } else if (ratio > 0.5) {
                        value = -(int)Math.round(100 * ratio);
                    } else if (ratio > 0.33) {
                        value = (int)Math.round(100 * ratio);
                    } else {
                        value = 1000;
                    }                       
                    break;
                case UNCONTACTED:
                default:
                    value = Integer.MIN_VALUE;
                    break;
                }

            } else if (item instanceof UnitTradeItem) {
                if (item.getSource() == player) {
                    if (player.getUnits().size() < 10) {
                        value = Integer.MIN_VALUE;
                    } else {
                        value = -evaluateUnit(item.getUnit());
                    }
                } else {
                    value = evaluateUnit(item.getUnit());
                }

            } else {
                throw new RuntimeException("Bogus item: " + item);
            }

            if (value == Integer.MIN_VALUE) unacceptable++;
            scores.put(item, new Integer(value));
        }

        // If too many items are unacceptable, reject
        double ratio = (double)unacceptable
            / (unacceptable + agreement.getTradeItems().size());
        if (ratio > 0.5 - 0.5 * agreement.getVersion()) {
            return rejectAgreement(peace, agreement);
        }

        // Dump the unacceptable offers, sum the rest
        int value = 0;
        for (Entry<TradeItem, Integer> entry : scores.entrySet()) {
            if (entry.getValue() == Integer.MIN_VALUE) {
                agreement.remove(entry.getKey());
            } else {
                value += entry.getValue();
            }
        }
        // If the result is non-negative, accept/propose-without-unacceptable
        if (value >= 0) {
            return (agreement.getContext() == DiplomaticTrade.TradeContext.CONTACT
                && agreement.getVersion() == 0) ? TradeStatus.PROPOSE_TRADE
                : (unacceptable == 0) ? TradeStatus.ACCEPT_TRADE
                : TradeStatus.PROPOSE_TRADE;
        }

        // Give up?
        if (Utils.randomInt(logger, "Enough diplomacy?", getAIRandom(),
                            1 + agreement.getVersion()) > 5) {
            return rejectAgreement(peace, agreement);
        }

        // Dump the negative offers until the sum is positive.
        // Return a proposal with items we like/can accept, or reject
        // if none are left.
        List<TradeItem> items
            = new ArrayList<TradeItem>(agreement.getTradeItems());
        Collections.sort(items, new Comparator<TradeItem>() {
                public int compare(TradeItem t1, TradeItem t2) {
                    return scores.get(t1) - scores.get(t2);
                }
            });
        while (!items.isEmpty()) {
            TradeItem item = items.remove(0);
            value += scores.get(item);
            agreement.remove(item);
            if (value > 0) break;
        }
        return (value > 0 && !items.isEmpty()) ? TradeStatus.PROPOSE_TRADE
            : rejectAgreement(peace, agreement);
    }


    /**
     * {@inheritDoc}
     */
    public void registerSellGoods(Goods goods) {
        String goldKey = "tradeGold#" + goods.getType().getId()
            + "#" + goods.getAmount() + "#" + goods.getLocation().getId();
        sessionRegister.put(goldKey, null);
    }

    /**
     * {@inheritDoc}
     */
    public int buyProposition(Unit unit, Settlement settlement, Goods goods,
                              int gold) {
        logger.finest("Entering method buyProposition");
        final Specification spec = getSpecification();
        Player buyer = unit.getOwner();
        IndianSettlement is = (IndianSettlement)settlement;
        String goldKey = "tradeGold#" + goods.getType().getId()
            + "#" + goods.getAmount() + "#" + settlement.getId();
        String hagglingKey = "tradeHaggling#" + unit.getId();
        Integer registered = sessionRegister.get(goldKey);
        if (registered == null) {
            int price = is.getPriceToSell(goods)
                + getPlayer().getTension(buyer).getValue();
            if (is.hasMissionary(buyer)
                && spec.getBoolean(GameOptions.ENHANCED_MISSIONARIES)) {
                Unit u = is.getMissionary();
                price = (int)FeatureContainer.applyModifierSet(price,
                    getGame().getTurn(), u.getMissionaryTradeModifiers(false));
            }
            sessionRegister.put(goldKey, new Integer(price));
            return price;
        } else {
            int price = registered.intValue();
            if (price < 0 || price == gold) {
                return price;
            } else if (gold < (price * 9) / 10) {
                logger.warning("Cheating attempt: sending a offer too low");
                sessionRegister.put(goldKey, new Integer(-1));
                return NetworkConstants.NO_TRADE;
            } else {
                int haggling = 1;
                if (sessionRegister.containsKey(hagglingKey)) {
                    haggling = sessionRegister.get(hagglingKey).intValue();
                }
                if (Utils.randomInt(logger, "Buy gold", getAIRandom(),
                        3 + haggling) <= 3) {
                    sessionRegister.put(goldKey, new Integer(gold));
                    sessionRegister.put(hagglingKey, new Integer(haggling + 1));
                    return gold;
                } else {
                    sessionRegister.put(goldKey, new Integer(-1));
                    return NetworkConstants.NO_TRADE_HAGGLE;
                }
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public int sellProposition(Unit unit, Settlement settlement, Goods goods, 
                               int gold) {
        logger.finest("Entering method sellProposition");
        Colony colony = (Colony) settlement;
        Player otherPlayer = unit.getOwner();
        // don't pay for more than fits in the warehouse
        int amount = colony.getWarehouseCapacity() - colony.getGoodsCount(goods.getType());
        amount = Math.min(amount, goods.getAmount());
        // get a good price
        Tension.Level tensionLevel = getPlayer().getTension(otherPlayer).getLevel();
        int percentage = (9 - tensionLevel.ordinal()) * 10;
        // what we could get for the goods in Europe (minus taxes)
        int netProfits = ((100 - getPlayer().getTax())
                          * getPlayer().getMarket().getSalePrice(goods.getType(), amount)) / 100;
        int price = (netProfits * percentage) / 100;
        return price;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptTax(int tax) {
        boolean ret = true;
        StringBuilder sb = new StringBuilder(32);
        sb.append("Tax demand to ").append(getPlayer().getId())
            .append(" of ").append(tax).append("%: ");
        Goods toBeDestroyed = getPlayer().getMostValuableGoods();
        GoodsType goodsType = (toBeDestroyed == null) ? null
            : toBeDestroyed.getType();

        // Is this cheat to look at what the crown will destroy?
        if (toBeDestroyed == null) {
            ret = false;
            sb.append("refused: no-goods-under-threat");
        } else if (goodsType.isFoodType()) {
            ret = false;
            sb.append("refused: food-type");
        } else if (goodsType.isBreedable()) {
            // Refuse if we already have this type under production in
            // multiple places.
            int n = 0;
            for (Settlement s : getPlayer().getSettlements()) {
                if (s.getGoodsCount(goodsType) > 0) n++;
            }
            ret = n < 2;
            if (ret) {
                sb.append("accepted: breedable-type-")
                    .append(goodsType.getSuffix()).append("-missing");
            } else {
                sb.append("refused: breedable-type-")
                    .append(goodsType.getSuffix())
                    .append("-present-in-").append(n).append("-settlements");
            }
        } else if (goodsType.isMilitaryGoods()
            || goodsType.isTradeGoods()
            || goodsType.isBuildingMaterial()) {
            // By age 3 we should be able to produce enough ourselves.
            // TODO: check whether we have an armory, at least
            int age = getGame().getTurn().getAge();
            ret = age < 3;
            sb.append((ret) ? "accepted" : "rejected")
                .append(": special-goods-in-age-").append(age);
        } else {
            int averageIncome = 0;
            int numberOfGoods = 0;
            // TODO: consider the amount of goods produced. If we
            // depend on shipping huge amounts of cheap goods, we
            // don't want these goods to be boycotted.
            List<GoodsType> goodsTypes = getSpecification().getGoodsTypeList();
            for (GoodsType type : goodsTypes) {
                if (type.isStorable()) {
                    averageIncome += getPlayer().getIncomeAfterTaxes(type);
                    numberOfGoods++;
                }
            }
            averageIncome /= numberOfGoods;
            int income = getPlayer().getIncomeAfterTaxes(toBeDestroyed.getType());
            ret = income < averageIncome;
            sb.append((ret) ? "accepted" : "rejected")
                .append(": standard-goods-with-income-").append(income)
                .append((ret) ? "-less-than-" : "-greater-than-")
                .append(averageIncome);
        }
        logger.finest(sb.toString());
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean acceptMercenaries() {
        return getPlayer().isAtWar() || "conquest".equals(getAIAdvantage());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public FoundingFather selectFoundingFather(List<FoundingFather> ffs) {
        int age = getGame().getTurn().getAge();
        FoundingFather bestFather = null;
        int bestWeight = Integer.MIN_VALUE;
        for (FoundingFather father : ffs) {
            if (father == null) continue;

            // For the moment, arbitrarily: always choose the one
            // offering custom houses.  Allowing the AI to build CH
            // early alleviates the complexity problem of handling all
            // TransportMissions correctly somewhat.
            if (father.hasAbility(Ability.BUILD_CUSTOM_HOUSE)) {
                bestFather = father;
                break;
            }

            int weight = father.getWeight(age);
            if (weight > bestWeight) {
                bestWeight = weight;
                bestFather = father;
            }
        }
        return bestFather;
    }


    // Serialization

    /**
     * {@inheritDoc}
     */
    public String getXMLTagName() { return getXMLElementTagName(); }
}
