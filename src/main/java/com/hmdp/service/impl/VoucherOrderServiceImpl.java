package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IVoucherService;
import com.hmdp.utils.RedisWork;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisWork redisWork;
    @Resource
    private IVoucherOrderService orderService;
    @Override
    public Result seckillVoucher(Long voucherId) {
        //提交优惠券Id,查询优惠券信息
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始
        if(!LocalDateTime.now().isAfter(voucher.getBeginTime()) && !LocalDateTime.now().isBefore(voucher.getEndTime())){
            //秒杀未开始返回错误信息
            return Result.fail("当前时间未在秒杀时间内");
        }
        //秒杀开始，判断库存是否充足
        if(voucher.getStock()<=0){
            //库存不足返回错误信息
            return Result.fail("库存不足");
        }
        return CreateVoucherOrder(voucherId);

    }
    @Transactional
    public Result CreateVoucherOrder(Long voucherId) {
        //库存充足
        //一人一单,判断用户是否下过单
        Long userId = UserHolder.getUser().getId();
        long orderId = redisWork.nextId("order");
        synchronized (userId.toString().intern()){
            int count = orderService.query().eq("user_id",userId).eq("voucher_id", voucherId).count();
            if(count>0){
                return Result.fail("该用户已经购买！");
            }
            //扣减库存
            boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                    .eq("voucher_id",voucherId)
                    .gt("stock",0)
                    .update();
            if(!success){
                return Result.fail("库存不足");
            }

            //创建订单
            VoucherOrder order = new VoucherOrder();
            order.setId(orderId);
            order.setUserId(userId);
            order.setVoucherId(voucherId);
            orderService.save(order);
            return Result.ok(orderId);
        }
    }
}
