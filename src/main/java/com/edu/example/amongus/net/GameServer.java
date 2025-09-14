package com.edu.example.amongus.net;

import com.edu.example.amongus.logic.GameState;
import com.edu.example.amongus.logic.PlayerInfo;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 简单的多人服务器（阻塞IO + 每连接一个线程）
 * 用于学习与小组项目原型（局域网）。
 */
public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    // 线程安全保存每个 client handler
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    private final GameState gameState = new GameState();

    Map<String, ClientHandler> connectedPlayers = new HashMap<>();
    List<String> waitingQueue = new ArrayList<>();
    final int MAX_PLAYERS = 5;
    final int NUM_EVIL = 1;

    public GameServer(int port) {
        this.port = port;
    }

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
    // 将原始字符串 raw 广播给所有已连接客户端
    private void broadcastRaw(String raw) {
        List<ClientHandler> toRemove = new ArrayList<>();

        synchronized (clients) {
            for (ClientHandler ch : clients) {
                if (ch.playerId == null) continue; // 忽略还没 JOIN 的客户端

                try {
                    ch.sendRaw(raw); // 广播消息
                } catch (IOException e) {
                    System.out.println("客户端发送失败，将移除: " + ch.playerId);
                    toRemove.add(ch); // 出现异常，标记为待移除
                }
            }

            // 移除掉线客户端
            for (ClientHandler ch : toRemove) {
                clients.remove(ch);
                if (ch.playerId != null) {
                    gameState.removePlayer(ch.playerId);
                }
            }
        }
    }


//                try {
//                    ch.sendRaw(raw);
//                } catch (IOException e) {
//                    // 客户端断开则移除
//                    System.out.println("Remove client " + ch.playerId);
//                    gameState.removePlayer(ch.playerId);
//                    it.remove();
//                }


    //每个客户端对应一个 ClientHandler
    private class ClientHandler implements Runnable {
        private final Socket sock;
        private final BufferedReader in;
        private final BufferedWriter out;
        String playerId = null;

        public ClientHandler(Socket s) throws IOException {
            this.sock = s;
            this.in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));
            this.out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"));
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

                    // 广播离开
                    Map<String, String> pl = new HashMap<>();
                    pl.put("id", playerId);
                    broadcastRaw(Message.build("LEAVE", pl));
                    System.out.println("Player disconnected: " + playerId);
                } else {
                    System.out.println("Client disconnected before JOIN, skip remove");
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

        // ======================= 游戏开始逻辑 =========================
        private void startGame() {
            System.out.println("满员，开始游戏！");

            // 随机分配角色：第一个坏人，其他好人
            List<String> shuffled = new ArrayList<>(waitingQueue);
            Collections.shuffle(shuffled);
            String evilId = shuffled.get(0);

            for (String id : shuffled) {
                Map<String, String> rolePayload = new HashMap<>();
                rolePayload.put("type", id.equals(evilId) ? "EVIL" : "GOOD");
                ClientHandler ch = findClientById(id);
                if (ch != null) {
                    try {
                        System.out.println("发送角色消息");
                        ch.sendRaw(Message.build("ROLE", rolePayload)); } catch (IOException e) { e.printStackTrace(); }
                }
            }

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


                    PlayerInfo pi = new PlayerInfo(id, nick, color, x, y);
                    gameState.addOrUpdatePlayer(pi);

                    // 1️⃣ 给新玩家发送所有已存在玩家信息
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
                                try {
                                    sendRawToClient(payloadExisting, id); // 给新玩家发
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    // 2️⃣ 广播新玩家加入给所有人（包括自己）
                    broadcastRaw(rawLine);
                    System.out.println("Player JOIN: " + id + " nick=" + nick);

                    // === 等待队列处理 ===
                    if (!waitingQueue.contains(id)) {
                        waitingQueue.add(id);
                    }


                    // 如果还没满员，不广播，只是等待
                    if (waitingQueue.size() < MAX_PLAYERS) {
                        break;
                    }

                    // 满员，开始游戏
                    System.out.println("满员啦！");
                    startGame();
                    break;
                }
                case "MOVE": {
                    String id = m.payload.get("id");
                    double x = Double.parseDouble(m.payload.getOrDefault("x", "0"));
                    double y = Double.parseDouble(m.payload.getOrDefault("y", "0"));
                    PlayerInfo pi = gameState.getPlayer(id);
                    if (pi != null) {
                        pi.setX(x);
                        pi.setY(y);
                    } else {
                        // 如果服务端没记录，创建临时记录
                        pi = new PlayerInfo(id, "Player", "green", x, y);
                        gameState.addOrUpdatePlayer(pi);
                    }
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
                    if (id != null) {
                        gameState.removePlayer(id);
                        broadcastRaw(rawLine);
                    }
                    break;
                }
                default:
                    // TODO: 其他类型（kill/report/vote）后续添加
                    broadcastRaw(rawLine);
            }
        }
    }

    // quick start
    public static void main(String[] args) throws Exception {
        int port = 55555;
        GameServer server = new GameServer(port);
        server.start();
    }
    // 新增：只发送给指定客户端
    private void sendRawToClient(Map<String, String> payload, String targetId) throws IOException {
        String raw = Message.build("JOIN", payload);
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
