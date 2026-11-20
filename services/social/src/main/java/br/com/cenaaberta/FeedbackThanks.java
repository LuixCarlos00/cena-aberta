package br.com.cenaaberta;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component @EnableScheduling
class FeedbackThanks {
 private final JdbcTemplate db;private final JavaMailSender mail;private final String mode,from;
 FeedbackThanks(JdbcTemplate db,ObjectProvider<JavaMailSender> mail,@Value("${EMAIL_MODE:disabled}") String mode,@Value("${MAIL_FROM:}") String from){this.db=db;this.mail=mail.getIfAvailable();this.mode=mode;this.from=from;}
 @Scheduled(fixedDelay=60000,initialDelay=60000) @Transactional
 public void deliver(){
  if(!"smtp".equals(mode)||mail==null||from.isBlank())return;
  var rows=db.queryForList("select feedback_id,attempts from feedback_thanks where state='pending' and next_attempt<=CURRENT_TIMESTAMP order by next_attempt limit 10 for update skip locked");
  for(var row:rows){String id=(String)row.get("feedback_id");
   var addresses=db.queryForList("select a.email from email_account a join feedback f on f.actor=a.actor where f.id=? and a.state<>'deleted'",String.class,id);
   if(addresses.isEmpty()){db.update("update feedback_thanks set state='skipped' where feedback_id=?",id);continue;}
   try{var message=new SimpleMailMessage();message.setFrom(from);message.setTo(addresses.getFirst());message.setSubject("Seu relato ajudou a melhorar o Cena Aberta");message.setText("Olá!\n\nO problema que você reportou foi marcado como resolvido pela nossa equipe. Obrigado por ajudar a melhorar o Cena Aberta!\n\nOs anexos desse relato foram removidos. Se o problema continuar, envie um novo feedback pelo app.\n\nEquipe Cena Aberta");mail.send(message);db.update("update feedback_thanks set state='sent',sent_at=CURRENT_TIMESTAMP,attempts=attempts+1 where feedback_id=?",id);
   }catch(org.springframework.mail.MailException e){int attempts=((Number)row.get("attempts")).intValue()+1;db.update("update feedback_thanks set attempts=?,next_attempt=? where feedback_id=?",attempts,java.time.OffsetDateTime.now().plusMinutes(Math.min(1440L,5L*(1L<<Math.min(attempts,8)))),id);org.slf4j.LoggerFactory.getLogger(getClass()).warn("Falha no agradecimento SMTP; nova tentativa agendada.");}
  }
 }
}
