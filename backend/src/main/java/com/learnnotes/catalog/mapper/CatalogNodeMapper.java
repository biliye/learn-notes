package com.learnnotes.catalog.mapper;

import com.learnnotes.catalog.entity.CatalogNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogNodeMapper {

    List<CatalogNode> selectAll();

    CatalogNode selectById(@Param("id") Long id);

    /** parent_id=0 表示查找大类 */
    CatalogNode selectByParentAndSlug(@Param("parentId") long parentId, @Param("slug") String slug);

    int countByParent(@Param("parentId") long parentId);

    List<CatalogNode> selectByParent(@Param("parentId") long parentId);

    int insert(CatalogNode node);

    int update(CatalogNode node);

    int updateName(CatalogNode node);

    int deleteById(@Param("id") Long id);

    int incrDocCount(@Param("id") Long id, @Param("delta") int delta);
}
