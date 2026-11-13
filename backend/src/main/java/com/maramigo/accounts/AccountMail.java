package com.maramigo.accounts;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Local messages never leave the demo machine; SMTP must be configured explicitly. @author Maram Abbas */
@Component
public class AccountMail {
    private final String mode, base, from;
    private final Path directory;
    private final JavaMailSenderImpl smtp = new JavaMailSenderImpl();
    public AccountMail(Environment env,
            @Value("${maramigo.mail.mode:disabled}") String mode,
            @Value("${maramigo.mail.base-url:https://localhost}") String base,
            @Value("${maramigo.mail.directory:.runtime/mail}") String directory,
            @Value("${maramigo.mail.from:}") String from,
            @Value("${maramigo.mail.host:}") String host,
            @Value("${maramigo.mail.port:587}") int port,
            @Value("${maramigo.mail.username:}") String username,
            @Value("${maramigo.mail.password:}") String password) {
        this.mode=mode; this.base=base; this.from=from; this.directory=Path.of(directory).toAbsolutePath().normalize();
        boolean demo=env.acceptsProfiles(Profiles.of("demo"));
        URI uri=URI.create(base);
        if(uri.getHost()==null || uri.getRawQuery()!=null || uri.getRawFragment()!=null || uri.getUserInfo()!=null
                || !("https".equals(uri.getScheme()) || (demo && "http".equals(uri.getScheme()) && "127.0.0.1".equals(uri.getHost()))))
            throw new IllegalArgumentException("Account mail requires a trusted HTTPS public base URL (loopback HTTP is demo only).");
        if(!java.util.Set.of("disabled","file","smtp").contains(mode) || (mode.equals("file") && !demo))
            throw new IllegalArgumentException("Local account mail is allowed only with the demo profile.");
        if(mode.equals("smtp") && (host.isBlank() || from.isBlank() || username.isBlank() || password.isBlank()))
            throw new IllegalArgumentException("SMTP account mail configuration is incomplete.");
        smtp.setHost(host); smtp.setPort(port); smtp.setUsername(username); smtp.setPassword(password);
        var p=smtp.getJavaMailProperties();
        p.setProperty("mail.smtp.auth","true"); p.setProperty("mail.smtp.starttls.enable","true");
        p.setProperty("mail.smtp.starttls.required","true"); p.setProperty("mail.smtp.ssl.checkserveridentity","true");
        p.setProperty("mail.smtp.connectiontimeout","5000"); p.setProperty("mail.smtp.timeout","5000"); p.setProperty("mail.smtp.writetimeout","5000");
    }
    public String mode(){return mode;}
    public void available(){if(mode.equals("disabled"))throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account email delivery is not configured.");}
    public void send(String email,String purpose,String token) {
        available();
        String route=purpose.equals("RESET")?"reset-password":purpose.equals("EMAIL")?"confirm-email-change":"verify-email";
        String subject=purpose.equals("RESET")?"Mavelle — reset your password":"Mavelle — verify your email";
        String content="עברית / العربية / English\n\n"+subject+"\n\n"+base.replaceAll("/+$","")+"/#"+route+"/"+token
            +"\n\n"+(purpose.equals("RESET")?"Valid for 30 minutes. / בתוקף ל־30 דקות. / صالح لمدة 30 دقيقة.":"Valid for 24 hours. / בתוקף ל־24 שעות. / صالح لمدة 24 ساعة.")
            +"\nIf you did not request this, ignore this message.\nאם לא ביקשת זאת, התעלמי מההודעה.\nإذا لم تطلبي ذلك، تجاهلي الرسالة.\n";
        deliver(email,subject,content);
    }
    public void sendOrder(String email,String number,String summary) {
        available();
        deliver(email,"Mavelle — order confirmation / אישור הזמנה / تأكيد الطلب",summary+"\n\n"+base.replaceAll("/+$","")+"/#order/"+number
            +"\n\nDemo order confirmation. This is not a tax invoice. No payment was collected.\nאישור הזמנה להדגמה בלבד. זו אינה חשבונית מס ולא נגבה תשלום.\nتأكيد طلب تجريبي فقط. ليس فاتورة ضريبية ولم يتم تحصيل أي مبلغ.\n");
    }
    private void deliver(String email,String subject,String content) {
        try {
            if(mode.equals("file")){
                Files.createDirectories(directory);
                Files.writeString(directory.resolve(UUID.randomUUID()+".txt"),"To: "+email+"\n"+content,StandardCharsets.UTF_8,StandardOpenOption.CREATE_NEW);
            }else{
                var message=new SimpleMailMessage();message.setFrom(from);message.setTo(email);message.setSubject(subject);message.setText(content);smtp.send(message);
            }
        }catch(Exception failure){throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Account email could not be delivered. Please try again later.");}
    }
}
