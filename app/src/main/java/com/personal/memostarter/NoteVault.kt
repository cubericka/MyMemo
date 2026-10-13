package com.personal.memostarter

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class UnlockRequired(val root: Long) : IllegalStateException("메모 비밀번호를 입력하세요")
class WrongPin : IllegalArgumentException("비밀번호가 일치하지 않습니다")
interface DeviceWrap { fun seal(data: ByteArray, aad: ByteArray): String; fun open(data: String, aad: ByteArray): ByteArray }
class KeystoreWrap : DeviceWrap {
 private val alias = "mymemo.pin.envelope.v1"
 @Synchronized private fun key(create: Boolean): SecretKey {
  val store=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
  if(store.containsAlias(alias)) return store.getKey(alias,null) as SecretKey
  check(create) { "기기의 암호화 키를 찾을 수 없습니다. 앱을 삭제하거나 데이터를 초기화하지 마세요." }
  return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
   init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
  }.generateKey()
 }
 override fun seal(data: ByteArray,aad: ByteArray)=VaultCrypto.seal(key(true),data,aad)
 override fun open(data: String,aad: ByteArray): ByteArray = try { VaultCrypto.open(key(false),data,aad) } catch(e: Exception) { throw IllegalStateException("기기의 암호화 키를 사용할 수 없습니다. 데이터는 삭제하지 않았습니다.",e) }
}
object VaultCrypto {
 private val random=SecureRandom()
 fun random(size: Int)=ByteArray(size).also(random::nextBytes)
 fun encode(bytes: ByteArray)=Base64.getEncoder().encodeToString(bytes)
 fun decode(text: String)=Base64.getDecoder().decode(text)
 fun seal(key: SecretKey,bytes: ByteArray,aad: ByteArray): String {
  val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad)
  return encode(cipher.iv+cipher.doFinal(bytes))
 }
 fun open(key: SecretKey,data: String,aad: ByteArray): ByteArray {
  val bytes=decode(data);require(bytes.size>=28);val cipher=Cipher.getInstance("AES/GCM/NoPadding")
  cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));cipher.updateAAD(aad)
  return cipher.doFinal(bytes.copyOfRange(12,bytes.size))
 }
 fun pinKey(pin: String,salt: ByteArray): SecretKey {
  require(pin.matches(Regex("[0-9]{4}"))) { "숫자 4자리를 입력하세요" }
  val spec=PBEKeySpec(pin.toCharArray(),salt,210_000,256)
  return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,"AES") } finally { spec.clearPassword() }
 }
}
class NoteVault(private val device: DeviceWrap = KeystoreWrap()) {
 private val keys=mutableMapOf<Long,ByteArray>();val revision=MutableStateFlow(0L)
 @Synchronized fun has(root: Long)=keys.containsKey(root)
 @Synchronized fun cache(root: Long,key: ByteArray) { keys.remove(root)?.fill(0);keys[root]=key.copyOf();revision.value++ }
 @Synchronized fun clear() { keys.values.forEach { it.fill(0) };keys.clear();revision.value++ }
 @Synchronized fun forget(root: Long) { keys.remove(root)?.fill(0);revision.value++ }
 @Synchronized private fun key(root: Long)=SecretKeySpec(keys[root] ?: throw UnlockRequired(root),"AES")
 fun rewrap(group: LockGroup,newPin: String): LockGroup {
  val bytes=key(group.rootId).encoded
  return try { envelope(group.rootId,newPin,bytes) } finally { bytes.fill(0) }
 }
 fun envelope(root: Long,pin: String,key: ByteArray): LockGroup {
  val salt=VaultCrypto.random(16);val aad="mymemo.pin.v1:$root".toByteArray()
  val pinBox=VaultCrypto.seal(VaultCrypto.pinKey(pin,salt),key,aad)
  return LockGroup(root,VaultCrypto.encode(salt),device.seal(pinBox.toByteArray(),aad))
 }
 fun unlock(group: LockGroup,pin: String): ByteArray {
  val aad="mymemo.pin.v1:${group.rootId}".toByteArray()
  val pinBox=device.open(group.wrappedKey,aad).toString(Charsets.UTF_8)
  return try { VaultCrypto.open(VaultCrypto.pinKey(pin,VaultCrypto.decode(group.salt)),pinBox,aad) } catch(e: AEADBadTagException) { throw WrongPin() }
 }
 fun seal(noteId: Long,root: Long,draft: Draft,keyBytes: ByteArray? = null): String {
  val json=JSONObject().put("title",draft.title).put("body",draft.body).put("template",draft.template).put("details",JSONObject(draft.details))
  json.put("checks",JSONArray().apply { draft.checks.forEach { put(JSONObject().put("id",it.id).put("text",it.text).put("done",it.done).put("sort",it.sortOrder)) } })
  return VaultCrypto.seal(keyBytes?.let { SecretKeySpec(it,"AES") } ?: key(root),json.toString().toByteArray(),"mymemo.note.v1:$root:$noteId".toByteArray())
 }
 fun read(note: Note): Draft {
  val root=requireNotNull(note.lockRootId);val json=JSONObject(VaultCrypto.open(key(root),requireNotNull(note.sealedContent),"mymemo.note.v1:$root:${note.id}".toByteArray()).toString(Charsets.UTF_8))
  val array=json.getJSONArray("checks");val checks=(0 until array.length()).map { val item=array.getJSONObject(it);CheckItem(item.getString("id"),note.id,item.getString("text"),item.getBoolean("done"),item.getInt("sort")) }
  return Draft(json.getString("title"),json.getString("body"),checks,json.getString("template"),TemplateDetails.decode(json.getJSONObject("details").toString()))
 }
 fun project(note: Note): Note {
  if(note.lockRootId==null || !has(note.lockRootId))return note
  return runCatching { val d=read(note);note.copy(title=d.title,body=d.body,template=d.template,details=TemplateDetails.encode(d.details)) }.getOrDefault(note)
 }
}
