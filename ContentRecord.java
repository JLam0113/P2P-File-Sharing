import java.util.Scanner;

class ContentRecord {
    public final String ContentName;
    public final String ContentOwnerIP;
    public ContentRecord(String ContentName, String ContentOwnerIP)
    {
        this.ContentName = ContentName;
        this.ContentOwnerIP = ContentOwnerIP;
    }

    public static ContentRecord parseRecord(String formattedString) {
        Scanner sc = new Scanner(formattedString);
        String name = sc.next();
        String ip = sc.next();
        sc.close();
        return new ContentRecord(name, ip);
    }

    public String toString()
    {
        return String.format("%s %s\n", ContentName, ContentOwnerIP);
    }
}
