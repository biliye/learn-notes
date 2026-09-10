package com.learnnotes.doc;

import com.learnnotes.markdown.Block;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /**
     * 图片路径迁移专用：正文里有若干块只是图片 URL 被改写（图片没换、位置没变，只换了存储路径），
     * 先把挂在那些块上的见解 anchor_hash 前移到新哈希，随后的 reanchor 才能按 hash 命中判 ACTIVE；
     * 否则它们会被降级成 STALE——块文本确实变了，但那不是内容变更，不该让用户看到待复核标记。
     *
     * @param oldBlocks        改写前的块列表
     * @param newBlocks        改写后的块列表
     * @param rewrittenIndexes 仅因 URL 改写而变化的块序号（调用方已核对块身份未变）
     * @return 前移的见解条数
     */
    int shiftAnchorsForRewrittenBlocks(Long docId, List<Block> oldBlocks, List<Block> newBlocks,
                                       Set<Integer> rewrittenIndexes);

    /**
     * 图片路径迁移专用：改写该文档见解（块快照 + 见解正文）里的 /uploads/ 老路径。
     *
     * @param pathMapping 老相对路径 → 新相对路径
     * @return 被改写的见解条数
     */
    int rewriteUploadPaths(Long docId, Map<String, String> pathMapping);

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
