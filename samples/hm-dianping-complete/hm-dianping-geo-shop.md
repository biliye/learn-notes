---
path: [项目, 黑马点评]
slugs: [project, hm-dianping]
title: 附近商户：Redis GEO 按距离排序分页
slug: hm-dianping-geo-shop
tags: [Redis, GEO, 附近商户, 距离排序]
summary: 用 Redis GEO 存店铺坐标，GEOSEARCH 按坐标与半径检索并按距离排序、分页，回填距离字段，无坐标时回退数据库分页。
order: 50
spec_version: v2
---

# 附近商户：Redis GEO 按距离排序分页

「附近商户」要解决的是：给定用户经纬度，找出半径范围内的商铺，并按照距离从近到远排序、分页返回。Redis 的 GEO 结构（底层是 ZSet）通过 `GEOSEARCH` 能一次完成范围检索与距离排序，很适合这类场景。

## 预写入 GEO：店铺坐标

GEO 是 Redis 3.2 之后提供的功能，本质上是一个按坐标编码后的有序集合。写入前先把所有店铺按 `typeId` 分组，每组一个 key（`shop:geo:{typeId}`），成员为店铺 id，坐标是店铺的经纬度 `x`、`y`。这一步在发布后的一次跑批里完成即可，因为店铺坐标基本不动。

```java
@Test
void loadShopData() {
    List<Shop> list = shopService.list();
    Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
    for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
        Long typeId = entry.getKey();
        String key = SHOP_GEO_KEY + typeId;
        List<Shop> value = entry.getValue();
        List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
        for (Shop shop : value) {
            locations.add(new RedisGeoCommands.GeoLocation<>(
                    shop.getId().toString(),
                    new Point(shop.getX(), shop.getY())
            ));
        }
        stringRedisTemplate.opsForGeo().add(key, locations);
    }
}
```

`GeoLocation` 包装了成员名（店铺 id）和坐标，`opsForGeo().add` 一次写入一整批，等价于 `GEOADD key 经度 纬度 member`。按类型打散成多个 key，是为了查询时只扫一类商铺，缩小检索范围。

## 按坐标与半径检索并排序

`queryShopByType` 是整个 GEO 查询的核心。当请求带 `x`、`y` 时走 Redis；不带坐标时说明不支持定位，直接按数据库分页。分页参数与数据库分页对齐：`from` 为数据起始下标，`end` 为截止下标。

```java
@Override
public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    if (x == null || y == null) {
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        return Result.ok(page.getRecords());
    }
    int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
    int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
    String key = SHOP_GEO_KEY + typeId;
    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
            .search(
                    key,
                    GeoReference.fromCoordinate(x, y),
                    new Distance(5000),
                    RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
            );
    if (results == null) {
        return Result.ok(Collections.emptyList());
    }
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if (list.size() <= from) {
        return Result.ok(Collections.emptyList());
    }
    List<Long> ids = new ArrayList<>(list.size());
    Map<String, Distance> distanceMap = new HashMap<>(list.size());
    list.stream().skip(from).forEach(result -> {
        String shopIdStr = result.getContent().getName();
        ids.add(Long.valueOf(shopIdStr));
        distanceMap.put(shopIdStr, result.getDistance());
    });
    String idStr = StrUtil.join(",", ids);
    List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
    for (Shop shop : shops) {
        shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }
    return Result.ok(shops);
}
```

`GeoReference.fromCoordinate(x, y)` 表示参考中心点，`new Distance(5000)` 为半径（米）。参数 `includeDistance()` 让返回结果带上每个成员与中心点的距离，`limit(end)` 限制只取前 `end` 条。等价于 Redis 命令 `GEOSEARCH key BYLONLAT x y BYRADIUS 5000 WITHDISTANCE`。

`list.size() <= from` 说明当前页已经超出可用数据，返回空集，代表没有下一页。否则用 `skip(from)` 甩掉前面已经返回过的那几页，只取当前页对应的 `from` 到 `end` 之间的记录，Redis 这一步已按距离升序排好。

## 回填距离字段

GEO 只返回店铺 id 和距离，店铺的完整信息还要回数据库查。查询后遍历结果，把距离写到 `Shop.distance` 回填给前端展示。

```java
List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
for (Shop shop : shops) {
    shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
}
```

`distance` 是「非表字段」，数据库 `tb_shop` 表里并没有这一列，而是查询后临时计算的，所以实体上用 `@TableField(exist = false)` 标记，让 MyBatis-Plus 忽略它。

```java
@TableField(exist = false)
private Double distance;
```

## 无坐标时回退数据库分页

当用户未开启定位、请求里没有 `x`、`y` 时，不能用 GEO，直接走数据库按 `type_id` 分页查。因为没有了距离维度，这里是常规的页码分页（`current` 与 `DEFAULT_PAGE_SIZE`）。这保证老接口仍然可用，只是没有距离信息。

```java
if (x == null || y == null) {
    Page<Shop> page = query()
            .eq("type_id", typeId)
            .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
    return Result.ok(page.getRecords());
}
```

## 小结

附近商户用 Redis GEO 承接超出数据库能力的「半径搜索 + 距离排序」。发布前把店铺坐标按类型分组批量写入 `shop:geo:{typeId}`；查询时用 `GEOSEARCH` 按中心坐标、半径、距离过滤，配合 `includeDistance` 与 `limit` 拿到按距离升序的候选；再用 `from`/`end` 截取当前页，回库取完整店铺信息并回填非表字段 `distance`。无坐标时回退到数据库普通分页，保证功能兼容。
