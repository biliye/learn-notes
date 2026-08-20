package com.learnnotes.doc.mapper;

import com.learnnotes.doc.dto.DocListRow;
import com.learnnotes.doc.entity.Doc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocMapper {

    List<DocListRow> selectList(@Param("topicId") Long topicId,
                                @Param("categoryId") Long categoryId,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long countList(@Param("topicId") Long topicId,
                   @Param("categoryId") Long categoryId,
                   @Param("keyword") String keyword);

    /** 详情：含正文 */
    Doc selectById(@Param("id") Long id);

    Doc selectByTopicAndSlug(@Param("topicId") Long topicId, @Param("slug") String slug);

    int insert(Doc doc);

    int update(Doc doc);

    int deleteById(@Param("id") Long id);

    int countByTopic(@Param("topicId") Long topicId);

    List<Doc> selectAll();

    List<com.learnnotes.doc.dto.DocSearchRow> search(@Param("pattern") String pattern, @Param("limit") int limit);

    /** 测试用：清空版本与文档 */
    void deleteAllVersions();

    void deleteAllDocs();
}
