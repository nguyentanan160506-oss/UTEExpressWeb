package com.ute.uteexpress.operations;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import javax.sql.DataSource;

/** Loopback SMTP sink: no relay, no AUTH, no persistence/logging, bounded disposable test mail only. */
public final class DemoSmtpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final ExecutorService threads=Executors.newFixedThreadPool(4);
    private final Deque<Mail> mailbox=new ArrayDeque<>();
    private volatile boolean closed;
    static final class Mail {
        final String recipient,code;
        final Instant received;
        Mail(String recipient,String code){this.recipient=recipient;this.code=code;this.received=Instant.now();}
        @Override public String toString(){return "Local demo mail [redacted]";}
    }
    static void requireDisposableH2(DataSource source)throws Exception {
        try(var connection=source.getConnection()) {
            if(!"H2".equals(connection.getMetaData().getDatabaseProductName())
                    ||!connection.getMetaData().getURL().startsWith("jdbc:h2:mem:uteexpress_demo_"))
                throw new IllegalStateException("Test SMTP accepts only the owned disposable H2 demo");
        }
    }
    DemoSmtpServer()throws IOException {
        listener=new ServerSocket();listener.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),0));
        threads.execute(()->{while(!closed){try{var socket=listener.accept();threads.execute(()->receive(socket));}
            catch(IOException ignored){if(!closed)close();}}});
    }
    public int port(){return listener.getLocalPort();}
    synchronized List<Mail> messages() {
        mailbox.removeIf(mail->mail.received.isBefore(Instant.now().minusSeconds(300)));
        return List.copyOf(mailbox);
    }
    private synchronized void capture(String recipient,byte[] bytes)throws Exception {
        var mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(bytes));
        Object content=mime.getContent();
        if(!(content instanceof String text))throw new IOException("Only text demo mail is supported");
        var code=Pattern.compile("(?<![0-9])[0-9]{6}(?![0-9])").matcher(text);
        if(!code.find())throw new IOException("Missing test verification code");
        while(mailbox.size()>=50)mailbox.removeFirst();
        mailbox.addLast(new Mail(recipient,code.group()));
    }
    private void receive(Socket socket) {
        try(socket;var in=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                var out=new PrintWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.US_ASCII),true)) {
            socket.setSoTimeout(5000);out.print("220 localhost H2 demo SMTP\r\n");out.flush();
            String recipient=null,line;int commands=0;
            while(!closed&&(line=in.readLine())!=null&&++commands<100) {
                String upper=line.toUpperCase(Locale.ROOT);
                if(upper.startsWith("EHLO")||upper.startsWith("HELO"))reply(out,"250 localhost");
                else if(upper.startsWith("MAIL FROM:")){recipient=null;reply(out,"250 OK");}
                else if(upper.startsWith("RCPT TO:")) {
                    String address=line.substring(8).trim().replace("<","").replace(">","");
                    if(address.matches("[A-Za-z0-9._+-]+@example\\.test")){recipient=address;reply(out,"250 OK");}
                    else reply(out,"550 Local example.test recipients only");
                } else if(upper.equals("DATA")&&recipient!=null) {
                    reply(out,"354 End with dot");var body=new StringBuilder();
                    while((line=in.readLine())!=null&&!line.equals(".")){
                        if(body.length()+line.length()>65536)throw new IOException("Demo message too large");
                        body.append(line.startsWith("..")?line.substring(1):line).append("\r\n");
                    }
                    capture(recipient,body.toString().getBytes(StandardCharsets.UTF_8));reply(out,"250 Captured locally");
                } else if(upper.equals("RSET")){recipient=null;reply(out,"250 OK");}
                else if(upper.equals("NOOP"))reply(out,"250 OK");
                else if(upper.equals("QUIT")){reply(out,"221 Bye");return;}
                else reply(out,"502 Unsupported");
            }
        } catch(Exception ignored) { /* Never log recipient, SMTP body, OTP or provider failure. */ }
    }
    private static void reply(PrintWriter out,String line){out.print(line+"\r\n");out.flush();}
    @Override public void close() {
        closed=true;try{listener.close();}catch(IOException ignored){}
        threads.shutdownNow();synchronized(this){mailbox.clear();}
    }
}
