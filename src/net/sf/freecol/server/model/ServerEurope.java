/**
 *  Copyright (C) 2002-2022   The FreeCol Team
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

package net.sf.freecol.server.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import net.sf.freecol.common.FreeColException;
import net.sf.freecol.common.model.*;
import net.sf.freecol.common.networking.ChangeSet;
import net.sf.freecol.common.networking.ChangeSet.See;
import net.sf.freecol.common.option.GameOptions;
import net.sf.freecol.common.option.IntegerOption;
import net.sf.freecol.common.util.LogBuilder;
import net.sf.freecol.common.util.RandomChoice;
import static net.sf.freecol.common.util.CollectionUtils.*;
import static net.sf.freecol.common.util.RandomUtils.*;


/**
 * The server version of Europe.
 */
public class ServerEurope extends Europe implements TurnTaker {

    private static final Logger logger = Logger.getLogger(ServerEurope.class.getName());

    /**
     * Trivial constructor for Game.newInstance.
     *
     * @param game The {@code Game} this object belongs to.
     * @param id The object identifier.
     */
    public ServerEurope(Game game, String id) {
        super(game, id);
    }

    /**
     * Creates a new ServerEurope.
     *
     * @param game The {@code Game} in which this object belongs.
     * @param owner The {@code Player} that will be using this object of
     *            {@code Europe}.
     */
    public ServerEurope(Game game, Player owner) {
        super(game, owner);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equipForRole(Unit unit, Role role, int roleCount) {
        if (!unit.roleIsAvailable(role)) return false;

        // Get the change in goods
        List<AbstractGoods> req = unit.getGoodsDifference(role, roleCount);

        // Check the pricing and ability to trade
        try {
            int price = priceGoods(req);
            if (price > 0 && !unit.getOwner().checkGold(price)) return false;
        } catch (FreeColException fce) {
            return false;
        }

        // Sell any excess
        final Player owner = getOwner();
        for (AbstractGoods ag : transform(req, g -> g.getAmount() < 0)) {
            int rm = ((ServerPlayer)owner).sellInEurope(null, null, ag.getType(), -ag.getAmount());
            if (rm > 0) {
                ((ServerPlayer)owner).addExtraTrade(new AbstractGoods(ag.getType(), rm));
            }
        }
        // Buy what is needed
        for (AbstractGoods ag : transform(req, AbstractGoods::isPositive)) {
            int m = ((ServerPlayer)owner).buyInEurope(null, null,
                    ag.getType(), ag.getAmount());
            if (m > 0) {
                ((ServerPlayer)owner).addExtraTrade(new AbstractGoods(ag.getType(), -m));
            }
        }

        unit.changeRole(role, roleCount);
        return true;
    }

    /**
     * Generates the initial recruits for this player.  Recruits may
     * be determined by the difficulty level, or generated randomly.
     *
     * @param random A pseudo-random number source.
     */
    public void initializeMigration(Random random) {
        final Specification spec = getGame().getSpecification();
        for (AbstractUnit au : spec.getUnitList(GameOptions.IMMIGRANTS)) {
            addRecruitable(au, true);
        }
        fillRecruitables(random);
    }

    /**
     * Fill out to the full amount of recruits.
     *
     * @param random A pseudo-random number source.
     */
    private void fillRecruitables(Random random) {
        List<RandomChoice<UnitType>> recruits = generateRecruitablesList();
        UnitType unitType;
        do {
            unitType = RandomChoice.getWeightedRandom(logger, "Recruits",
                    recruits, random);
        } while (addRecruitable(unitType, false));
    }

    /**
     * Increases the base price and lower cap for recruits.
     */
    public void increaseRecruitmentDifficulty() {
        final Specification spec = getSpecification();
        this.baseRecruitPrice += spec.getInteger(GameOptions.RECRUIT_PRICE_INCREASE);
        this.recruitLowerCap += spec.getInteger(GameOptions.LOWER_CAP_INCREASE);
    }

    /**
     * Extract the recruitable at a given slot, and replace it with
     * the given new recruitable type.
     *
     * Note that we shift the old units down, because the AI always
     * recruits from the lowest slot.
     *
     * @param slot The slot to recruit with.
     * @param random A pseudo-random number source.
     * @return The recruited {@code AbstractUnit}.
     */
    public AbstractUnit extractRecruitable(int slot, Random random) {
        // An invalid slot is normal when the player has no control over
        // recruit type.
        final int count = MigrationType.getMigrantCount();
        int index = (MigrationType.specificMigrantSlot(slot))
                ? MigrationType.migrantSlotToIndex(slot)
                : randomInt(logger, "Choose emigrant", random, count);
        List<AbstractUnit> expanded = getExpandedRecruitables(true);
        AbstractUnit result = expanded.remove(index);
        this.recruitables.clear();
        AbstractUnit top = expanded.remove(0);
        this.recruitables.add(top);
        for (AbstractUnit au : expanded) {
            if (au.getId().equals(top.getId())
                    && au.getRoleId().equals(top.getRoleId())) {
                top.addToNumber(au.getNumber());
            } else {
                this.recruitables.add(au);
                top = au;
            }
        }
        fillRecruitables(random);
        return result;
    }

    /**
     * Generate a weighted list of unit types recruitable by this player.
     *
     * @return A weighted list of recruitable unit types.
     */
    private List<RandomChoice<UnitType>> generateRecruitablesList() {
        final Player owner = getOwner();
        return transform(getSpecification().getUnitTypeList(),
                ut -> ut.isRecruitable()
                        && owner.hasAbility(Ability.CAN_RECRUIT_UNIT, ut)
                        && ut.isAvailableTo(owner),
                ut -> new RandomChoice<>(ut, ut.getRecruitProbability()));
    }
    private List<RandomChoice<UnitType>> generatePossibleBoatsList() {
        return transform(getSpecification().getUnitTypeList(),
                ut ->   ut.isNaval(),
                ut -> new RandomChoice<>(ut, 100));
    }

    private List<RandomChoice<UnitType>> generatePossibleBoatsCaravel() {
        return transform(getSpecification().getUnitTypeList(),
                ut ->   ut.isNaval() && ut.hasAbility(Ability.CARAVEL),
                ut -> new RandomChoice<>(ut, 100));
    }
    /**
     * Replace any non-recruitable recruits.
     *
     * @param random A pseudo-random number source.
     * @return True if any recruit was replaced.
     */
    public boolean replaceRecruits(Random random) {
        final Specification spec = getSpecification();
        boolean result = removeInPlace(recruitables,
                au -> !hasAbility(Ability.CAN_RECRUIT_UNIT, au.getType(spec)));
        fillRecruitables(random);
        return result;
    }

    /**
     * Generate new recruits following a Fountain of Youth discovery.
     *
     * FIXME: Get rid of this, it is only used because the AI is stupid.
     *
     * @param n The number of new units.
     * @param random A pseudo-random number source.
     * @return The generated units.
     */
    public List<Unit> generateFountainRecruits(int n, Random random) {
        final Game game = getGame();
        final Player owner = getOwner();
        List<Unit> ret = new ArrayList<>(n);
        List<RandomChoice<UnitType>> recruits = generateRecruitablesList();
        for (int k = 0; k < n; k++) {
            UnitType ut = RandomChoice.getWeightedRandom(logger, "Choose FoY",
                    recruits, random);
            ret.add(new ServerUnit(game, this, owner, ut));//-vis: safe, Europe
        }
        return ret;
    }
    public Unit generateFreeBoat(Random random) {
        final Game game = getGame();
        final Player owner = getOwner();
        List<RandomChoice<UnitType>> boats = generatePossibleBoatsList();
        UnitType ut = RandomChoice.getWeightedRandom(logger, "Choose FoY",
                boats, random);
        return new ServerUnit(game, this, owner, ut);
    }

    public Unit generateFreeArmedBoat(Random random) {
        final Game game = getGame();
        final Player owner = getOwner();
        Specification spec = game.getSpecification();
        List<RandomChoice<UnitType>> boats = generatePossibleBoatsCaravel();
        UnitType ut = RandomChoice.getWeightedRandom(logger, "Choose FoY",
                boats, random);
        Unit boat = new ServerUnit(game, this, owner, ut);
        GoodsType t1 = spec.getGoodsType("model.goods.horses");
        GoodsType t2 = spec.getGoodsType("model.goods.muskets");
        ((ServerPlayer) owner).stealInEurope(random,boat.getGoodsContainer(),t1, 100);
        ((ServerPlayer) owner).stealInEurope(random,boat.getGoodsContainer(),t2, 100);
        return boat;
    }



    /**
     * Increases the price for a unit.
     *
     * @param unitType The {@code UnitType}, trained or purchased
     * @param price The current price of the unit
     */
    public void increasePrice(UnitType unitType, int price) {
        final Specification spec = getSpecification();
        String baseOption = GameOptions.PRICE_INCREASE_PER_TYPE;
        String option = (spec.getBoolean(baseOption))
                ? "model.option.priceIncrease." + unitType.getSuffix()
                : "model.option.priceIncrease";
        int increase = (spec.hasOption(option, IntegerOption.class))
                ? spec.getInteger(option) : 0;
        if (increase != 0) {
            unitPrices.put(unitType, price + increase);
        }
    }

    /**
     * Equip a unit for a specific role.
     *
     * @param unit The {@code Unit} to equip.
     * @param role The {@code Role} to equip for.
     * @param roleCount The role count.
     * @param random A pseudo-random number source.
     * @param cs A {@code ChangeSet} to update.
     * @return True if the equipping succeeds.
     */
    public boolean csEquipForRole(Unit unit, Role role, int roleCount,
                                  Random random, ChangeSet cs) {
        boolean ret = equipForRole(unit, role, roleCount);

        if (ret) {
            Player owner = getOwner();
            cs.addPartial(See.only(owner), owner,
                    "gold", String.valueOf(owner.getGold()));
            cs.add(See.only(owner), unit);
            ((ServerPlayer)owner).flushExtraTrades(random);
            ((ServerPlayer)owner).csFlushMarket(cs);
        }
        return ret;
    }


    // Implement TurnTaker

    /**
     * New turn for this colony tile.
     *
     * FIXME: give Europe a shipyard and remove this?
     *
     * @param random A {@code Random} number source.
     * @param lb A {@code LogBuilder} to log to.
     * @param cs A {@code ChangeSet} to update.
     */
    @Override
    public void csNewTurn(Random random, LogBuilder lb, ChangeSet cs) {
        lb.add(this);

        for (Unit unit : transform(getUnits(),
                u -> u.isNaval() && u.isDamaged())) {
            ((ServerUnit)unit).csRepairUnit(cs);
        }
    }
}
