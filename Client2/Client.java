
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    Socket clientSocket;            //socket connection to the server
    ObjectOutputStream out;         //stream write to the socket
    ObjectInputStream in;           //stream read from the socket
    String sentMessage;             //message sent from client to server
    String receivedMessage;         //message received from server
	int port;
	private Scanner sc;

    public Client(int port) throws IOException {
		this.port=port;
        clientSocket = new Socket("localhost", port);
        out = new ObjectOutputStream(clientSocket.getOutputStream());
        out.flush();
        in = new ObjectInputStream(clientSocket.getInputStream());
        System.out.println("Enter username: ");
        sc = new Scanner(System.in);
        String name = sc.nextLine();
        sendUsername(name);
        handler();
    }

    private void handler() throws IOException {
    	//thread to send messages and files.
    	new Thread(new Runnable(){
			public void run(){
                System.out.println("You are now connected");
    			try {
                    while (true) {
                        sentMessage = sc.nextLine();
                        String[] str = sentMessage.split(" ");
                        //checks if command entered is a message
                        if (str[1].equalsIgnoreCase("message")) {
                            sendMessage(sentMessage);
                        }
                        //checks if file is to be transferred
                        else if (str[1].equalsIgnoreCase("file")) {
                            try {
                                sendMessage(sentMessage);
                                sendFile(str[2]);
                            }
                            catch (IOException | InterruptedException ignored) {
                            }
                        }
                        else {
                            System.out.println("Invalid Command");
                        }
                    }
                }
                catch (Exception ignored){
                }
    		}

			//send a file to output stream
            void sendFile(String filepath) throws IOException, InterruptedException {
                String filename = filepath.substring(1, filepath.length()-1);
                File transferFile = new File(filename);
                byte [] buffer = new byte [(int)transferFile.length()];
                FileInputStream fis = new FileInputStream(transferFile);
                BufferedInputStream bis = new BufferedInputStream(fis);
                FileOutputStream fos = (FileOutputStream) clientSocket.getOutputStream();
                try {
                	//read the file
                    bis.read(buffer, 0, buffer.length);
                    //write the bytes to output stream
                    fos.write(buffer, 0, buffer.length);
                    fos.flush();
                }
                catch(Exception ignored){
                }
                finally {
                	fis.close();
                	bis.close();
                }
            }

            //send a message to the output stream
            void sendMessage(String msg) {
                try{
                    //stream write the message
                    out.writeObject(msg);
                    out.flush();
                }
                catch(IOException ignored){
                }
            }
    	}).start();

    	//thread to receive messages and file
    	new Thread(new Runnable(){
		    public void run(){
			    while(true) {
				    try {
					    receivedMessage = (String)in.readObject();
                        String[] str = receivedMessage.split(" ");
                        //check if a file is to be received
                        if(str[0].equalsIgnoreCase("file")){
                            receiveFile(str[1], str[2]);
                        }
                        else{
                            //show the message to the user
                            System.out.println(receivedMessage);
                        }
				    }
                    catch (ClassNotFoundException | IOException ignored) {
				    }
			    }
		    }

		    //receive the file
            private void receiveFile(String filename, String senderClientName) throws IOException {
                int bytesRead;
                InputStream is = clientSocket.getInputStream();
                FileOutputStream fos = new FileOutputStream(new File(filename));
                try {
                    byte[] buffer = new byte[1024];
                    int prev = 0;
                    //read the contents from input stream
                    while ((bytesRead = is.read(buffer)) != -1) {
                    	//write  the contents in new file
                        fos.write(buffer, 0, bytesRead);
                        fos.flush();
                        if(prev > bytesRead){
                        	is.wait();
                        	is.notify();
                        }
                        prev = bytesRead;
                    }
                }
                catch (Exception ignored){
                }
                finally{
                    fos.close();
                }
                System.out.println("File: " + filename + " received from Client " + senderClientName);
            }
        }).start();
    }

    //send a message to the output stream
    void sendUsername(String msg) {
        try{
            //stream write the message
            out.writeObject(msg);
            out.flush();
        }
        catch(IOException ignored){
        }
    }

    //main method
    public static void main(String args[]) throws NumberFormatException, IOException {
        new Client(Integer.parseInt(args[0]));
    }
}