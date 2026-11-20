package br.com.cenaaberta;



import java.net.URI;

import java.net.URLEncoder;

import java.net.http.*;

import java.nio.charset.StandardCharsets;

import java.security.MessageDigest;

import java.time.Duration;

import java.text.Normalizer;

import java.util.*;

import com.fasterxml.jackson.databind.*;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;

import org.springframework.boot.*;

import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.http.HttpStatus;

import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.server.ResponseStatusException;



@SpringBootApplication

@RestController

public class Catalog {

    public record Title(String id, String format, boolean anime, String name, String original,

                        String year, String summary, String poster, Details details) {

        Title(String id, String format, boolean anime, String name, String original,

              String year, String summary, String poster) {

            this(id,format,anime,name,original,year,summary,poster,null);

        }

        Title withDetails(Details details) {

            return new Title(id,format,anime,name,original,year,summary,poster,details);

        }

    }

    public record Details(List<String> genres, Integer runtime, Integer seasons, Integer episodes,

                          List<String> directors, List<String> creators, List<String> cast) {}

    private final JdbcTemplate db;

    private final ObjectMapper mapper;

    private final String secret, token;

    private final String apiBase;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    private final List<Title> fixtures = List.of(

        new Title("demo-ultimo-rolo","movie",false,"O Último Rolo","O Último Rolo","2024","Uma projecionista procura o último filme de uma sala prestes a fechar.",null),

        new Title("demo-mare-alta","tv",false,"Maré Alta","Maré Alta","2025","O retorno de uma fotógrafa põe em movimento os segredos de uma cidade litorânea.",null),

        new Title("demo-lua-de-papel","movie",true,"Lua de Papel","Kami no Tsuki","2023","Uma aprendiz de cartógrafa desenha um caminho para casa num céu em constante mudança.",null),

        new Title("demo-neon-norte","tv",true,"Neon Norte","Neon North","2025","Mensageiros cruzam de bicicleta uma cidade que só desperta depois da meia-noite.",null),

        new Title("demo-fim-da-linha","movie",false,"Fim da Linha","Fim da Linha","2022","Duas pessoas dividem o último ônibus e um plano para recomeçar.",null),

        new Title("demo-arquipelago","tv",false,"Arquipélago","Arquipélago","2024","Uma equipe de rádio documenta a vida em ilhas separadas pela névoa.",null)

    );



    public static void main(String[] args) { SpringApplication.run(Catalog.class, args); }



    Catalog(JdbcTemplate db, ObjectMapper mapper, @Value("${internal.secret}") String secret,

            @Value("${TMDB_TOKEN:}") String token,

            @Value("${tmdb.api-base:https://api.themoviedb.org/3}") String apiBase) {

        this.db = db; this.mapper = mapper; this.secret = secret; this.token = token.trim(); this.apiBase = apiBase;

        if (secret.length() < 24) throw new IllegalArgumentException("INTERNAL_SECRET precisa de pelo menos 24 caracteres");

    }



    @ModelAttribute

