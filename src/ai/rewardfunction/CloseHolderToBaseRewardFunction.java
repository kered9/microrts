package ai.rewardfunction;

import java.util.List;

import rts.GameState;
import rts.PhysicalGameState;
import rts.TraceEntry;
import rts.UnitAction;
import rts.units.Unit;
import util.Pair;

public class CloseHolderToBaseRewardFunction extends RewardFunctionInterface {
    public void computeReward(int maxplayer, int minplayer, TraceEntry te, GameState afterGs) {
        reward = 0.0;
        done = false;
        int baseX = 0;
        int baseY = 0;
        boolean baseExists = false;
        for (Unit t : te.getPhysicalGameState().getUnits()) {
            if (t.getPlayer() == maxplayer && t.getType().name.equals("Base")) {
                baseExists = true;
                baseX = t.getX();
                baseY = t.getY();
                break;
            }
        }
        if (!baseExists) {
            return;
        }
        double oldMinDistanceHolderToBase = 2000000000;
        for (Unit t : te.getPhysicalGameState().getUnits()) {
            if (t.getPlayer() == maxplayer && t.getType().name.equals("Worker") && t.getResources() == 1) {
                // Euclidean distance
                double distance = Math.sqrt(Math.pow((baseX - t.getX()), 2.0) + Math.pow((baseY - t.getY()), 2.0));
                if (distance < oldMinDistanceHolderToBase) {
                    oldMinDistanceHolderToBase = distance;
                }
            }
        }

        double newMinDistanceToEnemyBase = 2000000000;
        for (Unit t : afterGs.getPhysicalGameState().getUnits()) {
            if (t.getPlayer() == maxplayer && t.getType().name.equals("Worker") && t.getResources() == 1) {
                // Euclidean distance
                double distance = Math.sqrt(Math.pow((baseX - t.getX()), 2.0) + Math.pow((baseY - t.getY()), 2.0));
                if (distance < newMinDistanceToEnemyBase) {
                    newMinDistanceToEnemyBase = distance;
                }
            }
        }
        reward = oldMinDistanceHolderToBase - newMinDistanceToEnemyBase;
    }

    public double getReward() {
        return reward;
    }

    public boolean isDone() {
        return done;
    }
}
