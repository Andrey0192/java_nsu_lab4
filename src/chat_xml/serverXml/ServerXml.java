package chat_xml.serverXml;

import chat_xml.configXml.ConfigXml;
import chat_xml.loggerXml.LoggerXml;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ServerXml {
    public static ServerSocket server;

    public static final LinkedList<ClientHandlerXml> clients = new LinkedList<>();
    private static final Deque<String> historyOfMassages = new ArrayDeque<>();
    static final Map<UUID,ClientHandlerXml> sessions =  new ConcurrentHashMap<>();
    static final Set<String> names = ConcurrentHashMap.newKeySet();

    public static ConfigXml cfg;
    public static LoggerXml log;

    public static void main(String[] args) throws IOException {
        try {
            log = new LoggerXml();
            cfg = new ConfigXml("chatXml.configXml.properties");

            server = new ServerSocket(cfg.getPort());

            //Поток для читания
            new Thread(ServerXml::readClients, "timeout-cleaner").start();

            System.out.println("ServerXml started");
            int count = 0;
            while (server != null && server.isBound() && !server.isClosed() && (count <= cfg.getMaxConnections())) {
                Socket clientSocket = server.accept();
                log.info("Client connected");
                ClientHandlerXml clientHandlerXml = new ClientHandlerXml(clientSocket);
                clients.add(clientHandlerXml);
                clientHandlerXml.setName( "chat_xml.client-" + clientSocket.getPort());
                clientHandlerXml.start();
                count++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            server.close();
        }
    }

    public static void addToHistory(String message) {
        historyOfMassages.addLast(message);
        while (historyOfMassages.size() > cfg.getHistorySize()) {         //брать из конфига
            historyOfMassages.removeFirst();
        }
        log.info("В историю добавленно " +  message + "\n теперь история содержит : " + historyOfMassages);
    }

    public static List<String>  snapshotHistory(){
        return new LinkedList<>(historyOfMassages);
    }

    public static void broadcast(String xml) {
        for (var it = clients.iterator(); it.hasNext();) {
            ClientHandlerXml c = it.next();
            try {
                c.send(xml);
            } catch (IOException e) {
                try { c.close(); } catch (Exception _) {}
                it.remove();
            }
        }
        ServerXml.log.info("отправлено сообщение всем " + xml);

    }

    public static void broadcastExcept(String xml, ClientHandlerXml except) {
        for (var it = clients.iterator(); it.hasNext();) {
            ClientHandlerXml c = it.next();
            if (c == except) continue;
            try {
                c.send(xml);
            } catch (IOException e) {
                try { c.close(); } catch (Exception _) {}
                it.remove();
            }
        }
        ServerXml.log.info("отправленно сообщение всем кроме " + except.getName() +  " : " + xml);

    }

    private static void readClients() {
        while (server != null && server.isBound() && !server.isClosed()) {
            try {
                Thread.sleep(10_000);
                long now = System.currentTimeMillis();
                for (ClientHandlerXml c : clients) {
                    if (now - c.getLastSeen() > cfg.getConnectionTimeout()) {
                        c.close();
                        log.info("Disconnect " + c.getName() + " by timeout");
                    }
                }
            } catch (InterruptedException _) {
            }
        }
    }
}

