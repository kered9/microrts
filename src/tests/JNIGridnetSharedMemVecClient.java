/*
* To change this template, choose Tools | Templates
* and open the template in the editor.
*/
package tests;

import java.io.Writer;
import java.nio.file.Paths;
import java.nio.IntBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.awt.image.BufferedImage;
import java.io.StringWriter;
import java.awt.image.DataBufferByte;
import java.awt.image.WritableRaster;

import ai.PassiveAI;
import ai.RandomBiasedAI;
import ai.RandomNoAttackAI;
import ai.core.AI;
import ai.jni.JNIAI;
import ai.rewardfunction.RewardFunctionInterface;
import ai.jni.JNIInterface;
import ai.jni.Response;
import ai.jni.Responses;
import gui.PhysicalGameStateJFrame;
import gui.PhysicalGameStatePanel;
import rts.GameState;
import rts.PhysicalGameState;
import rts.Player;
import rts.PlayerAction;
import rts.Trace;
import rts.TraceEntry;
import rts.UnitAction;
import rts.UnitActionAssignment;
import rts.units.Unit;
import rts.units.UnitTypeTable;
import util.NDBuffer;
import tests.JNIGridnetClientSelfPlay;

/**
 *
 * Improved performance for JVM <-> NumPy data exchange
 * with direct buffer (JVM allocated).
 * 
 */
public class JNIGridnetSharedMemVecClient {
    public final JNIGridnetSharedMemClient[] clients;
    public final JNIGridnetSharedMemClientSelfPlay[] selfPlayClients;
    public final int maxSteps;
    public final int[] envSteps; 
    public final RewardFunctionInterface[] rfs;
    public final UnitTypeTable utt;
    public final boolean partialObs;
    public final String mapPath;

    // storage
    final NDBuffer obsBuffer;
    final NDBuffer unitMaskBuffer;
    final NDBuffer actionMaskBuffer;
    final double[][] reward;
    final boolean[][] done;
    final Response[] rs;
    final Responses responses;

    final ExecutorService pool;

    public JNIGridnetSharedMemVecClient(int a_num_selfplayenvs, int a_num_envs, int a_max_steps, RewardFunctionInterface[] a_rfs,
            String a_micrortsPath, String mapPath, AI[] a_ai2s, UnitTypeTable a_utt, boolean partial_obs,
            IntBuffer obsBuffer, IntBuffer unitMaskBuffer, IntBuffer actionMaskBuffer, int threadPoolSize) throws Exception {
        maxSteps = a_max_steps;
        utt = a_utt;
        rfs = a_rfs;
        partialObs = partial_obs;

        this.mapPath = Paths.get(a_micrortsPath, mapPath).toString();

        // get dims
        final PhysicalGameState pgs = PhysicalGameState.load(this.mapPath, utt);
        final int s1 = a_num_selfplayenvs + a_num_envs;
        final int maxAttackRadius = utt.getMaxAttackRange() * 2 + 1;
        final int actionMaskNumEntries = 6+4+4+4+4+utt.getUnitTypes().size()+maxAttackRadius*maxAttackRadius;

        // initialize shared storage
        this.obsBuffer = new NDBuffer(obsBuffer, new int[]{s1, pgs.getHeight(), pgs.getWidth(), GameState.numFeaturePlanes});
        this.unitMaskBuffer = new NDBuffer(unitMaskBuffer, new int[]{s1, pgs.getHeight(), pgs.getWidth()});
        this.actionMaskBuffer = new NDBuffer(actionMaskBuffer, new int[]{s1, pgs.getHeight(), pgs.getWidth(), actionMaskNumEntries});

        // initialize clients
        envSteps = new int[a_num_selfplayenvs + a_num_envs];
        selfPlayClients = new JNIGridnetSharedMemClientSelfPlay[a_num_selfplayenvs/2];
        for (int i = 0; i < selfPlayClients.length; i++) {
            int clientOffset = i*2;
            selfPlayClients[i] = new JNIGridnetSharedMemClientSelfPlay(a_rfs, a_micrortsPath, mapPath, a_utt, partialObs,
                clientOffset, this.obsBuffer, this.unitMaskBuffer, this.actionMaskBuffer);
        }
        clients = new JNIGridnetSharedMemClient[a_num_envs];
        for (int i = 0; i < clients.length; i++) {
            int clientOffset = i+selfPlayClients.length*2;
            clients[i] = new JNIGridnetSharedMemClient(a_rfs, this.mapPath, a_ai2s[i], a_utt, partialObs,
                clientOffset, this.obsBuffer, this.unitMaskBuffer, this.actionMaskBuffer);
        }

        // initialize storage
        reward = new double[s1][rfs.length];
        done = new boolean[s1][rfs.length];
        responses = new Responses(null, null, null);
        rs = new Response[s1];

        if (threadPoolSize > 0) {
            pool = Executors.newFixedThreadPool(threadPoolSize);
        } else {
            pool = null;
        }
    }

