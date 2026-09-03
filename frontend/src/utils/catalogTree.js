/**
 * 目录树工具（V4 多级目录）：任意深度判定——
 * - 可放文档的叶目录：非顶层大类、且当前没有子目录；
 * - 可再建子目录：本身未直接放文档（docCount==0）、且未超过所属大类 max_level；
 * - 顶层大类之间不能互相搬移（大树自身决定层级）。
 */

export const PROTECTED_SLUGS = ['inbox', 'uncategorized']

function walk(nodes, parent, root, depth, out) {
  for (const n of nodes || []) {
    const rec = { node: n, parent, root: parent ? root : n, depth }
    out.push(rec)
    if (n.children && n.children.length) {
      walk(n.children, n, root, depth + 1, out)
    }
  }
}

/** 展平整棵树（含 parent/root/depth 引用），任意深度 */
export function indexTree(tree) {
  const out = []
  walk(tree || [], null, null, 0, out)
  return out
}

export function findRec(tree, id) {
  const idNum = Number(id)
  if (!idNum) return null
  return indexTree(tree).find(r => Number(r.node.id) === idNum) || null
}

/** rec 对应的完整路径（根→本节点） */
export function pathRec(rec) {
  const arr = []
  let cur = rec
  while (cur) {
    arr.unshift(cur.node)
    cur = cur.parent
  }
  return arr
}

/** rec 对应节点的完整路径文案（根→本节点，用 / 分隔） */
export function pathLabel(rec) {
  if (!rec) return ''
  return pathRec(rec).map(n => n.name).join(' / ')
}

/** 是否是顶层大类（parent_id 为空/0） */
export function isRoot(node) {
  return !node || !node.parentId || node.parentId === 0
}

/** 可放文档的叶目录：非顶层且当前没有子目录 */
export function isDocTarget(node) {
  return !!node && !isRoot(node) && !(node.children && node.children.length)
}

/** 所属大类的有效层级上限 */
export function rootMaxLevel(rec) {
  return rec?.root?.maxLevel ?? 2
}

/** 该节点还能否再建子目录（含文档的目录不能再细分） */
export function canAddChild(rec) {
  const n = rec?.node
  if (!n) return false
  if ((n.docCount || 0) > 0) return false
  const level = n.nodeLevel || 1
  return level < rootMaxLevel(rec)
}

/** 节点能否整体移动（顶层大类除外，兜底路径除外） */
export function canMoveNode(rec) {
  const n = rec?.node
  return !!n && !isRoot(n) && !PROTECTED_SLUGS.includes(n.slug)
}

/** 是兜底路径（不可删除/改名/移动） */
export function isProtectedNode(node) {
  return !!node && PROTECTED_SLUGS.includes(node.slug)
}

/**
 * 按条件复制一棵带 disabled 标记的目录树（供 el-tree-select 用）。
 * filter 命中或祖先命中时整棵子树禁用。
 */
export function disabledTree(tree, filter) {
  const walk = (list, inherited) => (list || []).map(n => {
    const self = inherited || !!filter(n)
    const children = walk(n.children || [], self)
    return { ...n, disabled: self, children }
  })
  return walk(tree, false)
}

/** 节点移动时的候选目标：可接收子目录的节点（排除自身子树/含文档节点/兜底目录） */
export function moveTargets(tree, movedNode) {
  const movedId = Number(movedNode?.id)
  if (!movedId) return []
  const idx = indexTree(tree)
  const moved = idx.find(r => Number(r.node.id) === movedId)
  // 被移动节点子树 id（含自身）
  const subIds = new Set()
  if (moved) {
    subIds.add(movedId)
    const stack = [...(moved.node.children || [])]
    while (stack.length) {
      const c = stack.pop()
      subIds.add(Number(c.id))
      if (c.children) stack.push(...c.children)
    }
  }
  return idx.filter(r => {
    const n = r.node
    if (subIds.has(Number(n.id))) return false
    return (n.docCount || 0) === 0 && !PROTECTED_SLUGS.includes(n.slug)
  })
}
