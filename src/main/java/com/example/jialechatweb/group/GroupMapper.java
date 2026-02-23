package com.example.jialechatweb.group;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface GroupMapper {
    @Insert("INSERT INTO `groups`(name, owner_id) VALUES(#{name}, #{ownerId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Group group);

    @Insert("INSERT IGNORE INTO group_members(group_id, user_id) VALUES(#{groupId}, #{userId})")
    void addMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Delete("DELETE FROM group_members WHERE group_id = #{groupId} AND user_id = #{userId}")
    void removeMember(@Param("groupId") Long groupId, @Param("userId") Long userId);

    @Select("SELECT user_id FROM group_members WHERE group_id = #{groupId}")
    List<Long> getMemberIds(@Param("groupId") Long groupId);

    @Select("""
        SELECT g.id, g.name, g.owner_id AS ownerId
        FROM `groups` g
        JOIN group_members gm ON g.id = gm.group_id
        WHERE gm.user_id = #{userId}
        """)
    List<Group> listByUser(Long userId);
}
