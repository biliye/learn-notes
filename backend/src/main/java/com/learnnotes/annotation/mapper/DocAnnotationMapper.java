package com.learnnotes.annotation.mapper;

import com.learnnotes.annotation.entity.DocAnnotation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocAnnotationMapper {

    List<DocAnnotation> selectByDoc(@Param("docId") Long docId);

    DocAnnotation selectById(@Param("id") Long id);

    int insert(DocAnnotation ann);

    int updateContent(@Param("id") Long id, @Param("contentMd") String contentMd);

    int updateAnchor(@Param("id") Long id,
                     @Param("anchorHash") String anchorHash,
                     @Param("anchorIndex") int anchorIndex,
                     @Param("status") String status,
                     @Param("blockSnippet") String blockSnippet);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int deleteById(@Param("id") Long id);

    int deleteByDoc(@Param("docId") Long docId);

    int countByDoc(@Param("docId") Long docId);

    int deleteAll();
}
