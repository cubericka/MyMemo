package com.personal.memostarter

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONObject
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec

class AppUnlockRequired : IllegalStateException("앱 PIN을 입력하세요")

/** Device-bound envelope: neither PIN nor master key is stored in plaintext. */
class AppPinLock(private val prefs: SharedPreferences, private val device: DeviceWrap = KeystoreWrap()) {
 private val aad="mymemo.app.pin.v1".toByteArray()
 private var master: ByteArray?=null
 private fun record()=prefs.getString("record",null)?.let(::JSONObject)
 val enabled=MutableStateFlow(runCatching { record()?.getBoolean("enabled") ?: false }.getOrDefault(true))
 val unlocked=MutableStateFlow(!enabled.value)
 @Synchronized fun requireOpen() { if(!unlocked.value)throw AppUnlockRequired() }
 private fun commit(r: JSONObject) { check(prefs.edit().putString("record",r.toString()).commit()) { "잠금 설정 저장에 실패했습니다" } }
 private fun master(): ByteArray {
  requireOpen();master?.let { return it };check(!enabled.value) { "앱 PIN을 다시 입력하세요" }
  val r=record();val key=if(r==null)VaultCrypto.random(32) else device.open(r.getString("box"),aad)
  if(r==null)commit(JSONObject().put("enabled",false).put("box",device.seal(key,aad)).put("legacy",JSONObject()))
  master=key;return key
 }
 private fun writeMode(pin: String?) {
  val key=master();val r=record() ?: JSONObject().put("legacy",JSONObject())
  if(pin!=null) {
   val salt=VaultCrypto.random(16);val box=VaultCrypto.seal(VaultCrypto.pinKey(pin,salt),key,aad)
   r.put("salt",VaultCrypto.encode(salt)).put("box",device.seal(box.toByteArray(),aad))
  } else { r.remove("salt");r.put("box",device.seal(key,aad)) }
  r.put("enabled",pin!=null).put("failures",0).put("blockedUntil",0)
  commit(r);enabled.value=pin!=null;unlocked.value=true
 }
 @Synchronized fun enable(pin: String) { require(pin.matches(Regex("[0-9]{4}")));check(!enabled.value);writeMode(pin) }
 @Synchronized fun unlock(pin: String,now: Long=System.currentTimeMillis()) {
  require(pin.matches(Regex("[0-9]{4}")));check(enabled.value)
  val r=requireNotNull(record());val blocked=r.optLong("blockedUntil")
  require(now>=blocked) { "입력 횟수를 초과했습니다. ${(blocked-now)/1000+1}초 후 다시 시도하세요" }
  val box=device.open(r.getString("box"),aad).toString(Charsets.UTF_8)
  val key=try { VaultCrypto.open(VaultCrypto.pinKey(pin,VaultCrypto.decode(r.getString("salt"))),box,aad) }
  catch(e: AEADBadTagException) {
   val count=r.optInt("failures").coerceAtMost(100)+1
   val delay=if(count>=5)(30_000L shl (count-5).coerceAtMost(5)).coerceAtMost(900_000L) else 0L
   commit(r.put("failures",count).put("blockedUntil",now+delay));throw WrongPin()
  }
  try { commit(r.put("failures",0).put("blockedUntil",0)) } catch(e: Exception) { key.fill(0);throw e }
  master?.fill(0);master=key;unlocked.value=true
 }
 @Synchronized fun change(old: String,new: String) { require(new.matches(Regex("[0-9]{4}")));unlock(old);writeMode(new) }
 @Synchronized fun disable(pin: String) { unlock(pin);writeMode(null) }
 @Synchronized fun lock() { master?.fill(0);master=null;unlocked.value=!enabled.value }
 @Synchronized fun remember(root: Long,key: ByteArray) {
  val k=master();val r=requireNotNull(record());val legacy=r.optJSONObject("legacy") ?: JSONObject()
  legacy.put(root.toString(),VaultCrypto.seal(SecretKeySpec(k,"AES"),key,"legacy:$root".toByteArray()))
  commit(r.put("legacy",legacy))
 }
 @Synchronized fun recover(root: Long): ByteArray? {
  requireOpen();val box=record()?.optJSONObject("legacy")?.optString(root.toString()).orEmpty()
  if(box.isEmpty())return null
  return VaultCrypto.open(SecretKeySpec(master(),"AES"),box,"legacy:$root".toByteArray())
 }
}
