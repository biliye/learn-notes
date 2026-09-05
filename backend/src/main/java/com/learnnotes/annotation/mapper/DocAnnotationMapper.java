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

    int countByDocIds(@Param("docIds") List<Long> docIds);

    /** 除 excludeDocId 外还有多少见解快照引用了指定内容（清孤儿图片用） */
    int countOtherRefs(@Param("excludeDocId") long excludeDocId, @Param("pattern") String pattern);

    int deleteAll();
}
