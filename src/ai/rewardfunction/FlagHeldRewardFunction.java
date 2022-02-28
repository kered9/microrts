package ai.rewardfunction;

import java.util.List;

import rts.GameState;
import rts.PhysicalGameState;
import rts.TraceEntry;
import rts.UnitAction;
import rts.units.Unit;
import util.Pair;

public class FlagHeldRewardFunction extends RewardFunctionInterface {

    public static float HOLD_REWARD = 1;

    public void computeReward(int maxplayer, int minplayer, TraceEntry te, GameState afterGs) {
        reward = 0.0;
        done = false;
        for (Pair<Unit, UnitAction> p : te.getActions()) {
            if (p.m_a.getPlayer() == maxplayer && p.m_a.getResources() == 1) {
                reward += HOLD_REWARD;
            }
        }
    }

    public double getReward() {
        return reward;
    }

    public boolean isDone() {
        return done;
    }

}
