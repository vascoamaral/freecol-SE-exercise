package net.sf.freecol.client.control;


import net.sf.freecol.common.model.*;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.control.InGameController;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;

import java.util.List;

public class RuinedLostCityTest extends FreeColTestCase{

    private static final TileType plains
            = spec().getTileType("model.tile.plains");

    private static final UnitType pioneerType
            = spec().getUnitType("model.unit.hardyPioneer");

    private static final UnitType artilleryType
            = spec().getUnitType("model.unit.artillery");

    private static final UnitType damagedArtilleryType
            = spec().getUnitType("model.unit.damagedArtillery");

    private static final UnitType wagonType
            = spec().getUnitType("model.unit.wagonTrain");

    private static final UnitType caravelType
            = spec().getUnitType("model.unit.caravel");

    public void testExploreRuinedCityArtillery(){

        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();

        InGameController igc = ServerTestHelper.getInGameController();
        ServerPlayer dutch = getServerPlayer(game,"model.nation.dutch");

        Tile plains1 = map.getTile(5, 8);
        plains1.setExplored(dutch, true);

        Tile plains2 = map.getTile(5, 7);
        plains2.setExplored(dutch, true);

        ServerUnit hardyPioneer = new ServerUnit(game, plains1, dutch, pioneerType);

        RuinedLostCityRumour r1 = new RuinedLostCityRumour(game, plains2);
        r1.setType(RuinedLostCityRumour.RumourType.GIVE_ARTILLERY);
        plains2.addRuinedLostCityRumour(r1);

        igc.move(dutch, hardyPioneer, plains2);
        assertEquals(2, plains2.getUnitCount());
        Unit artillery = plains2.getLastUnit();
        assertEquals(artillery.getType(), artilleryType);

        Tile plains3 = map.getTile(5, 6);
        plains3.setExplored(dutch, true);

        RuinedLostCityRumour r2 = new RuinedLostCityRumour(game, plains3);
        r2.setType(RuinedLostCityRumour.RumourType.GIVE_DAMAGED_ARTILLERY);
        plains3.addRuinedLostCityRumour(r2);

        igc.move(dutch, hardyPioneer, plains3);
        assertEquals(2, plains3.getUnitCount());
        Unit damagedArtillery = plains3.getLastUnit();
        assertEquals(damagedArtillery.getType(), damagedArtilleryType);

    }

    public void testExploreRuinedCityWagon(){

        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();

        InGameController igc = ServerTestHelper.getInGameController();
        ServerPlayer dutch = getServerPlayer(game,"model.nation.dutch");

        Tile plains1 = map.getTile(5, 8);
        plains1.setExplored(dutch, true);

        Tile plains2 = map.getTile(5, 7);
        plains2.setExplored(dutch, true);

        ServerUnit hardyPioneer = new ServerUnit(game, plains1, dutch, pioneerType);

        RuinedLostCityRumour r = new RuinedLostCityRumour(game, plains2);
        r.setType(RuinedLostCityRumour.RumourType.GIVE_WAGON);
        plains2.addRuinedLostCityRumour(r);

        igc.move(dutch, hardyPioneer, plains2);
        assertEquals(2, plains2.getUnitCount());
        Unit wagon = plains2.getLastUnit();
        assertEquals(wagon.getType(), wagonType);
    }

    public void testExploreRuinedCityBoat(){

        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();

        InGameController igc = ServerTestHelper.getInGameController();
        ServerPlayer dutch = getServerPlayer(game,"model.nation.dutch");

        Tile plains1 = map.getTile(5, 8);
        plains1.setExplored(dutch, true);

        Tile plains2 = map.getTile(5, 7);
        plains2.setExplored(dutch, true);

        ServerUnit hardyPioneer = new ServerUnit(game, plains1, dutch, pioneerType);

        RuinedLostCityRumour r = new RuinedLostCityRumour(game, plains2);
        r.setType(RuinedLostCityRumour.RumourType.GIVE_BOAT);
        plains2.addRuinedLostCityRumour(r);

        igc.move(dutch, hardyPioneer, plains2);
        List<Unit> boats = dutch.getEurope().getNavalUnits();
        assertFalse(boats.isEmpty());
        Unit ship = boats.get(0);
        assertFalse(ship.hasGoodsCargo());
    }

    public void testExploreRuinedCityArmedBoat(){

        Game game = ServerTestHelper.startServerGame(getTestMap(plains));
        Map map = game.getMap();

        InGameController igc = ServerTestHelper.getInGameController();
        ServerPlayer dutch = getServerPlayer(game,"model.nation.dutch");

        Tile plains1 = map.getTile(5, 8);
        plains1.setExplored(dutch, true);

        Tile plains2 = map.getTile(5, 7);
        plains2.setExplored(dutch, true);

        ServerUnit hardyPioneer = new ServerUnit(game, plains1, dutch, pioneerType);

        RuinedLostCityRumour r = new RuinedLostCityRumour(game, plains2);
        r.setType(RuinedLostCityRumour.RumourType.GIVE_ARMED_BOAT);
        plains2.addRuinedLostCityRumour(r);

        igc.move(dutch, hardyPioneer, plains2);
        List<Unit> boats = dutch.getEurope().getNavalUnits();
        assertFalse(boats.isEmpty());
        Unit ship = boats.get(0);
        assertTrue(ship.hasGoodsCargo());
        assertEquals(ship.getType(), caravelType);

    }

}
