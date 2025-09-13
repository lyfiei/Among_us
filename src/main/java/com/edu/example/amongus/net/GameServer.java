package com.edu.example.amongus.net;

import com.edu.example.amongus.PlayerStatus;
import com.edu.example.amongus.logic.GameState;
import com.edu.example.amongus.logic.PlayerInfo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多人服务器（阻塞IO，每连接一个线程）——增加了“会议（讨论+投票）”流程支持
 *
 * 协议（摘要）：
 * - JOIN: 客户端加入（payload: id, nick, color, x, y）
 * - MOVE: 客户位移广播（payload: id, x, y）
 * - CHAT: 聊天广播（payload: id, msg）
 * - REPORT: 有人举报/开会（payload: id [可选]）
 * - MEETING_DISCUSSION_START: 服务器 -> 客户（payload: duration）
 * - MEETING_VOTE_START: 服务器 -> 客户（payload: duration）
 * - VOTE: 客户 -> 服务器（payload: voter, target）
 * - VOTE_UPDATE: 服务器 -> 客户（每收到一票时广播）
 * - VOTE_RESULT: 服务器 -> 客户（payload: votedOut, optional: counts...）
 * - DEAD: 服务器 -> 客户（payload: id）  ✅ 新增，通知某个玩家出局
 */
public class GameServer {
    private final int port;
    private ServerSocket serverSocket;

    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final GameState gameState = new GameState();

    private volatile boolean meetingActive = false;
    private volatile boolean inVotePhase = false;

    private final Map<String, String> currentVotes = new ConcurrentHashMap<>();

