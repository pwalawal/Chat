
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

public class Server {
    private static int sPort;   //The server will be listening on this port number
    private static final ArrayList<Handler> thread = new ArrayList<>();  //to store client threads

    public Server(int sPort){
        Server.sPort = sPort;
    }
	
    /**
     * A handler thread class.  Handlers are spawned from the listening
     * loop and are responsible for dealing with a single client's requests.
     */

    private static class Handler extends Thread {
        private Object receivedMessage;                //message received from the client
        private Socket serverSocket;                  //socket connection to the clients
        private ObjectInputStream in;	              //stream read from the socket
        private ObjectOutputStream out;               //stream write to the socket
        private String name = null;                   //username of the clients
        private final ArrayList<Handler> thread;

        public Handler(Socket connection, ArrayList<Handler> thread) {
            this.serverSocket = connection;
            this.thread = thread;
        }

        @Override
        public void run() {
            ArrayList<Handler> thread = this.thread;
            try{
                //initialize Input and Output streams
                out = new ObjectOutputStream(serverSocket.getOutputStream());
                out.flush();
                in = new ObjectInputStream(serverSocket.getInputStream());
                if(name == null) {
                    try {
                        name = (String) in.readObject();
                        System.out.println("Client " + name + " is connected");
                    }
                    catch (ClassNotFoundException ignored) {
                    }
                }
                try{
                    while(true) {
                        //receive the message sent from the client
                        receivedMessage = in.readObject();
                        String temporaryString = (String)receivedMessage;
                        //check the input received and call appropriate method
                        String[] splitMessage = temporaryString.split(" ");
                        if(splitMessage[0].equalsIgnoreCase("broadcast") && splitMessage[1].equalsIgnoreCase("message")){
                            broadCastMessage(thread, splitMessage);
                        }
                        else if(splitMessage[0].equalsIgnoreCase("unicast") && splitMessage[1].equalsIgnoreCase("message")){
                            uniCastMessage(thread, splitMessage);
                        }
                        else if(splitMessage[0].equalsIgnoreCase("blockcast") && splitMessage[1].equalsIgnoreCase("message")){
                            blockCastMessage(thread, splitMessage);
                        }
                        else if(splitMessage[0].equalsIgnoreCase("broadcast") && splitMessage[1].equalsIgnoreCase("file")){
                        	String[] splitFilepath =  splitMessage[2].split("/");
                            String filename = splitFilepath[splitFilepath.length - 1];
                            receiveBroadcastFile(filename.substring(0, filename.length()-1));
                        }
                        else if(splitMessage[0].equalsIgnoreCase("unicast") && splitMessage[1].equalsIgnoreCase("file")){
                            String[] splitFilepath =  splitMessage[2].split("/");
                            String filename = splitFilepath[splitFilepath.length - 1];
                            receiveUnicastFile(filename.substring(0, filename.length()-1), splitMessage[splitMessage.length - 1]);
                        }
                        else{
                            System.out.println("No such command or user found");
                        }
                    }
                }
                catch(ClassNotFoundException | InterruptedException ignored){
                }
            }
            catch(IOException e){
                System.out.println("Disconnected with Client " + name);
            }
            finally{
                //Close connections
                try{
                    in.close();
                    out.close();
                    serverSocket.close();
                }
                catch(IOException e){
                    System.out.println("Disconnected with Client " + name);
                }
            }
        }

        //broadcast message to all clients
        public void broadCastMessage(ArrayList<Handler> thread, String[] str){
            StringBuilder sb = new StringBuilder();
            for(int i = 2; i < str.length; i++){
                sb.append(str[i]).append(" ");
            }
            String returnThis = "@" + name + ": " + sb.substring(1, sb.length()-2);
            broadCastHelper(thread, returnThis);
            System.out.println("Client " + name + " broadcasted message");
        }

        //helper function for broadcasting message to clients
        private void broadCastHelper(ArrayList<Handler> thread, String filenameOrMessage) {
            for (Handler handler : thread) {
                if (handler != null && handler.name != null && !handler.name.equalsIgnoreCase(name)) {
                    try {
                        handler.out.writeObject(filenameOrMessage);
                        handler.out.flush();
                    }
                    catch (IOException ignored) {
                    }
                }
            }
        }
        
        //send message to a particular client
        public void uniCastMessage(ArrayList<Handler> thread, String[] str){
            String returnThis = getMessageFromString(str);
            uniCastHelper(thread, returnThis, str[str.length - 1]);
            System.out.println("Client " + name + " unicasted message to " + str[str.length - 1]);
        }

