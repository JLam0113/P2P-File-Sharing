import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Scanner;

public class P2PClient {
    private final static HashMap<Integer, String> serverIPs = new HashMap<Integer, String>();
    private final static HashMap<String, Integer> contentDHT = new HashMap<String, Integer>();
    static final int port1 = 20720;
    static final int port2 = 20721;
    private static P2PServer p2pServer;
    public static File directory;
    
	public static void main(String[] args) throws IOException {
		System.out.println("\n------P2PClient------\n");
		// Get IP of current computer
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("P2PClient: Enter IP of DHT Node:");
            String IP = sc.nextLine();
            IP = InetAddress.getByName(IP).getHostAddress();
            try {
            	// init command
                init(IP);
            } catch (IOException e) {
                System.out.println("P2PClient: Connection Error: " + e.getMessage() + ". Please try again.");
                continue;
            }
            break;
        }
        
        System.out.println("P2PClient: Please input your sharing folder path");
        String name = sc.nextLine();
        if (name.isEmpty()) {
        	System.out.println("Please enter a valid path");
        	sc.nextLine();
        }
        
        directory = new File(name);
        share();
        p2pServer = new P2PServer();
        p2pServer.start();
        // Handling the update, query, and exit commands
        System.out.println("P2PClient: Input a command(update, query or exit)");
        String command = sc.nextLine();
        if (command.equalsIgnoreCase("update")) {
          	System.out.println("P2PClient: Enter the file you'd like to share. (File must be in shares directory)");
            String filename = sc.nextLine();
            update(filename);
            } else if (command.equalsIgnoreCase("query")) {
            	System.out.println("P2PClient: Enter the file you'd like to download.");
                String filename = sc.nextLine();
                query(filename);
            } else if (command.equalsIgnoreCase("exit")) {
            	exit();
                System.out.println("P2PClient: The exit message was sent to all DHT servers. Goodbye.");
                System.exit(0);
                sc.close();
                return;
            }else{
            	System.out.println("P2PClient: Input not Accepted.");
                }
        sc.close();
        }
	
	// Init command
	static void init(String serverIP) throws IOException {
        serverIPs.put(1, serverIP);
        String response = sendRequest("init", 1);
        // Store all received IP addresses into serverIPs
        Scanner sc = new Scanner(response);
        while (sc.hasNext())
        {
            String ip = sc.next();
            int id = Integer.parseInt(sc.next());
            if (!serverIPs.containsKey(id))
                serverIPs.put(id, ip);
        }
        sc.close();
        System.out.println("P2PClient: The DHT server returned all server IPs and they have been stored.");
    }
	
	// Sending message to DHT through port #
	// *NOTE* Might need another port #, as we can't communicate with P2PServer and DHTServer using same port #?
	static String sendRequest(String request, int serverNum) throws IOException
    {
		// Send message on port #
        String ip = serverIPs.get(serverNum);
        DatagramSocket socket;
        socket = new DatagramSocket(port2);
        byte[] buf = request.getBytes();
        InetAddress address = InetAddress.getByName(ip);
        DatagramPacket packet = new DatagramPacket(buf, buf.length, address, port1);
        socket.send(packet);
        if (request.equals("exit")) {
        	socket.close();
        	return "";
        }
        // DHT Server response
        buf = new byte[256];
        packet = new DatagramPacket(buf, buf.length);
        socket.receive(packet);

        String received = new String(packet.getData(), 0, packet.getLength());
        socket.close();
        return received;
    }
	
	// Get files in directory and update
	private static void share() throws IOException {

        File[] shares = directory.listFiles();
        for (File s : shares) {
            update(s.getName());
        }
    }
	
	// Updates the content
	private static void update(String contentName) throws IOException
    {
        File f = new File(directory, contentName);
        if (!f.exists())
        {
            System.out.println("P2PClient: File was not found in shares directory.");
            return;
        }
        String request = "update " + contentName;
        int serverNum = hashServerNum(contentName);
        String response = sendRequest(request, serverNum);
        System.out.println(response);
        System.out.printf("P2PClient: Stored %s in server %d \n", contentName, serverNum);
        contentDHT.put(contentName, serverNum);
    }
	
	// Getting server number for corresponding item
	private static int hashServerNum(String content) {
		int total = 0;
        for(char c : content.toCharArray())
        {
            if (c == '.') break;
            total += (int)c;
        }
        return (total % 4) + 1;
	}
	
	// Exit command
    private static void exit() throws IOException
    {
        sendRequest("exit", 1);
    }
    
    private static void query(String contentName) throws IOException
    {
        String request = "query " + contentName;
        int serverNum = hashServerNum(contentName);
        String response = sendRequest(request, serverNum);
        
        if (response.startsWith("404")) {
            System.out.println(response);
            return;
        }
        
        ContentRecord peerProvider = ContentRecord.parseRecord(response);
        System.out.printf("P2PClient: The file %s can be found at the peer IP: %s\n", contentName, peerProvider.toString());
        getFile(peerProvider);
    }
    
    static void getFile(ContentRecord record) throws UnknownHostException {

        String hostName = record.ContentOwnerIP;
        System.out.println("P2PClient: Attempting to contact server");
        try (Socket socket = new Socket(hostName, port1)) {
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader( new InputStreamReader(socket.getInputStream()));
            out.println("GET " + record.ContentName + " " + "HTTP_1_1");
            out.println();

            ArrayList<String> headers = new ArrayList<String>();
            while (true) {
                String line = in.readLine();
                if (line.equals("")) break;
                headers.add(line);
            }
            String getR = headers.get(0);
            String[] splitR = getR.split(" ");
            if (!splitR[1].equals("200")) {
                System.out.println("P2PClient: Error " + splitR[1] + ": " + splitR[2]);
                return;
            }
            File f = new File(directory, record.ContentName);
            FileOutputStream fos = new FileOutputStream(f);
            for (int i = 0; i < f.length(); i++) {
                fos.write(in.read());
            }
            out.close();
            in.close();
            fos.close();
            socket.close();
            System.out.println("P2PClient: The File " + record.ContentName + " has been successfully transferred.");
        } catch (IOException e) {
            System.out.println("P2PClient: P2P server did not respond or P2P server did not contain record");
            e.printStackTrace();
        }
    }
}
