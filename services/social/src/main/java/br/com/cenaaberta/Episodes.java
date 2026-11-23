package br.com.cenaaberta;

import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import static br.com.cenaaberta.Guards.*;

/** Notas por episódio e médias por temporada. */
@RestController
public class Episodes {

    public record EpisodeRating(Integer rating) {}

    private final JdbcTemplate db;
    private final ActiveActors actors;
    private final CatalogSeasons seasons;
    private final LibraryEntries entries;

    Episodes(JdbcTemplate db,ActiveActors actors,CatalogSeasons seasons,LibraryEntries entries) {
        this.db=db; this.actors=actors; this.seasons=seasons; this.entries=entries;
    }

    @GetMapping("/me/episodes/{title}")
    List<Map<String,Object>> mySeasonAverages(@RequestHeader("X-Actor") String actor,@PathVariable String title) {
        actors.requireActive(actor);
        validateTitle(title);
        return db.queryForList("""
            select season, avg(cast(rating as decimal(8,4)))/2 as average
            from episode_rating where actor=? and title=? group by season order by season
            """,actor,title);
    }

    @GetMapping("/me/episodes/{title}/{season}")
    List<Map<String,Object>> episodeRatings(@RequestHeader("X-Actor") String actor,
            @PathVariable String title,@PathVariable int season) {
        actors.requireActive(actor);
        return db.queryForList("select episode,rating from episode_rating where actor=? and title=? and season=?",actor,title,season);
    }

    @PutMapping("/me/episodes/{title}/{season}/{episode}")
    @Transactional
    void rateEpisode(@RequestHeader("X-Actor") String actor,@PathVariable String title,
            @PathVariable int season,@PathVariable int episode,@RequestBody EpisodeRating input) {
        actors.lockActive(actor);
        validateTitle(title);
        if(season<0||season>1000||episode<1||input.rating()!=null&&(input.rating()<1||input.rating()>10))
            fail(HttpStatus.BAD_REQUEST,"Episódio ou nota inválidos.");
        boolean found=false;
        for(var item:seasons.season(title,season))if(item.path("number").asInt()==episode)found=true;
        if(!found)fail(HttpStatus.NOT_FOUND,"Episódio não encontrado.");
        db.update("delete from episode_rating where actor=? and title=? and season=? and episode=?",actor,title,season,episode);
        if(input.rating()!=null) {
            db.update("insert into episode_rating values(?,?,?,?,?)",actor,title,season,episode,input.rating());
            if(!Rows.exists(db,"select count(*) from library where actor=? and title=?",actor,title))
                db.update("insert into library(actor,title,evaluation_public,spoiler) values(?,?,true,false)",actor,title);
            entries.markWatched(actor,title);
        }
    }

}
