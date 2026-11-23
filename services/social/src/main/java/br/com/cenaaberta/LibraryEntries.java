package br.com.cenaaberta;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import static br.com.cenaaberta.Guards.validateTitle;

/** Operações compartilhadas sobre a linha da biblioteca de um usuário (diário, episódios e biblioteca). */
@Component
class LibraryEntries {

    private final JdbcTemplate db;
    private final TitleVerifier titles;

    LibraryEntries(JdbcTemplate db,TitleVerifier titles) { this.db=db; this.titles=titles; }

    /** Garante a linha da biblioteca, verificando a obra no catálogo antes de criá-la; actor e titleId identificam a linha. */
    void ensure(String actor,String titleId) {
        validateTitle(titleId);
        if (!Rows.exists(db,"select count(*) from library where actor=? and title=?",actor,titleId)) {
            titles.verify(titleId);
            db.update("insert into library(actor,title,evaluation_public,spoiler) values(?,?,true,false)",actor,titleId);
        }
    }

    /** Marca a obra como assistida e a tira do Vou ver. */
    void markWatched(String actor,String titleId) { db.update("update library set watched=true,interest=false where actor=? and title=?",actor,titleId); }

    /** Diz se a obra é uma série; ids tmdb-tv-* ou demo-* que o catálogo informa como "tv". */
    boolean episodic(String titleId) {
        return titleId.startsWith("tmdb-tv-") || (titleId.startsWith("demo-") && "tv".equals(titles.verify(titleId)));
    }
}
