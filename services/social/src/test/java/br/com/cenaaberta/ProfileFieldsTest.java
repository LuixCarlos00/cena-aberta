package br.com.cenaaberta;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.web.server.ResponseStatusException;
class ProfileFieldsTest {
 @Test void acceptsSpacesAccentsAndSymbols(){
  assertEquals("maro fã / anime #1!",ProfileFields.username("  Maro  Fã / Anime #1!  "));
  assertEquals(ProfileFields.username("José"),ProfileFields.username("Jose\u0301"));
 }
 @Test void rejectsInvisibleControlsAndInvalidLengths(){
  for(String s:new String[]{"ab"," ","a".repeat(33),"maro\nuser","maro\u202euser"})
   assertThrows(ResponseStatusException.class,()->ProfileFields.username(s));
 }
}
