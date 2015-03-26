/**
 *  Copyright (C) 2002-2015   The FreeCol Team
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

package net.sf.freecol.client.gui.panel;

import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultMutableTreeNode;
import net.miginfocom.swing.MigLayout;

import net.sf.freecol.client.FreeColClient;
import net.sf.freecol.client.gui.FontLibrary;
import net.sf.freecol.client.gui.GUI;
import net.sf.freecol.client.gui.action.ColopediaAction.PanelType;
import net.sf.freecol.common.i18n.Messages;
import net.sf.freecol.common.model.Ability;
import net.sf.freecol.common.model.AbstractGoods;
import net.sf.freecol.common.model.BuildingType;
import net.sf.freecol.common.model.Europe;
import net.sf.freecol.common.model.GoodsType;
import net.sf.freecol.common.model.IndianNationType;
import net.sf.freecol.common.model.Modifier;
import net.sf.freecol.common.model.Player;
import net.sf.freecol.common.model.StringTemplate;
import net.sf.freecol.common.model.UnitType;
import net.sf.freecol.common.util.RandomChoice;


/**
 * This panel displays details of units in the Colopedia.
 */
public class UnitDetailPanel extends ColopediaGameObjectTypePanel<UnitType> {

    // layout of production modifier panel
    private static final int MODIFIERS_PER_ROW = 5;


    /**
     * Creates a new instance of this ColopediaDetailPanel.
     *
     * @param freeColClient The <code>FreeColClient</code> for the game.
     * @param colopediaPanel The parent ColopediaPanel.
     */
    public UnitDetailPanel(FreeColClient freeColClient,
                           ColopediaPanel colopediaPanel) {
        super(freeColClient, colopediaPanel, PanelType.UNITS.toString(), 0.5f);
    }


    /**
     * Adds one or several subtrees for all the objects for which this
     * ColopediaDetailPanel could build a detail panel to the given
     * root node.
     *
     * @param root a <code>DefaultMutableTreeNode</code>
     */
    public void addSubTrees(DefaultMutableTreeNode root) {
        List<UnitType> units = new ArrayList<>();
        List<UnitType> skills = new ArrayList<>();
        for (UnitType u : getSpecification().getUnitTypeList()) {
            if (u.getSkill() <= 0 || u.hasAbility(Ability.EXPERT_SOLDIER)) {
                units.add(u);
            } else {
                skills.add(u);
            }
        }
        super.addSubTrees(root, "colopediaAction." + PanelType.UNITS, units);
        super.addSubTrees(root, "colopediaAction." + PanelType.SKILLS, skills);
    }

