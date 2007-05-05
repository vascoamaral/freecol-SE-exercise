package net.sf.freecol.client.gui.panel;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.sf.freecol.client.gui.Canvas;
import net.sf.freecol.client.gui.ImageLibrary;
import net.sf.freecol.client.gui.i18n.Messages;
import net.sf.freecol.common.model.Colony;
import net.sf.freecol.common.model.Location;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.Unit;

import org.w3c.dom.Element;

import cz.autel.dmi.HIGConstraints;
import cz.autel.dmi.HIGLayout;

/**
 * This panel displays the Naval Report.
 */
public final class ReportUnitPanel extends JPanel implements ActionListener {
    public static final String COPYRIGHT = "Copyright (C) 2003-2006 The FreeCol Team";

    public static final String LICENSE = "http://www.gnu.org/licenses/gpl.html";

    public static final String REVISION = "$Revision$";

    // The HIGLayout widths.
    private static final int[] widths = new int[] { 0, 12, 0 };

    // The HIGLayout heights.
    private static int[] heights;

    // The column for location labels.
    private static final int labelColumn = 1;

    // The column for unit panels.
    private static final int unitColumn = 3;
    
    // The extra rows needed (one row for REF, one for summary, one separator).
    private static final int extraRows = 3;

    // The height of the separator row.
    private static final int separator = 12;

    /**
     * Whether this is a naval unit report.
     */
    private boolean isNaval;

    /**
     * Whether to display empty locations.
     */
    private boolean ignoreEmptyLocations;

    /**
     * The current HIGLayout row.
     */
    private int row = 1;

    private int locationIndex = 0;

    /**
     * The main data structure.
     */
    private HashMap<String, ArrayList<Unit>> locations;

    private static final HIGConstraints higConst = new HIGConstraints();

    private Canvas parent;

    private List<Colony> colonies;

    private final ReportPanel reportPanel;

    private final Player player;

    /**
     * Records the number of units of each type.
     */
    private int[][] unitCounts = new int[Unit.UNIT_COUNT][2];

    /**
     * Records the total cargo capacity of the fleet (currently
     * unused).
     */
    int capacity = 0;

    /**
     * The constructor that will add the items to this panel.
     * 
     * @param parent The parent of this panel.
     */
    public ReportUnitPanel(boolean isNaval, boolean ignoreEmptyLocations, Canvas parent, ReportPanel reportPanel) {
        this.isNaval = isNaval;
        this.ignoreEmptyLocations = ignoreEmptyLocations;
        this.parent = parent;
        this.reportPanel = reportPanel;
        player = parent.getClient().getMyPlayer();
        heights = null;
        setOpaque(false);
    }

    /**
     * Prepares this panel to be displayed.
     */
    public void initialize() {
        locations = new HashMap<String, ArrayList<Unit>>();
        colonies = player.getColonies();
        Collections.sort(colonies, parent.getClient().getClientOptions().getColonyComparator());
        ArrayList<String> colonyNames = new ArrayList<String>();
        Iterator<Colony> colonyIterator = colonies.iterator();
        String colonyName;
        while (colonyIterator.hasNext()) {
            colonyName = (colonyIterator.next()).getName();
            colonyNames.add(colonyName);
        }

        ArrayList<String> otherNames = new ArrayList<String>();

        // Display Panel
        removeAll();

        // reset row
        row = 1;
        // reset location index
        locationIndex = 0;

        gatherData(colonyNames, otherNames);

        heights = new int[colonies.size() + otherNames.size() + extraRows];
        heights[extraRows - 1] = separator;

        setLayout(new HIGLayout(widths, heights));

        // REF
        add(createREFPanel(), higConst.rcwh(row, labelColumn, widths.length, 1));
        row++;
        add(createUnitPanel(), higConst.rcwh(row, labelColumn, widths.length, 1));
        row += 2;
        
        // colonies first, sorted according to user preferences
        Iterator<String> locationIterator = colonyNames.iterator();
        while (locationIterator.hasNext()) {
            handleLocation(locationIterator.next(), true);
        }

        // Europe next
        if (player.getEurope() != null) {
            if (locations.get(player.getEurope().getLocationName()) != null) {
                handleLocation(player.getEurope().getLocationName(), true);
            }
            otherNames.remove(player.getEurope().getLocationName());
        }

        // finally all other locations, sorted alphabetically
        Collections.sort(otherNames);
        locationIterator = otherNames.iterator();
        while (locationIterator.hasNext()) {
            handleLocation(locationIterator.next(), false);
        }

    }

