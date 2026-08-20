package com.learnnotes.annotation;

import com.learnnotes.markdown.AnchorUtil;
import com.learnnotes.markdown.Block;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 锚点重挂算法 —— 严格实现设计规格 D6 四步（纯静态，无 Spring 依赖，可单测）。
 *
 * <pre>
 * 1. 新块列表中 hash8 唯一命中 → ACTIVE（更新 anchor_index）
 * 2. hash8 多个命中 → 取与原 anchor_index 距离最近者 → ACTIVE
 * 3. hash8 无命中 → 在 [anchor_index-2, anchor_index+2] 窗口内 trigram Jaccard ≥ 0.6 → STALE
 * 4. 仍无 → ORPHAN（保留 block_snippet 供人工重挂）
 * </pre>
 *
 * <p>硬要求：任何情况下不得"删除"见解 —— 最坏结果是 ORPHAN。
 */
public final class ReanchorService {

    /** 相似度阈值（D6） */
    private static final double JACCARD_THRESHOLD = 0.6;

    private ReanchorService() {
    }

    /**
     * 对一条见解计算重挂结果。
     *
     * @param anchorHash 见解原 hash8
     * @param origIndex  见解原 anchor_index（相对 oldBlocks）
     * @param oldBlocks  旧内容块列表（正文变更前）
     * @param newBlocks  新内容块列表（正文变更后）
     */
    public static AnchorMatch findMatch(String anchorHash, int origIndex,
                                        List<Block> oldBlocks, List<Block> newBlocks) {
        String oldText = oldBlocksText(oldBlocks, origIndex);
        return findMatchWithText(anchorHash, origIndex, oldText, newBlocks);
    }

    /**
     * 直接给定旧文本（创建时的块快照）做匹配 —— 见解回灌（R33）场景没有 oldBlocks，用 blockSnippet。
     */
    public static AnchorMatch findMatchWithText(String anchorHash, int origIndex, String oldText,
                                                List<Block> newBlocks) {
        if (newBlocks == null || newBlocks.isEmpty()) {
            return AnchorMatch.orphan();
        }

        // 1 & 2. hash8 命中
        List<Integer> hits = new ArrayList<>();
        for (int i = 0; i < newBlocks.size(); i++) {
            if (hashOf(newBlocks.get(i)).equals(anchorHash)) {
                hits.add(i);
            }
        }
        if (hits.size() == 1) {
            return new AnchorMatch(hits.get(0), newBlocks.get(hits.get(0)).getAnchor(), BlockStatus.ACTIVE);
        }
        if (hits.size() > 1) {
            int best = hits.get(0);
            int bestDist = Math.abs(best - origIndex);
            for (int i : hits) {
                int d = Math.abs(i - origIndex);
                if (d < bestDist) {
                    best = i;
                    bestDist = d;
                }
            }
            return new AnchorMatch(best, newBlocks.get(best).getAnchor(), BlockStatus.ACTIVE);
        }

        // 3. 窗口 trigram Jaccard
        int lo = Math.max(0, origIndex - 2);
        int hi = Math.min(newBlocks.size() - 1, origIndex + 2);
        int bestIdx = -1;
        double bestSim = 0;
        for (int j = lo; j <= hi; j++) {
            double sim = trigramJaccard(oldText, blockText(newBlocks.get(j)));
            if (sim >= JACCARD_THRESHOLD && sim > bestSim) {
                bestIdx = j;
                bestSim = sim;
            }
        }
        if (bestIdx >= 0) {
            return new AnchorMatch(bestIdx, newBlocks.get(bestIdx).getAnchor(), BlockStatus.STALE);
        }

        // 4. ORPHAN
        return AnchorMatch.orphan();
    }

    /**
     * trigram Jaccard：对文本取长度 3 的字符 n-gram 集合，|A∩B| / |A∪B|；
     * 文本长度 &lt; 3 时退化为字符串相等比较。
     */
    public static double trigramJaccard(String a, String b) {
        if (a.length() < 3 || b.length() < 3) {
            return a.equals(b) ? 1.0 : 0.0;
        }
        Set<String> A = trigrams(a);
        Set<String> B = trigrams(b);
        Set<String> intersection = new HashSet<>(A);
        intersection.retainAll(B);
        Set<String> union = new HashSet<>(A);
        union.addAll(B);
        if (union.isEmpty()) {
            return 0.0;
        }
        return (double) intersection.size() / union.size();
    }

    private static Set<String> trigrams(String text) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 3 <= text.length(); i++) {
            set.add(text.substring(i, i + 3));
        }
        return set;
    }

    private static String oldBlocksText(List<Block> oldBlocks, int origIndex) {
        if (oldBlocks == null || oldBlocks.isEmpty()) {
            return "";
        }
        int idx = Math.min(Math.max(origIndex, 0), oldBlocks.size() - 1);
        return blockText(oldBlocks.get(idx));
    }

    private static String blockText(Block b) {
        return AnchorUtil.normalize(b.getRaw(), "code".equals(b.getType()));
    }

    private static String hashOf(Block b) {
        return b.getAnchor().substring(b.getAnchor().indexOf('-') + 1);
    }

    public enum BlockStatus {
        ACTIVE, STALE, ORPHAN
    }

    /** 重挂决策结果。index=-1 表示 ORPHAN。 */
    @Data
    @AllArgsConstructor
    public static class AnchorMatch {
        private int index;
        private String anchor;
        private BlockStatus status;

        public static AnchorMatch orphan() {
            return new AnchorMatch(-1, null, BlockStatus.ORPHAN);
        }
    }
}
