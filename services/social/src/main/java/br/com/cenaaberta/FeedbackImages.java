package br.com.cenaaberta;
import java.util.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
final class FeedbackImages {
 static List<byte[]> decode(List<String> images){
  if(images==null)return List.of();if(images.size()>4)throw invalid();
  var result=new ArrayList<byte[]>();
  for(String value:images){
   if(value==null||!value.startsWith("data:image/jpeg;base64,")||value.length()>1_333_360)throw invalid();
   try{
    byte[] bytes=Base64.getDecoder().decode(value.substring(23));if(bytes.length>1_000_000)throw invalid();
    try(var input=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){
     var readers=ImageIO.getImageReaders(input);if(!readers.hasNext())throw invalid();var reader=readers.next();
     try{reader.setInput(input);int w=reader.getWidth(0),h=reader.getHeight(0);if(!reader.getFormatName().equalsIgnoreCase("JPEG")||w<1||h<1||w>4096||h>4096||(long)w*h>16_000_000)throw invalid();
      BufferedImage image=reader.read(0);var output=new ByteArrayOutputStream();ImageIO.write(image,"jpeg",output);byte[] clean=output.toByteArray();if(clean.length==0||clean.length>1_000_000)throw invalid();result.add(clean);
     }finally{reader.dispose();}
    }
   }catch(IOException|IllegalArgumentException e){throw invalid();}
  }
  return result;
 }
 private static ResponseStatusException invalid(){return new ResponseStatusException(HttpStatus.BAD_REQUEST,"Envie até 4 imagens de no máximo 1 MB cada, com até 4096 pixels por lado.");}
}