        //helper function for unicasting message to client
        private void uniCastHelper(ArrayList<Handler> thread, String filenameOrMessage, String username) {
            for (Handler handler : thread) {
                if (handler != null && handler.name != null && handler.name.equalsIgnoreCase(username)) {
                    try {
                        handler.out.writeObject(filenameOrMessage);
                        handler.out.flush();
                    }
                    catch (IOException ignored) {
                    }
                }
            }
        }
        
        //send message to all but one client.
        public void blockCastMessage(ArrayList<Handler> thread, String[] str){
            String returnThis = getMessageFromString(str);
            for(Handler handler : thread){
                if(handler != null && handler.name != null && !handler.name.equalsIgnoreCase(name) &&
                        !handler.name.equalsIgnoreCase(str[str.length - 1])){
                    try {
                        handler.out.writeObject(returnThis);
                        handler.out.flush();
                    }
                    catch (IOException ignored) {
                    }
                }
            }
            System.out.println("Client " + name + " blockcasted message excluding " + str[str.length - 1]);
        }

        //helper function to extract message from the string received from client
        public String getMessageFromString(String[] str){
            StringBuilder sb = new StringBuilder();
            for(int i = 2; i < str.length - 1; i++){
                sb.append(str[i]).append(" ");
            }
            return "@" + name + ": " + sb.substring(1, sb.length()-2);
        }
                
        //method to receive the file from client
        private void receiveBroadcastFile(String filename) throws IOException, InterruptedException {
        	int bytesRead;
        	InputStream is = serverSocket.getInputStream();
			FileOutputStream fos = new FileOutputStream(new File(filename));
			try {
                byte[] buffer = new byte[1024];
                //broadcast filename to be received to the clients
                broadCastHelper(thread, "file " + filename + " " + name);
                int prev = 0;
                //read the file contents
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    fos.flush();
                   if(prev > bytesRead){
                	   //send the received file to other clients
                    	sendBroadCastFile(thread, filename);
                    	fos.wait();
                    }
                    prev = bytesRead;
                }
            }
			catch (Exception ignored){
			}
			finally{
			    fos.close();
			}
		}

        //method to broadcast file
        private void sendBroadCastFile(ArrayList<Handler> thread, String filename) {
            for (Handler handler : thread) {
                if (handler != null && handler.name != null && !handler.name.equalsIgnoreCase(name)) {
                    sendFileHelper(filename, handler);
                }
            }
            System.out.println("Client " + name + " broadcasted file " + filename);
        }

        //receive the file and receiving client username to whom file is to be sent from sending client
        private void receiveUnicastFile(String filename, String username) throws IOException {
            int bytesRead;
            InputStream is = serverSocket.getInputStream();
            FileOutputStream fos = new FileOutputStream(filename);
            try {
                byte[] buffer = new byte[1024];
                //unicast filename to be received to the client
                uniCastHelper(thread, "file " + filename + " " + name, username);
                int prev = 0;
                //read the file contents
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                    fos.flush();
                    if(prev > bytesRead){
                    	//send the file to receiver
                    	sendUniCastFile(thread, filename, username);
                    	fos.wait();
                    }
                    prev = bytesRead;
                }
            }
            catch (Exception ignored){
            }
            finally{
                fos.close();
            }
        }

        //method to unicast file
        private void sendUniCastFile(ArrayList<Handler> thread, String filename, String username) throws InterruptedException {
            for (Handler handler : thread) {
                if (handler != null && handler.name != null && handler.name.equalsIgnoreCase(username)) {
                    sendFileHelper(filename, handler);
                }
            }
            System.out.println("Client " + name + " unicasted file " + filename + " to Client " + username);
		}
        
        //method to send file to the client
        private void sendFileHelper(String filename, Handler handler) {
            try {
                File transferFile = new File(filename);
                byte[] buffer = new byte[(int) transferFile.length()];
                FileInputStream fis = new FileInputStream(transferFile);
                BufferedInputStream bin = new BufferedInputStream(fis);
                FileOutputStream fos = (FileOutputStream) handler.serverSocket.getOutputStream();
                try {
                    //read the file
                    bin.read(buffer, 0, buffer.length);
                    //write the bytes to output stream
                    fos.write(buffer, 0, buffer.length);
                    fos.flush();
                }
                catch (Exception ignored) {
                }
                finally {
                    fis.close();
                    bin.close();
                }
            }
            catch (IOException ignored) {
            }
        }
    }

    //main method
    public static void main(String[] args) throws Exception {
        //take port number as input to initialize the server
        new Server(Integer.parseInt(args[0]));
        System.out.println("The server is running on port: " + sPort);
        try (ServerSocket listener = new ServerSocket(sPort)) {
            while (true) {
                for (int i = 0; i < 10; i++) {
                	//initiate new handler for each client
                    thread.add(i, new Handler(listener.accept(), thread));
                    thread.get(i).start();
                }
            }
        }
    }
}