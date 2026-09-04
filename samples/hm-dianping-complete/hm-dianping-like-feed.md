---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: 达人探店点赞、关注与推模式 Feed 流
slug: hm-dianping-like-feed
tags: [Redis, ZSet, Feed流, 滚动分页]
summary: 用 Redis ZSet 实现笔记点赞（按时间排序、Top5）、关注与共同关注，以及推模式 Feed 流的推送与滚动分页。
order: 40
spec_version: v2
---

# 达人探店点赞、关注与推模式 Feed 流

这一篇讲黑马点评里围绕「达人探店」实现的三块东西：点赞（用 ZSet 存用户和点赞时间）、关注体系（用 Set 同步关注关系并求共同关注）、以及推模式 Feed 流。核心工具是 Redis 的有序集合 ZSet 与集合 Set。

## 点赞：ZSet 记录用户与时间戳

点赞要做的两件事：记录谁点了赞，以及判断当前用户是否点过赞。这里用 ZSet 存放「用户 id」作为成员，「当前时间戳」作为分数。分数是时间戳后，按分数排序天然就得到「按点赞时间从早到晚」的顺序。

点赞与取消实现在 `likeBlog` 中。先用 `score` 判断当前用户是否已点赞，是 `null` 表示没点过。

```java
@Override
public Result likeBlog(Long id) {
    Long userId = UserHolder.getUser().getId();
    String key = BLOG_LIKED_KEY + id;
    Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
    if (score == null) {
        boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
        if (isSuccess) {
            stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
        }
    } else {
        boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
        if (isSuccess) {
            stringRedisTemplate.opsForZSet().remove(key, userId.toString());
        }
    }
    return Result.ok();
}
```

数据库的 `liked` 字段负责计数与排序按钮，Redis 的 `blog:liked:{id}` 负责记录具体点赞了哪些用户。修改成功后写 Redis，失败时完全不影响数据库一致性。

查询笔记时，通过 `isBlogLiked` 判断当前登录用户是否点过赞，把结果回填到 `blog.isLike`。

```java
private void isBlogLiked(Blog blog) {
    UserDTO user = UserHolder.getUser();
    if (user == null) {
        return;
    }
    Long userId = user.getId();
    String key = "blog:liked:" + blog.getId();
    Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
    blog.setIsLike(score != null);
}
```

这里用 `UserHolder.getUser()` 从 ThreadLocal 取当前登录用户；若用户未登录则跳过，笔记默认显示未点赞。

## 点赞排行榜 Top5

「谁赞过 TA」需要按时间倒序取最近 5 人。ZSet 因为分数是时间戳，用 `range` 直接从大到小截取前 5 个成员。

```java
@Override
public Result queryBlogLikes(Long id) {
    String key = BLOG_LIKED_KEY + id;
    Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
    if (top5 == null || top5.isEmpty()) {
        return Result.ok(Collections.emptyList());
    }
    List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
    String idStr = StrUtil.join(",", ids);
    List<UserDTO> userDTOS = userService.query().in("id", ids)
            .last("ORDER BY FIELD(id," + idStr + ")").list()
            .stream()
            .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
            .collect(Collectors.toList());
    return Result.ok(userDTOS);
}
```

注意查询用的是 `ORDER BY FIELD(id, ...)`，让数据库按 Redis 返回的 id 顺序把结果排回去，否则 `IN` 查询出来的顺序是随机的，点赞时间顺序就丢了。

## 探店笔记

探店笔记用 `Blog` 实体承载，`queryHotBlog` 按点赞数倒序分页查询热门笔记，`queryBlogById` 查单条并回填作者信息与点赞状态。两者都会先查作者（昵称、头像）再判断点赞。

```java
@Override
public Result queryHotBlog(Integer current) {
    Page<Blog> page = query()
            .orderByDesc("liked")
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    List<Blog> records = page.getRecords();
    records.forEach(blog -> {
        this.queryBlogUser(blog);
        this.isBlogLiked(blog);
    });
    return Result.ok(records);
}
```

## 关注 / 取关：Set 同步 Redis

关注关系持久化在数据库 `tb_follow` 表中，同时把「我关注了谁」的用户 id 同步到 Redis 的 Set（`follows:{userId}`），这样求共同关注时不用查数据库。`follow` 方法接收 `isFollow` 判断当前是关注还是取关。

