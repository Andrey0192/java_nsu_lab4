package chat_xml.utilsXml;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

public class XmlProtocol {
    public static Document parse(String xml) throws Exception  {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }
    public static String error(String msg) {
        return  "<error>" +
                    "<message>" + esc(msg) + "</message> " +
                "</error>";
    }
    public static String esc(String s) {
        return s==null?"": s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");
    }
    public static String successAnswer(UUID uuid) {
         return "<success>" +
                    "<session>"  + uuid + "</session>" +
                "</success>";
    }
    public static String evUserLogout(String name){
            return "<event name=\"userlogout\"><name>"+esc(name)+"</name></event>";
    }
    public static String evHistory(List<String> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("<event name=\"history\"><messages>");
        for (String e : events) {
            sb.append(e);
        }
        sb.append("</messages></event>");
        return sb.toString();
    }
    public static String sucsess() {
        return "<success/>";
    }
    public static String evMessage(String from, String msg){
        return  "<event name=\"message\">" +
                    "<message>"+esc(msg)+"</message>" +
                    "<name>"+esc(from)+"</name>" +
                "</event>";
    }
    public static String evUserLogin(String name){
        return  "<event name=\"userlogin\">" +
                    "<name>"+esc(name)+"</name>" +
                "</event>";
    }
    public static String successUsers(List<String> users) {
        StringBuilder ans = new StringBuilder();
        ans.append("<success><listusers>");
        for (String user : users) {
            ans.append( "<user>" +
                            "<name>" + esc(user) + "</name>" +
                            "<type>CHAT_CLIENT_1</type>" +
                        "</user>");
        }
        ans.append("</listusers></success>");
        return ans.toString();
    }
    public static String textof(Document d, String tag){
        if(d.getElementsByTagName(tag).getLength()==0) return null;
        return d.getElementsByTagName(tag).item(0).getTextContent();
    }

    public static String getCommand(Document d){
        Element root = d.getDocumentElement();
        String tag = root.getTagName();
        if ("command".equals(tag) || "event".equals(tag)) {
            return root.getAttribute("name");
        }
        if ("success".equals(tag) || "error".equals(tag)) {
            return tag;
        }
        return null;
    }
    public static String evFile(String sender, String filename, String contentType, String main, long size, String base64){
        return  "<event name=\"file\">"+
                    "<name>"+esc(sender)+"</name>"+
                    "<filename>"+esc(filename)+"</filename>"+
                    "<type>"+esc(contentType)+"</type>"+
                    "<main>"+esc(main)+"</main>"+
                    "<size>"+size+"</size>"+
                    "<data>"+base64+"</data>"+
                "</event>";
    }
}
