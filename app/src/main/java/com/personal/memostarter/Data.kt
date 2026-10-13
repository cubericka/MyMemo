package com.personal.memostarter

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "notes", indices = [Index("parent_id")])
data class Note(
 @PrimaryKey(autoGenerate = true) val id: Long = 0,
 @ColumnInfo(name = "parent_id") val parentId: Long? = null,
 val title: String = "", val body: String = "",
 @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
 @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
 val pinned: Boolean = false,
 @ColumnInfo(name = "sort_order") val sortOrder: Long = 0,
 @ColumnInfo(name = "trashed_at") val trashedAt: Long? = null,
 @ColumnInfo(name = "trash_batch") val trashBatch: String? = null,
 @ColumnInfo(defaultValue = "'quick'") val template: String = "quick",
 @ColumnInfo(defaultValue = "'{}'") val details: String = "{}",
 @ColumnInfo(name = "lock_root_id") val lockRootId: Long? = null,
 @ColumnInfo(name = "sealed_content") val sealedContent: String? = null
) { val displayTitle get() = title.ifBlank { "제목 없음" } }
@Entity(tableName = "checks", foreignKeys = [ForeignKey(entity = Note::class, parentColumns = ["id"], childColumns = ["note_id"], onDelete = ForeignKey.CASCADE)], indices = [Index("note_id")])
data class CheckItem(@PrimaryKey val id: String, @ColumnInfo(name = "note_id") val noteId: Long, val text: String, val done: Boolean = false, @ColumnInfo(name = "sort_order") val sortOrder: Int = 0)
@Entity(tableName = "lock_groups")
data class LockGroup(@PrimaryKey @ColumnInfo(name = "root_id") val rootId: Long, val salt: String, @ColumnInfo(name = "wrapped_key") val wrappedKey: String, val failures: Int = 0, @ColumnInfo(name = "blocked_until") val blockedUntil: Long = 0)
@Dao interface MemoDao {
 @Query("SELECT * FROM lock_groups WHERE root_id=:id") suspend fun lockGroup(id: Long): LockGroup?
 @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLock(group: LockGroup)
 @Query("DELETE FROM lock_groups WHERE root_id=:id") suspend fun eraseLock(id: Long)
 @Query("SELECT * FROM notes") fun observeNotes(): Flow<List<Note>>
 @Query("SELECT * FROM checks ORDER BY sort_order") fun observeChecks(): Flow<List<CheckItem>>
 @Query("SELECT * FROM notes") suspend fun all(): List<Note>
 @Query("SELECT * FROM notes WHERE id=:id") suspend fun get(id: Long): Note?
 @Query("SELECT * FROM checks WHERE note_id=:id ORDER BY sort_order") suspend fun checks(id: Long): List<CheckItem>
 @Query("SELECT * FROM checks ORDER BY sort_order") suspend fun allChecks(): List<CheckItem>
 @Insert suspend fun insert(note: Note): Long
 @Update suspend fun update(note: Note)
 @Query("UPDATE notes SET title=:title,body=:body,template=:template,details=:details,updated_at=:time WHERE id=:id AND trashed_at IS NULL") suspend fun content(id: Long, title: String, body: String, template: String, details: String, time: Long): Int
 @Query("DELETE FROM checks WHERE note_id=:id") suspend fun clearChecks(id: Long)
 @Insert suspend fun insertChecks(items: List<CheckItem>)
 @Query("DELETE FROM notes WHERE id=:id") suspend fun erase(id: Long)
}
@Database(entities = [Note::class, CheckItem::class, LockGroup::class], version = 4, exportSchema = true)
abstract class MemoDatabase : RoomDatabase() {
 abstract fun dao(): MemoDao
 companion object {
  // Legacy SQLiteOpenHelper v1 -> Room v2. No destructive fallback.
  val MIGRATION_1_2 = object : Migration(1, 2) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("CREATE TABLE notes_new (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, parent_id INTEGER, title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, pinned INTEGER NOT NULL, sort_order INTEGER NOT NULL, trashed_at INTEGER, trash_batch TEXT)")
    db.execSQL("INSERT INTO notes_new (id,title,body,created_at,updated_at,pinned,sort_order) SELECT id,title,body,updated_at,updated_at,0,-updated_at FROM notes")
    db.execSQL("DROP TABLE notes")
    db.execSQL("ALTER TABLE notes_new RENAME TO notes")
    db.execSQL("CREATE INDEX index_notes_parent_id ON notes(parent_id)")
    db.execSQL("CREATE TABLE checks (id TEXT NOT NULL PRIMARY KEY, note_id INTEGER NOT NULL, text TEXT NOT NULL, done INTEGER NOT NULL, sort_order INTEGER NOT NULL, FOREIGN KEY(note_id) REFERENCES notes(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
    db.execSQL("CREATE INDEX index_checks_note_id ON checks(note_id)")
   }
  }
  val MIGRATION_2_3 = object : Migration(2, 3) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE notes ADD COLUMN template TEXT NOT NULL DEFAULT 'quick'")
    db.execSQL("ALTER TABLE notes ADD COLUMN details TEXT NOT NULL DEFAULT '{}'")
   }
  }
  val MIGRATION_3_4 = object : Migration(3, 4) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE notes ADD COLUMN lock_root_id INTEGER")
    db.execSQL("ALTER TABLE notes ADD COLUMN sealed_content TEXT")
    db.execSQL("CREATE TABLE IF NOT EXISTS lock_groups (root_id INTEGER NOT NULL PRIMARY KEY, salt TEXT NOT NULL, wrapped_key TEXT NOT NULL, failures INTEGER NOT NULL, blocked_until INTEGER NOT NULL)")
   }
  }
  fun open(context: Context, name: String = "notes.db") = Room.databaseBuilder(context, MemoDatabase::class.java, name).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).setJournalMode(JournalMode.TRUNCATE).addCallback(object : Callback() {
   override fun onOpen(db: SupportSQLiteDatabase) { db.query("PRAGMA secure_delete=ON").use { it.moveToFirst() } }
  }).build()
 }
}
object TreeRules {
 fun descendants(id: Long, notes: List<Note>): Set<Long> {
  val result = mutableSetOf(id)
  while (true) { if (!result.addAll(notes.filter { it.parentId in result }.map { it.id })) return result }
 }
 fun depth(id: Long?, notes: List<Note>): Int {
  var current = id; var depth = 0; val seen = mutableSetOf<Long>(); val map = notes.associateBy { it.id }
  while (current != null) { require(seen.add(current)) { "순환 관계는 만들 수 없습니다" }; depth++; current = map[current]?.parentId }
  return depth
 }
 fun validateMove(id: Long, parent: Long?, notes: List<Note>) {
  val subtree = descendants(id, notes)
  require(parent !in subtree) { "자신 또는 하위 메모 아래로 이동할 수 없습니다" }
  if (parent != null) require(notes.any { it.id == parent && it.trashedAt == null }) { "이동할 상위 메모가 없습니다" }
  val height = subtree.maxOf { depth(it, notes) - depth(id, notes) + 1 }
  require(depth(parent, notes) + height <= 3) { "최대 3단계까지 지원합니다" }
 }
 fun ordered(notes: List<Note>) = notes.sortedWith(compareByDescending<Note> { it.pinned }.thenBy { it.sortOrder }.thenBy { it.id })
 fun path(id: Long?, notes: List<Note>): String {
  val names = mutableListOf<String>(); val seen = mutableSetOf<Long>(); var current = id
  while (current != null && seen.add(current)) { val n = notes.find { it.id == current } ?: break; names.add(n.displayTitle); current = n.parentId }
  return names.asReversed().joinToString(" / ")
 }
}
