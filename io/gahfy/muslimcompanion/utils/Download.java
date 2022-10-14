package io.gahfy.muslimcompanion.utils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

public class Download {
    public static void main(String[] args) {
        try{
            for(int i=0; i<args.length; i+=2) {
                String url = args[i];
                String md5 = args[i+1];
                download(url, md5);
            }
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void download(String urlString, String md5) throws URISyntaxException, IOException, NoSuchAlgorithmException{
        System.out.println(String.format(Locale.US, "Downloading %s", urlString));
        File directory = new File("download");
        if(!directory.exists()) {
            directory.mkdir();
        }

        URI uri = new URI(urlString);
        URL url = uri.toURL();
        String fileName = Paths.get(uri.getPath()).getFileName().toString();
        String filePath = String.format(Locale.US, "download/%s", fileName);
        File file = new File(filePath);
        if(file.exists() && getMD5Checksum(filePath).equals(md5)) {
            System.out.println("File has already been downloaded and checksum matches.");
            return;
        } else if(file.exists()){
            System.out.println("File checksum does not match. Expected "+md5+" but got "+getMD5Checksum(filePath));
        }
        
        InputStream is = url.openStream();
        FileOutputStream fos = new FileOutputStream(filePath);

        byte[] data = new byte[1024];
        int x = 0;
        while ((x = is.read(data)) != -1) {
            fos.write(data, 0, x);
        }
        is.close();
        fos.close();

        String checksum = getMD5Checksum(filePath);
        if(!checksum.equals(md5)) {
            System.err.println("Files signatures not matches, retrying.");
            download(urlString, md5);
        }
    }

    public static byte[] createChecksum(String filename) throws NoSuchAlgorithmException, IOException {
        InputStream fis =  new FileInputStream(filename);
 
        byte[] buffer = new byte[1024];
        MessageDigest complete = MessageDigest.getInstance("MD5");
        int numRead;
 
        do {
            numRead = fis.read(buffer);
            if (numRead > 0) {
                complete.update(buffer, 0, numRead);
            }
        } while (numRead != -1);
 
        fis.close();
        return complete.digest();
    }
 
    public static String getMD5Checksum(String filename) throws NoSuchAlgorithmException, IOException {
        byte[] b = createChecksum(filename);
        String result = "";
 
        for (int i=0; i < b.length; i++) {
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }
}
