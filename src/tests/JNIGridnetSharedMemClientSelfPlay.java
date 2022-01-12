package tests;

import java.io.Writer;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane.SystemMenuBar;

import java.awt.image.BufferedImage;
import java.io.StringWriter;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;
import com.beust.jcommander.Parameter;

import ai.PassiveAI;
import ai.RandomBiasedAI;
import ai.RandomNoAttackAI;
import ai.core.AI;
import ai.jni.JNIAI;
import ai.rewardfunction.RewardFunctionInterface;
import ai.jni.JNIInterface;
import ai.jni.Response;
import gui.PhysicalGameStateJFrame;
import gui.PhysicalGameStatePanel;
import rts.GameState;
import rts.PartiallyObservableGameState;
import rts.PhysicalGameState;
import rts.PlayerAction;
import rts.Trace;
import rts.TraceEntry;
import rts.UnitAction;
import rts.UnitActionAssignment;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.NDBuffer;
import weka.core.pmml.jaxbbindings.False;

/**
 *
 * Improved performance for JVM <-> NumPy data exchange
 * with direct buffer (JVM allocated).
 * 
 */
public class JNIGridnetSharedMemClientSelfPlay {


    // Settings
    public RewardFunctionInterface[] rfs;
    String micrortsPath;
    String mapPath;
    public AI ai2;
    UnitTypeTable utt;
    boolean partialObs = false;

    // Internal State
    PhysicalGameStateJFrame w;
    public JNIInterface[] ais = new JNIInterface[2];
    PhysicalGameState pgs;
    GameState gs;
    GameState[] playergs = new GameState[2];
    boolean gameover = false;
    boolean layerJSON = true;
    public int renderTheme = PhysicalGameStatePanel.COLORSCHEME_WHITE;
    public int maxAttackRadius;
    public int numPlayers = 2;

    final int clientOffset;

    // storage
    final NDBuffer obsBuffer;
    final NDBuffer unitMaskBuffer;
    final NDBuffer actionMaskBuffer;
    double[][] rewards = new double[2][];
    boolean[][] dones = new boolean[2][];
    Response[] response = new Response[2];
    PlayerAction[] pas = new PlayerAction[2];

    public JNIGridnetSharedMemClientSelfPlay(RewardFunctionInterface[] a_rfs, String a_micrortsPath, String a_mapPath, UnitTypeTable a_utt, boolean partial_obs,
            int clientOffset, NDBuffer obsBuffer, NDBuffer unitMaskBuffer, NDBuffer actionMaskBuffer) throws Exception{
        this.clientOffset = clientOffset;
        this.obsBuffer = obsBuffer;
        this.unitMaskBuffer = unitMaskBuffer;
        this.actionMaskBuffer = actionMaskBuffer;

        micrortsPath = a_micrortsPath;
        mapPath = a_mapPath;
        rfs = a_rfs;
        utt = a_utt;
        partialObs = partial_obs;
        maxAttackRadius = utt.getMaxAttackRange() * 2 + 1;
        if (micrortsPath.length() != 0) {
            this.mapPath = Paths.get(micrortsPath, mapPath).toString();
        }

        pgs = PhysicalGameState.load(mapPath, utt);

        // initialize storage
        for (int i = 0; i < numPlayers; i++) {
            ais[i] = new JNIAI(100, 0, utt);
            rewards[i] = new double[rfs.length];
            dones[i] = new boolean[rfs.length];
            response[i] = new Response(null, null, null, null);
        }
    }

    public byte[] render(boolean returnPixels) throws Exception {
        if (w==null) {
            w = PhysicalGameStatePanel.newVisualizer(gs, 640, 640, partialObs, null, renderTheme);
        }
        w.setStateCloning(gs);
        w.repaint();

        if (!returnPixels) {
            return null;
        }
        BufferedImage image = new BufferedImage(w.getWidth(),
            w.getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            w.paint(image.getGraphics());

        WritableRaster raster = image.getRaster();
        DataBufferByte data = (DataBufferByte) raster.getDataBuffer();
        return data.getData();
    }

    public void gameStep(int[][] action1, int[][] action2) throws Exception {
        TraceEntry te  = new TraceEntry(gs.getPhysicalGameState().clone(), gs.getTime());
        for (int i = 0; i < numPlayers; i++) {
            playergs[i] = gs;
            if (partialObs) {
                playergs[i] = new PartiallyObservableGameState(gs, i);
            }
            pas[i] = i == 0 ? ais[i].getAction(i, playergs[0], action1) : ais[i].getAction(i, playergs[1], action2);
            gs.issueSafe(pas[i]);
            te.addPlayerAction(pas[i].clone());
        }
        // simulate:
        gameover = gs.cycle();
        if (gameover) {
            // ai1.gameOver(gs.winner());
            // ai2.gameOver(gs.winner());
        }

        for (int i = 0; i < numPlayers; i++) {
            for (int j = 0; j < rfs.length; j++) {
                rfs[j].computeReward(i, 1 - i, te, gs);
                rewards[i][j] = rfs[j].getReward();
                dones[i][j] = rfs[j].isDone();
            }

            playergs[i].getBufferObservation(i, clientOffset+i, obsBuffer);

            response[i].set(
                null,
                rewards[i],
                dones[i],
                "{}");
        }
    }

    public void getMasks(int player) throws Exception {
        unitMaskBuffer.resetSegment(new int[]{clientOffset+player});
        actionMaskBuffer.resetSegment(new int[]{clientOffset+player});

        for (int i = 0; i < pgs.getUnits().size(); i++) {
            Unit u = pgs.getUnits().get(i);
            UnitActionAssignment uaa = gs.getUnitActions().get(u);
            if (u.getPlayer() == player && uaa == null) {
                final int[] idxOffset = new int[]{clientOffset+player, u.getY(), u.getX()};
                unitMaskBuffer.set(idxOffset, 1);
                UnitAction.getValidActionBuffer(u, gs, utt, actionMaskBuffer, maxAttackRadius, idxOffset);
            }
        }
    }

    public String sendUTT() throws Exception {
        Writer w = new StringWriter();
        utt.toJSON(w);
        return w.toString(); // now it works fine
    }

    public void reset() throws Exception {
        pgs = PhysicalGameState.load(mapPath, utt);
        gs = new GameState(pgs, utt);
        for (int i = 0; i < numPlayers; i++) {
            playergs[i] = gs;
            if (partialObs) {
                playergs[i] = new PartiallyObservableGameState(gs, i);
            }
            ais[i].reset();
            for (int j = 0; j < rewards.length; j++) {
                rewards[i][j] = 0;
                dones[i][j] = false;
            }

            playergs[i].getBufferObservation(i, clientOffset+i, obsBuffer);

            response[i].set(
                null,
                rewards[i],
                dones[i],
                "{}");
        }
    }

    public Response getResponse(int player) {
        return response[player];
    }

    public void close() throws Exception {
        if (w!=null) {
            w.dispose();    
        }
    }
}
