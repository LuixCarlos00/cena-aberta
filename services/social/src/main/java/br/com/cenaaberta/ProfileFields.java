package br.com.cenaaberta;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

final class ProfileFields {
    private static final String JPEG = "data:image/jpeg;base64,";
    private static final Set<String> AVATAR_PRESETS = Set.of("preset:cinema","preset:estrelas","preset:fantasia","preset:espaco","preset:misterio","preset:animacao","preset:ninja","preset:samurai","preset:robo","preset:mago","preset:series","preset:retro");
    private ProfileFields() {}

    static String username(String value) {
        if(value==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um usuário.");
        String normalized=java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).strip().toLowerCase(java.util.Locale.ROOT).replaceAll(" +"," ");
        if(normalized.length()<3||normalized.length()>32||normalized.codePoints().anyMatch(c -> Character.isISOControl(c)||Character.getType(c)==Character.FORMAT))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Use de 3 a 32 caracteres visíveis. Espaços e símbolos são permitidos.");
        return normalized;
    }

    static String avatar(String value) {
        if(value==null || value.isEmpty()) return value;
        if(AVATAR_PRESETS.contains(value)) return value;
        if(!value.startsWith(JPEG) || value.length()>100_000) throw invalidAvatar();
        try {
            byte[] bytes=Base64.getDecoder().decode(value.substring(JPEG.length()));
            try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers=ImageIO.getImageReaders(input);
                if(!readers.hasNext()) throw invalidAvatar();
                var reader=readers.next();
                try {
                    if(!reader.getFormatName().equalsIgnoreCase("JPEG")) throw invalidAvatar();
                    input.seek(0);
                    reader.setInput(input);
                    if(reader.getWidth(0)>256 || reader.getHeight(0)>256) throw invalidAvatar();
                    var output=new ByteArrayOutputStream();
                    if(!ImageIO.write(reader.read(0),"JPEG",output)) throw invalidAvatar();
                    return JPEG+Base64.getEncoder().encodeToString(output.toByteArray());
                } finally { reader.dispose(); }
            }
        } catch(IOException | IllegalArgumentException failure) { throw invalidAvatar(); }
    }

    private static ResponseStatusException invalidAvatar() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem de perfil inválida.");
    }
}
