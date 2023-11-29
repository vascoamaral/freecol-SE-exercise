package net.sf.freecol.client.control;

import net.sf.freecol.common.model.*;
import net.sf.freecol.server.ServerTestHelper;
import net.sf.freecol.server.control.InGameController;
import net.sf.freecol.server.model.ServerPlayer;
import net.sf.freecol.server.model.ServerUnit;
import net.sf.freecol.util.test.FreeColTestCase;


public class TreasureChestTest extends FreeColTestCase{
    private static final UnitType caravelType
            = spec().getUnitType("model.unit.caravel");
    private static final TileType ocean
            = spec().getTileType("model.tile.ocean");
    private static final ResourceType treasureChest
            = spec().getResourceType("model.resource.treasureChest");

    public void testGetTreasure(){

        Game game = ServerTestHelper.startServerGame(getTestMap(ocean));
        Map map = game.getMap();

        InGameController igc = ServerTestHelper.getInGameController();
        ServerPlayer dutch = getServerPlayer(game,"model.nation.dutch");

        Tile ocean1 = map.getTile(5, 8);
        ocean1.setExplored(dutch, true);

        Tile ocean2 = map.getTile(5, 7);
        ocean2.setExplored(dutch, true);
        ocean2.addResource(new Resource(game, ocean2, treasureChest));

        ServerUnit caravel = new ServerUnit(game, ocean1, dutch, caravelType);
        int inicialGold = dutch.getGold();

        caravel.setTreasureGoldChance(1);
        igc.move(dutch, caravel, ocean2);

        assertNull(ocean2.getResource());
        assertTrue(inicialGold < dutch.getGold());

        Tile ocean3 = map.getTile(5, 6);
        ocean3.setExplored(dutch, true);
        ocean3.addResource(new Resource(game, ocean3, treasureChest));

        caravel.setTreasureGoldChance(0);
        igc.move(dutch, caravel, ocean3);

        assertNull(ocean3.getResource());
        assertTrue(caravel.isCursed());
    }

}

