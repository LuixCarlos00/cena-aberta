package br.com.cenaaberta;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import jakarta.mail.internet.InternetAddress;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/accounts")
class EmailAccounts {
    record Input(String email,String password,String token) {}
    record Login(String id,int version,String email) {}
    private final JdbcTemplate db;
    private final JavaMailSender mail;
    private final BCryptPasswordEncoder encoder=new BCryptPasswordEncoder(12);
    private final SecureRandom random=new SecureRandom();
    private final String dummy=encoder.encode(UUID.randomUUID().toString());
    private final byte[] secret;
    private final String mode, front, from;
    private final boolean inviteOnly;
    private final Set<String> invitedEmails;
    private long minute;
    private int requests;

    EmailAccounts(JdbcTemplate db,ObjectProvider<JavaMailSender> mail,
        @Value("${internal.secret}") String secret,@Value("${EMAIL_MODE:disabled}") String mode,
        @Value("${FRONTEND_URL:http://localhost:3000}") String front,@Value("${MAIL_FROM:}") String from,
        @Value("${INVITE_ONLY:false}") boolean inviteOnly,@Value("${INVITED_EMAILS:}") String invitedEmails) {
        this.db=db; this.mail=mail.getIfAvailable(); this.secret=secret.getBytes(StandardCharsets.UTF_8);
        this.mode=mode; this.front=front.replaceAll("/$",""); this.from=from;
        this.inviteOnly=inviteOnly;
        var invitations=new HashSet<String>();
        for(String entry:invitedEmails.split(",")) {
            if(entry.isBlank()) continue;
            try { invitations.add(email(entry)); }
            catch(ResponseStatusException invalid) { throw new IllegalArgumentException("INVITED_EMAILS contém um e-mail inválido"); }
        }
        this.invitedEmails=Set.copyOf(invitations);
        if (!Set.of("disabled","log","smtp").contains(mode)) throw new IllegalArgumentException("EMAIL_MODE inválido");
        if (!mode.equals("disabled")) {
            URI url=URI.create(front);
            boolean local=Set.of("localhost","127.0.0.1").contains(Objects.toString(url.getHost(),""));
            if (url.getHost()==null || url.getRawUserInfo()!=null || url.getRawQuery()!=null || url.getRawFragment()!=null ||
                (!"https".equals(url.getScheme()) && !(local && "http".equals(url.getScheme()))) ||
                (mode.equals("log") && !local) || (mode.equals("smtp") && (this.mail==null || from.isBlank())))
                throw new IllegalArgumentException("Confira FRONTEND_URL, MAIL_FROM e SMTP_HOST");
        }
    }

    @ModelAttribute
    void guard(HttpServletRequest request) {
        String key=request.getHeader("X-Internal-Key");
        if(key==null || !MessageDigest.isEqual(secret,key.getBytes(StandardCharsets.UTF_8)))
            throw error(HttpStatus.UNAUTHORIZED,"Acesso interno necessário.");
    }

    @PostMapping("/{purpose:register|reset}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void request(@PathVariable String purpose,@RequestBody Input input) {
        if(mode.equals("disabled")) throw error(HttpStatus.SERVICE_UNAVAILABLE,"Envio de e-mail indisponível.");
        limit();
        String email=email(input.email());
        if(purpose.equals("register") && !canRegister(email)) return;
        if (account(email).isEmpty() != purpose.equals("register")) return;
        var now=OffsetDateTime.now(ZoneOffset.UTC);
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash=hash(token);
        int changed=db.update("update email_link set token_hash=?,sent_at=?,expires_at=? where email=? and purpose=? and sent_at<=?",
            hash,now,now.plusMinutes(60),email,purpose,now.minusMinutes(1));
        if(changed==0) {
            try {
                db.update("insert into email_link(email,purpose,token_hash,sent_at,expires_at) values(?,?,?,?,?)",
                    email,purpose,hash,now,now.plusMinutes(60));
            } catch(DuplicateKeyException cooldown) { return; }
        }
        try {
            send(email,purpose,front+"/conta#"+purpose+"="+token);
        } catch(MailException failure) {
            db.update("delete from email_link where token_hash=?",hash);
            LoggerFactory.getLogger(getClass()).warn("Falha no envio SMTP: {}",failure.getClass().getSimpleName());
        }
    }

