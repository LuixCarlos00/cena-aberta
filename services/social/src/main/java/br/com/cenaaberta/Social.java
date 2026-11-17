package br.com.cenaaberta;



import java.net.URI;

import java.net.http.*;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;

import java.time.*;

import java.util.*;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.*;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.dao.DuplicateKeyException;

import org.springframework.http.HttpStatus;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;



@SpringBootApplication

@RestController

public class Social {

    private static final ZoneId DIARY_ZONE=ZoneId.of("America/Sao_Paulo");

    public record Profile(String username,String name,String bio,String cpf,String avatar) {}

    public record RankedFilm(String title,Integer tier) {}

    public record RankingChange(Integer version,List<RankedFilm> items) {}

    public record LibraryChange(Boolean value,Integer rating,String review,Boolean spoiler,Integer version) {}

    public record Viewing(String title,LocalDate date,boolean rewatch,

                          @JsonInclude(JsonInclude.Include.NON_NULL) OffsetDateTime watchedAt) {}

    public record CollectionChange(String name,String description,List<String> items,Integer version) {}

    private final JdbcTemplate db;

    private final ObjectMapper mapper;

    private final String secret, catalogKey, catalog;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();



    public static void main(String[] args) { SpringApplication.run(Social.class,args); }



    Social(JdbcTemplate db,ObjectMapper mapper,@Value("${internal.secret}") String secret,

           @Value("${CATALOG_INTERNAL_SECRET}") String catalogKey,

           @Value("${CATALOG_URL:http://127.0.0.1:8181}") String catalog) {

        this.db=db; this.mapper=mapper; this.secret=secret; this.catalogKey=catalogKey; this.catalog=catalog;

        if (secret.length()<24 || catalogKey.length()<24) throw new IllegalArgumentException("Chaves internas precisam de pelo menos 24 caracteres");

    }



    @ModelAttribute

