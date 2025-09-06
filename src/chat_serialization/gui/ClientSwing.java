package chat_serialization.gui;
import chat_serialization.utils.FileMassage;
import chat_serialization.utils.TextMassage;
import chat_xml.guiXml.ClientSwingXml;
import chat_xml.utilsXml.FrameIO;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class ClientSwing extends JFrame {
    private final JTextArea chat = new JTextArea();
    private final DefaultListModel<String> userModel = new DefaultListModel<>();
    private final JList<String> users = new JList<>(userModel);
    private final JTextField inputField = new JTextField();
    private Timer keepAliveTimer;
    private String name;

    private  ObjectInputStream input ;
    private ObjectOutputStream output ;
    private Socket socket;

    public ClientSwing() {
        setTitle("Chat (Serialization)");
        setSize(700,500); setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        chat.setEditable(false);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(chat),
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
        btnList.addActionListener(e -> sendListOfUsers());
        btnFile.addActionListener(e -> sendFile());
        //-------//
        setLocationRelativeTo(null);
        addWindowListener(new WindowAdapter(){
            @Override public void windowClosed(WindowEvent e){ logoutAndClose(); }
        });
    }
//
//    private void logoutAndClose() {
//        try {
//            if ( output != null ) {
//                output.writeObject(new TextMassage(TextMassage.Type.LOGOUT ,null,name));
//            }
//            if ( input != null ) {
//                input.close();
//            }
//            if ( output != null ) {
//                output.close();
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//    }
    private void logoutAndClose(){
        try { if (keepAliveTimer != null) keepAliveTimer.cancel(); } catch (Exception ignored) {}
        try {
            if (output != null) {
                output.writeObject(new TextMassage(TextMassage.Type.LOGOUT ,null,name));
            }
        } catch (Exception ignored) {}

        try {
            if (socket != null && !socket.isClosed()) socket.shutdownOutput();
        } catch (Exception ignored) {}

        try { if (input  != null) input.close();  } catch (Exception ignored) {}
        try { if (output != null) output.close(); } catch (Exception ignored) {}
        try { if (socket != null) socket.close(); } catch (Exception ignored) {}
    }

    public void connect(String host, Integer port, String name) throws Exception {
        socket = new Socket(host, port);
        this.name=name;
        try  {
            if (socket.isConnected()) {
                output = new ObjectOutputStream(socket.getOutputStream()); output.flush();
                input = new ObjectInputStream(socket.getInputStream());
            } else {
                JOptionPane.showMessageDialog(ClientSwing.this,
                        "Connection failed", "Error", JOptionPane.ERROR_MESSAGE);
            }

        } catch (IOException ex) {
            JOptionPane.showMessageDialog(ClientSwing.this,
                    "Error Socket " + ex.getMessage() , "Error" , JOptionPane.ERROR_MESSAGE);
        }
        //Отправляем логиин
        try {
            output.writeObject(new TextMassage(TextMassage.Type.LOGIN ,null ,name));
            output.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        keepAliveTimer  =  new Timer(true);
        keepAliveTimer.scheduleAtFixedRate(new TimerTask() {
                                                @Override
                                                public void run()  {
                                                    try {
                                                        output.writeObject(new TextMassage(TextMassage.Type.KEEPALIVE ,
                                                                null, name));
                                                        output.flush();
                                                    } catch (Exception ignored) {
                                                    }
                                                }},
                3000,3000 );

        Thread thread = recivedMessege();
        thread.start();
    }

    private Thread recivedMessege() {
        Thread thread = new Thread(() -> {
            TextMassage m;
            FileMassage f;
            Object obj;
            try {
                while(((obj = input.readObject())!= null)){
                    if (obj instanceof TextMassage) {
                        m = (TextMassage) obj;
                        switch (m.getType()) {
                            case ERROR ->{
                                JOptionPane.showMessageDialog(ClientSwing.this,
                                        "Error connection to chat_serialization.server ", "Error", JOptionPane.ERROR_MESSAGE);
                                append("[ Error ]" + m.getSender() + ": " + m.getMassage());
                            }
                            case MASSAGE ->{
                                append(m.getSender() + ": " + m.getMassage());
                            }
                            case HISTORY -> {
                                for (String s : m.getList()){
                                    append(s);
                                }
                            }
                            case USERS -> {
                                updateUsersList(m.getList());
                            }
                        }
                    } else if (obj instanceof FileMassage) {
                        f = (FileMassage) obj;
                        Path dir = Paths.get("C:\\Users\\" + System.getProperty("user.name") + "\\Desktop" );
                        Path outPath = dir.resolve(System.currentTimeMillis() + "_" + f.getFileName());
                        Files.write(outPath,f.getData());
                        if ("image".equalsIgnoreCase(f.getMainType())){
                            try {
                                ImageIcon icon = new ImageIcon(f.getData());
                                JLabel label = new JLabel(icon);
                                JOptionPane.showMessageDialog(this, new JScrollPane(label),
                                        "Image: " + f.getFileName(), JOptionPane.PLAIN_MESSAGE);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ex) {
                append("[соединение закрыто]");
            }
        });
        thread.setDaemon(true);
        return thread;
    }

    public void updateUsersList(List<String> users){
        userModel.clear();
        users.forEach(userModel::addElement);
    }

    public void append(String s) {
        chat.append(s + "\n");
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        int returnVal = chooser.showOpenDialog(ClientSwing.this);

        if (returnVal != JFileChooser.APPROVE_OPTION) { return; }

        JOptionPane.showMessageDialog(ClientSwing.this,
                "File selected: " + chooser.getSelectedFile().getAbsolutePath());

        File file = chooser.getSelectedFile();
        if (file.canRead()){
            try {
                byte[] buffer = Files.readAllBytes(file.toPath());
                String ct = java.nio.file.Files.probeContentType(file.toPath());
                if (ct == null){ ct ="application/octet-stream";}
                String mt = ct.contains("/") ? ct.substring(0,ct.lastIndexOf("/") ) : ct;
                FileMassage fm = new FileMassage(file.getName(),name , ct, mt,buffer.length,buffer);

                append("file: " + file.getName() + " отправлен. ");

                output.writeObject(fm);
                output.flush();

            } catch (IOException ex) {
                JOptionPane.showMessageDialog(ClientSwing.this,
                        "Error when trying to make FileInputStream of file: "
                                                                    + file.getAbsolutePath());
                append("[ Error when trying to make FileInputStream of file: "
                                                                    + file.getAbsolutePath());
                throw new RuntimeException(ex);
            }
        } else {
            JOptionPane.showMessageDialog(ClientSwing.this, "File not readable");
        }
    }

    public void sendListOfUsers(){
        try {
            output.writeObject(new TextMassage(TextMassage.Type.LIST ,
                        "list of users", "Cient"));//сделать тип специальный для работы
            output.flush();
        } catch (Exception _ ) {}
    }

    public void sendMassage()  {
        String message = inputField.getText();
        if (message.isEmpty()) {
            JOptionPane.showMessageDialog(ClientSwing.this,
                                "Error to send message", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        inputField.setText("");
        try {
            output.writeObject(TextMassage.text(message,name));
            output.flush();
        } catch (IOException e) {
            System.err.println(e);
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        ClientSwing clientSwing = new ClientSwing();
        clientSwing.setVisible(true);
        String host = JOptionPane.showInputDialog("Хост","localhost");
        Integer port = Integer.valueOf(JOptionPane.showInputDialog("Порт",12345));
        String name = JOptionPane.showInputDialog("Имя", "admin");
        // в тз было сделать тип
        clientSwing.connect(host,port ,name);
    }
}
