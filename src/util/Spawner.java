package util;

import java.util.ArrayList;
import java.util.Random;

import rts.*;
import rts.units.*;

public class Spawner {
    String mode;

    static UnitTypeTable utt;

    UnitType resourceType;
    UnitType baseType;
    UnitType barracksType;
    UnitType workerType;
    UnitType lightType;
    UnitType heavyType;
    UnitType rangedType;

    ArrayList<UnitType> types;

    public Spawner(String mode, UnitTypeTable a_utt) {
        this.mode = mode;
        utt = a_utt;
        types = new ArrayList<UnitType>();
        resourceType = utt.getUnitType("Resource");
        baseType = utt.getUnitType("Base");
        barracksType = utt.getUnitType("Barracks");
        workerType = utt.getUnitType("Worker");
        lightType = utt.getUnitType("Light");
        heavyType = utt.getUnitType("Heavy");
        rangedType = utt.getUnitType("Ranged");

        // types.add(workerType);
        types.add(lightType);
        types.add(heavyType);
        types.add(rangedType);
    }

    public void spawn(GameState gs) {
        switch (mode) {
            case "blockade":
                int units = (int) (Math.random() * 2) + 5;
                for (int i = 0; i < units; i++) {
                    int u = (int) (Math.random() * 3);
                    int x = (int) (Math.random() * gs.getPhysicalGameState().getWidth());
                    int y = (int) (Math.random() * (gs.getPhysicalGameState().getHeight() - 6));

                    Unit newUnit = new Unit(0, types.get(u), x, y, 0);
                    if (gs.getPhysicalGameState().getUnitAt(x, y) != null)
                        i--;
                    else
                        gs.getPhysicalGameState().addUnit(newUnit);
                }
                break;
            case "skirmish":
                units = (int) (Math.random() * 5) + 3;
                for (int p = 0; p < 2; p++) {
                    for (int i = 0; i < units; i++) {
                        int u = (int) (Math.random() * 3);
                        int x = (int) (Math.random() * gs.getPhysicalGameState().getWidth());
                        int y = (p == 0) ? 1 : 6;

                        Unit newUnit = new Unit(p, types.get(u), x, y, 0);
                        if (gs.getPhysicalGameState().getUnitAt(x, y) != null)
                            i--;
                        else
                            gs.getPhysicalGameState().addUnit(newUnit);
                    }
                }
                break;
            case "defense":
                units = (int) (Math.random() * 2) + 5;
                for (int i = 0; i < units; i++) {
                    int u = (int) (Math.random() * 3);
                    int x = (int) (Math.random() * gs.getPhysicalGameState().getWidth());
                    int y = (int) (Math.random() * (gs.getPhysicalGameState().getHeight()-6))+6;

                    Unit newUnit = new Unit(1, types.get(u), x, y, 0);
                    if (gs.getPhysicalGameState().getUnitAt(x, y) != null)
                        i--;
                    else
                        gs.getPhysicalGameState().addUnit(newUnit);
                }
                break;
            case "mine":
                units = (int) (Math.random() * 2) + 1;
                for (int i = 0; i < units; i++) {
                    int u = (int) (Math.random() * 3);
                    int x = (int) (Math.random() * (gs.getPhysicalGameState().getWidth() - 2)) + 1;
                    int y = (int) (Math.random() * (gs.getPhysicalGameState().getHeight() - 2)) + 1;

                    Unit newUnit = new Unit(1, types.get(u), x, y, 0);
                    if (gs.getPhysicalGameState().getUnitAt(x, y) != null)
                        i--;
                    else
                        gs.getPhysicalGameState().addUnit(newUnit);
                }
                break;
        }
    }
}
