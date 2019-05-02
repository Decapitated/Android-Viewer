/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dev.headless.learning.chat_io;

import io.socket.client.IO;
import io.socket.client.Socket;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 *
 * @author brett
 */
public class Client {
    
    private final static String DIR ="src/main/java/dev/headless/learning/chat_io/";
    
    private static Process socket_server = null;
    static Socket socket = null;
    private static boolean connected = false;
    
    private static JFrame frame;
    private static JPanel panel;
    
    static boolean frameOpen = false;
    static boolean adbRunning = false;
    static boolean deviceConnected = false;
    
    
    public static void main(String[] args) {
        try {
            ProcessBuilder adbPB = new ProcessBuilder(DIR+"include/adb", "start-server");
            Process adbProc = adbPB.start();
            
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(adbProc.getErrorStream()));
            String line = null;
            while(!adbRunning){
                line = errorReader.readLine();
                if(line == null || line.equals("* daemon started successfully")){
                    adbRunning = true;
                    System.out.println("ADB Running.");
                }
            }
            adbProc.destroy();
            
            adbPB = new ProcessBuilder(DIR+"include/adb", "reverse", "tcp:5555", "tcp:5555");
            adbProc = adbPB.start();
            errorReader = new BufferedReader(new InputStreamReader(adbProc.getErrorStream()));
            System.out.println("Waiting for device...");
            while(!deviceConnected){
                line = errorReader.readLine();
                if(line != null && (line.equals("adb.exe: error: no devices/emulators found") || line.equals("adb.exe: error: device offline"))){
                    adbProc.destroy();
                    adbProc = adbPB.start();
                    errorReader = new BufferedReader(new InputStreamReader(adbProc.getErrorStream()));
                }else{
                    deviceConnected = true;
                    System.out.println("Device Connected.");
                }
            }
            adbProc.destroy();
            
            //Start the Node Socket.IO Server
            ProcessBuilder serverPB = new ProcessBuilder("node", DIR+"include/server.js");
            serverPB.inheritIO();
            socket_server = serverPB.start(); 
            
            //Run at shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(()->{
                System.out.println("Closing...");
                if(socket != null){ socket.close(); }
                if(socket_server != null){ socket_server.destroy(); }
                ProcessBuilder killAdb = new ProcessBuilder(DIR+"include/adb", "kill-server");
                try {
                    killAdb.start();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }));
            
            try {
                socket = IO.socket("http://localhost:5555");
                
                socket.on(Socket.EVENT_CONNECT, (Object... os) -> {
                    System.out.println("Connected to Server.");
                    connected = true;
                    inputThread.start();
                });
                
                socket.on(Socket.EVENT_DISCONNECT, args1 -> {
                    socket.close();
                    connected = false;
                    System.out.println("Server Closed.");
                    System.exit(0);
                });
                
                socket.on("message", args1 -> {
                    if(((String)args1[1]).equalsIgnoreCase("exit")){
                        System.exit(0);
                    }
                });
                
                socket.on("frame", args1 ->{
                    byte[] bytes = Base64.getMimeDecoder().decode((String)args1[0]);
                    ByteArrayInputStream bin = new ByteArrayInputStream(bytes);
                    try {
                        BufferedImage img = ImageIO.read(bin);
                        int type = img.getType() == 0 ? BufferedImage.TYPE_INT_ARGB : img.getType();
                        img = resizeImage(img, type);
                        if(!frameOpen){
                            openImage(img.getWidth());
                        }
                        panel.removeAll();
                        panel.add(new JLabel(new ImageIcon(img)));
                        frame.pack();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                });
                
                socket.connect();
            } catch (URISyntaxException e2) {
                System.exit(0);
            }
        } catch (IOException e1) {
            System.exit(0);
        }
    }
    
    public static void openImage(int width){
        frameOpen = true;
        frame = new JFrame("My First Media Player");
        panel = new JPanel();
        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter(){
            @Override
            public void windowClosing(WindowEvent e){
                frameOpen = false;
            }
        });
        frame.setVisible(true);
    }
    
    static Thread inputThread = new Thread(new Runnable() {
        @Override
        public void run() {
            Scanner scan = new Scanner(System.in);
            while(connected){
                String temp = scan.nextLine();
                socket.emit("message", "Brett", temp);
                if(temp.equalsIgnoreCase("exit")){
                    System.exit(0);
                }
            }
        }
    });
    
    private static BufferedImage resizeImage(BufferedImage originalImage, int type) {
        double ratio = 1.0 * originalImage.getWidth() / originalImage.getHeight();
        int imgHeight = Toolkit.getDefaultToolkit().getScreenSize().height - 100;
        BufferedImage resizedImage = new BufferedImage((int) ((int)imgHeight*ratio), imgHeight, type);
        Graphics2D g = resizedImage.createGraphics();
        g.drawImage(originalImage, 0, 0, (int) ((int)imgHeight*ratio), imgHeight, null);
        g.dispose();

        return resizedImage;
    }
}