    /**
     * Builds the details panel for the UnitType with the given identifier.
     *
     * @param id The object identifier.
     * @param panel the detail panel to build
     */
    public void buildDetail(String id, JPanel panel) {
        if (getId().equals(id) || ("colopediaAction." + PanelType.SKILLS).equals(id)) {
            return;
        }

        UnitType type = getSpecification().getUnitType(id);
        panel.setLayout(new MigLayout("wrap 4", "[]20[]40[]20[]"));

        JLabel name = GUI.localizedLabel(type);
        name.setFont(FontLibrary.SMALL_HEADER_FONT);
        panel.add(name, "span, align center, wrap 40");

        panel.add(GUI.localizedLabel("colopedia.unit.offensivePower"));
        panel.add(new JLabel(Integer.toString((int)type.getOffence())), "right");

        panel.add(GUI.localizedLabel("colopedia.unit.defensivePower"));
        panel.add(new JLabel(Integer.toString((int)type.getDefence())), "right");

        panel.add(GUI.localizedLabel("colopedia.unit.movement"));
        panel.add(new JLabel(String.valueOf(type.getMovement()/3)), "right");

        if (type.canCarryGoods() || type.canCarryUnits()) {
            panel.add(GUI.localizedLabel("colopedia.unit.capacity"));
            panel.add(new JLabel(Integer.toString(type.getSpace())), "right");
        }

        Player player = getMyPlayer();
        // player can be null when using the map editor
        Europe europe = (player == null) ? null : player.getEurope();

        String price = null;
        if (europe != null && europe.getUnitPrice(type) > 0) {
            price = Integer.toString(europe.getUnitPrice(type));
        } else if (type.getPrice() > 0) {
            price = Integer.toString(type.getPrice());
        }
        if (price != null) {
            panel.add(GUI.localizedLabel("colopedia.unit.price"));
            panel.add(new JLabel(price), "right");
        }


        if (type.hasSkill()) {
            panel.add(GUI.localizedLabel("colopedia.unit.skill"));
            panel.add(new JLabel(Integer.toString(type.getSkill())), "right");

            List<BuildingType> schools = new ArrayList<>();
            for (final BuildingType buildingType : getSpecification().getBuildingTypeList()) {
                if (buildingType.hasAbility(Ability.TEACH) && buildingType.canAdd(type)) {
                    schools.add(buildingType);
                }
            }

            if (!schools.isEmpty()) {
                panel.add(GUI.localizedLabel("colopedia.unit.school"), "newline");
                int count = 0;
                for (BuildingType school : schools) {
                    JButton label = getButton(school);
                    if (count > 0 && count % 3 == 0) {
                        panel.add(label, "skip");
                    } else {
                        panel.add(label);
                    }
                    count++;
                }
            }

            List<IndianNationType> nations = new ArrayList<>();
            for (IndianNationType nation : getSpecification().getIndianNationTypes()) {
                for (RandomChoice<UnitType> choice : nation.getSkills()) {
                    if (choice.getObject() == type) {
                        nations.add(nation);
                    }
                }
            }
            if (!nations.isEmpty()) {
                panel.add(GUI.localizedLabel("colopedia.unit.natives"), "newline");
                int count = 0;
                for (IndianNationType nation : nations) {
                    JButton label = getButton(nation);
                    if (count > 0 && count % 3 == 0) {
                        panel.add(label, "skip");
                    } else {
                        panel.add(label);
                    }
                    count++;
                }
            }

        }

        // Requires - prerequisites to build
        Map<String, Boolean> abilities = type.getRequiredAbilities();
        if (!abilities.isEmpty()) {
            panel.add(GUI.localizedLabel("colopedia.unit.requirements"), "newline, top");
            String key = abilities.keySet().iterator().next();
            try {
                JTextPane textPane = GUI.getDefaultTextPane();
                StyledDocument doc = textPane.getStyledDocument();
                appendRequiredAbilities(doc, type);
                panel.add(textPane, "span, width 60%");
            } catch (BadLocationException e) {
                //logger.warning(e.toString());
            }
        }

        List<Modifier> bonusList = new ArrayList<>();
        for (GoodsType goodsType : getSpecification().getGoodsTypeList()) {
            bonusList.addAll(type.getModifiers(goodsType.getId()));
        }
        int bonusNumber = bonusList.size();
        if (bonusNumber > 0) {
            StringTemplate template = StringTemplate
                .template("colopedia.unit.productionBonus")
                .addAmount("%number%", bonusNumber);
            panel.add(GUI.localizedLabel(template), "newline 20, top");
            JPanel productionPanel = new JPanel(new GridLayout(0, MODIFIERS_PER_ROW));
            productionPanel.setOpaque(false);
            for (Modifier productionBonus : bonusList) {
                GoodsType goodsType = getSpecification().getGoodsType(productionBonus.getId());
                String bonus = ModifierFormat.getModifierAsString(productionBonus);
                productionPanel.add(getGoodsButton(goodsType, bonus));
            }
            panel.add(productionPanel, "span");
        }

        if (type.needsGoodsToBuild()) {
            panel.add(GUI.localizedLabel("colopedia.unit.goodsRequired"),
                            "newline 20");
            List<AbstractGoods> required = type.getRequiredGoods();
            AbstractGoods goods = required.get(0);
            if (required.size() > 1) {
                panel.add(getGoodsButton(goods.getType(), goods.getAmount()),
                                "span, split " + required.size());
                for (int index = 1; index < required.size(); index++) {
                    goods = required.get(index);
                    panel.add(getGoodsButton(goods.getType(), goods.getAmount()));
                }
            } else {
                panel.add(getGoodsButton(goods.getType(), goods.getAmount()));
            }
        }

        panel.add(GUI.localizedLabel("colopedia.unit.description"),
                  "newline 20");
        panel.add(GUI.getDefaultTextArea(Messages.getDescription(type), 30),
                  "span");
    }
}
