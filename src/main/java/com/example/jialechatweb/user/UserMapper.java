package com.example.jialechatweb.user;

import org.apache.ibatis.annotations.*;

import java.util.Optional;

@Mapper
@org.apache.ibatis.annotations.CacheNamespace(
        implementation = org.apache.ibatis.cache.impl.PerpetualCache.class,
        eviction = org.apache.ibatis.cache.decorators.LruCache.class,
        size = 512, flushInterval = 60000, readWrite = true)
public interface UserMapper {
    @Select("SELECT id, username, email, password_hash AS passwordHash, display_name AS displayName, avatar_url AS avatarUrl, created_at AS createdAt FROM users WHERE username = #{username}")
    Optional<User> findByUsername(String username);

    @Select("SELECT id, username, email, password_hash AS passwordHash, display_name AS displayName, avatar_url AS avatarUrl, created_at AS createdAt FROM users WHERE email = #{email}")
    Optional<User> findByEmail(String email);

    @Select("SELECT id, username, email, password_hash AS passwordHash, display_name AS displayName, avatar_url AS avatarUrl, created_at AS createdAt FROM users WHERE id = #{id}")
    Optional<User> findById(Long id);

    @Select("""
        SELECT id, username, password_hash AS passwordHash, display_name AS displayName, avatar_url AS avatarUrl, created_at AS createdAt
        FROM users
        WHERE username LIKE CONCAT(#{prefix}, '%')
        ORDER BY username ASC
        LIMIT #{limit}
        """)
    java.util.List<User> listByAccountPrefix(@Param("prefix") String prefix, @Param("limit") int limit);

    @Insert("INSERT INTO users(username, email, password_hash, display_name, avatar_url, created_at) VALUES(#{username}, #{email}, #{passwordHash}, #{displayName}, #{avatarUrl}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);

    @Update("UPDATE users SET avatar_url = #{avatarUrl} WHERE id = #{id}")
    void updateAvatar(@Param("id") Long id, @Param("avatarUrl") String avatarUrl);

    @Update("UPDATE users SET display_name = #{displayName} WHERE id = #{id}")
    void updateDisplayName(@Param("id") Long id, @Param("displayName") String displayName);

    @Update("UPDATE users SET password_hash = #{passwordHash} WHERE id = #{id}")
    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    @Insert("INSERT INTO avatar_history(user_id, avatar_url, created_at) VALUES(#{userId}, #{avatarUrl}, NOW())")
    void insertAvatarHistory(@Param("userId") Long userId, @Param("avatarUrl") String avatarUrl);

    @Select("SELECT avatar_url FROM avatar_history WHERE user_id = #{userId} ORDER BY created_at DESC LIMIT #{limit}")
    java.util.List<String> findRecentAvatars(@Param("userId") Long userId, @Param("limit") int limit);
}
