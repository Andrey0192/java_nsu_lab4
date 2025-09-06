package chat_serialization.utils;
import java.io.Serializable;
import java.time.Instant;

public class FileMassage implements Serializable {
    private final String fileName;
    private final String sender;
    private final String contentType;
    private final String mainType;
    private final long fileSize;
    private final byte[] data;
    private final Instant lastModified;

    public FileMassage(String fileName, String sender, String contentType, String mainType, long fileSize, byte[] data) {
        this.fileName = fileName;
        this.sender = sender;
        this.contentType = contentType;
        this.mainType = mainType;
        this.fileSize = fileSize;
        this.data = data;
        this.lastModified = Instant.now();
    }

    @Override
    public String toString() {
        return  "Тип Файла : " +
                " Сообщение : "  +
                " Отправитель : "  +
                ".\n";
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public String getFileName() {
        return fileName;
    }

    public String getSender() {
        return sender;
    }

    public String getContentType() {
        return contentType;
    }

    public String getMainType() {
        return mainType;
    }

    public long getFileSize() {
        return fileSize;
    }

    public byte[] getData() {
        return data;
    }
}
