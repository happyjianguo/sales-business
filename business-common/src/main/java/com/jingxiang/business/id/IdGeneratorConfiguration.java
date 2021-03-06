package com.jingxiang.business.id;

import com.jingxiang.business.id.generator.ShortCardIdGenerator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ID生成器配置类
 * Created by liuzhaoming on 2019/8/7.
 */
@Configuration
public class IdGeneratorConfiguration {

    @Value("${jingxiang.business.idGenerator.machineId:1}")
    private int machineId;

    @Bean
    @ConditionalOnProperty(prefix = "jingxiang.business.idGenerator", name = "enable", havingValue = "true", matchIfMissing = true)
    public IdFactory idFactory() {
        IdFactory.INSTANCE = new IdFactory(new ShortCardIdGenerator(machineId), new ShortCardIdGenerator(machineId),
                new ShortCardIdGenerator(machineId));
        return IdFactory.INSTANCE;
    }
}
