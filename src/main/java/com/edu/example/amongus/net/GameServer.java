package com.edu.example.amongus.net;

import com.edu.example.amongus.logic.GameState;
import com.edu.example.amongus.logic.PlayerInfo;
import com.edu.example.amongus.task.TaskStatus;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简单的多人服务器（阻塞IO + 每连接一个线程）
 * 用于学习与小组项目原型（局域网）。
 */
public class GameServer {
    private final int port;
    private ServerSocket serverSocket;
    // 线程安全保存每个 client handler
    private final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private final GameState gameState = new GameState();
    // taskName -> completedSteps
    private final Map<String, Integer> taskProgress = new ConcurrentHashMap<>();

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

    //将原始字符串 raw 广播给所有已连接客户端。
    private void broadcastRaw(String raw) {
        synchronized (clients) {
            Iterator<ClientHandler> it = clients.iterator();
            while (it.hasNext()) {
                ClientHandler ch = it.next();
                try {
                    ch.sendRaw(raw);
                } catch (IOException e) {
                    // 客户端断开则移除
                    System.out.println("Remove client " + ch.playerId);
                    gameState.removePlayer(ch.playerId);
                    it.remove();
                }
            }
        }
    }

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
                                    sendRawToClient(payloadExisting, id, "JOIN");
                                    // 给新玩家发
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }

                    // 2️⃣ 给新玩家发送最新任务状态（安全版）
                    Map<String, String> taskPayload = new HashMap<>();
                    StringBuilder sb = new StringBuilder();
                    for (Map.Entry<String, TaskStatus> entry : gameState.getTaskStatuses().entrySet()) {
                        sb.append(entry.getKey()).append("=").append(entry.getValue().getCompleted()).append(",");
                    }
                    if (sb.length() > 0) {
                        sb.setLength(sb.length() - 1); // 去掉最后一个逗号
                    }
                    taskPayload.put("all", sb.toString());

                    System.out.println("[SERVER-SEND] TASK_SYNC -> 发送给新玩家 " + id + " : " + taskPayload);


                    try {
                        sendRawToClient(taskPayload, id, "TASK_SYNC");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }



                    // 3️⃣广播新玩家加入给所有人（包括自己）
                    broadcastRaw(rawLine);
                    System.out.println("Player JOIN: " + id + " nick=" + nick);
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
                    gameState.removePlayer(id);
                    broadcastRaw(rawLine);
                    break;
                }
                case "TASK_UPDATE": {
                    String taskName = m.payload.get("taskName");
                    int steps = Integer.parseInt(m.payload.getOrDefault("completedSteps", "0"));

                    System.out.println("[SERVER-RECV] 来自 "
                            + (playerId != null ? playerId : sock.getRemoteSocketAddress())
                            + " -> " + taskName + " steps=" + steps);

                    // 更新服务器全局状态
                    taskProgress.put(taskName, steps);

                    // 广播给所有客户端
                    broadcastRaw(rawLine);

                    System.out.println("[SERVER-BROADCAST] 广播 TASK_UPDATE -> "
                            + taskName + " steps=" + steps);
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
