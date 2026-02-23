package com.example.jialechatweb.chat;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@org.apache.ibatis.annotations.CacheNamespace(
        implementation = org.apache.ibatis.cache.impl.PerpetualCache.class,
        eviction = org.apache.ibatis.cache.decorators.LruCache.class,
        size = 1024, flushInterval = 60000, readWrite = false)
public interface MessageMapper {
    @Insert("""
        INSERT INTO messages(conversation_id, group_id, sender_id, receiver_id, content, content_type, is_read, created_at)
        VALUES(#{conversationId}, #{groupId}, #{senderId}, #{receiverId}, #{content}, #{contentType}, #{isRead}, NOW())
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ChatMessage msg);

    @Select("""
        SELECT m.id, m.conversation_id AS conversationId, m.group_id AS groupId, m.sender_id AS senderId, m.receiver_id AS receiverId,
               m.content, m.content_type AS contentType, m.is_read AS isRead, m.created_at AS createdAt,
               u.display_name AS senderName, u.avatar_url AS senderAvatar
        FROM messages m
        LEFT JOIN users u ON m.sender_id = u.id
        WHERE m.group_id = #{groupId}
        ORDER BY m.created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ChatMessage> listGroupMessages(@Param("groupId") Long groupId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("""
        SELECT id, conversation_id AS conversationId, group_id AS groupId, sender_id AS senderId, receiver_id AS receiverId,
               content, content_type AS contentType, is_read AS isRead, created_at AS createdAt
        FROM messages
        WHERE (sender_id = #{userId} AND receiver_id = #{friendId})
           OR (sender_id = #{friendId} AND receiver_id = #{userId})
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        """)
    List<ChatMessage> listP2P(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("limit") int limit, @Param("offset") int offset);

    @Update("UPDATE messages SET is_read = 1 WHERE receiver_id = #{userId} AND sender_id = #{friendId}")
    int markReadP2P(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
