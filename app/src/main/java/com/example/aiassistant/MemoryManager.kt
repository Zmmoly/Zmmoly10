package com.example.aiassistant

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * مدير الذاكرة للمساعد الذكي
 * مسؤول عن تخزين وإدارة واسترجاع المحادثات السابقة والمعلومات الشخصية للمستخدم
 */
class MemoryManager(private val context: Context) {

    companion object {
        private const val TAG = "MemoryManager"
        private const val MEMORY_PREFS = "memory_prefs"
        private const val CONVERSATION_HISTORY_FILE = "conversation_history.json"
        private const val USER_PROFILE_FILE = "user_profile.json"
        private const val MAX_CONVERSATION_ITEMS = 100
        private const val MAX_SHORT_TERM_MEMORY = 10
        private const val MAX_KNOWLEDGE_ITEMS = 200
    }

    // ملفات تخزين البيانات
    private val dataDir: File = context.filesDir
    private val gson = Gson()
    
    // المعلومات المخزنة في الذاكرة
    private val shortTermMemory = LinkedList<MemoryItem>() // الذاكرة قصيرة المدى
    private val longTermMemory = mutableListOf<MemoryItem>() // الذاكرة طويلة المدى
    private val userProfile = UserProfile() // معلومات المستخدم
    private val preferences: SharedPreferences
    
    // مؤشر إلى آخر محادثة تم تحميلها
    private var lastLoadedConversationId = ""
    private var isDirty = false // مؤشر على وجود تغييرات غير محفوظة

    init {
        // إنشاء المجلدات اللازمة لتخزين البيانات
        File(dataDir, "memory").mkdirs()
        
        // تهيئة الإعدادات المشتركة
        preferences = context.getSharedPreferences(MEMORY_PREFS, Context.MODE_PRIVATE)
        
        // تحميل المعلومات المخزنة
        loadUserProfile()
        loadLongTermMemory()
    }

    /**
     * حفظ عنصر جديد في الذاكرة
     * @param content محتوى العنصر
     * @param itemType نوع العنصر
     * @param importance مستوى أهمية العنصر
     * @param relatedTo العناصر المرتبطة بهذا العنصر (اختياري)
     * @return معرف العنصر الجديد
     */
    fun remember(
        content: String,
        itemType: MemoryItemType,
        importance: MemoryImportance = MemoryImportance.NORMAL,
        relatedTo: List<String> = emptyList()
    ): String {
        val id = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val memoryItem = MemoryItem(
            id = id,
            content = content,
            type = itemType,
            importance = importance,
            timestamp = timestamp,
            relatedToIds = relatedTo.toMutableList(),
            accessCount = 1
        )
        
        // إضافة العنصر إلى الذاكرة قصيرة المدى
        shortTermMemory.addFirst(memoryItem)
        
        // إذا امتلأت الذاكرة قصيرة المدى، نقل أقدم عنصر إلى الذاكرة طويلة المدى
        if (shortTermMemory.size > MAX_SHORT_TERM_MEMORY) {
            val oldest = shortTermMemory.removeLast()
            addToLongTermMemory(oldest)
        }
        
        // تعليم الذاكرة بأن هناك تغييرات غير محفوظة
        isDirty = true
        
        return id
    }

    /**
     * إضافة عنصر إلى الذاكرة طويلة المدى
     */
    private fun addToLongTermMemory(item: MemoryItem) {
        longTermMemory.add(item)
        
        // إذا امتلأت الذاكرة طويلة المدى، إزالة العناصر الأقل أهمية
        if (longTermMemory.size > MAX_KNOWLEDGE_ITEMS) {
            val toRemove = longTermMemory
                .sortedWith(compareBy({ it.importance.ordinal }, { it.accessCount }, { it.timestamp }))
                .take(longTermMemory.size - MAX_KNOWLEDGE_ITEMS)
            
            longTermMemory.removeAll(toRemove.toSet())
        }
        
        isDirty = true
    }

