package com.learnnotes.auth.mapper;

import com.learnnotes.auth.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper {

    SysUser findByUsername(@Param("username") String username);

    SysUser findByUsernameForUpdate(@Param("username") String username);

    /** 首个 ADMIN（X-Api-Token 通道的归属账号，R16） */
    SysUser findFirstAdmin();

    List<SysUser> selectAll();

    /** 用户列表（管理页用，含文档数） */
    List<com.learnnotes.admin.dto.AdminUserRow> selectAllWithDocCount();

    int countAll();

    int insert(SysUser user);
}
