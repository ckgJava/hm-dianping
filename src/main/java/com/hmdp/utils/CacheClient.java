package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR  = Executors.newFixedThreadPool(10);

    /**
     * 插入redis并设置过期时间
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void set(String key, Object value, Long time, TimeUnit timeUnit){
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,timeUnit);
    }

    /**
     * 插入redis并设置逻辑时间（为了解决缓存击穿问题）
     * @param key
     * @param value
     * @param time
     * @param timeUnit
     */
    public void setWithLogicExpire(String key, Object value, Long time, TimeUnit timeUnit){
        RedisData  redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 查询redis 解决缓存穿透
     * @param <R>
     * @return
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time, TimeUnit timeUnit){
        String key = keyPrefix+id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //redis中有数据 直接返回
        if(StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        //判断redis中的值是否为空值
        if(json != null){
            return null;
        }
        //查询数据库
        R r = dbFallBack.apply(id);
        //如果数据库为空，直接缓存空值
        if(r == null){
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL);
            return null;
        }
        //数据库不为空，直接返回r ,并且将查询的数据写入redis
        this.set(key,r,time,timeUnit);
        return r;
    }

    /**
     * 查询redis 解决缓存击穿问题
     * @param <R>
     * @param <ID>
     * @return
     */
    public <R,ID> R queryWithLogicExpire(String keyPrefix,ID id,Class<R> type,Function<ID,R> dbRollBack,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //redis为空直接返回空值
        if(StrUtil.isBlank(json)){
            return null;
        }
        //如果不为空
        RedisData redisData = JSONUtil.toBean(json,RedisData.class);
        JSONObject data = (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(data,type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //未过期,直接返回
        if(expireTime.isAfter(LocalDateTime.now())){
            return r;
        }
        //过期,获取锁
        String lockKey = LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if(isLock){
            CACHE_REBUILD_EXECUTOR.submit(()->{
               try{
                   R r1 = dbRollBack.apply(id);
                   this.setWithLogicExpire(key,r1,time,unit);
               }catch (Exception e){
                   e.printStackTrace();
               }finally {
                   reliefLock(lockKey);
               }
            });
            return r;
        }
        return r;

    }

    /**
     * 尝试获取锁
     * @param key
     * @return
     */
    public boolean tryLock(String key){
        Boolean aBoolean = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isFalse(aBoolean);
    }

    /**
     * 释放锁
     * @param key
     */
    public void reliefLock(String key){
        stringRedisTemplate.delete(key);
    }
}