    @PostMapping("/{purpose:register|reset}/confirm")
    @Transactional
    Login confirm(@PathVariable String purpose,@RequestBody Input input) {
        limit();
        String token=input.token();
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalid();
        String tokenHash=hash(token);
        var candidate=db.queryForList("select email from email_link where token_hash=? and purpose=? and expires_at>CURRENT_TIMESTAMP",tokenHash,purpose);
        if(candidate.isEmpty()) throw invalid();
        String password=input.password();
        if(password==null || password.codePointCount(0,password.length())<12 || password.getBytes(StandardCharsets.UTF_8).length>72 || password.isBlank())
            throw error(HttpStatus.BAD_REQUEST,"Use pelo menos 12 caracteres e no máximo 72 bytes.");
        String encoded=encoder.encode(password);
        var rows=db.queryForList("select email from email_link where token_hash=? and purpose=? and expires_at>CURRENT_TIMESTAMP for update",tokenHash,purpose);
        if(rows.isEmpty()) throw invalid();
        String email=(String)rows.getFirst().get("email");
        if(purpose.equals("register")) {
            if(!canRegister(email)) throw invalid();
            try { db.update("insert into email_account(actor,email,password_hash) values(?,?,?)","account_"+UUID.randomUUID().toString().replace("-",""),email,encoded); }
            catch(DuplicateKeyException duplicate) { throw invalid(); }
        } else if(db.update("update email_account set password_hash=?,session_version=session_version+1 where email=?",encoded,email)!=1) throw invalid();
        db.update("delete from email_link where token_hash=?",tokenHash);
        return account(email).orElseThrow(EmailAccounts::invalid);
    }

    @PostMapping("/login")
    Login login(@RequestBody Input input) {
        limit();
        String email=email(input.email());
        var rows=db.queryForList("select actor,password_hash,session_version from email_account where email=?",email);
        var row=rows.isEmpty()?Map.<String,Object>of():rows.getFirst();
        String password=input.password();
        if(password==null || password.getBytes(StandardCharsets.UTF_8).length>72 ||
           !encoder.matches(password,(String)row.getOrDefault("password_hash",dummy)) || row.isEmpty())
            throw error(HttpStatus.UNAUTHORIZED,"E-mail ou senha inválidos.");
        return new Login((String)row.get("actor"),(Integer)row.get("session_version"),email);
    }

    @GetMapping("/session")
    void session(@RequestHeader("X-Actor") String actor,@RequestParam int version) {
        Integer count=db.queryForObject("select count(*) from email_account where actor=? and session_version=?",Integer.class,actor,version);
        if(count==null || count!=1) throw error(HttpStatus.UNAUTHORIZED,"Sessão inválida.");
    }

    private Optional<Login> account(String email) {
        return db.query("select actor,session_version from email_account where email=?",
            (r,i)->new Login(r.getString("actor"),r.getInt("session_version"),email),email).stream().findFirst();
    }

    private boolean canRegister(String email) {
        return !inviteOnly || invitedEmails.contains(email);
    }

    private void send(String email,String purpose,String link) {
        if(mode.equals("log")) {
            LoggerFactory.getLogger(getClass()).info("Link local de {}: {}",purpose,link);
            return;
        }
        SimpleMailMessage message=new SimpleMailMessage();
        message.setFrom(from); message.setTo(email);
        message.setSubject(purpose.equals("register")?"Confirme sua conta no Cena Aberta":"Redefina sua senha do Cena Aberta");
        message.setText("Abra este link para definir sua senha:\n"+link+"\n\nEle expira em uma hora. Se você não solicitou, ignore esta mensagem.");
        mail.send(message);
    }

    private synchronized void limit() {
        long now=System.currentTimeMillis()/60000;
        if(now!=minute) {minute=now;requests=0;}
        if(++requests>100) throw error(HttpStatus.TOO_MANY_REQUESTS,"Muitas tentativas. Aguarde um minuto.");
    }

    private static String email(String raw) {
        String value=raw==null?"":raw.strip().toLowerCase(Locale.ROOT);
        try {
            InternetAddress parsed=new InternetAddress(value,true);
            parsed.validate();
            if(value.length()<=254 && value.equals(parsed.getAddress()) && !parsed.isGroup()) return value;
        } catch(jakarta.mail.internet.AddressException ignored) {}
        throw error(HttpStatus.BAD_REQUEST,"Informe um e-mail válido.");
    }

    private static String hash(String token) {
        try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible) {throw new IllegalStateException(impossible);}
    }
    private static ResponseStatusException invalid() {return error(HttpStatus.BAD_REQUEST,"Link inválido ou expirado. Solicite outro.");}
    private static ResponseStatusException error(HttpStatus status,String message) {return new ResponseStatusException(status,message);}
}
