package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.omg.CORBA.TIMEOUT;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 发送验证码
     * @param phone 手机号
     * @return OK
     */
    @Override
    public Result sendCode(String phone) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.返回错误信息
            return Result.fail("手机号格式错误");
        }
        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);

        //4.验证码保存到redis中
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //5.发送验证码
        log.debug("发送验证码成功：" + code);
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.校验手机号
        if(RegexUtils.isPhoneInvalid(loginForm.getPhone())){
            return Result.fail("手机号格式错误！");
        }

        //2.从redis获取验证码并校验验证码
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY+loginForm.getPhone());
        if(code == null || !code.equals(loginForm.getCode())){
            //3.验证码错误返回错误信息
            return Result.fail("验证码错误");
        }

        //4.校验通过, 根据手机号查询用户信息
        User user = query().eq("phone", loginForm.getPhone()).one();

        //5.如果用户不存在
        if(user == null){
            //保存用户信息
           user = createUserWithPhone(loginForm.getPhone());
        }

        //6.用户信息脱敏
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setIcon(user.getIcon());
        userDTO.setNickName(user.getNickName());

        //7.将用户保存到redis中
        //7.1随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        String tokenKey = LOGIN_USER_KEY+token;
        //7.2将UserDTO对象转成hash
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                .setIgnoreNullValue(true)
                .setFieldValueEditor((fieldName,filedValue) -> filedValue.toString())
                );
        //7.3存储
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //7.4设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8返回token
        return Result.ok(token);
    }

    //保存用户
    public User createUserWithPhone(String phone) {
        //1.创建用户
        User user = new User();
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        user.setPhone(phone);
        save(user);
        return user;
    }

}
