package scripts;

import org.json.JSONObject;
import java.nio.file.Files;
import java.nio.file.Paths;

public class JsonTest {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: JsonTest <json_file>");
            System.exit(1);
        }

        String jsonFile = args[0];
        
        try {
            // Read the file content
            byte[] fileBytes = Files.readAllBytes(Paths.get(jsonFile));
            String content = new String(fileBytes);
            
            System.out.println("File content:");
            System.out.println(content);
            System.out.println("File length: " + content.length() + " bytes");
            
            // Parse as JSON
            JSONObject json = new JSONObject(content);
            System.out.println("\nParsed JSON:");
            System.out.println(json.toString(2));
            
            // Check for connections array
            if (json.has("connections")) {
                System.out.println("\nConnections found!");
                System.out.println("Number of connections: " + json.getJSONArray("connections").length());
            } else {
                System.out.println("\nNo 'connections' key found in JSON!");
                System.out.println("Available keys: " + json.keySet());
            }
            
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
} 