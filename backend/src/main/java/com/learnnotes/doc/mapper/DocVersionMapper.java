package com.learnnotes.doc.mapper;

import com.learnnotes.doc.entity.DocVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocVersionMapper {

    int insert(DocVersion version);

    List<DocVersion> selectByDoc(@Param("docId") Long docId);

    DocVersion selectByDocAndVersion(@Param("docId") Long docId, @Param("version") int version);

    int deleteByDoc(@Param("docId") Long docId);
}
