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
 record Submission(String id,String kind,String description,String page,String version,List<String> images){ Submission(String id,String kind,String description,String page,String version){this(id,kind,description,page,version,null);} }
 record Change(String status){}
 private final JdbcTemplate db;private final String secret;
 @Value("${feedback.images.max-bytes:4000000000}") long imageLimit=4_000_000_000L;
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
  var images=FeedbackImages.decode(input.images());
  lockImages();
  long incoming=images.stream().mapToLong(a->a.length).sum();
  evictImages(incoming);
  db.update("insert into feedback(id,actor,kind,description,page,app_version) values(?,?,?,?,?,?)",input.id(),actor,input.kind(),input.description().strip(),input.page(),input.version());
  for(byte[] image:images)db.update("insert into feedback_image(id,feedback_id,content,size_bytes) values(?,?,?,?)",UUID.randomUUID().toString(),input.id(),image,image.length);
  return Map.of("id",input.id());
 }
 @GetMapping Map<String,Object> inbox(@RequestHeader("X-Actor") String actor,@RequestParam(defaultValue="all") String status,@RequestParam(defaultValue="0") int offset){
  requireAdmin(actor);if(!Set.of("all","new","reviewing","resolved").contains(status)||offset<0||offset>100000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var items=db.queryForList("select f.id,f.kind,f.description,f.page,f.app_version,f.status,f.created,p.username,p.name,p.avatar from feedback f join profile p on p.actor=f.actor where (?='all' or f.status=?) order by f.created desc,f.id limit 51 offset ?",status,status,offset);
  for(var item:items){item.put("images",db.queryForList("select id from feedback_image where feedback_id=? order by created,id",String.class,item.get("id")));item.put("thanks",db.queryForList("select state from feedback_thanks where feedback_id=?",String.class,item.get("id")).stream().findFirst().orElse("none"));}
  boolean more=items.size()>50;return Map.of("items",more?items.subList(0,50):items,"hasMore",more);
 }
 @PutMapping("/{id}") @Transactional Map<String,String> change(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody Change input){
  requireAdmin(actor);if(input.status()==null||!Set.of("new","reviewing","resolved").contains(input.status()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
  var reports=db.queryForList("select status,kind from feedback where id=? for update",id);
  if(reports.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  if(input.status().equals("resolved")){
   lockImages();db.update("delete from feedback_image where feedback_id=?",id);
   if("bug".equals(reports.getFirst().get("kind"))&&db.queryForObject("select count(*) from feedback_thanks where feedback_id=?",Integer.class,id)==0)db.update("insert into feedback_thanks(feedback_id) values(?)",id);
  }
  if(db.update("update feedback set status=?,updated=CURRENT_TIMESTAMP where id=?",input.status(),id)!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);
  return Map.of("status",input.status());
 }
 private void lockImages(){db.queryForList("select id from feedback_image_quota where id=1 for update");}
 private void evictImages(long incoming){
  if(incoming>imageLimit)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"Imagens excedem a capacidade disponível.");
  long used=db.queryForObject("select coalesce(sum(size_bytes),0) from feedback_image",Long.class);
  while(used+incoming>imageLimit){var oldest=db.queryForMap("select id,size_bytes from feedback_image order by created,id limit 1");db.update("delete from feedback_image where id=?",oldest.get("id"));used-=((Number)oldest.get("size_bytes")).longValue();}
 }
 @GetMapping("/{id}/images/{imageId}") org.springframework.http.ResponseEntity<byte[]> image(@RequestHeader("X-Actor") String actor,@PathVariable String id,@PathVariable String imageId){
  requireAdmin(actor);var rows=db.query("select content from feedback_image where feedback_id=? and id=?",(r,n)->r.getBytes(1),id,imageId);
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Anexo removido ou indisponível.");
  return org.springframework.http.ResponseEntity.ok().header("Cache-Control","no-store").header("X-Content-Type-Options","nosniff").contentType(org.springframework.http.MediaType.IMAGE_JPEG).body(rows.getFirst());
 }

}
