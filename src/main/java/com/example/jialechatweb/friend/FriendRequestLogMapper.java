package com.example.jialechatweb.friend;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;

@Mapper
public interface FriendRequestLogMapper {
    @Insert("INSERT INTO friend_request_logs(user_id, target_id, action, created_at) VALUES(#{userId}, #{targetId}, #{action}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(FriendRequestLog log);

    @Select("SELECT COUNT(*) FROM friend_request_logs WHERE user_id = #{userId} AND action = #{action} AND created_at >= #{since}")
    int countByUserSince(@Param("userId") Long userId, @Param("action") String action, @Param("since") Instant since);
}
