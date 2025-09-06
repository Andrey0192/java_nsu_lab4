package chat_xml.guiXml;

import chat_xml.utilsXml.FrameIO;
import chat_xml.utilsXml.XmlProtocol;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Timer;
import java.util.TimerTask;

import static chat_xml.utilsXml.XmlProtocol.*;

public class ClientSwingXml extends JFrame {
    private Socket socket;
    private final JTextPane chat = new JTextPane();
    private boolean closing = false;
    private Timer keepAliveTimer;

    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JList<String> users = new JList<>(userModel);
    private final JTextField inputField = new JTextField();
    private DataInputStream input;
    private DataOutputStream output;
    private String session;
    public ClientSwingXml() {
        setTitle("Chat (Serialization)");
        setSize(700,500); setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        chat.setEditable(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,  new JScrollPane(chat),
                                                                        new JScrollPane(users));
        split.setDividerLocation(500);

        JPanel bottom = new JPanel(new BorderLayout());
        JPanel flow   = new JPanel(new FlowLayout());

        JButton btnList = new JButton("List users");
        JButton btnSend = new JButton("Send");
        JButton btnFile = new JButton("Send file…");

        //----------добавляем элементы на окно JFrame-------------//
        flow.add(btnSend); flow.add(btnFile);
        flow.setVisible(true);

        bottom.add(inputField, BorderLayout.CENTER);
        bottom.add(flow, BorderLayout.EAST);
        bottom.add(btnList, BorderLayout.WEST);

        add(split, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);
        //----------ActionListener--------------------------------//
        btnSend.addActionListener(e -> sendMassage());
        btnList.addActionListener(e -> sendList());
        btnFile.addActionListener(e -> sendFile());

        addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent e){ logoutAndClose(); }
        });

        setLocationRelativeTo(null);
    }

    private void logoutAndClose(){
        closing = true;
        try { if (keepAliveTimer != null) keepAliveTimer.cancel(); } catch (Exception ignored) {}
        try {
            if (output != null) {
                sendLogout();
            }
        } catch (Exception ignored) {}

        try {
             if (socket != null && !socket.isClosed()) socket.shutdownOutput();
        } catch (Exception ignored) {}

        try { if (input  != null) input.close();  } catch (Exception ignored) {}
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }
    private void sendLogout(){
        try{ FrameIO.WriteFrame(output, "<command name=\"logout\">" +
                                                "<session>"+session+"</session>" +
                                            "</command>"); }
        catch(Exception _){}
    }
    public void connect(String host, Integer port, String name , String clientType) throws Exception {

        socket = new Socket(host, port);
        if (socket.isConnected()) {
            input = new DataInputStream(socket.getInputStream());
            output = new DataOutputStream(socket.getOutputStream()); output.flush();

            System.out.println("Connected to " + socket.getRemoteSocketAddress());
            System.out.println("output" + output);
        } else {
            JOptionPane.showMessageDialog(ClientSwingXml.this,
                    "Connection failed", "Error", JOptionPane.ERROR_MESSAGE);
        }

        //Отправляем логиин
        String login = "<command name=\"login\"><name>"+ XmlProtocol.esc(name)+"</name><type>"+XmlProtocol.esc(clientType)+"</type></command>";
        FrameIO.WriteFrame(output, login);

        Document d = parse(FrameIO.ReadFrame(input));
        if(!"success".equals(d.getDocumentElement().getTagName())){ appendText("[login error]"); return; }
        session = XmlProtocol.textof(d, "session");
        keepAliveTimer  =  new Timer(true);
        keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run()  {
                    try {
                        FrameIO.WriteFrame(output ,"<command name=\"keepalive\"><session>"+session+"</session></command>");
                    } catch (Exception ignored) {
                    }
                }},
                3000,3000 );


        Thread thread = new Thread(() -> {
            try {
                while(!closing){
                    Document document = parse(FrameIO.ReadFrame(input));
                    String commandName = getCommand(document);
                    switch (commandName) {
                        case "message"  ->{
                            appendText( XmlProtocol.textof(document , "name")  +  ": " +
                                        XmlProtocol.textof(document , "message"));
                        }
//                         тег истории по факту отсутсвует
//                        case "history" -> {
//
//                            var msgs = document.getElementsByTagName("m");
//                            for(int i=0;i<msgs.getLength();i++){
//                                Node m = msgs.item(i);
//                                String n = m.getChildNodes().item(0).getTextContent();
//                                String t = m.getChildNodes().item(1).getTextContent();
//                                appendText(n+": "+t);
//                            }
//                        }
                        case "file" -> {
                            String from     = XmlProtocol.textof(document, "name");
                            String filename = XmlProtocol.textof(document, "filename");
                            String main     = XmlProtocol.textof(document, "main");
                            String b64      = XmlProtocol.textof(document, "data");
                            byte[] data     = Base64.getDecoder().decode(b64);

                            Path dir = Paths.get(System.getProperty("user.home"), "Downloads", "chat");
                            Files.createDirectories(dir);
                            Path outPath = dir.resolve(System.currentTimeMillis()+"_"+filename);
                            Files.write(outPath, data);

                            appendText("[файл от "+from+"] "+filename+" → "+outPath);
                            if ("image".equalsIgnoreCase(main)) {
                                appendImage(data, from + ": " + filename);
                            }
                        }
                        case "error" -> {
                            appendText("[Ошибка] " + XmlProtocol.textof(document, "message"));
                        }
                        case "userlogin"  -> {
                            appendText("[Зашел] " +XmlProtocol.textof(document,"name"));
                            updateUsersList();
                        }
                        case "userlogout"  -> {
                            appendText("[Вышел] " +XmlProtocol.textof(document,"name"));
                            updateUsersList();
                        }
                        case "success" -> {
                            if(document.getElementsByTagName("listusers").getLength()>0){
                                DefaultListModel<String> model = new DefaultListModel<>();
                                NodeList users = document.getElementsByTagName("user");
                                for(int i=0;i<users.getLength();i++){
                                    var u = users.item(i);
                                    String uname = u.getChildNodes().item(0).getTextContent();
                                    model.addElement(uname);
                                }
                                SwingUtilities.invokeLater(() -> { this.users.setModel(model); });
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                if (!closing){
                    appendText("[соединение закрыто]");
                }
            }
        });
        thread.setDaemon(true);
        thread.start();

        updateUsersList();
    }
    public void updateUsersList(){
        sendList();
    }
    /**
     * @param text текст в чат
     *             выводит с text без переноса строки
     *
     */
    private void appendText(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                var doc = chat.getStyledDocument();
                doc.insertString(doc.getLength(), text + "\n", null);
                chat.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {}
        });
    }
    /**Вставить картинку
     * @param data  байты
     * @param caption подпись
     */
    private void appendImage(byte[] data, String caption) {
        SwingUtilities.invokeLater(() -> {
            try {
                ImageIcon icon = new ImageIcon(data);
                var doc = chat.getStyledDocument();
                if (caption != null && !caption.isBlank()) {
                    doc.insertString(doc.getLength(), caption + "\n", null);
                }
                chat.setCaretPosition(doc.getLength());
                chat.insertIcon(icon);

                doc.insertString(doc.getLength(), "\n", null);
                chat.setCaretPosition(doc.getLength());
            } catch (Exception ignored) {}
        });
    }

    private void sendFile(){
        try{
            JFileChooser ch = new JFileChooser();
            if(ch.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
            var file = ch.getSelectedFile();
            byte[] data = Files.readAllBytes(file.toPath());
            String ct = Files.probeContentType(file.toPath());
            if(ct==null) ct = "application/octet-stream";
            String main = ct.contains("/") ? ct.substring(0, ct.indexOf('/')) : ct;
            String b64 = Base64.getEncoder().encodeToString(data);
            String xml = "<command name=\"file\">"+
                             "<name>"+XmlProtocol.esc(file.getName())+"</name>"+
                             "<type>"+XmlProtocol.esc(ct)+"</type>"+
                             "<main>"+XmlProtocol.esc(main)+"</main>"+
                             "<size>"+data.length+"</size>"+
                             "<session>"+session+"</session>"+
                             "<data>"+b64+"</data>"+
                         "</command>";
            FrameIO.WriteFrame(output, xml);
            appendText("[отправлен файл] "+file.getName()+" ("+data.length+" bytes, "+ct+")");
        } catch(Exception ex){ appendText("[ошибка файла] "+ex.getMessage()); }
    }

    private void sendList() {
        try {
            FrameIO.WriteFrame(output,  "<command name=\"list\">" +
                                            "<session>" + session + "</session>" +
                                            "</command>");
        } catch (Exception ignored) {
        }
    }

    private void sendMassage () {

        String message = inputField.getText();

        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(ClientSwingXml.this,
                                "Empty message", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String xml = "<command name=\"message\">" +
                         "<message>" + message + "</message>" +
                         "<session>" + session + "</session>" +
                     "</command>";
        try {
            FrameIO.WriteFrame(output,xml);
        } catch (Exception error) {
            appendText("[ошибка отправки] " + error.getMessage());
            JOptionPane.showMessageDialog(ClientSwingXml.this,
                    "[ошибка отправки] " + error.getMessage(), "Ошибка", JOptionPane.ERROR_MESSAGE);
        }
        inputField.setText("");
    }

    public static void main(String[] args) throws Exception {
        ClientSwingXml clientSwingXml = new ClientSwingXml();
        clientSwingXml.setVisible(true);
        String host = JOptionPane.showInputDialog("Хост","localhost");
        Integer port = Integer.valueOf(JOptionPane.showInputDialog("Порт",12345));
        String name = JOptionPane.showInputDialog("Имя", "admin");
        String type = JOptionPane.showInputDialog("Тиа клиента ", "JavaSwing");
        // в тз было сделать тип
        clientSwingXml.connect(host,port ,name , type);
    }
}