```java
@Override
public Result follow(Long followUserId, Boolean isFollow) {
    Long userId = UserHolder.getUser().getId();
    String key = "follows:" + userId;
    if (isFollow) {
        Follow follow = new Follow();
        follow.setUserId(userId);
        follow.setFollowUserId(followUserId);
        boolean isSuccess = save(follow);
        if (isSuccess) {
            stringRedisTemplate.opsForSet().add(key, followUserId.toString());
        }
    } else {
        boolean isSuccess = remove(new QueryWrapper<Follow>()
                .eq("user_id", userId).eq("follow_user_id", followUserId));
        if (isSuccess) {
            stringRedisTemplate.opsForSet().remove(key, followUserId.toString());
        }
    }
    return Result.ok();
}
```

关注走 `save` 新增记录，取关走 `remove` 删除记录，各自成功后同步 Set。`isFollow` 则是查询数据库判断是否已关注，用于页面渲染关注按钮。

## 共同关注：Set 交集

「我们共同关注了谁」用 Redis 的 `intersect` 求两个 `follows:` Set 的交集。交集复杂度 O(N)，在 Redis 内存里完成远快于数据库联表。

```java
@Override
public Result followCommons(Long id) {
    Long userId = UserHolder.getUser().getId();
    String key = "follows:" + userId;
    String key2 = "follows:" + id;
    Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
    if (intersect == null || intersect.isEmpty()) {
        return Result.ok(Collections.emptyList());
    }
    List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
    List<UserDTO> users = userService.listByIds(ids).stream()
            .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
            .collect(Collectors.toList());
    return Result.ok(users);
}
```

## 推模式 Feed 流：笔记推给所有粉丝

Feed 流有两类模型：拉取（读时聚合）和推送（写时分发）。这里用的是推模式——作者一旦发布笔记，就把笔记 id 推给每个粉丝的「收件箱」（ZSet `feed:{userId}`），分数用当前时间戳。用户读 Feed 时直接读自己收件箱即可，查询快。

```java
@Override
public Result saveBlog(Blog blog) {
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    boolean isSuccess = save(blog);
    if (!isSuccess) {
        return Result.fail("新增笔记失败!");
    }
    List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
    for (Follow follow : follows) {
        Long userId = follow.getUserId();
        String key = FEED_KEY + userId;
        stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
    }
    return Result.ok(blog.getId());
}
```

先保存笔记拿到 id，再到 `tb_follow` 查出所有关注了「作者」的粉丝（`follow_user_id = authorId`），把笔记 id 逐个推给粉丝的 ZSet。推模式的好处是读时快，代价是发布时要写多个 key。

## 滚动分页：minTime + offset

Feed 流不能用传统页码分页——期间不断有新的笔记插入，页码会导致重复或漏读。这里用滚动分页：记住上次读到的时间戳（minTime）和相同时间戳下的偏移量（offset）。取出时用 `reverseRangeByScoreWithScores` 按分数倒序取 count 条。

```java
@Override
public Result queryBlogOfFollow(Long max, Integer offset) {
    Long userId = UserHolder.getUser().getId();
    String key = FEED_KEY + userId;
    Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
            .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
    if (typedTuples == null || typedTuples.isEmpty()) {
        return Result.ok();
    }
    List<Long> ids = new ArrayList<>(typedTuples.size());
    long minTime = 0;
    int os = 1;
    for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
        ids.add(Long.valueOf(tuple.getValue()));
        long time = tuple.getScore().longValue();
        if (time == minTime) {
            os++;
        } else {
            minTime = time;
            os = 1;
        }
    }
    String idStr = StrUtil.join(",", ids);
    List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
    for (Blog blog : blogs) {
        queryBlogUser(blog);
        isBlogLiked(blog);
    }
    ScrollResult r = new ScrollResult();
    r.setList(blogs);
    r.setOffset(os);
    r.setMinTime(minTime);
    return Result.ok(r);
}
```

遍历取出的元组时，用当前时间与 minTime 比较：若相等说明和上一条同分（同一时刻发布），offset 加一；否则更新 minTime 并把 offset 重置为 1。返回的 `ScrollResult` 携带 list、minTime、offset，前端下一次接着用它们作为 `max` 和 `offset` 传入。

```java
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
```

最后同样用 `ORDER BY FIELD(id, ...)` 保持 Redis 返回的顺序，再回填作者与点赞状态。

## 小结

点赞用 ZSet（成员 = 用户 id，分数 = 时间戳），天然支持点赞状态判断、Top5 排序；关注关系用 Set 同步到 Redis，用交集快速求共同关注；Feed 流采用推模式，发布时把笔记 id 推给所有粉丝的收件箱；滚动分页用 minTime 加 offset 配合 `reverseRangeByScoreWithScores`，避免页码分页在新增数据下重复与漏读。
