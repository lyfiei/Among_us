package com.edu.example.amongus.net;

import com.edu.example.amongus.GameManager;
import com.edu.example.amongus.Player;
import com.edu.example.amongus.PlayerStatus;
import com.edu.example.amongus.logic.GameState;
import com.edu.example.amongus.logic.PlayerInfo;
//import com.edu.example.amongus.task.TaskStatus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private GameManager gameManager;

    private final int port;
    private ServerSocket serverSocket;

    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final GameState gameState = new GameState();

    private volatile boolean meetingActive = false;
    private volatile boolean inVotePhase = false;

    private final Map<String, String> currentVotes = new ConcurrentHashMap<>();

    Map<String, ClientHandler> connectedPlayers = new HashMap<>();
    List<String> waitingQueue = new ArrayList<>();
    final int MAX_PLAYERS = 3;
    final int NUM_EVIL = 1;

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

    public static void broadcastRaw(String raw) {
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



    // ✅ 投票结算
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

        // ✅ 只淘汰一个玩家，不影响其他玩家
        if (votedOut != null && !votedOut.isEmpty()) {
            PlayerInfo pi = gameState.getPlayer(votedOut);
            if (pi != null) {
                pi.setAlive(false); // 只修改该玩家
                Map<String, String> deadPayload = new HashMap<>();
                deadPayload.put("id", votedOut);
                broadcastRaw(Message.build("DEAD", deadPayload));
                System.out.println("Player " + votedOut + " 出局 (DEAD)");

                // ✅ 检查游戏是否结束
                gameManager.checkGameOver();
            }
        }
        try {
            broadcastRaw(Message.build("MEETING_END", Map.of()));
            System.out.println("[SERVER] Broadcast MEETING_END");
        } catch (Exception ex) {
            System.err.println("[SERVER] 无法广播 MEETING_END: " + ex.getMessage());
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
        private ClientHandler findClientById(String id) {
            for (ClientHandler ch : clients) {
                if (id.equals(ch.playerId)) return ch;
            }
            System.out.println("Client not found: " + id);
            return null;
        }

        private void startGame() {
            System.out.println("满员，开始游戏！");

            // 随机分配角色：第一个坏人，其他好人
            List<String> shuffled = new ArrayList<>(waitingQueue);
            Collections.shuffle(shuffled);
            String evilId = shuffled.get(0);

            for (String id : shuffled) {
                Map<String, String> rolePayload = new HashMap<>();
                rolePayload.put("type", id.equals(evilId) ? "EVIL" : "GOOD");
                rolePayload.put("target", id); // 添加目标ID
                broadcastRaw(Message.build("ROLE", rolePayload));
                ClientHandler ch = findClientById(id);
                if (ch != null) {
                    try {
                        System.out.println("发送角色消息");
                        ch.sendRaw(Message.build("ROLE", rolePayload)); } catch (IOException e) { e.printStackTrace(); }
                }
            }
            List<Player> playerList = new ArrayList<>();
            for (PlayerInfo pi : gameState.getPlayers()) {
                Player p = new Player(pi.getX(), pi.getY(), null,null);
                p.setType(pi.getType());
                playerList.add(p);
            }
            // 初始化全局 GameManager
            GameServer.this.gameManager = new GameManager(playerList);

            // 广播游戏开始
            for (String id : shuffled) {
                ClientHandler ch = findClientById(id);
                if (ch == null) {
                    System.out.println("ch is null");
                } else {
                    System.out.println("ch is NOT null");
                    String pid = ch.playerId;
                    System.out.println("ch.playerId = " + pid);
                }

                if (ch != null && ch.playerId != null) {
                    try {
                        ch.sendRaw(Message.build("GAME_START", Map.of()));
                    } catch (IOException e) { e.printStackTrace(); }
                } else {
                    System.out.println("找不到客户端发送 GAME_START: " + id);
                }
            }

            waitingQueue.clear();
        }

        private void handleMessage(Message.Parsed m, String rawLine) {
            switch (m.type) {
                case "JOIN": {
                    String id = m.payload.get("id");
                    System.out.println("收到 JOIN: " + id);
                    if (gameState.getPlayer(id) != null) {
                        System.out.println("重复 JOIN，忽略: " + id);
                        break;
                    }
                    this.playerId = id;
                    if (!clients.contains(this)) {
                        clients.add(this);   // 确保放进去
                    }
                    String nick = m.payload.getOrDefault("nick", "Player");
                    String color = m.payload.getOrDefault("color", "green");
                    double x = Double.parseDouble(m.payload.getOrDefault("x", "0"));
                    double y = Double.parseDouble(m.payload.getOrDefault("y", "0"));

                    this.playerId = id;
                    PlayerInfo pi = new PlayerInfo(id, nick, color, x, y);
                    pi.setAlive(true); // 默认活着
                    gameState.addOrUpdatePlayer(pi);

                    // 确保自己在 clients 列表里
                    if (!clients.contains(this)) {
                        clients.add(this);
                    }
                    // 把已存在的玩家发给新加入的
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

                    // 3️⃣ 广播新玩家加入给所有人（包括自己）
                    Map<String, String> broadcastPayload = new HashMap<>();
                    broadcastPayload.put("id", id);
                    broadcastPayload.put("nick", nick);
                    broadcastPayload.put("color", color);
                    broadcastPayload.put("x", String.valueOf(x));
                    broadcastPayload.put("y", String.valueOf(y));
                    broadcastRaw(Message.build("JOIN", broadcastPayload));
                    System.out.println("Player JOIN: " + id + " nick=" + nick);


                    // === 等待队列处理 ===
                    if (!waitingQueue.contains(id)) {
                        waitingQueue.add(id);
                    }


                    // 额外广播匹配进度
                    Map<String, String> matchPayload = new HashMap<>();
                    matchPayload.put("current", String.valueOf(waitingQueue.size()));
                    matchPayload.put("total", String.valueOf(MAX_PLAYERS));
                    System.out.println("Match_UPDATE: " + id + " " + matchPayload);
                    try { sendRawToClient(matchPayload, id, "MATCH_UPDATE"); } catch (IOException e) { e.printStackTrace(); }


                    broadcastRaw(Message.build("MATCH_UPDATE", matchPayload));
                    System.out.println("[SERVER] MATCH_UPDATE -> " + waitingQueue.size() + "/" + MAX_PLAYERS);

                    // 如果满员，开始游戏
                    if (waitingQueue.size() >= MAX_PLAYERS) {
                        System.out.println("满员啦！");
                        startGame();
                    }

                    break;
                }
                case "MOVE": {
                    String id = m.payload.get("id");
                    double x = Double.parseDouble(m.payload.getOrDefault("x", "0"));
                    double y = Double.parseDouble(m.payload.getOrDefault("y", "0"));
                    PlayerInfo pi = gameState.getPlayer(id);

                    // ✅ 只屏蔽死亡玩家，活着的照常广播
                    if (pi != null) {
                        if (pi.isAlive()) {
                            pi.setX(x);
                            pi.setY(y);
                            broadcastRaw(rawLine);
                        } else {
                            System.out.println("忽略死亡玩家的移动: " + id);
                        }
                    } else {
                        // 服务端没记录，创建临时记录
                        pi = new PlayerInfo(id, "Player", "green", x, y);
                        gameState.addOrUpdatePlayer(pi);
                        broadcastRaw(rawLine);
                    }
                    break;
                }
                case "CHAT": {
                    String id = m.payload.get("id");
                    String msg = m.payload.get("msg");
                    PlayerInfo pi = gameState.getPlayer(id);
                    Map<String, String> chatPayload = new HashMap<>();
                    chatPayload.put("id", id);
                    chatPayload.put("msg", msg);
                    // 从服务器保存的 PlayerInfo 里补充 nick/color
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
                case "KILL": {
                    String killerId = m.payload.get("killer");
                    String victimId = m.payload.get("victim");
                    if (victimId == null) break;
                    PlayerInfo victim = gameState.getPlayer(victimId);
                    if (victim != null && victim.isAlive()) {
                        victim.setAlive(false);
                        Map<String, String> deadPayload = new HashMap<>();
                        deadPayload.put("id", victimId);
                        broadcastRaw(Message.build("DEAD", deadPayload));
                        System.out.println("[SERVER] Player " + victimId + " 被 " + killerId + " 杀死 (广播 DEAD)");
                        // 检查游戏结束
                        if (gameManager != null) gameManager.checkGameOver();
                    } else {
                        System.out.println("[SERVER] KILL 请求但目标不存在或已死: " + victimId);
                    }
                    break;
                }

                default:
                    broadcastRaw(rawLine);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        new GameServer(22222).start();
    }
    // 新增：只发送给指定客户端
    private void sendRawToClient(Map<String, String> payload, String targetId, String type) throws IOException {
        String raw = Message.build(type, payload);
        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (targetId.equals(ch.playerId)) {
                    ch.sendRaw(raw);
                    break;
                }
            }
        }
    }

}