    void verify(HttpServletRequest request) {

        if (request.getRequestURI().equals("/health")) return;

        String actual = request.getHeader("X-Internal-Key");

        if (actual == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8)))

            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

    }



    @GetMapping("/health") Map<String,String> health() { db.queryForObject("select 1", Integer.class); return Map.of("status","ok"); }



    private static final Map<Integer,String> GENRES=Map.ofEntries(Map.entry(28,"Ação"),Map.entry(12,"Aventura"),Map.entry(16,"Animação"),Map.entry(35,"Comédia"),Map.entry(80,"Crime"),Map.entry(99,"Documentário"),Map.entry(18,"Drama"),Map.entry(10751,"Família"),Map.entry(14,"Fantasia"),Map.entry(36,"História"),Map.entry(27,"Terror"),Map.entry(10402,"Música"),Map.entry(9648,"Mistério"),Map.entry(10749,"Romance"),Map.entry(878,"Ficção científica"),Map.entry(10770,"Cinema TV"),Map.entry(53,"Thriller"),Map.entry(10752,"Guerra"),Map.entry(37,"Faroeste"));
    record Suggestions(List<String> exclude,List<String> watched,List<String> liked,int page,Integer genre,List<Integer> genres) {}
    private static List<Integer> selectedGenres(Integer genre,List<Integer> genres) {
        var selected=genres!=null?genres:genre==null||genre==0?List.<Integer>of():List.of(genre);
        if(selected.size()>GENRES.size()||selected.stream().anyMatch(g->g==null||!GENRES.containsKey(g)))throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        return selected.stream().distinct().toList();
    }
    @PostMapping("/suggestions")
    Map<String,Object> suggestions(@RequestBody Suggestions input) {
        if(input.page()<1||input.page()>499||input.exclude()==null||input.exclude().size()>1000||input.watched()==null||input.watched().size()>10000||input.liked()==null||input.liked().size()>1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var genres=selectedGenres(input.genre(),input.genres());
        var excluded=new HashSet<>(input.exclude());var watched=new HashSet<>(input.watched());
        var preferred=new HashSet<String>();
        for(String id:input.liked().stream().distinct().limit(40).toList()) {
            var rows=db.queryForList("select details from title where id=?",String.class,id);
            for(String json:rows)if(json!=null)try {for(var preferredGenre:mapper.readTree(json).path("genres"))preferred.add(preferredGenre.asText());}catch(Exception ignored){}
        }
        if(token.isBlank())return Map.of("items",fixtures.stream().filter(t->t.format().equals("movie")&&!excluded.contains(t.id())&&(genres.isEmpty()||genres.stream().anyMatch(g->demoDetails(t.id()).genres().contains(GENRES.get(g))))).limit(20).toList(),"nextPage",input.page()+2);
        var buckets=new ArrayList<List<JsonNode>>();
        String today=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String year=java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusMonths(12).toString();
        String[] sorts={"primary_release_date.desc&primary_release_date.gte="+year+"&vote_count.gte=30","vote_average.desc&vote_count.gte=300","popularity.desc&vote_count.gte=30"};
        for(String sort:sorts){
            var bucket=new ArrayList<JsonNode>();
            for(int page=input.page();page<input.page()+2;page++)for(var item:remote("/discover/movie?include_adult=false&include_video=false&primary_release_date.lte="+today+"&sort_by="+sort+"&page="+page+(genres.isEmpty()?"":"&with_genres="+genres.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("%7C")))).path("results")) {
                String date=item.path("release_date").asText();
                if(!date.isBlank()&&date.compareTo(today)<=0&&!item.path("adult").asBoolean()&&!excluded.contains("tmdb-movie-"+item.path("id").asLong()))bucket.add(item);
            }
            bucket.sort(java.util.Comparator.<JsonNode>comparingDouble(item->{
                String id="tmdb-movie-"+item.path("id").asLong();
                double votes=item.path("vote_count").asDouble(),rating=item.path("vote_average").asDouble();
                double weighted=(votes*rating+300*6.5)/(votes+300);
                // Cached genre affinity is a bonus; unseen movies always take precedence.
                boolean affinity=false;
                for(String json:db.queryForList("select details from title where id=?",String.class,id))if(json!=null)try{for(var g:mapper.readTree(json).path("genres"))if(preferred.contains(g.asText()))affinity=true;}catch(Exception ignored){}
                return (watched.contains(id)?0:100)+weighted+(affinity?1.5:0);
            }).reversed());
            buckets.add(bucket);
        }
        var result=new ArrayList<Title>();var selected=new HashSet<>(excluded);
        int[] quotas={8,8,4};
        for(int b=0;b<3;b++){int taken=0;for(var item:buckets.get(b)){Title title=mapped("movie",item);if(selected.add(title.id())){storeSummary(title);result.add(title);if(++taken==quotas[b])break;}}}
        for(var bucket:buckets)for(var item:bucket){if(result.size()>=20)break;Title title=mapped("movie",item);if(selected.add(title.id())){storeSummary(title);result.add(title);}}
        Collections.shuffle(result);
        return Map.of("items",result,"nextPage",input.page()+2);
    }

    @GetMapping("/releases") List<Title> releases(){
        if(token.isBlank())return List.of();
        String today=java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        String since=java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(90).toString();
        var result=new ArrayList<Title>();
        for(var item:remote("/discover/movie?include_adult=false&include_video=false&region=BR&with_release_type=2%7C3&release_date.gte="+since+"&release_date.lte="+today+"&sort_by=popularity.desc").path("results")){
            if(item.path("adult").asBoolean()||item.path("poster_path").asText("").isBlank()||item.path("poster_path").isNull())continue;
            result.add(mapped("movie",item));if(result.size()==6)break;
        }
        return result;
    }

    @GetMapping("/titles")

    Map<String,Object> titles(@RequestParam(defaultValue="") String query, @RequestParam(defaultValue="all") String type,

                              @RequestParam(defaultValue="1") int page) {

        if (page < 1 || page > 500 || query.length() > 100 || !Set.of("all","movie","tv","anime").contains(type))

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        if (token.isBlank()) {

            var found = fixtures.stream().filter(t -> type.equals("all") || type.equals(t.format()) || type.equals("anime") && t.anime())

                .filter(t -> normalize(t.name()).contains(normalize(query)) || normalize(t.original()).contains(normalize(query))).toList();

            return Map.of("items",page == 1 ? found : List.of(),"hasMore",false,"demo",true);

        }

        List<Title> found = new ArrayList<>();

        boolean more = false;

        for (String format : type.equals("movie") || type.equals("tv") ? List.of(type) : List.of("movie","tv")) {

            String params = "&page=" + page + "&include_adult=false";

            String path = query.isBlank() ? "/discover/" + format + "?sort_by=popularity.desc" + (type.equals("anime") ? "&with_genres=16&with_original_language=ja" : "") :

                "/search/" + format + "?query=" + URLEncoder.encode(query,StandardCharsets.UTF_8);

            JsonNode result = remote(path + params);

            more |= page < Math.min(result.path("total_pages").asInt(), 500);

            for (JsonNode item : result.path("results")) {

                Title title = mapped(format,item);

                if (!type.equals("anime") || title.anime()) { storeSummary(title); found.add(title); }

            }

        }

        return Map.of("items",found,"hasMore",more,"demo",false);

    }



    @GetMapping("/titles/{id}/seasons/{season}")

    List<Map<String,Object>> episodes(@PathVariable String id,@PathVariable int season) {

        if(season<0 || season>1000 || !"tv".equals(detail(id).format()))

            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        if(id.startsWith("demo-")) {

            if(season==2 && id.equals("demo-mare-alta"))
                return List.of(Map.of("number",1,"name","O retorno","summary","Episódio ilustrativo."));
            if(season!=1)throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            return List.of(
                Map.of("number",1,"name","Piloto","summary","Episódio ilustrativo."),
                Map.of("number",2,"name","Segundo episódio","summary","Episódio ilustrativo."));

        }

        JsonNode data=remote("/tv/"+id.substring("tmdb-tv-".length())+"/season/"+season);

        List<Map<String,Object>> result=new ArrayList<>();

        for(JsonNode episode:data.path("episodes"))

            result.add(Map.of("number",episode.path("episode_number").asInt(),

                "name",episode.path("name").asText(),"summary",episode.path("overview").asText()));

        return result;

    }



    @GetMapping("/titles/{id}") Title detail(@PathVariable String id) {

        var sample = fixtures.stream().filter(t -> t.id().equals(id)).findFirst();

        if (sample.isPresent()) return sample.get().withDetails(demoDetails(id));

        var stored = db.query("select * from title where id=?", (row,n) -> {

            Title title = new Title(row.getString("id"),row.getString("format"),row.getBoolean("anime"),row.getString("name"),row.getString("original"),row.getString("release_year"),row.getString("summary"),row.getString("poster"));

            String details = row.getString("details");

            if (details == null) return title;

            try { return title.withDetails(mapper.readValue(details,Details.class)); }

            catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { return title; }

        },id);

        if (!stored.isEmpty() && (stored.getFirst().details()!=null || token.isBlank())) return stored.getFirst();

        if (token.isBlank() || !id.matches("tmdb-(movie|tv)-[1-9][0-9]{0,12}")) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

        String[] parts = id.split("-");

        Title title;

        try {

            JsonNode item=remote("/" + parts[1] + "/" + parts[2] + "?append_to_response=credits");

            title=mapped(parts[1],item).withDetails(details(parts[1],item));

        } catch (ResponseStatusException unavailable) {

            if (!stored.isEmpty()) return stored.getFirst();

            throw unavailable;

        }

        String details=mapper.valueToTree(title.details()).toString();

        if (!stored.isEmpty()) {

            db.update("update title set details=?,name=?,original=?,release_year=?,summary=?,poster=?,anime=? where id=?",

                details,title.name(),title.original(),title.year(),title.summary(),title.poster(),title.anime(),id);

            return title;

        }

        try {

            db.update("insert into title(id,format,anime,name,original,release_year,summary,poster,details) values(?,?,?,?,?,?,?,?,?)",title.id(),title.format(),title.anime(),title.name(),title.original(),title.year(),title.summary(),title.poster(),details);

        } catch (org.springframework.dao.DuplicateKeyException concurrentInsert) {

            return detail(id);

        }

        return title;

    }



    private static Details demoDetails(String id) {

        return switch (id) {

            case "demo-ultimo-rolo" -> new Details(List.of("Drama"),96,null,null,List.of(),List.of(),List.of());

            case "demo-mare-alta" -> new Details(List.of("Drama","Mistério"),null,2,3,List.of(),List.of(),List.of());

            case "demo-lua-de-papel" -> new Details(List.of("Animação","Fantasia"),104,null,null,List.of(),List.of(),List.of());

            case "demo-neon-norte" -> new Details(List.of("Animação","Aventura"),null,1,12,List.of(),List.of(),List.of());

            case "demo-fim-da-linha" -> new Details(List.of("Drama"),88,null,null,List.of(),List.of(),List.of());

            default -> new Details(List.of("Drama"),null,1,6,List.of(),List.of(),List.of());

        };

    }



    private static Details details(String format, JsonNode item) {

        boolean movie=format.equals("movie");

        JsonNode credits=item.path("credits");

        return new Details(names(item.path("genres"),null,8),movie?positive(item.path("runtime")):null,

            movie?null:positive(item.path("number_of_seasons")),movie?null:positive(item.path("number_of_episodes")),

            movie?names(credits.path("crew"),"Director",5):List.of(),names(item.path("created_by"),null,5),names(credits.path("cast"),null,8));

    }



    private static Integer positive(JsonNode value) { return value.asInt()>0?value.asInt():null; }



    private static List<String> names(JsonNode items,String job,int limit) {

        var names=new LinkedHashSet<String>();

        for(JsonNode item:items) {

            if(job!=null && !job.equals(item.path("job").asText())) continue;

            String name=item.path("name").asText("").strip();

            if(!name.isEmpty())names.add(name);

            if(names.size()==limit)break;

        }

        return List.copyOf(names);

    }



    private void storeSummary(Title title) {
        if(db.queryForObject("select count(*) from title where id=?",Integer.class,title.id())>0)return;
        try {
            db.update("insert into title(id,format,anime,name,original,release_year,summary,poster) values(?,?,?,?,?,?,?,?)",
                title.id(),title.format(),title.anime(),title.name(),title.original(),title.year(),title.summary(),title.poster());
        } catch(org.springframework.dao.DuplicateKeyException concurrentInsert) { /* Another request already cached it. */ }
    }

    @GetMapping("/title-batch") List<Title> batch(@RequestParam List<String> ids,@RequestParam(defaultValue="0") int genre,@RequestParam(required=false) List<Integer> genres) {

        var selected=selectedGenres(genre,genres);
        if (ids.size() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);

        var output = new ArrayList<Title>();

        for (String id : ids.stream().distinct().toList()) {

            fixtures.stream().filter(t -> t.id().equals(id)).findFirst().ifPresent(output::add);

        }

        var storedIds = ids.stream().distinct().filter(id -> id.matches("tmdb-(movie|tv)-[1-9][0-9]{0,12}")).toList();

        if (!storedIds.isEmpty()) {

            String placeholders = String.join(",", Collections.nCopies(storedIds.size(), "?"));

            output.addAll(db.query("select * from title where id in (" + placeholders + ")",

                (row,n) -> new Title(row.getString("id"),row.getString("format"),row.getBoolean("anime"),row.getString("name"),row.getString("original"),row.getString("release_year"),row.getString("summary"),row.getString("poster")),storedIds.toArray()));

        }

        var missing=storedIds.stream().filter(id -> output.stream().noneMatch(t -> t.id().equals(id))).toList();
        if(!missing.isEmpty()&&!token.isBlank()) {
            try(var executor=java.util.concurrent.Executors.newFixedThreadPool(6)) {
                var tasks=missing.stream().map(id -> executor.submit(() -> {
                    try { return detail(id); }
                    catch(ResponseStatusException failure) {
                        if(failure.getStatusCode().value()==404)return null;
                        throw failure;
                    }
                })).toList();
                for(var task:tasks) {
                    try { Title title=task.get();if(title!=null)output.add(title); }
                    catch(InterruptedException interrupted) { Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Consulta interrompida."); }
                    catch(java.util.concurrent.ExecutionException failure) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível carregar todos os títulos. Tente novamente."); }
                }
            }
        }
        if(!selected.isEmpty()) {
            var filtered=new ArrayList<Title>();
            try(var executor=java.util.concurrent.Executors.newFixedThreadPool(6)) {
                var tasks=output.stream().map(t->executor.submit(()->detail(t.id()))).toList();
                for(var task:tasks)try{var t=task.get();if(t.details()!=null&&selected.stream().anyMatch(g->t.details().genres().contains(GENRES.get(g))))filtered.add(t);}catch(Exception e){if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Não foi possível verificar os gêneros. Tente novamente.");}
            }
            return filtered;
        }
        return output;

    }



    private JsonNode remote(String path) {

        try {

            String separator = path.contains("?") ? "&" : "?";

            var response = http.send(HttpRequest.newBuilder(URI.create(apiBase + path + separator + "language=pt-BR"))

                .timeout(Duration.ofSeconds(3)).header("Authorization","Bearer " + token).GET().build(),HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 404) throw new ResponseStatusException(HttpStatus.NOT_FOUND);

            if (response.statusCode() == 401 || response.statusCode() == 403)

                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"O acesso ao TMDB precisa ser configurado.");

            if (response.statusCode() == 429)

                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"O TMDB limitou as consultas. Tente novamente em instantes.");

            if (response.statusCode() != 200) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo externo indisponível.");

            return mapper.readTree(response.body());

        } catch (InterruptedException interrupted) {

            Thread.currentThread().interrupt();

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Consulta interrompida.");

        } catch (java.io.IOException failure) {

            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Catálogo externo indisponível.");

        }

    }



    private static Title mapped(String format, JsonNode item) {

        boolean animation = false;

        for (JsonNode genre : item.path("genre_ids")) animation |= genre.asInt() == 16;

        for (JsonNode genre : item.path("genres")) animation |= genre.path("id").asInt() == 16;

        String date = item.path(format.equals("movie") ? "release_date" : "first_air_date").asText("");

        String original = item.path(format.equals("movie") ? "original_title" : "original_name").asText("");

        String name = item.path(format.equals("movie") ? "title" : "name").asText("");

        String poster = item.path("poster_path").asText("");

        return new Title("tmdb-" + format + "-" + item.path("id").asLong(),format,animation && item.path("original_language").asText().equals("ja"),

            name.isBlank() ? original.isBlank() ? "Título indisponível" : original : name, original,

            date.length() >= 4 ? date.substring(0,4) : "",item.path("overview").asText(""),

            poster.matches("/[A-Za-z0-9._-]+") ? "https://image.tmdb.org/t/p/w500" + poster : null);

    }



    private static String normalize(String input) {

        return Normalizer.normalize(input,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);

    }

}
