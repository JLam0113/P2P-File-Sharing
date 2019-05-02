import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;

public class DServer
{
    static final Map<Integer, String> serverIPs = new ConcurrentHashMap<Integer, String>();
    static final Map<String, ArrayList<ContentRecord>> contentRecords = new ConcurrentHashMap<String, ArrayList<ContentRecord>>();
    static String thisServerIP, nextServerIP;
    static int serverid;
    static int port2 = 20721;
    static int port1 = 20720;

    public static void main(String[] args) throws IOException {
        Scanner sc = new Scanner(System.in);
        System.out.println("\n------DHT SERVER------\n");
        InetAddress curr = InetAddress.getLocalHost();
        String address = curr.getHostAddress();
        System.out.println("DHT Server: The IP of this DHT node is: " + address);
        thisServerIP = address;
        boolean temp = true;
        if (temp){
            System.out.println("DHT Server: Please enter ServerID (from 1 to 4) of this DHT Node:");
            String idString;
            idString = sc.nextLine();
            while (!idString.matches("^[1-4]$"))
            {
                System.out.println("DHT Server: Error, please enter a valid server number from 1 to 4:");
                idString = sc.nextLine();
            }
            serverid = Integer.parseInt(idString);

            System.out.println("DHT Server: Please enter the IP of the successor DHT Node:");
            String nextIP = sc.nextLine();
            while (!nextIP.isEmpty()){
            	System.out.println("DHT Server: Error, the IP cannot be empty, please enter the IP of the successor DHT Node:");
                nextIP = sc.nextLine();
            }
            nextServerIP = nextIP;
            sc.close();
        }

        System.out.println("DHT Server: The DHT server is now listening.");
        UpdateThread upThread = new UpdateThread();
        upThread.start();

        try (ServerSocket server = new ServerSocket(port2)) {
            while (true)
            {
                Socket socket = server.accept();
                DTCPThread tcpThread = new DTCPThread(socket);
                tcpThread.start();
            }
        } catch (IOException e) {
            System.out.println("DHTServer: Exception caught when trying to listen on port " + port2);
            System.out.println(e.getMessage());
        }
    }
}

class UpdateThread extends Thread {

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket(DServer.port1)) {
            while (true) {
                try {
                    byte[] buf = new byte[256];
                    DatagramPacket packet = new DatagramPacket(buf, buf.length);
                    socket.receive(packet);
                    String receive = new String(packet.getData(), 0, packet.getLength());
                    System.out.println("DHTServer: Message received: ("+ receive +")\n");
                    String response;
                    if (receive.equals("init")) {
                        response = init(packet.getAddress().getHostAddress());
                    } else if (receive.startsWith("update")) {
                        response = update(receive, packet.getAddress().getHostAddress());
                    } else if (receive.startsWith("query")) {
                        response = query(receive);
                    } else if (receive.startsWith("exit")) {
                        response = exit(packet.getAddress().getHostAddress());
                    } else {
                        response = "invalid request: " + receive;
                        System.out.println(response);
                        System.out.println("Please enter one of the following options: init, update, query for content, exit");
                    }
                    if (!response.equals("no response")) {
                        buf = response.getBytes();
                        InetAddress address = packet.getAddress();
                        int port = packet.getPort();
                        packet = new DatagramPacket(buf, buf.length, address, port);
                        socket.send(packet);
                        }
                } catch (IOException e) {
                    e.printStackTrace();
                    }
                }
        } catch (IOException e) {
            e.printStackTrace();
            }
    }

    String init(String senderIP) {
        if (DServer.serverIPs.size() < 4) {
                try (Socket socket = new Socket(DServer.nextServerIP, DServer.port2)) {
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    in.readLine();
                    String message = "init\n" + senderIP + "\n" + DServer.thisServerIP + " " + DServer.serverid + "\nend";
                    out.println(message);
                    out.flush();
                } catch (UnknownHostException e) {
                    System.err.println("DHTServer: Unknown Host: " + DServer.nextServerIP);
                } catch (IOException e) {
                    System.err.println("DHTServer: Couldn't get I/O for the connection to " +
                            DServer.nextServerIP);
                }
                return "no response";
        } else {
            //return server records
            String message = "";
            for (int i = 1; i <= 4; i++) {
                message += DServer.serverIPs.get(i) + " " + i + "\n";
            }
            return message;
            }
    }

    String update(String received, String ownerIP) {
        Scanner sc = new Scanner(received);
        sc.next();
        String contentName = sc.next();
        if (!DServer.contentRecords.containsKey(contentName)) {
            DServer.contentRecords.put(contentName, new ArrayList<ContentRecord>());
        }
        DServer.contentRecords.get(contentName).add(new ContentRecord(contentName, ownerIP));
        sc.close();
        return "Success: The content record was stored on the DHT server";
    }

    String query(String received) {
        Scanner sc = new Scanner(received);
        sc.next();
        String contentName = sc.next();
        sc.close();
        if (!DServer.contentRecords.containsKey(contentName)) {
            return "404 content not found";
        } else {
            ContentRecord rec = DServer.contentRecords.get(contentName).get(0); //gets the first client in the list of providers
            return rec.toString();
        }
    }
    
    String exit(String senderIP) {
        try  (Socket socket = new Socket(DServer.nextServerIP, DServer.port2)){
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            in.readLine();
            String message = "exit\n" + senderIP + " " + DServer.thisServerIP;
            out.println(message);

        } catch (UnknownHostException e) {
            System.err.println("DHTServer: Unknown Host: " + DServer.nextServerIP);
        } catch (IOException e) {
            System.err.println("DHTServer: Couldn't get I/O for the connection to " + DServer.nextServerIP);
        }
        return "no response";
    }
}

