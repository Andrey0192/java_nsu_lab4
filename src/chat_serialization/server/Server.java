package chat_serialization.server;

import chat_serialization.config.Config;
import chat_serialization.logger.Logger;
import chat_serialization.utils.TextMassage;
import chat_xml.serverXml.ClientHandlerXml;
import chat_xml.serverXml.ServerXml;

import java.io.*;
import java.net.ServerSocket;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Server  {
    public static ServerSocket server;

    public static LinkedList<ClientHandler> clients = new LinkedList<>();
    public static Deque<String> historyOfMassages = new ArrayDeque<>();
    static final Set<String> names = ConcurrentHashMap.newKeySet();
    public static Config cfg;
    public static Logger log;

    public Server() throws IOException {
        log = new Logger();
        cfg  = new Config("chatXml.configXml.properties");
    }

    public static void main(String[] args) throws IOException {
        try {
            server = new ServerSocket(cfg.getPort());

            System.out.println("ServerXml started");

            new Thread(Server::readClients, "timeout-cleaner").start();

            //мониторим входящие подключения
            int count = 0;
            while (server != null && server.isBound() && !server.isClosed() && (count <= cfg.getMaxConnections())) {
                Socket clientSocket = server.accept();
                log.info("Client connected");
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                clientHandler.setName( "chat_serialization.client-" + clientSocket.getPort() + ". ");
                clientHandler.start();
                count++;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            server.close();
        }
    }
    public static void broadcast(TextMassage msg) throws IOException {
        for (ClientHandler client : clients) {
            client.getOutput().writeObject(msg);
            client.getOutput().flush();
        }
        ServerXml.log.info("отправлено сообщение всем " + msg);

    }

    public static void addToHistory(String message) {
        historyOfMassages.addLast(message);
        while (historyOfMassages.size() > cfg.getHistorySize()) {         //брать из конфига
            historyOfMassages.removeFirst();
        }
        log.info("В историю добавленно " +  message + "\n теперь история содержит : " + historyOfMassages);
    }
    private static void readClients() {
        while (server != null && server.isBound() && !server.isClosed()) {
            try {
                Thread.sleep(10_000);
                long now = System.currentTimeMillis();
                for (ClientHandler c : clients) {
                    if (now - c.getLastSeen() > cfg.getConnectionTimeout()) {
                        c.close();
                        log.info("Disconnect " + c.getName() + " by timeout");
                    }
                }
            } catch (InterruptedException _) {
            }
        }
    }

    public static List<String>  snapshotHistory(){
        return new LinkedList<>(historyOfMassages);
    }

    public static Set<String> getNames() {
        return names;
    }

}