    private void gatherData(ArrayList<String> colonyNames, ArrayList<String> otherNames) {
        Iterator<Unit> units = player.getUnitIterator();
        while (units.hasNext()) {
            Unit unit = units.next();
            int type = unit.getType();
            String locationName = null;

            if (isNaval && unit.isNaval()) {

                unitCounts[type][0]++;
                capacity += unit.getInitialSpaceLeft();
                Location location = unit.getLocation();
                if (unit.getState() == Unit.TO_AMERICA) {
                    locationName = Messages.message("goingToAmerica");
                } else if (unit.getState() == Unit.TO_EUROPE) {
                    locationName = Messages.message("goingToEurope");
                } else if (unit.getDestination() != null) {
                    locationName = Messages.message("sailingTo", new String[][] { { "%location%",
                            unit.getDestination().getLocationName() } });
                } else {
                    locationName = location.getLocationName();
                }
            } else if (!isNaval && !unit.isNaval()) {
                switch (type) {
                case Unit.ARTILLERY:
                case Unit.DAMAGED_ARTILLERY:
                    unitCounts[type][0]++;
                    break;
                case Unit.VETERAN_SOLDIER:
                case Unit.COLONIAL_REGULAR:
                    if (unit.isArmed()) {
                        if (unit.isMounted()) {
                            unitCounts[type][1]++;
                        } else {
                            unitCounts[type][0]++;
                        }
                    } else {
                        continue;
                    }
                    break;
                default:
                    if (unit.isArmed()) {
                        if (unit.isMounted()) {
                            unitCounts[Unit.FREE_COLONIST][1]++;
                        } else {
                            unitCounts[Unit.FREE_COLONIST][0]++;
                        }
                    } else {
                        continue;
                    }
                    break;
                }                    

                Location location = unit.getLocation();
                if (unit.getDestination() != null) {
                    locationName = Messages.message("goingTo", new String[][] { { "%location%",
                            unit.getDestination().getLocationName() } });
                } else {
                    locationName = location.getLocationName();
                }
            }

            if (locationName != null) {
                ArrayList<Unit> unitList = locations.get(locationName);
                if (unitList == null) {
                    unitList = new ArrayList<Unit>();
                    locations.put(locationName, unitList);
                }
                unitList.add(unit);
                if (!(colonyNames.contains(locationName) || otherNames.contains(locationName))) {
                    otherNames.add(locationName);
                }
            }
        }
}

    private JPanel createREFPanel() {
        Element refUnits = parent.getClient().getInGameController().getREFUnits();
        JPanel refPanel;
        if (isNaval) {
            int menOfWar = Integer.parseInt(refUnits.getAttribute("menOfWar"));
            refPanel = new JPanel();
            JLabel menOfWarLabel = reportPanel.buildUnitLabel(ImageLibrary.MAN_O_WAR, 0.66f);
            menOfWarLabel.setText(String.valueOf(menOfWar));
            refPanel.add(menOfWarLabel);
        } else {
            int artillery = Integer.parseInt(refUnits.getAttribute("artillery"));
            int dragoons = Integer.parseInt(refUnits.getAttribute("dragoons"));
            int infantry = Integer.parseInt(refUnits.getAttribute("infantry"));
            int[] refUnitCounts = new int[] { artillery, dragoons, infantry };
            int[] libraryUnitType = new int[] { ImageLibrary.ARTILLERY, ImageLibrary.KINGS_CAVALRY,
                    ImageLibrary.KINGS_REGULAR };
            refPanel = new JPanel(new GridLayout(0, 8));
            for (int index = 0; index < refUnitCounts.length; index++) {
               JLabel unitLabel = reportPanel.buildUnitLabel(libraryUnitType[index], 0.66f);
               unitLabel.setText(String.valueOf(refUnitCounts[index]));
               refPanel.add(unitLabel);
            }
        }
        refPanel.setOpaque(false);
        refPanel.setBorder(BorderFactory.createTitledBorder(player.getREFPlayer().getNationAsString()));
        return refPanel;
    }

