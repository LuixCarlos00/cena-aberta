package br.com.cenaaberta;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class LetterboxdImportTest {
 byte[] zip(Map<String,String> files)throws Exception{
  var output=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(output)){for(var file:files.entrySet()){zip.putNextEntry(new ZipEntry(file.getKey()));zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return output.toByteArray();
 }
 @Test void readsQuotedCsvDiaryListPreambleAndIgnoresDeleted()throws Exception{
  var files=new LinkedHashMap<String,String>();
  files.put("reviews.csv","Date,Name,Year,Rating,Review\n2025-01-01,Movie,2024,4.5,\"Good, indeed!\nSecond line <b>bold</b>\"\n");
  files.put("diary.csv","Name,Year,Watched Date,Rewatch\nMovie,2024,2025-02-01,Yes\n");
  files.put("lists/favorites.csv","Letterboxd list export v7\nDate,Name,Description\n2025-01-01,Favorites,Text\n\nPosition,Name,Year,URL\n1,Movie,2024,https://letterboxd.com/film/movie/\n");
  files.put("deleted/reviews.csv","Name,Year\nDeleted,2000\n");
  var data=LetterboxdImport.parse(new ByteArrayInputStream(zip(files)));
  var rows=(List<LetterboxdImport.Row>)data.get("rows");assertEquals(3,rows.size());
  assertEquals(9,rows.get(0).rating());assertEquals("Good, indeed!\nSecond line bold",rows.get(0).review());
  assertTrue(rows.get(1).rewatch());assertEquals("2025-02-01",rows.get(1).date());assertEquals("Favorites",rows.get(2).list());
 }
 @Test void rejectsInvalidRatingsAndExpansionLimit()throws Exception{
  var bad=zip(Map.of("ratings.csv","Name,Year,Rating\nMovie,2024,9\n"));
  assertThrows(ResponseStatusException.class,()->LetterboxdImport.parse(new ByteArrayInputStream(bad)));
  var huge=zip(Map.of("ignored.txt","x".repeat(20_000_001)));
  assertThrows(ResponseStatusException.class,()->LetterboxdImport.parse(new ByteArrayInputStream(huge)));
 }
 @Test void fillsMissingValuesPreservesExistingAndReimportDoesNotDuplicate()throws Exception{
  var source=new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa","");
  var migrations=new ResourceDatabasePopulator();for(String f:List.of("V1__social.sql","V6__viewing_time.sql","V8__public_evaluations.sql","V12__letterboxd_import.sql"))migrations.addScript(new ClassPathResource("db/migration/"+f));migrations.execute(source);
  var db=new JdbcTemplate(source);db.update("insert into profile(actor,username,name) values('a','test','Test')");
  db.update("insert into library(actor,title,rating,review) values('a','tmdb-movie-777',8,'Existing')");
  var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/title-batch",e->{byte[] b="[{\"id\":\"tmdb-movie-777\",\"format\":\"movie\"}]".getBytes(StandardCharsets.UTF_8);e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);e.close();});server.start();
  try{
   var service=new LetterboxdImport(db,new ObjectMapper(),"secret","http://127.0.0.1:"+server.getAddress().getPort(),"catalog-key");
   var rows=List.of(new LetterboxdImport.Row("diary","Movie","2024",10,"Imported","2025-01-01",false,"",1,"tmdb-movie-777"),new LetterboxdImport.Row("watchlist","Movie","2024",null,"","",false,"",1,"tmdb-movie-777"),new LetterboxdImport.Row("list","Movie","2024",null,"","",false,"Favorites",1,"tmdb-movie-777"));
   assertEquals(3,service.commit("a",new LetterboxdImport.Batch(rows)).get("imported"));
   assertEquals(3,service.commit("a",new LetterboxdImport.Batch(rows)).get("skipped"));
   assertEquals(8,db.queryForObject("select rating from library",Integer.class));assertEquals("Existing",db.queryForObject("select review from library",String.class));
   assertEquals(false,db.queryForObject("select interest from library",Boolean.class));
   assertEquals(1,db.queryForObject("select count(*) from viewing",Integer.class));assertEquals(1,db.queryForObject("select count(*) from collection_item",Integer.class));
  }finally{server.stop(0);}
 }
}
