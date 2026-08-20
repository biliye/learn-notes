package com.learnnotes.auth.mapper;

import com.learnnotes.auth.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUser findByUsername(@Param("username") String username);

    SysUser findByUsernameForUpdate(@Param("username") String username);

    int countAll();

    int insert(SysUser user);
}
