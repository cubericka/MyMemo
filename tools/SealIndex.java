import java.nio.file.*;
import java.security.*;
import java.security.interfaces.RSAPublicKey;
import java.util.*;
public class SealIndex {
 public static void main(String[] args) throws Exception {
  char[] password=Objects.requireNonNull(System.getenv("MYMEMO_STORE_PASSWORD"),"Missing MYMEMO_STORE_PASSWORD").toCharArray();
  KeyStore store=KeyStore.getInstance("PKCS12"); try(var in=Files.newInputStream(Path.of(args[0]))) { store.load(in,password); }
  var cert=store.getCertificate(args[1]);
  if(!(cert.getPublicKey() instanceof RSAPublicKey) || ((RSAPublicKey)cert.getPublicKey()).getModulus().bitLength()<2048) throw new Exception("RSA >=2048 required");
  String pin=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(cert.getEncoded()));
  if(!pin.equals(args[2])) throw new Exception("Selected signer differs from approved certificate pin");
  Signature s=Signature.getInstance("SHA256withRSA"); s.initSign((PrivateKey)store.getKey(args[1],password)); s.update(Files.readAllBytes(Path.of(args[3])));
  Files.write(Path.of(args[4]),s.sign()); Files.write(Path.of(args[5]),cert.getEncoded()); Arrays.fill(password,'\0');
 }
}
