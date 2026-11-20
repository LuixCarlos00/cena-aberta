package br.com.cenaaberta;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;
class FeedbackTest {
 JdbcTemplate db;Feedback service;
 @BeforeEach void setup(){
 var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
 new ResourceDatabasePopulator(new ClassPathResource("db/migration/V1__social.sql"),new ClassPathResource("db/migration/V3__profile_cpf_avatar.sql"),new ClassPathResource("db/migration/V13__feedback_admin.sql")).execute(source);
 db=new JdbcTemplate(source);for(String a:List.of("admin","member"))db.update("insert into profile(actor,username,name) values(?,?,?)",a,a,a);
 db.update("insert into app_admin(actor) values('admin')");service=new Feedback(db,"secret");
 }
 @Test void onlyAdminCanReadAndChangeReports(){
 String id=UUID.randomUUID().toString();var input=new Feedback.Submission(id,"bug","O cartaz não aparece","/salas","0.1.0-alpha");
 service.submit("member",input);service.submit("member",input);
 assertEquals(1,db.queryForObject("select count(*) from feedback",Integer.class));
 assertFalse(service.access("member").get("admin"));
 assertThrows(ResponseStatusException.class,()->service.inbox("member","all",0));
 assertThrows(ResponseStatusException.class,()->service.change("member",id,new Feedback.Change("resolved")));
 assertEquals(1,((List<?>)service.inbox("admin","new",0).get("items")).size());
 service.change("admin",id,new Feedback.Change("reviewing"));
 assertEquals("reviewing",db.queryForObject("select status from feedback",String.class));
 }
 @Test void rejectsSensitiveUrlAndLimitsReports(){
 assertThrows(ResponseStatusException.class,()->service.submit("member",new Feedback.Submission(UUID.randomUUID().toString(),"bug","Descrição válida aqui","/conta?token=secret","0.1")));
 for(int i=0;i<10;i++)service.submit("member",new Feedback.Submission(UUID.randomUUID().toString(),"suggestion","Uma sugestão de melhoria","/","0.1"));
 var e=assertThrows(ResponseStatusException.class,()->service.submit("member",new Feedback.Submission(UUID.randomUUID().toString(),"bug","Descrição válida aqui","/","0.1")));
 assertEquals(429,e.getStatusCode().value());
 }
}
