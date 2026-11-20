package br.com.cenaaberta;

import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/rooms")
public class MovieRooms {
    record Create(String name) {}
    record Vote(Boolean liked) {}
    record Invitation(String username) {}
    record Settings(Integer genre,List<Integer> genres) {
        Settings(Integer genre){this(genre,null);}
    }
    private static final Set<Integer> GENRES=Set.of(28,12,16,35,80,99,18,10751,14,36,27,10402,9648,10749,878,10770,53,10752,37);
    private List<Integer> genres(Map<String,Object> room) {
        String value=Objects.toString(room.get("genres"),"");
        return value.isBlank()?List.of():Arrays.stream(value.split(",")).map(Integer::valueOf).toList();
    }
    private final JdbcTemplate db;
    private final ObjectMapper mapper;
    private final String secret,catalogKey,catalog;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    MovieRooms(JdbcTemplate db,ObjectMapper mapper,@Value("${internal.secret}") String secret,
        @Value("${CATALOG_INTERNAL_SECRET}") String catalogKey,@Value("${CATALOG_URL:http://127.0.0.1:8181}") String catalog) {
        this.db=db;this.mapper=mapper;this.secret=secret;this.catalogKey=catalogKey;this.catalog=catalog;
    }
    @ModelAttribute
    void verify(HttpServletRequest request) {
        String key=request.getHeader("X-Internal-Key");
        if(key==null||!MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),key.getBytes(StandardCharsets.UTF_8)))fail(HttpStatus.UNAUTHORIZED,"Acesso interno necessário.");
        active(request.getHeader("X-Actor"));
    }
    private void active(String actor) {
        if(actor==null||db.queryForObject("select count(*) from profile where actor=? and active=true",Integer.class,actor)!=1)
            fail(HttpStatus.FORBIDDEN,"Conclua seu perfil para continuar.");
    }
    private static void fail(HttpStatus status,String text){throw new ResponseStatusException(status,text);}
    private Map<String,Object> room(String id,boolean lock) {
        var rows=db.queryForList("select * from movie_room where id=? and expires>CURRENT_TIMESTAMP"+(lock?" for update":""),id);
        if(rows.isEmpty())fail(HttpStatus.NOT_FOUND,"Sala inexistente ou expirada.");
        return rows.getFirst();
    }
    private boolean member(String id,String actor){return db.queryForObject("select count(*) from movie_room_member where room=? and actor=?",Integer.class,id,actor)==1;}
    private void requireMember(String id,String actor){if(!member(id,actor))fail(HttpStatus.FORBIDDEN,"Entre na sala pelo convite primeiro.");}
    private void owner(Map<String,Object> room,String actor){if(!actor.equals(room.get("owner")))fail(HttpStatus.FORBIDDEN,"Somente o anfitrião pode fazer isso.");}

    @GetMapping
    List<Map<String,Object>> mine(@RequestHeader("X-Actor") String actor){
        return db.queryForList("select r.id,r.name,r.status,(r.owner=?) as \"isOwner\",(m.actor is null) as invited from movie_room r left join movie_room_member m on m.room=r.id and m.actor=? left join movie_room_invitation i on i.room=r.id and i.recipient=? where r.expires>CURRENT_TIMESTAMP and (m.actor is not null or (i.recipient is not null and r.status='lobby')) order by r.created desc limit 100",actor,actor,actor);
    }
    @GetMapping("/{id}/friends")
    Map<String,Object> friends(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestParam(defaultValue="0") int offset){
        var r=room(id,false);requireMember(id,actor);
        if(offset<0||offset>100000)fail(HttpStatus.BAD_REQUEST,"Página inválida.");
        if(!r.get("status").equals("lobby"))fail(HttpStatus.CONFLICT,"A rodada já começou.");
        var rows=db.queryForList("select p.username,p.name,p.avatar,(m.actor is not null) as joined,(i.recipient is not null) as invited from friendship f join profile p on p.actor=case when f.requester=? then f.recipient else f.requester end left join movie_room_member m on m.room=? and m.actor=p.actor left join movie_room_invitation i on i.room=? and i.recipient=p.actor where (f.requester=? or f.recipient=?) and f.status='accepted' and p.active=true order by p.username limit 51 offset ?",actor,id,id,actor,actor,offset);
        return Map.of("items",rows.stream().limit(50).toList(),"hasMore",rows.size()>50);
    }
    @PostMapping("/{id}/invite") @Transactional
    Map<String,Boolean> invite(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody Invitation input){
        var r=room(id,true);requireMember(id,actor);
        if(!r.get("status").equals("lobby"))fail(HttpStatus.CONFLICT,"A rodada já começou.");
        if(input.username()==null||input.username().length()>32)fail(HttpStatus.BAD_REQUEST,"Usuário inválido.");
        var targets=db.queryForList("select p.actor from friendship f join profile p on p.actor=case when f.requester=? then f.recipient else f.requester end where (f.requester=? or f.recipient=?) and f.status='accepted' and p.active=true and p.username=?",String.class,actor,actor,actor,input.username());
        if(targets.isEmpty())fail(HttpStatus.FORBIDDEN,"Você só pode convidar seus amigos.");
        String target=targets.getFirst();
        if(member(id,target))return Map.of("joined",true);
        if(db.queryForObject("select count(*) from movie_room_invitation where room=? and recipient=?",Integer.class,id,target)>0)return Map.of("invited",true);
        if(db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id)>=8)fail(HttpStatus.CONFLICT,"A sala já tem 8 participantes.");
        if(db.queryForObject("select count(*) from movie_room_invitation where room=?",Integer.class,id)>=50)fail(HttpStatus.TOO_MANY_REQUESTS,"Esta sala já tem 50 convites. Compartilhe o link para convidar mais pessoas.");
        db.update("insert into movie_room_invitation(room,recipient,sender) values(?,?,?)",id,target,actor);
        return Map.of("invited",true);
    }
    @DeleteMapping("/{id}") @Transactional
    Map<String,Boolean> delete(@RequestHeader("X-Actor") String actor,@PathVariable String id){
        var r=room(id,true);owner(r,actor);
        db.update("delete from movie_room where id=?",id);
        return Map.of("deleted",true);
    }
    @PostMapping
    @Transactional
    Map<String,String> create(@RequestHeader("X-Actor") String actor,@RequestBody Create input){
        active(actor);
        db.queryForList("select actor from profile where actor=? for update",actor);
        if(db.queryForObject("select count(*) from movie_room where owner=? and expires>CURRENT_TIMESTAMP",Integer.class,actor)>=10)fail(HttpStatus.CONFLICT,"Você já tem 10 salas ativas. Aguarde uma sala expirar.");
        String name=input.name()==null?"":input.name().strip();
        if(name.isEmpty()||name.length()>80)fail(HttpStatus.BAD_REQUEST,"Informe um nome de até 80 caracteres.");
        String id=UUID.randomUUID().toString();
        db.update("insert into movie_room(id,owner,name,expires) values(?,?,?,?)",id,actor,name,OffsetDateTime.now(ZoneOffset.UTC).plusDays(7));
        db.update("insert into movie_room_member(room,actor) values(?,?)",id,actor);
        return Map.of("id",id);
    }
    @PostMapping("/{id}/join")
    @Transactional
    Map<String,String> join(@RequestHeader("X-Actor") String actor,@PathVariable String id){
        var r=room(id,true);
        if(member(id,actor))return Map.of("id",id);
        if(!r.get("status").equals("lobby"))fail(HttpStatus.CONFLICT,"A rodada já começou. Peça uma nova sala ao anfitrião.");
        if(db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id)>=8)fail(HttpStatus.CONFLICT,"A sala já tem 8 participantes.");
        db.update("insert into movie_room_member(room,actor) values(?,?)",id,actor);
        return Map.of("id",id);
    }
    @PutMapping("/{id}/settings") @Transactional
    Map<String,Object> settings(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody Settings input){
        var r=room(id,true);owner(r,actor);
        if(!r.get("status").equals("lobby"))fail(HttpStatus.CONFLICT,"O gênero só pode mudar antes do início. Crie outra sala para trocar.");
        List<Integer> selected=input.genres();
        if(selected==null) {
            if(input.genre()==null)fail(HttpStatus.BAD_REQUEST,"Informe os gêneros.");
            selected=input.genre()==0?List.of():List.of(input.genre());
        }
        if(selected.size()>GENRES.size()||selected.stream().anyMatch(g->g==null||!GENRES.contains(g)))fail(HttpStatus.BAD_REQUEST,"Gênero inválido.");
        selected=selected.stream().distinct().sorted().toList();
        int legacy=selected.isEmpty()?0:selected.getFirst();
        db.update("update movie_room set genre=?,genres=? where id=?",legacy,selected.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")),id);
        return Map.of("genre",legacy,"genres",selected);
    }
    @PostMapping("/{id}/start")
    @Transactional
    Map<String,String> start(@RequestHeader("X-Actor") String actor,@PathVariable String id){
        var r=room(id,true);owner(r,actor);
        if(!r.get("status").equals("lobby"))return Map.of("id",id);
        int count=db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id);
        if(count<2)fail(HttpStatus.CONFLICT,"Convide pelo menos mais uma pessoa antes de começar.");
        var ids=db.queryForList("select distinct l.title from library l join movie_room_member m on m.actor=l.actor join profile p on p.actor=m.actor where m.room=? and l.interest=true and p.active=true",String.class,id);
        Collections.shuffle(ids);
        List<Map<String,Object>> deck=new ArrayList<>();
        // Bound upstream requests; a round contains at most 60 films.
        if(ids.size()>300)ids=new ArrayList<>(ids.subList(0,300));
        try {
            for(int i=0;i<ids.size()&&deck.size()<60;i+=30){
                String group=String.join(",",ids.subList(i,Math.min(i+30,ids.size())));
                if(!group.matches("[a-z0-9,\\-]+"))throw new IllegalArgumentException();
                var response=http.send(HttpRequest.newBuilder(URI.create(catalog+"/title-batch?ids="+group+"&genres="+Objects.toString(r.get("genres"),""))).header("X-Internal-Key",catalogKey).timeout(Duration.ofSeconds(25)).GET().build(),HttpResponse.BodyHandlers.ofString());
                if(response.statusCode()!=200)throw new IllegalStateException();
                for(var title:mapper.readTree(response.body()))if(title.path("format").asText().equals("movie")){
                    Map<String,Object> film=new LinkedHashMap<>();
                    for(String field:List.of("id","name","year","summary","poster"))film.put(field,title.path(field).isNull()?null:title.path(field).asText());
                    if(ids.contains(film.get("id"))&&deck.stream().noneMatch(t->t.get("id").equals(film.get("id"))))deck.add(film);
                    if(deck.size()==60)break;
                }
            }
        }catch(Exception failure){if(failure instanceof InterruptedException)Thread.currentThread().interrupt();fail(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível carregar os filmes. Tente iniciar novamente.");}
        if(deck.isEmpty()){addSuggestions(id);db.update("update movie_room set status='voting' where id=?",id);return Map.of("id",id);}
        Collections.shuffle(deck);
        for(int i=0;i<deck.size();i++){
            var film=deck.get(i);
            try {db.update("insert into movie_room_title(room,title,position,snapshot) values(?,?,?,?)",id,film.get("id"),i,mapper.writeValueAsString(film));}
            catch(com.fasterxml.jackson.core.JsonProcessingException impossible){throw new IllegalStateException(impossible);}
        }
        db.update("update movie_room set status='voting' where id=?",id);
        return Map.of("id",id);
    }
    private void addSuggestions(String id) {
        var excluded=db.queryForList("select title from movie_room_title where room=?",String.class,id);
        if(excluded.size()>=1000)fail(HttpStatus.CONFLICT,"Esta sala atingiu o limite de filmes. Crie outra sala.");
        int page=db.queryForObject("select suggestion_page from movie_room where id=?",Integer.class,id);
        if(page>499)fail(HttpStatus.CONFLICT,"Não há mais sugestões nesta sala.");
        var watched=db.queryForList("select distinct l.title from library l join movie_room_member m on m.actor=l.actor where m.room=? and l.watched=true limit 10000",String.class,id);
        var liked=db.queryForList("select title from movie_room_vote where room=? and liked=true group by title order by count(*) desc limit 40",String.class,id);
        try {
            var payload=mapper.writeValueAsString(Map.of("exclude",excluded,"watched",watched,"liked",liked,"page",page,"genres",genres(room(id,false))));
            var response=http.send(HttpRequest.newBuilder(URI.create(catalog+"/suggestions")).header("X-Internal-Key",catalogKey).header("Content-Type","application/json").timeout(Duration.ofSeconds(25)).POST(HttpRequest.BodyPublishers.ofString(payload)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()!=200)throw new IllegalStateException();
            var data=mapper.readTree(response.body());int position=excluded.size();
            var added=new HashSet<>(excluded);
            for(var film:data.path("items"))if(film.path("format").asText().equals("movie")&&added.add(film.path("id").asText())){
                db.update("insert into movie_room_title(room,title,position,snapshot) values(?,?,?,?)",id,film.path("id").asText(),position++,film.toString());
            }
            db.update("update movie_room set suggestion_page=? where id=?",data.path("nextPage").asInt(page+2),id);
            if(position==excluded.size())fail(HttpStatus.CONFLICT,"Nenhuma sugestão nova encontrada. Tente outra rodada mais tarde.");
        }catch(ResponseStatusException e){throw e;}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();fail(HttpStatus.SERVICE_UNAVAILABLE,"Sugestões indisponíveis. Seus votos foram preservados; tente novamente.");}
    }
    @PostMapping("/{id}/more")
    @Transactional
    Map<String,String> more(@RequestHeader("X-Actor") String actor,@PathVariable String id){
        var r=room(id,true);owner(r,actor);
        if(!r.get("status").equals("voting"))fail(HttpStatus.CONFLICT,"A sala não está em votação.");
        int titles=db.queryForObject("select count(*) from movie_room_title where room=?",Integer.class,id);
        int members=db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id);
        int votes=db.queryForObject("select count(*) from movie_room_vote where room=?",Integer.class,id);
        if(votes<titles*members)fail(HttpStatus.CONFLICT,"Aguarde todos terminarem a rodada.");
        addSuggestions(id);return Map.of("id",id);
    }
    @GetMapping("/{id}")
    @Transactional
    Map<String,Object> state(@RequestHeader("X-Actor") String actor,@PathVariable String id){
        var r=room(id,true);requireMember(id,actor);
        var result=new LinkedHashMap<String,Object>();
        result.put("id",id);result.put("name",r.get("name"));result.put("status",r.get("status"));result.put("isOwner",actor.equals(r.get("owner")));result.put("chosen",r.get("chosen"));
        var members=db.queryForList("select case when p.active then p.username else '' end as username,case when p.active then p.name else 'Conta indisponível' end as name,case when p.active then p.avatar else '' end as avatar,p.active,(select count(*) from movie_room_vote v where v.room=m.room and v.actor=m.actor) as voted from movie_room_member m join profile p on p.actor=m.actor where m.room=? order by m.joined,p.username",id);
        for(var participant:members)participant.put("isSelf",Objects.equals(participant.get("username"),db.queryForObject("select username from profile where actor=?",String.class,actor)));
        result.put("members",members);result.put("genre",r.get("genre"));result.put("genres",genres(r));
        var titles=db.queryForList("select snapshot from movie_room_title where room=? order by position",String.class,id);
        result.put("titles",titles.stream().map(s->{try{return mapper.readTree(s);}catch(Exception e){throw new IllegalStateException(e);}}).toList());
        result.put("votes",db.queryForList("select title,liked from movie_room_vote where room=? and actor=?",id,actor));
        result.put("matches",matches(id,members.size()));
        var reconsiderations=new ArrayList<Map<String,Object>>();
        if(r.get("status").equals("voting"))for(String title:reconsiderationTitles(id,actor,members.size())){
            var supporters=db.queryForList("select case when p.active then p.username else '' end as username,case when p.active then p.name else 'Conta indisponível' end as name,case when p.active then p.avatar else '' end as avatar,p.active from movie_room_vote v join profile p on p.actor=v.actor where v.room=? and v.title=? and v.liked=true order by p.username",id,title);
            for(var supporter:supporters)supporter.put("isSelf",Objects.equals(supporter.get("username"),db.queryForObject("select username from profile where actor=?",String.class,actor)));
            reconsiderations.add(Map.of("title",title,"supporters",supporters));
        }
        result.put("reconsiderations",reconsiderations);
        return result;
    }
    private List<String> reconsiderationTitles(String id,String actor,int size){
        return db.queryForList("select v.title from movie_room_vote v join movie_room_title t on t.room=v.room and t.title=v.title where v.room=? and v.actor=? and v.liked=false and v.reconsidered=false and (select count(*) from movie_room_vote a where a.room=v.room and a.title=v.title)=? and (select count(*) from movie_room_vote a where a.room=v.room and a.title=v.title and a.liked=true)>? order by t.position",String.class,id,actor,size,size/2);
    }
    @PutMapping("/{id}/reconsider/{title}") @Transactional
    Map<String,Boolean> reconsider(@RequestHeader("X-Actor") String actor,@PathVariable String id,@PathVariable String title,@RequestBody Vote input){
        var r=room(id,true);requireMember(id,actor);
        if(!r.get("status").equals("voting"))fail(HttpStatus.CONFLICT,"Esta sala não está recebendo votos.");
        if(input.liked()==null)fail(HttpStatus.BAD_REQUEST,"Informe seu voto.");
        var previous=db.queryForList("select liked,reconsidered from movie_room_vote where room=? and actor=? and title=?",id,actor,title);
        if(!previous.isEmpty()&&Boolean.TRUE.equals(previous.getFirst().get("reconsidered"))){
            if(input.liked().equals(previous.getFirst().get("liked")))return Map.of("saved",true);
            fail(HttpStatus.CONFLICT,"Você já respondeu à segunda rodada.");
        }
        int size=db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id);
        if(!reconsiderationTitles(id,actor,size).contains(title))fail(HttpStatus.CONFLICT,"Este filme não está aguardando sua reconsideração.");
        db.update("update movie_room_vote set liked=?,reconsidered=true where room=? and actor=? and title=?",input.liked(),id,actor,title);
        return Map.of("saved",true);
    }
    private List<String> matches(String id,int size){
        return db.queryForList("select title from movie_room_vote where room=? and liked=true group by title having count(*)=? order by title",String.class,id,size);
    }
    @PutMapping("/{id}/votes/{title}")
    @Transactional
    Map<String,Boolean> vote(@RequestHeader("X-Actor") String actor,@PathVariable String id,@PathVariable String title,@RequestBody Vote input){
        var r=room(id,true);requireMember(id,actor);
        if(!r.get("status").equals("voting"))fail(HttpStatus.CONFLICT,"Esta sala não está recebendo votos.");
        if(input.liked()==null||db.queryForObject("select count(*) from movie_room_title where room=? and title=?",Integer.class,id,title)!=1)fail(HttpStatus.BAD_REQUEST,"Filme ou voto inválido.");
        var settled=db.queryForList("select liked from movie_room_vote where room=? and actor=? and title=? and reconsidered=true",Boolean.class,id,actor,title);
        if(!settled.isEmpty()){
            if(settled.getFirst().equals(input.liked()))return Map.of("saved",true);
            fail(HttpStatus.CONFLICT,"Você já respondeu à segunda rodada.");
        }
        if(db.update("update movie_room_vote set liked=? where room=? and actor=? and title=?",input.liked(),id,actor,title)==0)
            db.update("insert into movie_room_vote(room,actor,title,liked) values(?,?,?,?)",id,actor,title,input.liked());
        return Map.of("saved",true);
    }
    @PostMapping("/{id}/choose/{title}")
    @Transactional
    Map<String,String> choose(@RequestHeader("X-Actor") String actor,@PathVariable String id,@PathVariable String title){
        var r=room(id,true);owner(r,actor);
        if(r.get("status").equals("chosen")&&title.equals(r.get("chosen")))return Map.of("title",title);
        int count=db.queryForObject("select count(*) from movie_room_member where room=?",Integer.class,id);
        if(!r.get("status").equals("voting")||!matches(id,count).contains(title))fail(HttpStatus.CONFLICT,"Escolha um filme que todos curtiram.");
        db.update("update movie_room set status='chosen',chosen=? where id=?",title,id);
        return Map.of("title",title);
    }
}
