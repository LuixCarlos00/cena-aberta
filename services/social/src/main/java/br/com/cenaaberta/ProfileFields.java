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
    private static final Set<String> AVATAR_PRESETS = Set.of("preset:cinema","preset:estrelas","preset:fantasia","preset:espaco","preset:misterio","preset:animacao");
    private ProfileFields() {}

    static String cpf(String value) {
        String input=value.strip();
        if(!input.matches("[0-9]{11}|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}"))
            throw invalidCpf();
        String digits=input.replaceAll("[.-]","");
        if(digits.chars().distinct().count()==1) throw invalidCpf();
        for(int length=9;length<=10;length++) {
            int sum=0;
            for(int i=0;i<length;i++) sum+=(digits.charAt(i)-'0')*(length+1-i);
            int check=(sum*10)%11;
            if((check==10?0:check)!=digits.charAt(length)-'0') throw invalidCpf();
        }
        return digits;
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

    private static ResponseStatusException invalidCpf() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Informe um CPF válido.");
    }

    private static ResponseStatusException invalidAvatar() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Imagem de perfil inválida.");
    }
}
