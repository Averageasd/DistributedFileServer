package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) throws IOException {
        String basedUrl = System.getProperty("user.dir") + File.separator + "src" + File.separator + "client" + File.separator + "data" + File.separator;
        String address = "127.0.0.1";
        int port = 23456;
        String action = "";
        Socket socket = new Socket(InetAddress.getByName(address), port);
        DataInputStream input = new DataInputStream(socket.getInputStream());
        DataOutputStream output = new DataOutputStream(socket.getOutputStream());
        ClientAction clientAction = new ClientAction(output);
        System.out.print("Enter action (1 - get a file, 2 - save a file, 3 - delete a file): ");
        Scanner scanner = new Scanner(System.in);
        String fileName = "";
        String response = "";
        String fileIdentification = "";
        String[] responseArr = {};
        String mode = "";
        String getFileMode = "";
        action = scanner.nextLine();
        if (!action.equals("exit")) {
            switch (action) {
                case "1":
                    System.out.print("Do you want to get the file by name or by id (1 - name, 2 - id): ");
                    getFileMode = scanner.nextLine();
                    if (getFileMode.equals("1")) {
                        mode = "BY_NAME";
                        System.out.print("Enter name: ");
                    } else {
                        mode = "BY_ID";
                        System.out.print("Enter id: ");
                    }
                    fileIdentification = scanner.nextLine();
                    String request = ActionConstant.GET + "," + mode + "," + fileIdentification;
                    System.out.println("The request was sent.");
                    output.writeUTF(request);
                    response = input.readUTF();
                    responseArr = response.split(",");
                    if (responseArr[0].equals("200")) {
                        int dataLength = input.readInt();
                        byte[] data = new byte[dataLength];
                        System.out.print("The file was downloaded! Specify a name for it: ");
                        String clientNameFile = scanner.nextLine();
                        File clientVersion = new File(basedUrl + clientNameFile);
                        clientVersion.createNewFile();
                        Path path = Path.of(clientVersion.getAbsolutePath());
                        input.readFully(data, 0, dataLength);
                        Files.write(path, data);
                        System.out.println("File saved on the hard drive!");
                    } else if (responseArr[0].equals("404")) {
                        System.out.print("The response says that " + responseArr[1]);
                    }
                    socket.close();
                    break;
                case "2":
                    System.out.print("Enter name of the filename: ");
                    fileName = scanner.nextLine();
                    File file = new File(basedUrl + fileName);
                    byte[] data = Files.readAllBytes(Path.of(file.getAbsolutePath()));
                    System.out.print("Enter name of the file to be saved on server: ");
                    String serverFileName = scanner.nextLine();
                    if (serverFileName.isBlank()) {
                        serverFileName = fileName;
                    }
                    int dataLength = data.length;
                    clientAction.put(dataLength, serverFileName, data);
                    System.out.println("The request was sent.");
                    response = input.readUTF();
                    responseArr = response.split(",");
                    System.out.println("Response says that " + responseArr[1]);
                    socket.close();
                    break;
                case "3":
                    System.out.print("Do you want to delete the file by name or by id (1 - name, 2 - id): ");
                    getFileMode = scanner.nextLine();
                    if (getFileMode.equals("1")) {
                        mode = "BY_NAME";
                        System.out.print("Enter name: ");
                    } else if (getFileMode.equals("2")) {
                        mode = "BY_ID";
                        System.out.print("Enter id: ");
                    }
                    fileIdentification = scanner.nextLine();
                    request = ActionConstant.DELETE + "," + mode + "," + fileIdentification;
                    output.writeUTF(request);
                    System.out.println("The request was sent.");
                    response = input.readUTF();
                    System.out.println(response);
                    socket.close();
                    break;
                default:
                    break;
            }
        } else {
            output.writeUTF(action);
            System.out.println("The request was sent.");
            socket.close();
        }
    }

}
