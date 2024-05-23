package client;

import java.io.DataOutputStream;
import java.io.IOException;

public class ClientAction {

    private DataOutputStream dataOutputStream;

    public ClientAction(DataOutputStream dataOutputStream) {
        this.dataOutputStream = dataOutputStream;
    }

    public void put(int dataLength, String fileName, byte[] data) throws IOException {
        String request = ActionConstant.PUT + "," + fileName;
        dataOutputStream.writeUTF(request);
        dataOutputStream.writeInt(dataLength);
        dataOutputStream.write(data);
    }

    public void get(String fileName) throws IOException {
        String request = ActionConstant.GET + "," + fileName;
        dataOutputStream.writeUTF(request);
    }

    public void delete(String fileName) throws IOException {
        String request = ActionConstant.DELETE + "," + fileName;
        dataOutputStream.writeUTF(request);
    }
}