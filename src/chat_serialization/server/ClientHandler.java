package chat_serialization.server;
import chat_serialization.utils.FileMassage;
import chat_serialization.utils.TextMassage;
import chat_xml.serverXml.ServerXml;

import java.io.*;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;


/**
 * Класс обрабатывает одного клиента
 *
 */
public class ClientHandler  extends Thread {
    private long lastSeen;
    private final Socket clientSocket;
    private ObjectInputStream input ;
    private ObjectOutputStream output ;
    private String name;

    ClientHandler(Socket clientSocket) throws IOException {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            output  = new ObjectOutputStream(clientSocket.getOutputStream()); output.flush();
            input  = new ObjectInputStream(clientSocket.getInputStream());
            // LOGIN
            TextMassage first = (TextMassage) input.readObject();
            if(first.getType()!= TextMassage.Type.LOGIN || first.getSender()==null || first.getSender().isBlank()){
                send(TextMassage.error("LOGIN required"));
                close(); return;
            }
            this.lastSeen = System.currentTimeMillis();
            name = first.getSender();
            if (Server.names.contains(name)) {
                send(TextMassage.error("Name already in use"));
                close(); return;
            }
            Server.names.add(name);

            List<String> history = Server.snapshotHistory();

            send(new TextMassage(TextMassage.Type.HISTORY ,"history", name,history));

            send(TextMassage.text("Welcome to the chat !", name));// nроработка команд

            while(Server.server != null && Server.server.isBound() && !Server.server.isClosed()) {
                TextMassage m ;
                FileMassage fl;
                Object o ;
                try {
                    o =  input.readObject();
                    this.lastSeen = System.currentTimeMillis(); // для KEEPALIVE
                } catch (SocketTimeoutException toe){
                    break;
                }
                if (o == null) {
                    break;
                } else if (o instanceof TextMassage) {
                    m = (TextMassage) o;
                    switch (m.getType()) {
                        case MASSAGE -> {
                            Server.addToHistory(name + " : " + m.getMassage());
                            Server.broadcast(new TextMassage(TextMassage.Type.MASSAGE,name,m.getMassage()));
                        }
                        case LOGOUT -> {
                            send(new TextMassage(TextMassage.Type.LOGOUT,"logout","server"));
                            return;
                        }
                        case LIST -> {
                            List<String> users = new ArrayList<>(Server.getNames());
                            System.out.println(users);
                            send(new TextMassage(TextMassage.Type.USERS,null,name,users));
                        }
                        case KEEPALIVE -> {// уже обработанно обновлением при получении обьекта
                        }
                        default -> {
                            send(new TextMassage(TextMassage.Type.ERROR,"unknown Object Type","SERVER"));
                        }
                    }
                } else if (o instanceof FileMassage) {
                    fl = (FileMassage) o;
                    Server.addToHistory(name + " : " + fl.getFileName());
                    for (ClientHandler client : Server.clients) {
                        client.sendObject(fl);
                    }
                } else {
                    send(new TextMassage(TextMassage.Type.ERROR,"unknown Object Type","SERVER"));
                }
            }
        } catch (IOException | ClassNotFoundException e) {

            throw new RuntimeException(e);
        } finally {
            close();
        }
    }

    public void close() {
        Server.names.remove(name);
        try {
            clientSocket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Server.clients.remove(this);
        Server.log.info("Пользователь " + this.getName() + " успешно вышел");
    }

    private void send(TextMassage msg)  {
        try {
            output.writeObject(msg);
            output.flush();
            Server.log.info("отправленно сообщение " + msg);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendObject(Object obj)  {
        try {
            output.writeObject(obj);
            output.flush();
            Server.log.info("отправленно сообщение " + obj);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public ObjectOutputStream getOutput() {
        return output;
    }

    public long getLastSeen() {
        return lastSeen;
    }
}
