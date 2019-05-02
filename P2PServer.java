import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Date;

class P2PServer extends Thread {
	static final int port = 20720;

    @Override
    public void run() {
    	// Listen for connection
        try (ServerSocket serverSocket = new ServerSocket(port)){
            while (true) {
                System.out.println("P2PServer: Waiting for client");
                final Socket clientSocket = serverSocket.accept();
                new FileSendThread(clientSocket).start();
            }
        } catch (SocketException e) {
            System.out.println("P2PServer: Socket Closed.");
        } catch (IOException e) {
            System.out.println("P2PServer: Exception caught listening on port, "
                    + port + ", or listening for a connection");
            System.out.println(e.getMessage());
        }
    }
}

class FileSendThread extends Thread {

    private final Socket clientSocket;

    FileSendThread(Socket clientSocket) {
        this.clientSocket = clientSocket;
    }

    public void run() {
        try {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            // Based on Images in textbook, format should be "GET FileName HTTP/1.1"
            ArrayList<String> headers = new ArrayList<String>();
            while (true) {
                String line = in.readLine();
                if (line.equals("")) break;
                headers.add(line);
            }
            String getR = headers.get(0);
            String[] splitR = getR.split(" ");
            // Implementing 400
            if (splitR.length != 3 || !splitR[0].equals("GET")) {
                out.print("400 - Bad Request");
            // Implementing 505
            } else if (!splitR[2].equals("HTTP/1.1")) {
                out.print("505 - HTTP Version Not Supported");
            } else {
                File requestedFile = new File(P2PClient.directory, splitR[1]);
                // Implementing 404
                if (!requestedFile.exists()) {
                    out.print("404 - File not found");
                // Emulating proper HTTP response when properly receiving the file
                } else {
                    String response =
                            "HTTP/1.1 " + 200 + " OK \n" +
                                    "Connection: Closed\n" +
                                    "Date: " + new Date().toString() + "\n" +
                                    "Last-Modified: " + new Date(requestedFile.lastModified()).toString() + "\n" +
                                    "Content-Length: " + requestedFile.length() + "\n" +
                                    "\n";
                    out.print(response);
                    // Writing the file/image
                    FileInputStream fin = new FileInputStream(requestedFile);
                    int temp;
                    while((temp = fin.read())!=-1) {
                    	out.write(temp);
                    }
                    fin.close();
                }
            }
            out.flush();
            in.close();
            out.close();
            clientSocket.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