class DTCPThread extends Thread {

    private static int counter = 0;
    private Socket socket = null;

    public DTCPThread(Socket socket) {
        this.socket = socket;
    }

    public void run() {
        try {
            PrintWriter out =
                    new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));

            String inputLine, outputLine;
            outputLine = "Request has been received from server " + DServer.thisServerIP;
            out.println(outputLine);
            inputLine = in.readLine();
            if (inputLine.equals("init")) {
                String fullmessage = in.readLine() + "\n";
                while ((inputLine = in.readLine()) != null) {
                    if (inputLine.equals("end")) break;
                    if (!inputLine.isEmpty())
                        fullmessage += inputLine + "\n";
                }
                SendInitMessage(fullmessage);

            } else if (inputLine.equals("exit")) {
                SendExitMessage(in.readLine());

            } else {
                do {
                    outputLine = "I'm server: " + counter++;
                    out.println(outputLine);
                } while ((inputLine = in.readLine()) != null);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void SendExitMessage(String clientInfo) {
        Scanner sc = new Scanner(clientInfo);
        String clientIP = sc.next();
        String firstServerIP = sc.next();
        RemoveContentRecords(clientIP);
        sc.close();
        if (!firstServerIP.equals(DServer.thisServerIP)) {
            //continue traversing dht ring
            try (Socket socket = new Socket(DServer.nextServerIP, DServer.port2)){
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                in.readLine();
                out.println("exit\n" + clientInfo);
            } catch (UnknownHostException e) {
                System.err.println("DHTServer: Unknown Host: " + DServer.nextServerIP);
            } catch (IOException e) {
                System.err.println("DHTServer: Couldn't get I/O for the connection to " +
                        DServer.nextServerIP);
            }
        }
    }

    void RemoveContentRecords(String clientIP) {
        for (String contentName : DServer.contentRecords.keySet()) {
            ArrayList<ContentRecord> removeList = new ArrayList<ContentRecord>();
            for (ContentRecord rec : DServer.contentRecords.get(contentName))
            {
                if (rec.ContentOwnerIP.equals(clientIP))
                {
                    removeList.add(rec);
                }
            }
            for (ContentRecord rec : removeList) {
                DServer.contentRecords.get(contentName).remove(rec);
                System.out.println("DHTServer: Removed content record: " + rec.toString());
            }
            if (DServer.contentRecords.get(contentName).size() == 0)
                DServer.contentRecords.remove(contentName);
        }
    }

    void SendInitMessage(String fullmessage) throws IOException {
        Scanner sc = new Scanner(fullmessage);
        String p2pClient = sc.next();
        String server1 = sc.next();
        String servNum = sc.next();

        String newMessage = fullmessage + DServer.thisServerIP + " " + DServer.serverid;
        if (server1.equals(DServer.thisServerIP)) {
            //send back upd
            String returnMessage = server1 + " " + servNum + "\n";
            if (!DServer.serverIPs.containsKey(Integer.parseInt(servNum)))
            {
                DServer.serverIPs.put(Integer.parseInt(servNum), server1);
            }
            while(sc.hasNext())
            {
                String ip = sc.next();
                if (!ip.isEmpty())
                {
                    int id = Integer.parseInt(sc.next());
                    if (!DServer.serverIPs.containsKey(id))
                    {
                        DServer.serverIPs.put(id, ip);
                    }
                    returnMessage += ip + " " + id + "\n";
                }
            }
            sc.close();
            DatagramSocket socket = new DatagramSocket();
            byte[] buf = returnMessage.getBytes();
            InetAddress address = InetAddress.getByName(p2pClient);
            DatagramPacket packet = new DatagramPacket(buf, buf.length, address, DServer.port2);
            socket.send(packet);
            socket.close();
        } else {
            //keep going tcp
            try (Socket socket = new Socket(DServer.nextServerIP, DServer.port2)){
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                in.readLine();
                out.println("init\n" + newMessage + "\nend");
            } catch (UnknownHostException e) {
                System.err.println("DHTServer: Unknown Host: " + DServer.nextServerIP);
            } catch (IOException e) {
                System.err.println("DHTServer: Couldn't get I/O for the connection to " +
                        DServer.nextServerIP);
            }
        }
    }
}