    /**
     * استرجاع عنصر من الذاكرة بواسطة المعرف
     * @param id معرف العنصر
     * @return العنصر المطلوب أو null إذا لم يتم العثور عليه
     */
    fun recall(id: String): MemoryItem? {
        // البحث في الذاكرة قصيرة المدى
        val fromShortTerm = shortTermMemory.find { it.id == id }
        if (fromShortTerm != null) {
            fromShortTerm.accessCount++
            return fromShortTerm
        }
        
        // البحث في الذاكرة طويلة المدى
        val fromLongTerm = longTermMemory.find { it.id == id }
        if (fromLongTerm != null) {
            fromLongTerm.accessCount++
            
            // إذا تم الوصول بشكل متكرر، نقل العنصر إلى الذاكرة قصيرة المدى
            if (fromLongTerm.accessCount > 5) {
                longTermMemory.remove(fromLongTerm)
                shortTermMemory.addFirst(fromLongTerm)
                
                // إذا امتلأت الذاكرة قصيرة المدى، نقل أقدم عنصر إلى الذاكرة طويلة المدى
                if (shortTermMemory.size > MAX_SHORT_TERM_MEMORY) {
                    val oldest = shortTermMemory.removeLast()
                    addToLongTermMemory(oldest)
                }
            }
            
            return fromLongTerm
        }
        
        return null
    }

    /**
     * البحث في الذاكرة عن عناصر تحتوي على النص المحدد
     * @param query نص البحث
     * @param itemType نوع العنصر المطلوب (اختياري)
     * @param limit الحد الأقصى لعدد النتائج
     * @return قائمة بالعناصر المطابقة
     */
    fun search(
        query: String,
        itemType: MemoryItemType? = null,
        limit: Int = 10
    ): List<MemoryItem> {
        val results = mutableListOf<MemoryItem>()
        
        // البحث في الذاكرة قصيرة المدى
        shortTermMemory.forEach { item ->
            if ((itemType == null || item.type == itemType) && item.content.contains(query, ignoreCase = true)) {
                item.accessCount++
                results.add(item)
            }
        }
        
        // البحث في الذاكرة طويلة المدى إذا لم يتم الوصول إلى الحد الأقصى
        if (results.size < limit) {
            longTermMemory.forEach { item ->
                if ((itemType == null || item.type == itemType) && item.content.contains(query, ignoreCase = true)) {
                    item.accessCount++
                    results.add(item)
                    
                    // التوقف عند الوصول إلى الحد الأقصى
                    if (results.size >= limit) {
                        return@forEach
                    }
                }
            }
        }
        
        return results.take(limit)
    }

    /**
     * استرجاع محادثة كاملة من الذاكرة
     * @param limit الحد الأقصى لعدد عناصر المحادثة
     * @return قائمة برسائل المحادثة
     */
    fun getConversationHistory(limit: Int = 20): List<MemoryItem> {
        val conversationItems = mutableListOf<MemoryItem>()
        
        // جمع عناصر المحادثة من الذاكرة قصيرة المدى
        shortTermMemory.forEach { item ->
            if (item.type == MemoryItemType.CONVERSATION) {
                conversationItems.add(item)
            }
        }
        
        // إذا لم يتم الوصول إلى الحد الأقصى، البحث في الذاكرة طويلة المدى
        if (conversationItems.size < limit) {
            longTermMemory.forEach { item ->
                if (item.type == MemoryItemType.CONVERSATION) {
                    conversationItems.add(item)
                    
                    // التوقف عند الوصول إلى الحد الأقصى
                    if (conversationItems.size >= limit) {
                        return@forEach
                    }
                }
            }
        }
        
        // ترتيب العناصر حسب الزمن (الأحدث أولاً)
        return conversationItems
            .sortedByDescending { it.timestamp }
            .take(limit)
    }

    /**
     * حفظ معلومة في الملف الشخصي للمستخدم
     * @param key المفتاح
     * @param value القيمة
     */
    fun setUserInfo(key: String, value: String) {
        userProfile.info[key] = value
        saveUserProfile()
    }

    /**
     * استرجاع معلومة من الملف الشخصي للمستخدم
     * @param key المفتاح
     * @return القيمة أو null إذا لم يتم العثور عليها
     */
    fun getUserInfo(key: String): String? {
        return userProfile.info[key]
    }

