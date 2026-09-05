package com.learnnotes.annotation.service;

import com.learnnotes.annotation.ReanchorService;
import com.learnnotes.annotation.dto.AnnotationDto;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.auth.CurrentUser;
import com.learnnotes.common.BizException;
import com.learnnotes.doc.AnnotationAccess;
import com.learnnotes.doc.entity.Doc;
import com.learnnotes.doc.mapper.DocMapper;
import com.learnnotes.markdown.AnchorUtil;
import com.learnnotes.markdown.Block;
import com.learnnotes.markdown.MarkdownBlockParser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 个人见解服务（R18–R22、D5/D6）。硬要求：任何情况下不得自动删除见解。
 * V3 起校验见解所属文档归属（本人或管理员），防止越权读写他人文档下的见解。
 */
@Service
public class AnnotationService implements AnnotationAccess {

    private static final int SNIPPET_MAX = 300;

    private final DocAnnotationMapper mapper;
    private final DocMapper docMapper;

    public AnnotationService(DocAnnotationMapper mapper, DocMapper docMapper) {
        this.mapper = mapper;
        this.docMapper = docMapper;
    }

    // ---------- AnnotationAccess ----------

    @Override
    public List<Object> listForDoc(Long docId) {
        return mapper.selectByDoc(docId).stream()
                .map(AnnotationDto::from)
                .map(o -> (Object) o)
                .collect(Collectors.toList());
    }

    @Override
    public int deleteByDoc(Long docId) {
        return mapper.deleteByDoc(docId);
    }

    @Override
    public ReanchorCount reanchor(Long docId, List<Block> oldBlocks, List<Block> newBlocks) {
        List<DocAnnotation> anns = mapper.selectByDoc(docId);
        if (anns.isEmpty()) {
            return ReanchorCount.zero();
        }
        ReanchorCount count = ReanchorCount.zero();
        for (DocAnnotation ann : anns) {
            ReanchorService.AnchorMatch match = ReanchorService.findMatch(
                    ann.getAnchorHash(), ann.getAnchorIndex(), oldBlocks, newBlocks);
            switch (match.getStatus()) {
                case ACTIVE -> {
                    mapper.updateAnchor(ann.getId(), hashOf(match.getAnchor()), match.getIndex(),
                            DocAnnotation.STATUS_ACTIVE, snippetOf(newBlocks.get(match.getIndex())));
                    count.setActive(count.getActive() + 1);
                }
                case STALE -> {
                    mapper.updateAnchor(ann.getId(), hashOf(match.getAnchor()), match.getIndex(),
                            DocAnnotation.STATUS_STALE, snippetOf(newBlocks.get(match.getIndex())));
                    count.setStale(count.getStale() + 1);
                }
                case ORPHAN -> {
                    mapper.updateStatus(ann.getId(), DocAnnotation.STATUS_ORPHAN);
                    count.setOrphan(count.getOrphan() + 1);
                }
            }
        }
        return count;
    }

    // ---------- 业务接口 ----------

