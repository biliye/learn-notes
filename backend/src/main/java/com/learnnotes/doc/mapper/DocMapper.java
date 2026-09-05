package com.learnnotes.doc.mapper;

import com.learnnotes.doc.dto.DocListRow;
import com.learnnotes.doc.entity.Doc;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocMapper {

    List<DocListRow> selectList(@Param("ownerId") Long ownerId,
                                @Param("topicId") Long topicId,
                                @Param("categoryIds") List<Long> categoryIds,
                                @Param("keyword") String keyword,
                                @Param("offset") int offset,
                                @Param("size") int size);

    long countList(@Param("ownerId") Long ownerId,
                   @Param("topicId") Long topicId,
                   @Param("categoryIds") List<Long> categoryIds,
                   @Param("keyword") String keyword);

    /** 详情：含正文 */
    Doc selectById(@Param("id") Long id);

    Doc selectByTopicAndSlug(@Param("ownerId") Long ownerId,
                             @Param("topicId") Long topicId,
                             @Param("slug") String slug);

    int insert(Doc doc);

    int update(Doc doc);

    /** 乐观锁更新：WHERE current_version = expectedVersion，0 行表示并发修改冲突 */
    int updateGuarded(@Param("doc") Doc doc, @Param("expectedVersion") int expectedVersion);

    /** 除 excludeDocId 外还有多少文档正文引用了指定内容（清孤儿图片用） */
    int countOtherRefs(@Param("excludeDocId") long excludeDocId, @Param("pattern") String pattern);

    int deleteById(@Param("id") Long id);

    int countByTopic(@Param("ownerId") Long ownerId, @Param("topicId") Long topicId);

    /** 全量（管理员导出） */
    List<Doc> selectAll();

    /** 某用户的全部文档（个人导出） */
    List<Doc> selectByOwner(@Param("ownerId") Long ownerId);

    /** 某用户的文档数（管理页用户列表用） */
    long countByOwner(@Param("ownerId") Long ownerId);

    List<com.learnnotes.doc.dto.DocSearchRow> search(@Param("ownerId") Long ownerId,
                                                     @Param("pattern") String pattern,
                                                     @Param("limit") int limit);

    /** 管理员跨用户文档列表（含归属用户与分类信息） */
    List<com.learnnotes.admin.dto.AdminDocRow> selectAdminPage(@Param("keyword") String keyword,
                                                               @Param("offset") int offset,
                                                               @Param("size") int size);

    long countAdminPage(@Param("keyword") String keyword);

    /** 测试用：清空版本与文档 */
    void deleteAllVersions();

    void deleteAllDocs();
}
