import com.android.apksig.ApkVerifier;
import com.personal.memostarter.PackageIntegrity;
import java.io.File;
import java.util.*;
public class VerifyApk {
 public static void main(String[] args) throws Exception {
  var result=new ApkVerifier.Builder(new File(args[0])).build().verify();
  if(!result.isVerified()) { System.out.println("APK_SIGNATURE_INVALID"); System.exit(2); }
  var certs=new ArrayList<byte[]>(); for(var cert:result.getSignerCertificates()) certs.add(cert.getEncoded());
  String verdict=PackageIntegrity.verify(new File(args[0]),certs,new HashSet<>(Arrays.asList(args[1].split(","))),"com.personal.memostarter",342);
  System.out.println(verdict); if(!verdict.equals("OK"))System.exit(3);
 }
}