    public Responses reset(int[] players) throws Exception {
        for (int i = 0; i < selfPlayClients.length; i++) {
            selfPlayClients[i].reset();
            rs[i*2] = selfPlayClients[i].getResponse(0);
            rs[i*2+1] = selfPlayClients[i].getResponse(1);
        }
        for (int i = selfPlayClients.length*2; i < players.length; i++) {
            rs[i] = clients[i-selfPlayClients.length*2].reset(players[i]);
        }

        for (int i = 0; i < rs.length; i++) {
            reward[i] = rs[i].reward;
            done[i] = rs[i].done;
        }
        responses.set(null, reward, done);
        return responses;
    }

    public Responses gameStep(int[][][] action, int[] players) throws Exception {
        for (int i = 0; i < selfPlayClients.length; i++) {
            selfPlayClients[i].gameStep(action[i*2], action[i*2+1]);
            rs[i*2] = selfPlayClients[i].getResponse(0);
            rs[i*2+1] = selfPlayClients[i].getResponse(1);
            envSteps[i*2] += 1;
            envSteps[i*2+1] += 1;
            if (rs[i*2].done[0] || envSteps[i*2] >= maxSteps) {
                selfPlayClients[i].reset();
                rs[i*2].done[0] = true;
                rs[i*2+1].done[0] = true;
                envSteps[i*2] =0;
                envSteps[i*2+1] =0;
            }
        }

        List<Future<Response>> stepResults = null;
        if (pool != null) {
            final List<Callable<Response>> stepRequests = new ArrayList<>();

            for (int i = selfPlayClients.length*2; i < players.length; i++) {
                final int clientInd = i-selfPlayClients.length*2;
                final int playerInd = i;
                stepRequests.add(() -> {
                    try {
                        return clients[clientInd].gameStep(action[playerInd], players[playerInd]);
                    } catch (Exception e) {
                        // xxx(okachiaev): likely need log it here
                        // not sure what is the best cource of actions here
                        return new Response(null, null, null, null);
                    }
                });
            }

            stepResults = pool.invokeAll(stepRequests);
        }

        for (int i = selfPlayClients.length*2; i < players.length; i++) {
            envSteps[i] += 1;
            if (null == stepResults) {
                rs[i] = clients[i-selfPlayClients.length*2].gameStep(action[i], players[i]);
            } else {
                rs[i] = stepResults.get(i-selfPlayClients.length*2).get();
            }
            if (rs[i].done[0] || envSteps[i] >= maxSteps) {
                clients[i-selfPlayClients.length*2].reset(players[i]);
                rs[i].done[0] = true;
                envSteps[i] = 0;
            }
        }
        for (int i = 0; i < rs.length; i++) {
            reward[i] = rs[i].reward;
            done[i] = rs[i].done;
        }
        responses.set(null, reward, done);
        return responses;
    }

    public void getMasks(final int player) throws Exception {
        for (int i = 0; i < selfPlayClients.length; i++) {
            selfPlayClients[i].getMasks(0);
            selfPlayClients[i].getMasks(1);
        }

        List<Future<Boolean>> maskResults = null;
        if (pool != null) {
            final List<Callable<Boolean>> maskRequests = new ArrayList<>();
            for (int i = 0; i < clients.length; i++) {
                final int clientIndex = i;
                maskRequests.add(() -> {
                    clients[clientIndex].getMasks(player);
                    return true;
                });
            }
            maskResults = pool.invokeAll(maskRequests);
        }

        for (int i = 0; i < clients.length; i++) {
            if (null == maskResults) {
                clients[i].getMasks(player);
            } else {
                maskResults.get(i).get();
            }
        }
    }

    public void close() throws Exception {
        if (clients != null) {
            for (JNIGridnetSharedMemClient client: clients) {
                client.close();
            }
        }
        if (selfPlayClients != null) {
            for (JNIGridnetSharedMemClientSelfPlay client: selfPlayClients) {
                client.close();
            }
        }

        if (pool != null) {
            pool.shutdownNow();
        }
    }
}
