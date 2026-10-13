package com.personal.memostarter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateFactory;
import java.util.*;
import java.util.zip.*;

/** Shared by the Android adapter and the host APK tests. No network or data writes. */
public final class PackageIntegrity {
 public static final String INDEX = "assets/integrity.index";
 public static final String SIGNATURE = "assets/integrity.signature";
 public static String digest(byte[] data) throws Exception { return hex(MessageDigest.getInstance("SHA-256").digest(data)); }
 private static String hex(byte[] data) { StringBuilder out = new StringBuilder(); for(byte b:data) out.append(String.format(Locale.ROOT,"%02x", b & 255)); return out.toString(); }
 private static byte[] small(ZipFile zip, String name, int limit) throws Exception {
  ZipEntry e=zip.getEntry(name); if(e==null || e.getSize()<0 || e.getSize()>limit) throw new IOException("INDEX_MISSING_OR_SIZE");
  try(InputStream in=zip.getInputStream(e); ByteArrayOutputStream out=new ByteArrayOutputStream()) {
   byte[] buf=new byte[4096]; int n; while((n=in.read(buf))!=-1) { if(out.size()+n>limit) throw new IOException("INDEX_SIZE"); out.write(buf,0,n); } return out.toByteArray();
  }
 }
 public static String verify(File apk, List<byte[]> currentCertificates, Set<String> allowedPins, String appId, long version) {
  try {
   // Only explicitly approved CURRENT signer; history alone never grants access.
   if(currentCertificates.size()!=1 || !allowedPins.contains(digest(currentCertificates.get(0)))) return "SIGNER";
   java.security.cert.Certificate cert=CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(currentCertificates.get(0)));
   try(ZipFile zip=new ZipFile(apk)) {
    Map<String,ZipEntry> actual=new TreeMap<>(); Set<String> names=new HashSet<>();
    Enumeration<? extends ZipEntry> es=zip.entries();
    while(es.hasMoreElements()) { ZipEntry e=es.nextElement(); if(!names.add(e.getName())) return "DUPLICATE_ENTRY"; if(!e.isDirectory() && !e.getName().equals(INDEX) && !e.getName().equals(SIGNATURE)) actual.put(e.getName(),e); }
    byte[] index=small(zip,INDEX,2*1024*1024); byte[] signature=small(zip,SIGNATURE,8192);
    Signature verifier=Signature.getInstance("SHA256withRSA"); verifier.initVerify(cert.getPublicKey()); verifier.update(index);
    if(!verifier.verify(signature)) return "INDEX_SIGNATURE";
    String[] lines=new String(index,StandardCharsets.UTF_8).split("\n",-1);
    if(lines.length<4 || !lines[0].equals("MYMEMO-INTEGRITY-1") || !lines[1].equals(appId+"\t"+version) || !lines[lines.length-1].isEmpty()) return "INDEX_FORMAT";
    Map<String,String> expected=new TreeMap<>();
    for(int i=2;i<lines.length-1;i++) { String[] pair=lines[i].split("\t",-1); if(pair.length!=2 || !pair[0].matches("[0-9a-f]{64}") || expected.put(pair[1],pair[0])!=null) return "INDEX_FORMAT"; }
    if(!expected.keySet().equals(actual.keySet()) || !actual.containsKey("classes.dex") || !actual.containsKey("AndroidManifest.xml") || !actual.containsKey("resources.arsc")) return "ENTRY_SET";
    long total=0; byte[] buf=new byte[32768];
    for(Map.Entry<String,ZipEntry> item:actual.entrySet()) {
     ZipEntry e=item.getValue(); if(e.getSize()<0 || e.getSize()>256L*1024*1024) return "ENTRY_SIZE";
     MessageDigest hash=MessageDigest.getInstance("SHA-256"); long size=0;
     try(InputStream in=zip.getInputStream(e)) { int n; while((n=in.read(buf))!=-1) { size+=n; total+=n; if(size>256L*1024*1024 || total>512L*1024*1024) return "ENTRY_SIZE"; hash.update(buf,0,n); } }
     if(size!=e.getSize() || !hex(hash.digest()).equals(expected.get(item.getKey()))) return "CONTENT_HASH";
    }
    return "OK";
   }
  } catch(Exception e) { return "UNREADABLE"; }
 }
}