    /**
     * تحميل الملف الشخصي للمستخدم من التخزين المحلي
     */
    private fun loadUserProfile() {
        try {
            val file = File(File(dataDir, "memory"), USER_PROFILE_FILE)
            if (file.exists()) {
                FileReader(file).use { reader ->
                    val type = object : TypeToken<UserProfile>() {}.type
                    val loadedProfile = gson.fromJson<UserProfile>(reader, type)
                    userProfile.info.clear()
                    userProfile.info.putAll(loadedProfile.info)
                    Log.d(TAG, "تم تحميل الملف الشخصي للمستخدم")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تحميل الملف الشخصي للمستخدم", e)
        }
    }

    /**
     * حفظ الملف الشخصي للمستخدم في التخزين المحلي
     */
    private fun saveUserProfile() {
        try {
            val file = File(File(dataDir, "memory"), USER_PROFILE_FILE)
            FileWriter(file).use { writer ->
                gson.toJson(userProfile, writer)
                Log.d(TAG, "تم حفظ الملف الشخصي للمستخدم")
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في حفظ الملف الشخصي للمستخدم", e)
        }
    }

    /**
     * تحميل الذاكرة طويلة المدى من التخزين المحلي
     */
    private fun loadLongTermMemory() {
        try {
            val file = File(File(dataDir, "memory"), CONVERSATION_HISTORY_FILE)
            if (file.exists()) {
                FileReader(file).use { reader ->
                    val type = object : TypeToken<List<MemoryItem>>() {}.type
                    val loadedMemory = gson.fromJson<List<MemoryItem>>(reader, type)
                    longTermMemory.clear()
                    longTermMemory.addAll(loadedMemory)
                    Log.d(TAG, "تم تحميل الذاكرة طويلة المدى: ${longTermMemory.size} عنصر")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في تحميل الذاكرة طويلة المدى", e)
        }
    }

    /**
     * حفظ الذاكرة (قصيرة وطويلة المدى) في التخزين المحلي
     */
    suspend fun saveMemory() = withContext(Dispatchers.IO) {
        if (!isDirty) {
            return@withContext
        }
        
        try {
            // دمج الذاكرة قصيرة المدى والذاكرة طويلة المدى للحفظ
            val allMemory = mutableListOf<MemoryItem>()
            allMemory.addAll(shortTermMemory)
            allMemory.addAll(longTermMemory)
            
            // حفظ الذاكرة في ملف
            val file = File(File(dataDir, "memory"), CONVERSATION_HISTORY_FILE)
            FileWriter(file).use { writer ->
                gson.toJson(allMemory, writer)
                Log.d(TAG, "تم حفظ الذاكرة: ${allMemory.size} عنصر")
            }
            
            isDirty = false
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في حفظ الذاكرة", e)
        }
    }

    /**
     * إضافة عناصر مرتبطة لعنصر موجود في الذاكرة
     * @param itemId معرف العنصر
     * @param relatedIds معرفات العناصر المرتبطة
     */
    fun addRelatedItems(itemId: String, relatedIds: List<String>) {
        // البحث في الذاكرة قصيرة المدى
        shortTermMemory.find { it.id == itemId }?.let { item ->
            item.relatedToIds.addAll(relatedIds)
            isDirty = true
            return
        }
        
        // البحث في الذاكرة طويلة المدى
        longTermMemory.find { it.id == itemId }?.let { item ->
            item.relatedToIds.addAll(relatedIds)
            isDirty = true
        }
    }

    /**
     * تحديث أهمية عنصر في الذاكرة
     * @param itemId معرف العنصر
     * @param importance مستوى الأهمية الجديد
     */
    fun updateImportance(itemId: String, importance: MemoryImportance) {
        // البحث في الذاكرة قصيرة المدى
        shortTermMemory.find { it.id == itemId }?.let { item ->
            item.importance = importance
            isDirty = true
            return
        }
        
        // البحث في الذاكرة طويلة المدى
        longTermMemory.find { it.id == itemId }?.let { item ->
            item.importance = importance
            isDirty = true
        }
    }

    /**
     * حذف عنصر من الذاكرة
     * @param itemId معرف العنصر
     * @return true إذا تم العثور على العنصر وحذفه، false خلاف ذلك
     */
    fun forget(itemId: String): Boolean {
        // البحث في الذاكرة قصيرة المدى
        val shortTermItem = shortTermMemory.find { it.id == itemId }
        if (shortTermItem != null) {
            shortTermMemory.remove(shortTermItem)
            isDirty = true
            return true
        }
        
        // البحث في الذاكرة طويلة المدى
        val longTermItem = longTermMemory.find { it.id == itemId }
        if (longTermItem != null) {
            longTermMemory.remove(longTermItem)
            isDirty = true
            return true
        }
        
        return false
    }

    /**
     * الحصول على العناصر المرتبطة بعنصر معين
     * @param itemId معرف العنصر
     * @return قائمة بالعناصر المرتبطة
     */
    fun getRelatedItems(itemId: String): List<MemoryItem> {
        val relatedIds = mutableSetOf<String>()
        
        // البحث عن معرفات العناصر المرتبطة
        shortTermMemory.find { it.id == itemId }?.let { item ->
            relatedIds.addAll(item.relatedToIds)
        } ?: longTermMemory.find { it.id == itemId }?.let { item ->
            relatedIds.addAll(item.relatedToIds)
        }
        
        if (relatedIds.isEmpty()) {
            return emptyList()
        }
        
        // جمع العناصر المرتبطة من الذاكرة
        val relatedItems = mutableListOf<MemoryItem>()
        
        // البحث في الذاكرة قصيرة المدى
        shortTermMemory.forEach { item ->
            if (item.id in relatedIds) {
                relatedItems.add(item)
                item.accessCount++
            }
        }
        
        // البحث في الذاكرة طويلة المدى
        longTermMemory.forEach { item ->
            if (item.id in relatedIds) {
                relatedItems.add(item)
                item.accessCount++
            }
        }
        
        return relatedItems
    }

    /**
     * استرجاع آخر عناصر المحادثة حسب عددها والفترة الزمنية
     * @param count عدد العناصر المطلوبة
     * @param timeWindowMs الفترة الزمنية بالمللي ثانية
     * @return قائمة بعناصر المحادثة
     */
    fun getRecentConversation(count: Int = 5, timeWindowMs: Long = 30 * 60 * 1000): List<MemoryItem> {
        val currentTime = System.currentTimeMillis()
        val minTime = currentTime - timeWindowMs
        
        // جمع عناصر المحادثة الحديثة من الذاكرة قصيرة المدى
        val recentItems = shortTermMemory
            .filter { it.type == MemoryItemType.CONVERSATION && it.timestamp >= minTime }
            .sortedByDescending { it.timestamp }
            .take(count)
            .toMutableList()
        
        // إذا لم يتم الوصول إلى العدد المطلوب، البحث في الذاكرة طويلة المدى
        if (recentItems.size < count) {
            val remainingCount = count - recentItems.size
            
            longTermMemory
                .filter { it.type == MemoryItemType.CONVERSATION && it.timestamp >= minTime }
                .sortedByDescending { it.timestamp }
                .take(remainingCount)
                .forEach { recentItems.add(it) }
        }
        
        return recentItems.sortedByDescending { it.timestamp }
    }

    /**
     * مسح الذاكرة قصيرة المدى
     */
    fun clearShortTermMemory() {
        shortTermMemory.clear()
        isDirty = true
    }

    /**
     * مسح كل الذاكرة (قصيرة وطويلة المدى)
     */
    suspend fun clearAllMemory() = withContext(Dispatchers.IO) {
        shortTermMemory.clear()
        longTermMemory.clear()
        isDirty = true
        
        // حذف ملفات الذاكرة من التخزين
        try {
            val file = File(File(dataDir, "memory"), CONVERSATION_HISTORY_FILE)
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "خطأ في حذف ملفات الذاكرة", e)
        }
        
        saveMemory()
    }
}

/**
 * أنواع عناصر الذاكرة
 */
enum class MemoryItemType {
    CONVERSATION,  // محادثة
    FACT,          // حقيقة
    PREFERENCE,    // تفضيل
    ENTITY,        // كيان (شخص، مكان، شيء)
    TASK,          // مهمة
    REMINDER       // تذكير
}

/**
 * مستويات أهمية عناصر الذاكرة
 */
enum class MemoryImportance {
    CRITICAL,   // حرج
    HIGH,       // مرتفع
    NORMAL,     // عادي
    LOW         // منخفض
}

/**
 * عنصر في الذاكرة
 */
data class MemoryItem(
    val id: String,                          // معرف العنصر
    val content: String,                     // محتوى العنصر
    val type: MemoryItemType,                // نوع العنصر
    var importance: MemoryImportance,        // مستوى أهمية العنصر
    val timestamp: Long,                     // الطابع الزمني للعنصر
    val relatedToIds: MutableList<String>,   // معرفات العناصر المرتبطة
    var accessCount: Int                     // عدد مرات الوصول إلى العنصر
)

/**
 * الملف الشخصي للمستخدم
 */
data class UserProfile(
    val info: MutableMap<String, String> = mutableMapOf()  // معلومات المستخدم
)