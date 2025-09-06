package chat_xml.configXml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigXml {

    private final Properties p = new Properties();

    public ConfigXml(String resource) throws IOException {
        try (InputStream in = getClass()
                .getClassLoader()
                .getResourceAsStream(resource)) {
            if (in == null) throw new IOException("Config not found");
            p.load(in);
        }

    }

    public Integer getPort() {
        return Integer.parseInt(p.getProperty("port"));
    }
    public Integer getMaxConnections() {
        return Integer.parseInt(p.getProperty("maxConnections"));
    }
    public Integer getHistorySize() {
        return Integer.parseInt(p.getProperty("historySize"));
    }
    public Integer getConnectionTimeout() {
        return  Integer.parseInt(p.getProperty("connectionTimeout"));
    }

    public Integer getMaxFileMb() {
        return Integer.parseInt(p.getProperty("maxFileMb"));
    }
}
