package br.com.cenaaberta;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/feedback")
public class Feedback {
 record Submission(String id,String kind,String description,String page,String version){}
 record Change(String status){}
 private final JdbcTemplate db;private final String secret;
 Feedback(JdbcTemplate db,@Value("${internal.secret}") String secret){this.db=db;this.secret=secret;}
 @ModelAttribute void verify(HttpServletRequest request){
  String key=request.getHeader("X-Internal-Key"),actor=request.getHeader("X-Actor");
  if(key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
  if(actor==null||db.queryForObject("select count(*) from profile where actor=? and active=true",Integer.class,actor)!=1)throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Entre e conclua seu perfil para enviar feedback.");
 }
 boolean admin(String actor){return db.queryForObject("select count(*) from app_admin where actor=?",Integer.class,actor)==1;}
 void requireAdmin(String actor){if(!admin(actor))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Acesso exclusivo de administradores.");}
 @GetMapping("/access") Map<String,Boolean> access(@RequestHeader("X-Actor") String actor){return Map.of("admin",admin(actor));}
 @PostMapping @Transactional Map<String,String> submit(@RequestHeader("X-Actor") String actor,@RequestBody Submission input){
  if(input.id()==null||!input.id().matches("[a-f0-9-]{36}")||input.kind()==null||!Set.of("bug","suggestion").contains(input.kind())||input.description()==null||input.description().strip().length()<10||input.description().length()>3000||input.page()==null||input.page().length()>250||!input.page().startsWith("/")||input.page().contains("?")||input.page().contains("#")||input.version()==null||!input.version().matches("[a-zA-Z0-9._+\\-]{1,80}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe uma descrição de 10 a 3000 caracteres.");
  db.queryForList("select actor from profile where actor=? for update",actor);
  var existing=db.queryForList("select actor from feedback where id=?",String.class,input.id());
  if(!existing.isEmpty()){if(!existing.getFirst().equals(actor))throw new ResponseStatusException(HttpStatus.CONFLICT);return Map.of("id",input.id());}
  if(db.queryForObject("select count(*) from feedback where actor=? and created>?",Integer.class,actor,OffsetDateTime.now().minusDays(1))>=10)throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Limite de 10 relatos por dia. Tente novamente amanhã.");
  db.update("insert into feedback(id,actor,kind,description,page,app_version) values(?,?,?,?,?,?)",input.id(),actor,input.kind(),input.description().strip(),input.page(),input.version());
  return Map.of("id",input.id());
 }
 @GetMapping Map<String,Object> inbox(@RequestHeader("X-Actor") String actor,@RequestParam(defaultValue="all") String status,@RequestParam(defaultValue="0") int offset){
  requireAdmin(actor);if(!Set.of("all","new","reviewing","resolved").contains(status)||offset<0||offset>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var items=db.queryForList("select f.id,f.kind,f.description,f.page,f.app_version,f.status,f.created,p.username,p.name,p.avatar from feedback f join profile p on p.actor=f.actor where (?='all' or f.status=?) order by f.created desc,f.id limit 51 offset ?",status,status,offset);
  boolean more=items.size()>50;return Map.of("items",more?items.subList(0,50):items,"hasMore",more);
 }
 @PutMapping("/{id}") @Transactional Map<String,String> change(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody Change input){
  requireAdmin(actor);if(input.status()==null||!Set.of("new","reviewing","resolved").contains(input.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  if(db.update("update feedback set status=?,updated=CURRENT_TIMESTAMP where id=?",input.status(),id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return Map.of("status",input.status());
 }
}
