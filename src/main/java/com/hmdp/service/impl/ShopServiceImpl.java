package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.sun.xml.internal.ws.util.xml.CDATA;
import netscape.javascript.JSUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;



    /**
     * 查询店铺信息
     * @param id
     * @return
     */
    @Override
    public Result queryShopById(Long id) {
        //缓存穿透
        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //缓存击穿
        //Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class,this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        if(shop == null){
            return Result.fail("商户信息不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 缓存击穿(热点key问题 --- 高并发)
     * 互斥锁
     * @param id
     * @return
     */
    /*   public Shop queryWithMutex(Long id){

        String shopKey = CACHE_SHOP_KEY + id;
        String lockKey = LOCK_SHOP_KEY + id;
        //1.从redis查询
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        //1.1判断缓存是否存在
        if(StrUtil.isNotBlank(shopJson)){
            //2.缓存存在，直接返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //缓存为空值
        if(shopJson != null){
            return null;
        }

        Shop shop = null;
        try {
            //尝试获取锁
            boolean isLock = tryLock(lockKey);
            if(!isLock){
                //锁未获取到 睡眠十秒后，再次尝试获取
                    Thread.sleep(50);
                    queryWithMutex(id);
            }
            //3.缓存不存在，查询数据库
            shop = getById(id);
            //4.数据库不存在
            if(shop == null){
                //将空值写入redis
                stringRedisTemplate.opsForValue().set(shopKey,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                //返回错误信息
                return null;
            }
            //5.存在 写入redis 并设置超时时间
            stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }finally {
            reliefLock(lockKey);
        }
        return shop;
    }*/

    /**
     * 修改店铺信息(实现一致性策略)
     * @param shop
     * @return
     */
    @Override
    @Transactional
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺Id不能为空");
        }
        //1.更新数据库信息
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }
}
