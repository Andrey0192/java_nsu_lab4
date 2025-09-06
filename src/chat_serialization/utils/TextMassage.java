package chat_serialization.utils;
import java.io.Serializable;
import java.util.List;

public class TextMassage implements Serializable {

    public enum Type {MASSAGE , LOGIN,LIST,LOGOUT  , ERROR,KEEPALIVE, HISTORY,USERS;}

    private final Type      type;
    private final String    massage;
    private final String    sender;
    private List<String>    list;

    public TextMassage(Type type, String massage, String sender) {
        this.type    = type;
        this.massage = massage;
        this.sender  = sender;
    }
    public TextMassage(Type type, String massage, String sender , List<String> list) {
        this.type    = type;
        this.massage = massage;
        this.sender  = sender;
        this.list    = list;
    }

    public static TextMassage error(String loginRequired) {
        return new TextMassage(TextMassage.Type.ERROR, loginRequired, null);
    }

    public static TextMassage text(String message, String sender) {
        return new TextMassage(TextMassage.Type.MASSAGE,message,sender);
    }

    public List<String> getList() {
        return list;
    }

    public String getSender() {
        return sender;
    }

    public String getMassage() {
        return massage;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        return  "Тип Сообщения : " + type    +
                " Сообщение : "    + massage +
                " Отправитель : "  + sender  +
                ".\n";

    }
}
