/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package dev.headless.learning.chat_io;

import io.socket.client.IO;
import io.socket.client.Socket;
import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Base64;
import java.util.Scanner;
import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

/**
 *
 * @author brett
 */
public class Client {
    
    private final static String DIR ="src/main/java/dev/headless/learning/chat_io/";
    
    static Socket socket;
    private static boolean connected = false;
    
    private static JFrame frame;
    private static JPanel panel;
    private static EmbeddedMediaPlayerComponent mediaPlayerComponent;
    
    static boolean frameOpen = false;
    public static void main(String[] args) {
        try {
            //Start the Node Socket.IO Server
            ProcessBuilder serverPB = new ProcessBuilder("node", DIR+"include/server.js");
            serverPB.inheritIO();
            Process socket_server = serverPB.start();
            
            //Reverse Android Port
            ProcessBuilder adbPB = new ProcessBuilder(DIR+"include/adb", "reverse", "tcp:5555", "tcp:5555");
            adbPB.inheritIO();
            Process adbProc = adbPB.start();
            
            Runtime.getRuntime().addShutdownHook(new Thread(()->{
                socket_server.destroy();
                adbProc.destroy();
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