    private JPanel createUnitPanel() {
        JPanel unitPanel;
        if (isNaval) {
            int[] unitTypes = new int[] {
                Unit.CARAVEL, Unit.MERCHANTMAN, Unit.GALLEON,
                Unit.FRIGATE, Unit.MAN_O_WAR, Unit.PRIVATEER
            };
            unitPanel = new JPanel(new GridLayout(1, unitTypes.length));
            for (int index = 0; index < unitTypes.length; index++) {
                int count = unitCounts[unitTypes[index]][0];
                int graphicsType = reportPanel.getLibrary().getUnitGraphicsType(unitTypes[index], true, false, 0, false);
                JLabel unitLabel = reportPanel.buildUnitLabel(graphicsType, 0.66f);
                unitLabel.setText(String.valueOf(count));
                unitPanel.add(unitLabel);
            }
        } else {
            int[] unitTypes = new int[] {
                Unit.ARTILLERY, Unit.DAMAGED_ARTILLERY,
                Unit.COLONIAL_REGULAR, Unit.VETERAN_SOLDIER,
                Unit.FREE_COLONIST
            };
            unitPanel = new JPanel(new GridLayout(1, unitTypes.length));
            // artillery can not be mounted
            for (int index = 0; index < 2; index++) {
                int count = unitCounts[unitTypes[index]][0];
                int graphicsType = reportPanel.getLibrary().getUnitGraphicsType(unitTypes[index], true, false, 0, false);
                JLabel unitLabel = reportPanel.buildUnitLabel(graphicsType, 0.66f);
                unitLabel.setText(String.valueOf(count));
                unitPanel.add(unitLabel);
            }
            // other units can be mounted
            for (int mounted = 1; mounted >= 0; mounted--) {
                for (int index = 2; index < unitTypes.length; index++) {
                    int count = unitCounts[unitTypes[index]][mounted];
                    int graphicsType = reportPanel.getLibrary().getUnitGraphicsType(unitTypes[index], true, (mounted == 1), 0, false);
                    JLabel unitLabel = reportPanel.buildUnitLabel(graphicsType, 0.66f);
                    unitLabel.setText(String.valueOf(count));
                    unitPanel.add(unitLabel);
                }
            }
        }
        unitPanel.setOpaque(false);
        unitPanel.setBorder(BorderFactory.createTitledBorder(player.getNationAsString()));
        return unitPanel;
    }


    private void handleLocation(String location, boolean makeButton) {
        List<Unit> unitList = locations.get(location);
        if (!(unitList == null && ignoreEmptyLocations)) {
            if (makeButton) {
                JButton locationButton = new JButton(location);
                locationButton.setMargin(new Insets(0,0,0,0));
                locationButton.setOpaque(false);
                locationButton.setForeground(FreeColPanel.LINK_COLOR);
                locationButton.setAlignmentY(0.8f);
                locationButton.setBorder(BorderFactory.createEmptyBorder());
                locationButton.setActionCommand(String.valueOf(locationIndex));
                locationButton.addActionListener(this);
                add(locationButton, higConst.rc(row, labelColumn, "lt"));
            } else {
                JLabel locationLabel = new JLabel(location);
                add(locationLabel, higConst.rc(row, labelColumn));
            }
            if (unitList != null) {
                JPanel unitPanel = new JPanel();
                if (isNaval) {
                    unitPanel.setLayout(new GridLayout(0, 6));
                } else {
                    unitPanel.setLayout(new GridLayout(0, 10));
                }
                unitPanel.setOpaque(false);
                Collections.sort(unitList, reportPanel.getUnitTypeComparator());
                Iterator<Unit> unitIterator = unitList.iterator();
                while (unitIterator.hasNext()) {
                    UnitLabel unitLabel = new UnitLabel(unitIterator.next(), parent, true);
                    // this is necessary because UnitLabel deselects carriers
                    unitLabel.setSelected(true);
                    unitPanel.add(unitLabel);
                }
                add(unitPanel, higConst.rc(row, unitColumn, "l"));
            }
            row++;
        }
        locationIndex++;
    }


    /**
     * This function analyses an event and calls the right methods to take care
     * of the user's requests.
     * 
     * @param event The incoming ActionEvent.
     */
    public void actionPerformed(ActionEvent event) {
        String command = event.getActionCommand();
        int action = Integer.valueOf(command).intValue();
        if (action == ReportPanel.OK) {
            reportPanel.actionPerformed(event);
        } else if (action < colonies.size()) {
            parent.showColonyPanel(colonies.get(action));
        } else if (action == colonies.size()) {
            parent.showEuropePanel();
        }

    }
}
