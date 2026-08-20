package com.learnnotes.annotation.service;

import com.learnnotes.annotation.ReanchorService;
import com.learnnotes.annotation.dto.AnnotationDto;
import com.learnnotes.annotation.entity.DocAnnotation;
import com.learnnotes.annotation.mapper.DocAnnotationMapper;
import com.learnnotes.common.BizException;
import com.learnnotes.doc.AnnotationAccess;
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
        Map<String, Integer> blockIndexByAnchor = newBlocks.stream()
                .collect(Collectors.toMap(Block::getAnchor, Block::getIndex));
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
    public AnnotationDto create(Long docId, String anchor, String contentMd) {
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("见解内容不能为空");
        }
        requireDoc(docId);
        Block block = findBlockByAnchor(docId, anchor);
        if (block == null) {
            throw BizException.badRequest("anchor 不在当前块列表中：" + anchor);
        }
        int docVersion = requireDoc(docId).getCurrentVersion();
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
    public AnnotationDto update(Long id, String contentMd) {
        if (contentMd == null || contentMd.isBlank()) {
            throw BizException.badRequest("见解内容不能为空");
        }
        requireAnn(id);
        mapper.updateContent(id, contentMd.trim());
        return AnnotationDto.from(mapper.selectById(id));
    }

    /** 手动重挂（R22：ORPHAN → ACTIVE） */
    @Transactional
    public AnnotationDto reanchorManual(Long id, String anchor) {
        DocAnnotation ann = requireAnn(id);
        Block block = findBlockByAnchor(ann.getDocId(), anchor);
        if (block == null) {
            throw BizException.badRequest("anchor 不在当前块列表中：" + anchor);
        }
        mapper.updateAnchor(id, AnchorUtil.parseHash(anchor), block.getIndex(),
                DocAnnotation.STATUS_ACTIVE, snippetOf(block));
        return AnnotationDto.from(mapper.selectById(id));
    }

    /** STALE → ACTIVE，同时刷新 block_snippet（R22） */
    @Transactional
    public AnnotationDto confirm(Long id) {
        DocAnnotation ann = requireAnn(id);
        Block block = findBlockByAnchor(ann.getDocId(), ann.getAnchorHash());
        mapper.updateAnchor(id, ann.getAnchorHash(), ann.getAnchorIndex(),
                DocAnnotation.STATUS_ACTIVE,
                block != null ? snippetOf(block) : ann.getBlockSnippet());
        return AnnotationDto.from(mapper.selectById(id));
    }

    @Transactional
    public void delete(Long id) {
        requireAnn(id);
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

    private com.learnnotes.doc.entity.Doc requireDoc(Long docId) {
        com.learnnotes.doc.entity.Doc doc = docMapper.selectById(docId);
        if (doc == null) {
            throw BizException.notFound("文档不存在：" + docId);
        }
        return doc;
    }

    private Block findBlockByAnchor(Long docId, String anchor) {
        String hash = AnchorUtil.parseHash(anchor);
        if (hash == null) {
            return null;
        }
        return MarkdownBlockParser.parse(requireDoc(docId).getContentMd()).getBlocks().stream()
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
