package com.example.jialechatweb.friend;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
@org.apache.ibatis.annotations.CacheNamespace(
        implementation = org.apache.ibatis.cache.impl.PerpetualCache.class,
        eviction = org.apache.ibatis.cache.decorators.LruCache.class,
        size = 256, flushInterval = 60000, readWrite = true)
public interface FriendMapper {
    @Insert("INSERT INTO friends(user_id, friend_id, remark, status, created_at) VALUES(#{userId}, #{friendId}, #{remark}, #{status}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Friend friend);

    @Delete("DELETE FROM friends WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int delete(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Update("UPDATE friends SET remark = #{remark} WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateRemark(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("remark") String remark);

    @Select("""
            SELECT f.id, f.user_id AS userId, f.friend_id AS friendId, f.remark, f.status, f.created_at AS createdAt,
                   u.display_name AS displayName, u.avatar_url AS avatarUrl, u.username AS username
            FROM friends f
            JOIN users u ON f.friend_id = u.id
            WHERE f.user_id = #{userId} AND f.status = 'ACCEPTED'
            """)
    List<FriendDTO> list(Long userId);

    @Select("""
        SELECT f.id, f.user_id AS userId, f.friend_id AS friendId, f.remark, f.status, f.created_at AS createdAt,
               u.display_name AS displayName, u.avatar_url AS avatarUrl, u.username AS account
        FROM friends f
        JOIN users u ON f.friend_id = u.id
        WHERE f.user_id = #{userId} AND f.status = 'ACCEPTED'
        """)
    List<java.util.Map<String, Object>> listDetails(Long userId);

    @Select("""
        SELECT id, user_id AS userId, friend_id AS friendId, remark, status, created_at AS createdAt
        FROM friends
        WHERE (user_id = #{userId} AND friend_id = #{friendId})
           OR (user_id = #{friendId} AND friend_id = #{userId})
        """)
    List<Friend> listBetween(@Param("userId") Long userId, @Param("friendId") Long friendId);

    @Select("SELECT COUNT(*) FROM friends WHERE user_id = #{userId} AND status = 'ACCEPTED'")
    int countAccepted(Long userId);

    @Select("""
        SELECT f.id AS requestId, f.user_id AS requesterId, u.username AS account, u.display_name AS displayName,
               f.created_at AS createdAt
        FROM friends f
        JOIN users u ON f.user_id = u.id
        WHERE f.friend_id = #{userId} AND f.status = 'PENDING'
        ORDER BY f.created_at DESC
        """)
    List<FriendRequestView> listIncomingPending(Long userId);

    @Update("UPDATE friends SET status = #{status} WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateStatus(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("status") String status);

    @Delete("DELETE FROM friends WHERE user_id = #{userId} AND friend_id = #{friendId} AND status = 'PENDING'")
    int deletePending(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
