package com.learnnotes.doc;

import com.learnnotes.markdown.Block;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 见解与重挂的跨模块钩子（由 annotation 模块实现，doc 模块通过它完成编排）。
 * 避免 doc ↔ annotation 循环依赖：接口定义在 doc，实现在 annotation。
 */
public interface AnnotationAccess {

    /** 文档详情接口的 annotations 字段（含 ORPHAN，按 anchor_index, created_at 排序） */
    List<Object> listForDoc(Long docId);

    /** 删除文档时级联删除其全部见解（应用层级联，不依赖外键） */
    int deleteByDoc(Long docId);

    /**
     * 正文变更后的锚点重挂（D6 四步）。
     *
     * @return 统计结果；无任何见解时返回全 0
     */
    ReanchorCount reanchor(Long docId, List<Block> oldBlocks, List<Block> newBlocks);

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    class ReanchorCount {
        private int active;
        private int stale;
        private int orphan;

        public static ReanchorCount zero() {
            return new ReanchorCount(0, 0, 0);
        }
    }
}
