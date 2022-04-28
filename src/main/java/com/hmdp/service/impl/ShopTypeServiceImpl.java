package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOPTYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //1.查询缓存
        List<String> stringList = stringRedisTemplate.opsForList().range(CACHE_SHOPTYPE_KEY, 0, -1);
        //判断缓存是否为空
        if(stringList != null && stringList.size()>0){
            List<ShopType> shopTypeList = new ArrayList<>();
            //缓存不为空
            for (String shop :stringList) {
                shopTypeList.add(JSONUtil.toBean(shop,ShopType.class));
            }
            return Result.ok(shopTypeList);
        }
        //如果缓存为空，查询数据库
         List<ShopType> list = query().orderByAsc("sort").list();
        //如果数据库为空直接返回
        if(list == null && list.size()==0){
            return  Result.fail("空");
        }
        //不为空 存入缓存
        for (ShopType shopType : list){
            stringRedisTemplate.opsForList().rightPush(CACHE_SHOPTYPE_KEY,JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(list);
    }
}