    public GameServer(int port) { this.port = port; }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        System.out.println("GameServer listening on port " + port);
        while (true) {
            Socket s = serverSocket.accept();
            ClientHandler h = new ClientHandler(s);
            clients.add(h);
            new Thread(h).start();
        }
    }

    private void broadcastRaw(String raw) {
        synchronized (clients) {
            Iterator<ClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ClientHandler ch = it.next();
                try {
                    ch.sendRaw(raw);
                } catch (IOException e) {
                    System.out.println("Remove client " + ch.playerId);
                    gameState.removePlayer(ch.playerId);
                    it.remove();
                }
            }
        }
    }

    // ✅ 结算投票结果
    private synchronized void finalizeMeeting() {
        Map<String, Integer> tally = new HashMap<>();
        for (String target : currentVotes.values()) {
            if (target == null || target.isEmpty()) continue;
            tally.put(target, tally.getOrDefault(target, 0) + 1);
        }

        int maxVotes = 0;
        String votedOut = null;
        for (var entry : tally.entrySet()) {
            int v = entry.getValue();
            if (v > maxVotes) {
                maxVotes = v;
                votedOut = entry.getKey();
            } else if (v == maxVotes) {
                // tie
            }
        }

        if (maxVotes > 0) {
            int countMax = 0;
            for (var entry : tally.entrySet()) if (entry.getValue() == maxVotes) countMax++;
            if (countMax > 1) {
                votedOut = ""; // 平票无人出局
            }
        } else {
            votedOut = "";
        }

        Map<String, String> payload = new HashMap<>();
        payload.put("votedOut", votedOut == null ? "" : votedOut);
        for (var e : tally.entrySet()) {
            payload.put("count_" + e.getKey(), String.valueOf(e.getValue()));
        }

        broadcastRaw(Message.build("VOTE_RESULT", payload));

        // ✅ 若有玩家出局，修改状态并广播 DEAD
        if (votedOut != null && !votedOut.isEmpty()) {
            PlayerInfo pi = gameState.getPlayer(votedOut);
            if (pi != null) {
                // 使用你 PlayerInfo 中存在的方法：setAlive(false)
                pi.setAlive(false);
                Map<String, String> deadPayload = new HashMap<>();
                deadPayload.put("id", votedOut);
                broadcastRaw(Message.build("DEAD", deadPayload));
                System.out.println("Player " + votedOut + " 出局 (DEAD)");
            }
        }

        currentVotes.clear();
        meetingActive = false;
        inVotePhase = false;
    }

    public synchronized void startMeeting(int discussionSeconds, int voteSeconds) {
        if (meetingActive) return;
        meetingActive = true;
        inVotePhase = false;
        currentVotes.clear();

        Map<String, String> payload = new HashMap<>();
        payload.put("duration", String.valueOf(discussionSeconds));
        broadcastRaw(Message.build("MEETING_DISCUSSION_START", payload));
        System.out.println("Meeting: DISCUSSION started for " + discussionSeconds + "s");

        new Thread(() -> {
            try {
                Thread.sleep(discussionSeconds * 1000L);

                inVotePhase = true;
                Map<String, String> votePayload = new HashMap<>();
                votePayload.put("duration", String.valueOf(voteSeconds));
                broadcastRaw(Message.build("MEETING_VOTE_START", votePayload));
                System.out.println("Meeting: VOTE started for " + voteSeconds + "s");

                Thread.sleep(voteSeconds * 1000L);

                finalizeMeeting();
                System.out.println("Meeting: finalized");
            } catch (InterruptedException ignored) {
                meetingActive = false;
                inVotePhase = false;
            }
        }).start();
    }

    private void sendRawToClient(Map<String, String> payload, String targetId) throws IOException {
        String raw = Message.build("JOIN", payload);
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (targetId.equals(ch.playerId)) { ch.sendRaw(raw); break; }
            }
        }
    }

    private class ClientHandler implements Runnable {
        private final Socket sock;
        private final BufferedReader in;
        private final BufferedWriter out;
        String playerId = null;

        public ClientHandler(Socket s) throws IOException {
            this.sock = s;
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            this.out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
        }

        public void sendRaw(String raw) throws IOException {
            out.write(raw);
            out.write("\n");
            out.flush();
        }

        @Override
        public void run() {
            System.out.println("Client connected: " + sock.getRemoteSocketAddress());
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    Message.Parsed m = Message.parse(line);
                    if (m == null) continue;
                    handleMessage(m, line);
                }
            } catch (IOException e) {
                System.out.println("Client IO error: " + e.getMessage());
            } finally {
                try { sock.close(); } catch (IOException ignored) {}
                if (playerId != null) {
                    gameState.removePlayer(playerId);
                    Map<String, String> pl = new HashMap<>();
                    pl.put("id", playerId);
                    broadcastRaw(Message.build("LEAVE", pl));
                }
            }
        }

        private void handleMessage(Message.Parsed m, String rawLine) {
            switch (m.type) {
                case "JOIN": {
                    String id = m.payload.get("id");
                    String nick = m.payload.getOrDefault("nick", "Player");
                    String color = m.payload.getOrDefault("color", "green");
                    double x = Double.parseDouble(m.payload.getOrDefault("x", "0"));
                    double y = Double.parseDouble(m.payload.getOrDefault("y", "0"));

                    this.playerId = id;
                    PlayerInfo pi = new PlayerInfo(id, nick, color, x, y);
                    gameState.addOrUpdatePlayer(pi);

                    synchronized (clients) {
                        for (ClientHandler ch : clients) {
                            if (ch.playerId == null || ch.playerId.equals(id)) continue;
                            PlayerInfo existing = gameState.getPlayer(ch.playerId);
                            if (existing != null) {
                                Map<String, String> payloadExisting = new HashMap<>();
                                payloadExisting.put("id", existing.getId());
                                payloadExisting.put("nick", existing.getNick());
                                payloadExisting.put("color", existing.getColor());
                                payloadExisting.put("x", String.valueOf(existing.getX()));
                                payloadExisting.put("y", String.valueOf(existing.getY()));
                                try { sendRawToClient(payloadExisting, id); } catch (IOException e) { e.printStackTrace(); }
                            }
                        }
                    }

                    Map<String, String> broadcastPayload = new HashMap<>();
                    broadcastPayload.put("id", id);
                    broadcastPayload.put("nick", nick);
                    broadcastPayload.put("color", color);
                    broadcastPayload.put("x", String.valueOf(x));
                    broadcastPayload.put("y", String.valueOf(y));
                    broadcastRaw(Message.build("JOIN", broadcastPayload));
                    System.out.println("Player JOIN: " + id + " nick=" + nick);
                    break;
                }
                case "MOVE": {
                    String id = m.payload.get("id");
                    double x = Double.parseDouble(m.payload.getOrDefault("x", "0"));
                    double y = Double.parseDouble(m.payload.getOrDefault("y", "0"));
                    PlayerInfo pi = gameState.getPlayer(id);
                    if (pi != null) { pi.setX(x); pi.setY(y); }
                    else gameState.addOrUpdatePlayer(new PlayerInfo(id, "Player", "green", x, y));
                    broadcastRaw(rawLine);
                    break;
                }
                case "CHAT": {
                    String id = m.payload.get("id");
                    String msg = m.payload.get("msg");
                    PlayerInfo pi = gameState.getPlayer(id);
                    Map<String, String> chatPayload = new HashMap<>();
                    chatPayload.put("id", id);
                    chatPayload.put("msg", msg);
                    if (pi != null) {
                        chatPayload.put("nick", pi.getNick());
                        chatPayload.put("color", pi.getColor());
                    }
                    broadcastRaw(Message.build("CHAT", chatPayload));
                    break;
                }
                case "LEAVE": {
                    String id = m.payload.get("id");
                    gameState.removePlayer(id);
                    broadcastRaw(rawLine);
                    break;
                }
                case "REPORT": {
                    int discussion = Integer.parseInt(m.payload.getOrDefault("discussion", "120"));
                    int vote = Integer.parseInt(m.payload.getOrDefault("vote", "60"));
                    System.out.println("REPORT from " + m.payload.get("id") + " -> start meeting");
                    startMeeting(discussion, vote);
                    break;
                }
                case "VOTE": {
                    if (!meetingActive || !inVotePhase) {
                        System.out.println("Received VOTE outside vote phase - ignored");
                        break;
                    }
                    String voter = m.payload.get("voter");
                    String target = m.payload.get("target");
                    if (voter != null) {
                        currentVotes.put(voter, target == null ? "" : target);
                        System.out.println(voter + " 投票给 " + target);

                        Map<String, String> votePayload = new HashMap<>();
                        votePayload.put("voter", voter);
                        votePayload.put("target", target == null ? "" : target);
                        broadcastRaw(Message.build("VOTE_UPDATE", votePayload));
                    }
                    break;
                }
                case "FINALIZE": {
                    if (meetingActive) finalizeMeeting();
                    break;
                }
                default:
                    broadcastRaw(rawLine);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new GameServer(55555).start();
    }
}
