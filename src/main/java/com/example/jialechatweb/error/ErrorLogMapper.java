package com.example.jialechatweb.error;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ErrorLogMapper {
    @Insert("""
        INSERT INTO error_logs(
            user_id, username, error_type, severity, message, stack, url, component, module, route,
            user_agent, browser, os, version, resource_url, request_method, status_code, extra, created_at
        ) VALUES(
            #{userId}, #{username}, #{errorType}, #{severity}, #{message}, #{stack}, #{url}, #{component}, #{module}, #{route},
            #{userAgent}, #{browser}, #{os}, #{version}, #{resourceUrl}, #{requestMethod}, #{statusCode}, #{extra}, NOW()
        )
        """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(ErrorLog log);

    @Select("""
        <script>
        SELECT id, user_id AS userId, username, error_type AS errorType, severity, message, stack, url, component, module, route,
               user_agent AS userAgent, browser, os, version, resource_url AS resourceUrl, request_method AS requestMethod,
               status_code AS statusCode, extra, created_at AS createdAt
        FROM error_logs
        <where>
            <if test="version != null and version != ''">AND version = #{version}</if>
            <if test="browser != null and browser != ''">AND browser = #{browser}</if>
            <if test="os != null and os != ''">AND os = #{os}</if>
        </where>
        ORDER BY created_at DESC
        LIMIT #{limit} OFFSET #{offset}
        </script>
        """)
    List<ErrorLog> list(@Param("version") String version,
                        @Param("browser") String browser,
                        @Param("os") String os,
                        @Param("limit") int limit,
                        @Param("offset") int offset);

    @Select("""
        <script>
        SELECT COUNT(*) FROM error_logs
        <where>
            <if test="version != null and version != ''">AND version = #{version}</if>
            <if test="browser != null and browser != ''">AND browser = #{browser}</if>
            <if test="os != null and os != ''">AND os = #{os}</if>
        </where>
        </script>
        """)
    int countAll(@Param("version") String version,
                 @Param("browser") String browser,
                 @Param("os") String os);

    @Select("""
        <script>
        SELECT COUNT(DISTINCT user_id) FROM error_logs
        <where>
            <if test="version != null and version != ''">AND version = #{version}</if>
            <if test="browser != null and browser != ''">AND browser = #{browser}</if>
            <if test="os != null and os != ''">AND os = #{os}</if>
        </where>
        </script>
        """)
    int countDistinctUsers(@Param("version") String version,
                           @Param("browser") String browser,
                           @Param("os") String os);

    @Select("""
        <script>
        SELECT error_type AS `key`, COUNT(*) AS count
        FROM error_logs
        <where>
            <if test="version != null and version != ''">AND version = #{version}</if>
            <if test="browser != null and browser != ''">AND browser = #{browser}</if>
            <if test="os != null and os != ''">AND os = #{os}</if>
        </where>
        GROUP BY error_type
        ORDER BY count DESC
        </script>
        """)
    List<ErrorAggregate> countByType(@Param("version") String version,
                                     @Param("browser") String browser,
                                     @Param("os") String os);

    @Select("SELECT COUNT(*) FROM error_logs WHERE error_type = #{errorType} AND created_at >= #{since}")
    int countRecentByType(@Param("errorType") String errorType, @Param("since") Instant since);
}
