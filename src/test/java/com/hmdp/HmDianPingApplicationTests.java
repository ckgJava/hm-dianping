package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisWork;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

@SpringBootTest
class HmDianPingApplicationTests {
    @Resource
    CacheClient cacheClient;
    @Resource
    ShopServiceImpl shopService;
    @Resource
    RedisWork redisWork;
    @Test
    public void testId(){
        long order = redisWork.nextId("order");
        System.out.println(order);
    }
    @Test
    public void test(){
        Shop byId = shopService.getById(2);
        cacheClient.setWithLogicExpire(CACHE_SHOP_KEY+2,byId,10L, TimeUnit.SECONDS);
    }
}