    void verify(HttpServletRequest request) {

        if (request.getRequestURI().equals("/health")) return;

        String actual=request.getHeader("X-Internal-Key");

        if(actual==null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),actual.getBytes(StandardCharsets.UTF_8)))

            fail(HttpStatus.UNAUTHORIZED,"Acesso interno necessário.");

    }



    @GetMapping("/health") Map<String,String> health() { db.queryForObject("select 1",Integer.class); return Map.of("status","ok"); }



    @GetMapping("/me/ranking")

    Map<String,Object> ranking(@RequestHeader("X-Actor") String actor) {

        active(actor);

        return Map.of("version",db.queryForObject("select ranking_version from profile where actor=?",Integer.class,actor),

            "items",db.queryForList("select title,tier from film_ranking where actor=? order by tier,position",actor));

    }



    @PutMapping("/me/ranking")

    @Transactional

    Map<String,Object> saveRanking(@RequestHeader("X-Actor") String actor,@RequestBody RankingChange input) {

        lockActive(actor);

        Integer version=db.queryForObject("select ranking_version from profile where actor=?",Integer.class,actor);

        if(input.items()==null || input.items().size()>100) fail(HttpStatus.BAD_REQUEST,"Use até 100 obras.");
        if(!Objects.equals(version,input.version())) {
            if(input.version()!=null && input.version()==version-1 && input.items().stream()
                    .allMatch(item->item!=null && item.title()!=null && item.tier()!=null)) {
                var requested=input.items().stream().sorted(Comparator.comparing(RankedFilm::tier))
                    .map(item->Map.of("title",item.title(),"tier",item.tier())).toList();
                var current=ranking(actor);
                if(requested.equals(current.get("items")))return current;
            }
            fail(HttpStatus.CONFLICT,"Ranking alterado. Recarregue antes de editar.");
        }

        Set<String> used=new HashSet<>();

        List<String> previous=db.queryForList("select title from film_ranking where actor=?",String.class,actor);

        for(var item:input.items()) {

            if(item==null || item.tier()==null || item.tier()<0 || item.tier()>4 || !used.add(item.title()))

                fail(HttpStatus.BAD_REQUEST,"Ranking inválido.");

            if(!exists("select count(*) from library where actor=? and title=? and watched=true",actor,item.title()))

                fail(HttpStatus.BAD_REQUEST,"Adicione apenas obras marcadas como assistidas.");

            if(!previous.contains(item.title()))verifyTitle(item.title());

        }

        db.update("delete from film_ranking where actor=?",actor);

        int[] positions=new int[5];

        for(var item:input.items())db.update("insert into film_ranking values(?,?,?,?)",actor,item.title(),item.tier(),positions[item.tier()]++);

        db.update("update profile set ranking_version=ranking_version+1 where actor=?",actor);

        return ranking(actor);

    }



    public record TierChange(Integer tier) {}

    @PutMapping("/me/ranking/{title}")
    @Transactional
    Map<String,Object> rankTitle(@RequestHeader("X-Actor") String actor,@PathVariable String title,@RequestBody TierChange input) {
        lockActive(actor);
        validateTitle(title);
        Integer tier=input.tier();
        if(tier==null || tier< -1 || tier>4)fail(HttpStatus.BAD_REQUEST,"Categoria inválida.");
        var current=db.queryForList("select tier from film_ranking where actor=? and title=?",Integer.class,actor,title);
        if((current.isEmpty() && tier==-1) || (!current.isEmpty() && current.getFirst().equals(tier)))return ranking(actor);
        if(tier>=0) {
            if(!exists("select count(*) from library where actor=? and title=? and watched=true",actor,title))
                fail(HttpStatus.BAD_REQUEST,"Marque a obra como assistida para colocá-la no ranking.");
            if(current.isEmpty() && db.queryForObject("select count(*) from film_ranking where actor=?",Integer.class,actor)>=100)
                fail(HttpStatus.BAD_REQUEST,"Use até 100 obras.");
        }
        db.update("delete from film_ranking where actor=? and title=?",actor,title);
        if(tier>=0) {
            int position=db.queryForObject("select coalesce(max(position)+1,0) from film_ranking where actor=? and tier=?",Integer.class,actor,tier);
            db.update("insert into film_ranking(actor,title,tier,position) values(?,?,?,?)",actor,title,tier,position);
        }
        db.update("update profile set ranking_version=ranking_version+1 where actor=?",actor);
        return ranking(actor);
    }

    public record EpisodeRating(Integer rating) {}



    @GetMapping("/me/episodes/{title}")
    List<Map<String,Object>> mySeasonAverages(@RequestHeader("X-Actor") String actor,@PathVariable String title) {
        active(actor);
        validateTitle(title);
        return db.queryForList("""
            select season, avg(cast(rating as decimal(8,4)))/2 as average
            from episode_rating where actor=? and title=? group by season order by season
            """,actor,title);
    }

    @GetMapping("/me/episodes/{title}/{season}")

    List<Map<String,Object>> episodeRatings(@RequestHeader("X-Actor") String actor,

            @PathVariable String title,@PathVariable int season) {

        active(actor);

        return db.queryForList("select episode,rating from episode_rating where actor=? and title=? and season=?",actor,title,season);

    }



    @PutMapping("/me/episodes/{title}/{season}/{episode}")

    @Transactional

    void rateEpisode(@RequestHeader("X-Actor") String actor,@PathVariable String title,

            @PathVariable int season,@PathVariable int episode,@RequestBody EpisodeRating input) {

        lockActive(actor);

        validateTitle(title);

        if(season<0||season>1000||episode<1||input.rating()!=null&&(input.rating()<1||input.rating()>10))

            fail(HttpStatus.BAD_REQUEST,"Episódio ou nota inválidos.");

        try {

            var response=http.send(HttpRequest.newBuilder(URI.create(catalog+"/titles/"+title+"/seasons/"+season))

                .timeout(Duration.ofSeconds(5)).header("X-Internal-Key",catalogKey).GET().build(),HttpResponse.BodyHandlers.ofString());

            if(response.statusCode()!=200)fail(response.statusCode()==404?HttpStatus.NOT_FOUND:HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível consultar os episódios.");

            boolean found=false;

            for(var item:mapper.readTree(response.body()))if(item.path("number").asInt()==episode)found=true;

            if(!found)fail(HttpStatus.NOT_FOUND,"Episódio não encontrado.");

        } catch(InterruptedException e) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);

        } catch(java.io.IOException e) {

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE);

        }

        db.update("delete from episode_rating where actor=? and title=? and season=? and episode=?",actor,title,season,episode);

        if(input.rating()!=null) {
            db.update("insert into episode_rating values(?,?,?,?,?)",actor,title,season,episode,input.rating());
            if(!exists("select count(*) from library where actor=? and title=?",actor,title))
                db.update("insert into library(actor,title) values(?,?)",actor,title);
            mark(actor,title);
        }

    }



    @GetMapping("/me") Map<String,Object> me(@RequestHeader("X-Actor") String actor) {

        validateActor(actor);

        var profiles=db.queryForList("select username,name,bio,avatar,active from profile where actor=?",actor);

        if (profiles.isEmpty()) return Map.of("needsProfile",true);

        checkActive(profiles.getFirst());

        return dashboard(profiles.getFirst(),actor,true);

    }



    @PutMapping("/me/profile")

    Map<String,Object> profile(@RequestHeader("X-Actor") String actor,@RequestBody Profile input) {

        validateActor(actor);

        String username=required(input.username(),32).toLowerCase(Locale.ROOT);

        if (!username.matches("[a-z0-9_]{3,32}")) fail(HttpStatus.BAD_REQUEST,"Use de 3 a 32 letras, números ou sublinhados.");

        String name=required(input.name(),80), bio=optional(input.bio(),300);

        String cpf=input.cpf()==null||input.cpf().isBlank()?null:ProfileFields.cpf(input.cpf());

        String avatar=ProfileFields.avatar(input.avatar());

        try {

            int count=db.update("update profile set username=?,name=?,bio=?,cpf=coalesce(?,cpf),avatar=coalesce(?,avatar) where actor=? and active=true",username,name,bio,cpf,avatar,actor);

            if (count==0) {

                if (exists("select count(*) from profile where actor=?",actor)) fail(HttpStatus.FORBIDDEN,"Acesso suspenso.");

                if (cpf==null) fail(HttpStatus.BAD_REQUEST,"Informe seu CPF.");

                db.update("insert into profile(actor,username,name,bio,cpf,avatar) values(?,?,?,?,?,?)",actor,username,name,bio,cpf,avatar);

            }

        } catch (DuplicateKeyException duplicate) { fail(HttpStatus.CONFLICT,"Nome de usuário ou CPF já cadastrado."); }

        return me(actor);

    }



    @GetMapping("/profiles/{username}")

    Map<String,Object> publicProfile(@RequestHeader("X-Actor") String actor,@PathVariable String username) {

        active(actor);

        var target=one("select actor,username,name,bio,avatar from profile where username=? and active=true",username.toLowerCase(Locale.ROOT));

        return dashboard(target,(String)target.get("actor"),false);

    }



    @GetMapping("/titles/{id}/reviews")

    List<Map<String,Object>> reviews(@RequestHeader("X-Actor") String actor,@PathVariable String id) {

        active(actor);

        validateTitle(id);
        return publicReviews(id,episodic(id));
    }

    private List<Map<String,Object>> publicReviews(String id,boolean series) {
        return db.queryForList("select p.username,p.name,p.avatar,case when ? then null else l.rating end as rating,l.review,l.spoiler from library l join profile p on p.actor=l.actor where l.title=? and l.review is not null and l.evaluation_public=true and l.hidden=false and p.active=true order by p.username limit 50",series,id);

    }



    private boolean episodic(String id) {
        return id.startsWith("tmdb-tv-") || (id.startsWith("demo-") && "tv".equals(verifyTitle(id)));
    }

    @GetMapping("/titles/{id}/community")
    Map<String,Object> community(@RequestHeader("X-Actor") String actor,@PathVariable String id) {
        active(actor);
        validateTitle(id);
        boolean series=episodic(id);
        Map<String,Object> result;
        if(series) {
            var publicEpisodes=db.queryForList("""
                select e.actor,e.season,e.rating from episode_rating e
                join library l on l.actor=e.actor and l.title=e.title
                join profile p on p.actor=e.actor
                where e.title=? and l.evaluation_public=true and l.hidden=false and p.active=true
                """,id);
            var bySeason=new TreeMap<Integer,IntSummaryStatistics>();
            var viewers=new HashSet<>();
            for(var episode:publicEpisodes) {
                viewers.add(episode.get("actor"));
                bySeason.computeIfAbsent((Integer)episode.get("season"),key->new IntSummaryStatistics())
                    .accept((Integer)episode.get("rating"));
            }
            var seasons=bySeason.entrySet().stream().map(season->Map.<String,Object>of(
                "season",season.getKey(),"average",season.getValue().getAverage()/2)).toList();
            var average=bySeason.values().stream().mapToDouble(stats->stats.getAverage()/2).average();
            result=new HashMap<>();
            result.put("rating_count",viewers.size());
            result.put("episode_rating_count",publicEpisodes.size());
            result.put("average",average.isPresent()?average.getAsDouble():null);
            result.put("seasons",seasons);
        } else {
            result=new HashMap<>(db.queryForMap("""
                select count(l.rating) as rating_count, avg(cast(l.rating as decimal(8,4)))/2 as average
                from library l join profile p on p.actor=l.actor
                where l.title=? and l.evaluation_public=true and l.hidden=false and p.active=true
                """,id));
        }
        result.put("reviews",publicReviews(id,series));
        return result;
    }

    @PutMapping("/me/library/{id}/{action}")

    @Transactional

    Map<String,Object> library(@RequestHeader("X-Actor") String actor,@PathVariable String id,@PathVariable String action,@RequestBody LibraryChange input) {

        lockActive(actor);

        ensureLibrary(actor,id);

        var old=one("select * from library where actor=? and title=?",actor,id);

        switch(action) {

            case "watched" -> {

                if (input.value()==null) fail(HttpStatus.BAD_REQUEST,"Informe o estado.");

                if (input.value()) mark(actor,id);

                else if (!input.value()) {

                    db.update("update library set watched=false where actor=? and title=?",actor,id);

                    if(db.update("delete from film_ranking where actor=? and title=?",actor,id)>0)

                        db.update("update profile set ranking_version=ranking_version+1 where actor=?",actor);

                }

            }

            case "interest" -> {

                if (input.value()==null) fail(HttpStatus.BAD_REQUEST,"Informe o estado.");

                if(input.value() && Boolean.TRUE.equals(old.get("watched")))
                    fail(HttpStatus.CONFLICT,"A obra já está marcada como assistida.");
                db.update("update library set interest=? where actor=? and title=?",input.value(),actor,id);

            }

            case "rewatching" -> {

                if(input.value()==null) fail(HttpStatus.BAD_REQUEST,"Informe o estado.");

                db.update("update library set rewatching=? where actor=? and title=?",input.value(),actor,id);

                if(input.value()) mark(actor,id);

            }

            case "rating" -> {
                if(episodic(id))fail(HttpStatus.BAD_REQUEST,"Avalie os episódios para calcular a nota da série.");

                Integer value=input.rating();

                if (value!=null && (value<1 || value>10)) fail(HttpStatus.BAD_REQUEST,"Nota fora da escala.");

                db.update("update library set rating=? where actor=? and title=?",value,actor,id);

                if (value!=null) mark(actor,id);

            }

            case "visibility" -> {
                if(input.value()==null)fail(HttpStatus.BAD_REQUEST,"Informe a visibilidade da avaliação.");
                db.update("update library set evaluation_public=? where actor=? and title=?",input.value(),actor,id);
            }
            case "review" -> {

                String review=optional(input.review(),3000);

                if (input.version()==null || !input.version().equals(old.get("version"))) {
                    boolean alreadySaved=input.version()!=null && input.version()==((Integer)old.get("version"))-1
                        && Objects.equals(review.isBlank()?null:review,old.get("review"))
                        && Objects.equals(Boolean.TRUE.equals(input.spoiler()),old.get("spoiler"));
                    if(alreadySaved)break;
                    fail(HttpStatus.CONFLICT,"Resenha alterada. Atualize a página.");
                }

                db.update("update library set review=?,spoiler=?,version=version+1 where actor=? and title=?",review.isBlank()?null:review,Boolean.TRUE.equals(input.spoiler()),actor,id);

                if (!review.isBlank() && old.get("review")==null) {

                    mark(actor,id);

                    if (!Boolean.TRUE.equals(old.get("rewatching")) &&

                            !exists("select count(*) from viewing where actor=? and title=?",actor,id)) {

                        OffsetDateTime watchedAt=OffsetDateTime.now(DIARY_ZONE);

                        insertViewing(actor,id,watchedAt.toLocalDate(),false,watchedAt);

                    }

                }

            }

            default -> fail(HttpStatus.NOT_FOUND,"Ação desconhecida.");

        }

        return one("select title,watched,interest,rewatching,rating,review,spoiler,hidden,version,evaluation_public from library where actor=? and title=?",actor,id);

    }



    @PostMapping("/me/viewings")

    @Transactional

    Map<String,String> createViewing(@RequestHeader("X-Actor") String actor,@RequestHeader("Idempotency-Key") String key,@RequestBody Viewing input) {

        lockActive(actor);

        LocalDate date=input.watchedAt()==null?input.date():input.watchedAt().atZoneSameInstant(DIARY_ZONE).toLocalDate();

        checkDate(date);

        String prior=receipt(actor,"viewing",key,input);

        if (prior!=null) return Map.of("id",prior);

        ensureLibrary(actor,input.title());

        if (!input.rewatch() || !exists("select count(*) from library where actor=? and title=? and rewatching=true",actor,input.title()))

            fail(HttpStatus.CONFLICT,"Marque Vi novamente para registrar outra sessão.");

        String id=insertViewing(actor,input.title(),date,true,input.watchedAt());

        mark(actor,input.title());

        saveReceipt(actor,"viewing",key,input,id);

        return Map.of("id",id);

    }



    private String insertViewing(String actor,String title,LocalDate date,boolean rewatch,OffsetDateTime watchedAt) {

        String id=UUID.randomUUID().toString();

        db.update("insert into viewing(id,actor,title,watched_on,rewatch,watched_at) values(?,?,?,?,?,?)",

            id,actor,title,date,rewatch,watchedAt);

        return id;

    }



    @PutMapping("/me/viewings/{id}")

    @Transactional

    void editViewing(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody Viewing input) {

        lockActive(actor); checkDate(input.date());

        if (db.update("""

                update viewing set watched_at=case when watched_on=? then watched_at else null end,

                watched_on=?,rewatch=? where id=? and actor=?

                """,input.date(),input.date(),input.rewatch(),id,actor)==0)

            fail(HttpStatus.NOT_FOUND,"Registro não encontrado.");

    }



    @DeleteMapping("/me/viewings/{id}")

    @Transactional

    void deleteViewing(@RequestHeader("X-Actor") String actor,@PathVariable String id) {

        lockActive(actor);

        if (db.update("delete from viewing where id=? and actor=?",id,actor)==0) fail(HttpStatus.NOT_FOUND,"Registro não encontrado.");

    }



    @PostMapping("/me/lists")

    @Transactional

    Map<String,String> createList(@RequestHeader("X-Actor") String actor,@RequestHeader("Idempotency-Key") String key,@RequestBody CollectionChange input) {

        lockActive(actor);

        String prior=receipt(actor,"list",key,input);

        if (prior!=null) return Map.of("id",prior);

        if (exists("select case when count(*)>=30 then 1 else 0 end from collection where actor=?",actor))

            fail(HttpStatus.BAD_REQUEST,"Limite de 30 listas.");

        String id=UUID.randomUUID().toString();

        db.update("insert into collection(id,actor,name,description) values(?,?,?,?)",id,actor,required(input.name(),100),optional(input.description(),500));

        replaceItems(id,input.items());

        saveReceipt(actor,"list",key,input,id);

        return Map.of("id",id);

    }



    @PutMapping("/me/lists/{id}")

    @Transactional

    void updateList(@RequestHeader("X-Actor") String actor,@PathVariable String id,@RequestBody CollectionChange input) {

        lockActive(actor);

        var old=one("select version from collection where id=? and actor=?",id,actor);

        if (!Objects.equals(old.get("version"),input.version())) fail(HttpStatus.CONFLICT,"Lista alterada. Atualize a página.");

        db.update("update collection set name=?,description=?,version=version+1 where id=?",required(input.name(),100),optional(input.description(),500),id);

        replaceItems(id,input.items());

    }



    @DeleteMapping("/me/lists/{id}")

    @Transactional

    void deleteList(@RequestHeader("X-Actor") String actor,@PathVariable String id) {

        lockActive(actor);

        if (db.update("delete from collection where id=? and actor=?",id,actor)==0) fail(HttpStatus.NOT_FOUND,"Lista não encontrada.");

    }



    private void replaceItems(String id,List<String> items) {

        List<String> titles=items==null?List.of():items;

        if(titles.size()>30 || new HashSet<>(titles).size()!=titles.size()) fail(HttpStatus.BAD_REQUEST,"Use até 30 obras diferentes por lista.");

        var previous=db.queryForList("select title from collection_item where collection_id=?",String.class,id);

        for (String title:titles) if(!previous.contains(title)) verifyTitle(title);

        db.update("delete from collection_item where collection_id=?",id);

        for (int i=0;i<titles.size();i++) db.update("insert into collection_item values(?,?,?)",id,titles.get(i),i);

    }



    private Map<String,Object> dashboard(Map<String,Object> profile,String actor,boolean own) {

        var lists=db.queryForList("select id,name,description,version from collection where actor=? order by created desc limit 30",actor);

        var items=db.queryForList("select i.collection_id,i.title from collection_item i join collection c on c.id=i.collection_id where c.actor=? order by i.position",actor);

        for (var list:lists) list.put("items",items.stream().filter(item->item.get("collection_id").equals(list.get("id"))).map(item->item.get("title")).toList());

        var viewings=own?db.queryForList("select id,title,watched_on,rewatch,watched_at from viewing where actor=? order by watched_on desc nulls last,coalesce(watched_at,created) desc limit 100",actor):List.of();

        String library=own?

            "select title,watched,interest,rewatching,rating,review,spoiler,hidden,version,evaluation_public from library where actor=? order by title limit 1000":

            "select title,watched,rewatching,case when evaluation_public and not hidden then rating end as rating,case when evaluation_public and not hidden then review end as review,case when evaluation_public and not hidden then spoiler else false end as spoiler from library where actor=? and (watched=true or (evaluation_public and not hidden and (rating is not null or review is not null))) order by title limit 1000";

        return Map.of("profile",Map.of("username",profile.get("username"),"name",profile.get("name"),"bio",profile.get("bio"),

            "avatar",Objects.toString(profile.get("avatar"),"")),

            "library",db.queryForList(library,actor),"viewings",viewings,"lists",lists);

    }



    private void ensureLibrary(String actor,String id) {

        validateTitle(id);

        if (!exists("select count(*) from library where actor=? and title=?",actor,id)) {

            verifyTitle(id);

            db.update("insert into library(actor,title) values(?,?)",actor,id);

        }

    }



    private String verifyTitle(String id) {

        validateTitle(id);

        try {

            var response=http.send(HttpRequest.newBuilder(URI.create(catalog+"/titles/"+id)).timeout(Duration.ofSeconds(3))

                .header("X-Internal-Key",catalogKey).GET().build(),HttpResponse.BodyHandlers.ofString());

            int status=response.statusCode();

            if (status==404) fail(HttpStatus.NOT_FOUND,"Obra não encontrada.");

            if (status!=200) fail(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo indisponível.");

            return mapper.readTree(response.body()).path("format").asText();

        } catch (InterruptedException interrupted) {

            Thread.currentThread().interrupt(); fail(HttpStatus.SERVICE_UNAVAILABLE,"Consulta interrompida.");

        } catch (java.io.IOException failure) { fail(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo indisponível."); }

        throw new IllegalStateException("Catálogo indisponível.");

    }



    private void mark(String actor,String id) { db.update("update library set watched=true,interest=false where actor=? and title=?",actor,id); }

    private void active(String actor) { validateActor(actor); checkActive(one("select active from profile where actor=?",actor)); }

    private void lockActive(String actor) { validateActor(actor); checkActive(one("select active from profile where actor=? for update",actor)); }

    private static void checkActive(Map<String,Object> row) { if(!Boolean.TRUE.equals(row.get("active"))) fail(HttpStatus.FORBIDDEN,"Acesso suspenso."); }



    private String receipt(String actor,String action,String key,Object body) {

        if (key==null || !key.matches("[A-Za-z0-9_-]{8,80}")) fail(HttpStatus.BAD_REQUEST,"Chave de repetição inválida.");

        db.update("delete from request_receipt where actor=? and created<?",actor,OffsetDateTime.now(ZoneOffset.UTC).minusHours(24));

        var rows=db.queryForList("select body_hash,resource_id from request_receipt where actor=? and action=? and request_key=?",actor,action,key);

        if(rows.isEmpty()) return null;

        if(!hash(body).equals(rows.getFirst().get("body_hash"))) fail(HttpStatus.CONFLICT,"Chave já usada com outros dados.");

        return (String)rows.getFirst().get("resource_id");

    }

    private void saveReceipt(String actor,String action,String key,Object body,String id) {

        db.update("insert into request_receipt(actor,action,request_key,body_hash,resource_id) values(?,?,?,?,?)",actor,action,key,hash(body),id);

    }

    private String hash(Object body) {

        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(body))); }

        catch (java.security.NoSuchAlgorithmException | com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }

    }



    private boolean exists(String sql,Object... args) { return db.queryForObject(sql,Integer.class,args)>0; }

    private Map<String,Object> one(String sql,Object... args) {

        var rows=db.queryForList(sql,args);

        if(rows.isEmpty()) fail(HttpStatus.NOT_FOUND,"Registro não encontrado.");

        return rows.getFirst();

    }

    private static void validateActor(String actor) { if(actor==null || !actor.matches("[A-Za-z0-9_-]{8,160}")) fail(HttpStatus.UNAUTHORIZED,"Sessão inválida."); }

    private static void validateTitle(String id) { if(id==null || !id.matches("demo-[a-z0-9-]{1,60}|tmdb-(movie|tv)-[1-9][0-9]{0,12}")) fail(HttpStatus.BAD_REQUEST,"Identificador inválido."); }

    private static void checkDate(LocalDate date) { if(date!=null && date.isAfter(LocalDate.now(DIARY_ZONE))) fail(HttpStatus.BAD_REQUEST,"A data não pode estar no futuro."); }

    private static String required(String value,int limit) { String result=optional(value,limit); if(result.isBlank()) fail(HttpStatus.BAD_REQUEST,"Campo obrigatório."); return result; }

    private static String optional(String value,int limit) { String result=value==null?"":value.strip(); if(result.length()>limit) fail(HttpStatus.BAD_REQUEST,"Texto acima do limite de "+limit+" caracteres."); return result; }

    private static void fail(HttpStatus status,String message) { throw new ResponseStatusException(status,message); }

}
