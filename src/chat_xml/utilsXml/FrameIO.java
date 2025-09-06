package chat_xml.utilsXml;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class FrameIO {
    public static String ReadFrame(DataInputStream in) throws IOException {
        int length = in.readInt(); // в начале сообщения идет java int с размером xml документа
        byte[] b = in.readNBytes(length);
        return new String(b, StandardCharsets.UTF_8);
    }
    public static void WriteFrame(DataOutputStream out, String xml) throws IOException {
        byte[] b = xml.getBytes(StandardCharsets.UTF_8);
        out.writeInt(b.length);
        out.write(b);
        out.flush();

    }

}
