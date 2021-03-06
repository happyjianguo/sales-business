package com.jingxiang.business.user.uc.common.vo.shop;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.Length;
import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;

/**
 * 店铺创建请求
 * Created by liuzhaoming on 2019/8/25.
 */
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ShopCreateRequest implements Serializable {
    /**
     * 店铺所有者
     */
    private String owner;

    /**
     * 店铺关联的群ID
     */
    @NotBlank(message = "群不能为空")
    private String groupId;

    /**
     * 店铺名称
     */
    @NotBlank(message = "店铺名称不能为空")
    @Length(min = 1, max = 64, message = "店铺名称长度应该在[1,64]")
    private String name;

    /**
     * 合伙人
     */
    private String partner;
}
