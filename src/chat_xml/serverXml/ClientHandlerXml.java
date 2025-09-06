package chat_xml.serverXml;

import chat_xml.utilsXml.FrameIO;
import chat_xml.utilsXml.XmlProtocol;
import org.w3c.dom.Document;
import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import static chat_xml.utilsXml.XmlProtocol.*;

/**
 * Класс обрабатывает одного клиента
 *
 */
public class ClientHandlerXml extends Thread {
    private final Socket clientSocket;
    private DataInputStream input ;
    private DataOutputStream output ;
    private String name;
    private UUID uuid = UUID.randomUUID();
    private long lastSeen;

    ClientHandlerXml(Socket clientSocket) throws IOException {
        this.clientSocket = clientSocket;
    }

    @Override
    public void run() {
        try {
            input  = new DataInputStream(clientSocket.getInputStream());
            output  = new DataOutputStream(clientSocket.getOutputStream());
            output.flush();

            Document firstDocument = parse(FrameIO.ReadFrame(input));
            String command = getCommand(firstDocument);
            this.lastSeen = System.currentTimeMillis();

            name = textof(firstDocument,"name");

            if ( !command.equals("login" ) ) {
                send(error("Требуется логин"));
                ServerXml.log.info(name + " Требуется логин");
                close(); return;
            }
            if (name == null || name.isBlank()) {
                send(error("Требуется имя"));
                ServerXml.log.info(name + " Требуется имя");
                close(); return;
            }
            if (ServerXml.names.contains(name)) {
                send(error("Name already in use"));
                ServerXml.log.info(name + " Name already in use ");
                close(); return;
            }
            ServerXml.names.add(name);
            ServerXml.sessions.put(uuid, this);
            send(successAnswer(uuid));
            ServerXml.log.info("Клиент успешно вошел");

            List<String> history = ServerXml.snapshotHistory();

//            send(evHistory(history));
            for (String ev : history) { // в истории теперь храниться Xml
                send(ev);
            }
            ServerXml.broadcastExcept(evUserLogin(name), this);

            while( ServerXml.server != null && ServerXml.server.isBound() && !ServerXml.server.isClosed()) {
                Document d = parse(FrameIO.ReadFrame(input));
                String commandName = getCommand(d);
                this.lastSeen = System.currentTimeMillis();
                if (commandName == null) {
                    send(error("Отсутсвует имя команды"));
                    ServerXml.log.info("Отсутсвует имя команды");
                }
                switch (commandName) {
                    case "message" -> {
                        String text = textof(d,"message");
                        String ev = evMessage(name, text);
                        ServerXml.addToHistory(ev);
                        ServerXml.broadcast(evMessage(name,text));
                        ServerXml.log.info(name + " : " + text);
                    }
                    case "logout" -> {
                        send(sucsess());
                        return; // close отработает в finally
                    }
                    case "list" -> {
                        String sess = textof(d, "session");
                        if(sess==null || !sess.equals(uuid.toString())){
                            send(error("invalid session"));
                            break;
                        }
                        List<String> users = new ArrayList<>(ServerXml.names);
                        send(successUsers(users));
                    }
                    case "file" -> {
                        String sess = textof(d, "session");
                        if(sess==null || !sess.equals(uuid.toString())){ send(error("invalid session")); break; }
                        String filename = textof(d, "name");
                        String ctype = textof(d, "type");
                        String main = textof(d, "main");
                        String sizeStr = textof(d, "size");
                        String b64 = textof(d, "data");
                        if(filename==null || b64==null){ send(error("bad file")); break; }
                        byte[] data = Base64.getDecoder().decode(b64);
                        long size = data.length;
                        long max = (long) ServerXml.cfg.getMaxFileMb() * 1024 * 1024;
                        if(size > max){
                            send(error("File too large (max "+
                                        ServerXml.cfg.getMaxFileMb()+
                                        100 +
                                        " MB)"));
                            break;
                        }
                        if(ctype==null) ctype = "application/octet-stream";
                        if(main==null) main = ctype.contains("/") ? ctype.substring(0, ctype.indexOf('/')) : ctype;

                        String ev = evFile(name, filename, ctype, main, size, Base64.getEncoder().encodeToString(data));
                        ServerXml.addToHistory(ev);
                        ServerXml.broadcast(ev);
                        send(sucsess());
                    }
                    // уже обработанно обновлением при получении обьекта
                    case "keepalive" -> {}
                    default -> send(error("unknown command"));
                }
            }
        } catch (Exception _) {
        } finally {
            close();
        }
    }
    public void close() {
        try {
            if (name != null) {
                ServerXml.names.remove(name);
                ServerXml.broadcastExcept(XmlProtocol.evUserLogout(name), this);
            }
        } catch (Exception _) {}

        try { ServerXml.sessions.remove(uuid); } catch (Exception _) {}
        try { clientSocket.close(); } catch (IOException _) {}
        try { ServerXml.clients.remove(this); } catch (Exception _) {}
        ServerXml.log.info("Пользователь " + this.getName() + " успешно вышел");
    }

    public void send(String xml) throws IOException {
        if (clientSocket.isClosed()) throw new EOFException("socket closed");
        FrameIO.WriteFrame(output,xml);
        ServerXml.log.info("отправленно сообщение " + xml);

    }
    public long getLastSeen() {
        return lastSeen;
    }

}
