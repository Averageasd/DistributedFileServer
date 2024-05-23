package server;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class ServerRequestHandler {
    //    private boolean serverStop;
    private final String basedUrl;

    private final String nameIdMapUrl;
    private final Lock serverStopLock;
    private final Lock accessServerResLock;
    private String address = "127.0.0.1";
    private int port = 23456;
    private final ServerSocket server = new ServerSocket(port, 50, InetAddress.getByName(address));
    private final List<Socket> clientSockets;
    private final List<Thread> clientThreads;
    private final Map<Integer, String> idToFileNameMap;
    private final Map<String, Integer> fileNameToIdMap;

    public ServerRequestHandler() throws IOException, InterruptedException {
        address = "127.0.0.1";
        port = 23456;
//        serverStop = false;
        basedUrl = System.getProperty("user.dir") + File.separator + "src" + File.separator + "server" + File.separator + "data" + File.separator;
        nameIdMapUrl = System.getProperty("user.dir") + File.separator + "src" + File.separator + "server" + File.separator + "map" + File.separator;
        serverStopLock = new ReentrantLock();
        accessServerResLock = new ReentrantLock();
        clientSockets = new ArrayList<>();
        clientThreads = new ArrayList<>();
        idToFileNameMap = new HashMap<>();
        fileNameToIdMap = new HashMap<>();
        File idNameFileMapFolder = new File(nameIdMapUrl);
        File[] files = idNameFileMapFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                String[] nameIdSplit = file.getName().split("__");
                Integer id = Integer.parseInt(nameIdSplit[0]);
                String name = nameIdSplit[1];
                idToFileNameMap.put(id, name);
                fileNameToIdMap.put(name, id);
            }
        }
        handle();
    }

    public void handle() throws IOException, InterruptedException {
        do {
            try {
                Socket socket = server.accept();
                clientSockets.add(socket);
                DataInputStream input = new DataInputStream(socket.getInputStream());
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                ClientHandler clientHandler = new ClientHandler(socket, output, input);
                clientThreads.add(clientHandler);
                clientHandler.start();
            } catch (SocketException e) {
            }

        } while (!server.isClosed());

        for (Socket socket : clientSockets) {
            if (!socket.isClosed()) {
                socket.close();
            }
        }
        for (Thread thread : clientThreads) {
            if (thread.isAlive()) {
                thread.join();
            }

        }
    }

    private String getFileContent(String filePath) throws IOException {
        Path path = Path.of(filePath);
        return Files.readString(path);

    }

    private void writeToFile(String filePath, byte[] content) throws IOException {
        Path path = Path.of(filePath);
        Files.write(path, content);
    }

    class ClientHandler extends Thread {

        private Socket socket;
        private DataOutputStream dataOutputStream;
        private DataInputStream dataInputStream;

        public ClientHandler(Socket socket, DataOutputStream dataOutputStream, DataInputStream dataInputStream) {
            this.socket = socket;
            this.dataOutputStream = dataOutputStream;
            this.dataInputStream = dataInputStream;
        }

        @Override
        public void run() {
            String clientRequest = "";
            try {
                clientRequest = dataInputStream.readUTF();

                if (clientRequest.equals("exit")) {
                    serverStopLock.lock();
//                    serverStop = true;
                    if (!server.isClosed()) {
                        server.close();
                    }
                    serverStopLock.unlock();
                    return;
                }

                String[] actionArr = clientRequest.split(",");
                String action = actionArr[0];
                String response = "";
                String fileName = "";
                File file = null;
                String mode = null;
                switch (action) {
                    case "GET":
                        mode = actionArr[1];
                        if (mode.equals("BY_NAME")) {
                            accessServerResLock.lock();
                            if (fileNameToIdMap.containsKey(actionArr[2])) {
                                fileName = actionArr[2];
                                file = new File(basedUrl + fileName);
                                if (file.exists()) {
                                    response = ServerStatusConstant.SUCCESS_CODE + ",";
                                    byte[] data = Files.readAllBytes(Path.of(file.getPath()));
                                    dataOutputStream.writeUTF(response);
                                    dataOutputStream.writeInt(data.length);
                                    dataOutputStream.write(data);
                                } else {
                                    response = ServerStatusConstant.RES_NOT_FOUND_CODE + ",this file is not found!";
                                    dataOutputStream.writeUTF(response);
                                }
                            } else {
                                response = ServerStatusConstant.RES_NOT_FOUND_CODE + ",this file is not found!";
                                dataOutputStream.writeUTF(response);
                            }
                            accessServerResLock.unlock();
                        } else if (mode.equals("BY_ID")) {
                            accessServerResLock.lock();
                            System.out.println("get file with id " + actionArr[2]);
                            try {
                                if (idToFileNameMap.containsKey(Integer.parseInt(actionArr[2]))) {
                                    fileName = idToFileNameMap.get(Integer.parseInt(actionArr[2]));
                                    file = new File(basedUrl + fileName);
                                    response = ServerStatusConstant.SUCCESS_CODE + ",";
                                    byte[] data = Files.readAllBytes(Path.of(file.getPath()));
                                    dataOutputStream.writeUTF(response);
                                    dataOutputStream.writeInt(data.length);
                                    dataOutputStream.write(data);
                                } else {
                                    response = ServerStatusConstant.RES_NOT_FOUND_CODE + ",this file is not found!";
                                    dataOutputStream.writeUTF(response);
                                }
                            } catch (IllegalArgumentException e) {
                                response = ServerStatusConstant.RES_NOT_FOUND_CODE + ",this file is not found!";
                                dataOutputStream.writeUTF(response);
                                accessServerResLock.unlock();
                            }
                            accessServerResLock.unlock();
                        }
                        break;
                    case "PUT":
                        accessServerResLock.lock();
                        fileName = actionArr[1];
                        if (fileNameToIdMap.containsKey(fileName)) {
                            System.out.println(fileName + "already exists");
                            response = ServerStatusConstant.RES_EXIST + ",creating the file was forbidden!";
                            dataOutputStream.writeUTF(response);
                        } else {
                            int newId = 0;
                            while (idToFileNameMap.containsKey(newId)) {
                                newId += 1;
                            }
                            File newFile = new File(basedUrl + fileName);
                            idToFileNameMap.put(newId, fileName);
                            fileNameToIdMap.put(fileName, newId);
                            File idNameFile = new File(nameIdMapUrl + newId + "__" +fileName);
                            newFile.createNewFile();
                            idNameFile.createNewFile();
                            int dataLength = dataInputStream.readInt();
                            byte[] data = new byte[dataLength];
                            dataInputStream.readFully(data, 0, dataLength);
                            Files.write(Path.of(newFile.getPath()), data);
                            Files.write(Path.of(idNameFile.getPath()),data);
                            response = ServerStatusConstant.SUCCESS_CODE + ",file is saved! ID = " + newId;
                            dataOutputStream.writeUTF(response);
                        }
                        accessServerResLock.unlock();
                        break;
                    case "DELETE":
                        accessServerResLock.lock();
                        mode = actionArr[1];
                        if (mode.equals("BY_NAME")) {
                            if (fileNameToIdMap.containsKey(actionArr[2])) {
                                fileName = actionArr[2];
                                file = new File(basedUrl + fileName);
                                int id = fileNameToIdMap.get(actionArr[2]);
                                File idNameFile = new File(nameIdMapUrl + id + "__" + fileName);
                                if (file.exists()) {
                                    file.delete();
                                    idNameFile.delete();
                                    response = ServerStatusConstant.SUCCESS_CODE + ", this file was deleted successfully!";
                                    dataOutputStream.writeUTF(response);
                                } else {
                                    response = ServerStatusConstant.RES_NOT_FOUND_CODE + ", this file is not found!";
                                    dataOutputStream.writeUTF(response);
                                }
                            } else {
                                response = ServerStatusConstant.RES_NOT_FOUND_CODE + ",the file was not found!";
                                dataOutputStream.writeUTF(response);
                            }
                        } else if (mode.equals("BY_ID")) {
                            try {
                                if (idToFileNameMap.containsKey(Integer.parseInt(actionArr[2]))) {
                                    fileName = idToFileNameMap.get(Integer.parseInt(actionArr[2]));
                                    File idNameFile = new File(nameIdMapUrl + Integer.parseInt(actionArr[2]) + "__" + fileName);
                                    idNameFile.delete();
                                    file = new File(basedUrl + fileName);
                                    file.delete();
                                    response = ServerStatusConstant.SUCCESS_CODE + ", this file was deleted successfully!";
                                    dataOutputStream.writeUTF(response);
                                }
                            } catch (IllegalArgumentException e) {
                                response = ServerStatusConstant.RES_NOT_FOUND_CODE + ", this file is not found!";
                                dataOutputStream.writeUTF(response);
                                accessServerResLock.unlock();
                            }
                        }
                        accessServerResLock.unlock();
                        break;
                }
            } catch (IOException e) {
            }
        }
    }

}