    @Transactional
    public AnnotationDto create(CurrentUser user, Long docId, String anchor, String contentMd) {
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("见解内容不能为空");
        }
        requireDoc(docId, user);
        Block block = findBlockByAnchor(docId, anchor);
        if (block == null) {
            throw BizException.badRequest("anchor 不在当前块列表中：" + anchor);
        }
        int docVersion = requireDoc(docId, user).getCurrentVersion();
        DocAnnotation ann = new DocAnnotation();
        ann.setDocId(docId);
        ann.setAnchorHash(AnchorUtil.parseHash(anchor));
        ann.setAnchorIndex(block.getIndex());
        ann.setBlockSnippet(snippetOf(block));
        ann.setContentMd(contentMd.trim());
        ann.setStatus(DocAnnotation.STATUS_ACTIVE);
        ann.setDocVersionAtCreate(docVersion);
        mapper.insert(ann);
        return AnnotationDto.from(mapper.selectById(ann.getId()));
    }

    @Transactional
    public AnnotationDto update(CurrentUser user, Long id, String contentMd) {
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("见解内容不能为空");
        }
        DocAnnotation ann = requireAnn(id);
        requireDoc(ann.getDocId(), user);
        mapper.updateContent(id, contentMd.trim());
        return AnnotationDto.from(mapper.selectById(id));
    }

    /** 手动重挂（R22：ORPHAN → ACTIVE） */
    @Transactional
    public AnnotationDto reanchorManual(CurrentUser user, Long id, String anchor) {
        DocAnnotation ann = requireAnn(id);
        requireDoc(ann.getDocId(), user);
        Block block = findBlockByAnchor(ann.getDocId(), anchor);
        if (block == null) {
            throw BizException.badRequest("anchor 不在当前块列表中：" + anchor);
        }
        mapper.updateAnchor(id, AnchorUtil.parseHash(anchor), block.getIndex(),
                DocAnnotation.STATUS_ACTIVE, snippetOf(block));
        return AnnotationDto.from(mapper.selectById(id));
    }

    /**
     * STALE → ACTIVE，同时刷新 block_snippet（R22）。
     * anchorHash 是 8 位裸 hash（不是 b{index}-{hash} 格式），直接按 hash 在当前块列表中匹配；
     * 目标块已不存在时拒绝确认（保持 STALE，用户可手动重挂），绝不盲转 ACTIVE。
     */
    @Transactional
    public AnnotationDto confirm(CurrentUser user, Long id) {
        DocAnnotation ann = requireAnn(id);
        requireDoc(ann.getDocId(), user);
        Block block = findBlockByHash(ann.getDocId(), ann.getAnchorHash());
        if (block == null) {
            throw BizException.conflict("锚点块已不在当前正文中，无法确认；请改用手动重挂指定新位置");
        }
        mapper.updateAnchor(id, ann.getAnchorHash(), block.getIndex(),
                DocAnnotation.STATUS_ACTIVE, snippetOf(block));
        return AnnotationDto.from(mapper.selectById(id));
    }

    @Transactional
    public void delete(CurrentUser user, Long id) {
        DocAnnotation ann = requireAnn(id);
        requireDoc(ann.getDocId(), user);
        mapper.deleteById(id);
    }

    // ---------- 内部 ----------

    private DocAnnotation requireAnn(Long id) {
        DocAnnotation ann = mapper.selectById(id);
        if (ann == null) {
            throw BizException.notFound("见解不存在：" + id);
        }
        return ann;
    }

    private Doc requireDoc(Long docId, CurrentUser user) {
        Doc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw BizException.notFound("文档不存在：" + docId);
        }
        if (!user.isAdmin() && !doc.getOwnerId().equals(user.userId())) {
            throw BizException.forbidden("无权访问该文档");
        }
        return doc;
    }

    private Block findBlockByAnchor(Long docId, String anchor) {
        String hash = AnchorUtil.parseHash(anchor);
        if (hash == null) {
            return null;
        }
        return findBlockByHash(docId, hash);
    }

    /** 按 8 位裸 hash 在文档当前块列表中找块；调用方均已先 requireDoc 校验归属 */
    private Block findBlockByHash(Long docId, String hash) {
        if (hash == null || hash.isBlank()) {
            return null;
        }
        // 调用方均已先 requireDoc(docId, user) 校验归属，这里只读正文解析块列表
        Doc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw BizException.notFound("文档不存在：" + docId);
        }
        return MarkdownBlockParser.parse(doc.getContentMd()).getBlocks().stream()
                .filter(b -> b.getAnchor().endsWith("-" + hash))
                .findFirst()
                .orElse(null);
    }

    private String hashOf(String anchor) {
        return anchor.substring(anchor.indexOf('-') + 1);
    }

    private String snippetOf(Block block) {
        String norm = AnchorUtil.normalize(block.getRaw(), "code".equals(block.getType()));
        return norm.length() <= SNIPPET_MAX ? norm : norm.substring(0, SNIPPET_MAX);
    }
}
