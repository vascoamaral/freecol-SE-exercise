package net.sf.freecol.common.model;

import java.util.Random;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Represents a lost city rumour.
 */
public class LostCityRumour extends FreeColGameObject {
    public static final String  COPYRIGHT = "Copyright (C) 2003-2005 The FreeCol Team";
    public static final String  LICENSE = "http://www.gnu.org/licenses/gpl.html";
    public static final String  REVISION = "$Revision$";

    public static final int NO_SUCH_RUMOUR = -1,
        BURIAL_GROUND = 0,
        EXPEDITION_VANISHES = 1, 
        NOTHING = 2,
        SEASONED_SCOUT = 3,
        TRIBAL_CHIEF = 4,
        COLONIST = 5,
        TREASURE_TRAIN = 6,
        FOUNTAIN_OF_YOUTH = 7;

    public static final int NUMBER_OF_RUMOURS = 8;

    public static final Random random = new Random();

    public static int explore(Unit unit) {

        Player player = unit.getOwner();
        int type = unit.getType();
        // difficulty is in range 0-4, dx in range 2-6
        int dx = player.getDifficulty() + 2;

        // seasoned scouts should be more successful
        if (type == Unit.SEASONED_SCOUT) {
            dx--;
        }

        // dx is now in range 1-6
        int max = 7; // maximum difficulty + 1

        /** The higher the difficulty, the more likely bad things are
         * to happen.
         */
        int[] probability = new int[NUMBER_OF_RUMOURS];

        probability[BURIAL_GROUND] = dx;
        probability[EXPEDITION_VANISHES] = dx * 2;
        probability[NOTHING] = dx * 5;

        // only these units can be promoted
        if (type == Unit.FREE_COLONIST ||
            type == Unit.INDENTURED_SERVANT ||
            type == Unit.PETTY_CRIMINAL) {
            probability[SEASONED_SCOUT] = ( max - dx ) * 3;
        } else {
            probability[SEASONED_SCOUT] = 0;
        }

        /** The higher the difficulty, the less likely good things are
         * to happen.
         */
        probability[TRIBAL_CHIEF] = ( max - dx ) * 3;
        probability[COLONIST] = ( max - dx ) * 2;
        probability[TREASURE_TRAIN] = ( max - dx ) * 2;
        probability[FOUNTAIN_OF_YOUTH] = ( max - dx );

        int start = 0;

        if (player.hasFather(FoundingFather.HERNANDO_DE_SOTO)) {
            // rumours are always positive
            start = 3;
        }

        int accumulator = 0;
        for ( int i = start; i < NUMBER_OF_RUMOURS; i++ ) {
            accumulator += probability[i];
            probability[i] = accumulator;
        }

        int randomInt = random.nextInt(accumulator);
        int amount = 0;

        for ( int j = start; j < NUMBER_OF_RUMOURS; j++ ) {
            if (randomInt < probability[j]) {
                return j;
            }
        }
        return NO_SUCH_RUMOUR;
    }

    /**
    * Prepares the object for a new turn.
    */
    public void newTurn() {};

    /**
    * This method should return an XML-representation of this object.
    * Only attributes visible to <code>player</code> will be added to
    * that representation if <code>showAll</code> is set to <i>false</i>.
    *
    * @param player The <code>Player</code> this XML-representation is
    *               made for.
    * @param document The document to use when creating new componenets.
    * @param showAll Only attributes visible to <code>player</code> will be added to
    *                the representation if <code>showAll</code> is set to <i>false</i>.
    * @param toSavedGame If <i>true</i> then information that is only needed when saving a
    *                    game is added.
    * @return The DOM-element ("Document Object Model").
    */    
    public Element toXMLElement(Player player, Document document, boolean showAll, boolean toSavedGame) {
        return document.createElement("");
    };


    /**
    * Initialize this object from an XML-representation of this object.
    * @param element The DOM-element ("Document Object Model") made to represent this object.
    */
    public void readFromXMLElement(Element element) {};

}


