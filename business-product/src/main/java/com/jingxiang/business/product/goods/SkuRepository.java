package com.jingxiang.business.product.goods;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * SKU仓库
 * Created by liuzhaoming on 2019/8/2.
 */
@Repository
public interface SkuRepository extends JpaRepository<Sku, String> {
    /**
     * 修改商品发布时间
     *
     * @param shopId      店铺ID
     * @param skuId       SKU ID
     * @param publishTime 发布时间
     * @return 修改数目
     */
    @Modifying
    @Query("update Sku set publishTime=?3 where id=?2 and shopId=?1")
    int updatePublishTime(String shopId, String skuId, LocalDateTime publishTime);

    /**
     * 分页查询店铺下的商品，商品根据修改时间倒序排列
     *
     * @param shopId   店铺ID
     * @param pageable 分页请求
     * @return 查询结果
     */
    Page<Sku> findByShopIdOrderByUpdateTimeDesc(String shopId, Pageable pageable);

    /**
     * 根据商品ID和店铺ID查询SKU
     *
     * @param skuId  SKU ID
     * @param shopId 店铺ID
     * @return SKU
     */
    Optional<Sku> findByIdAndShopId(String skuId, String shopId);

    /**
     * 根据店铺ID和商品ID列表查询SKU
     *
     * @param shopId 店铺ID
     * @param idList 商品ID列表
     * @return SKU列表
     */
    List<Sku> findByShopIdAndIdIn(String shopId, List<String> idList);

    /**
     * 根据店铺ID和商品ID查询SKU
     *
     * @param shopId 店铺ID
     * @param skuId  商品ID
     * @return SKU
     */
    Optional<Sku> findByShopIdAndId(String shopId, String skuId);
}
