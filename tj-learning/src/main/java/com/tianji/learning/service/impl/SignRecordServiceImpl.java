package com.tianji.learning.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.constants.Constant;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.*;
import com.tianji.learning.constants.RedisConstants;
import com.tianji.learning.domain.po.PointsRecord;
import com.tianji.learning.domain.vo.SignRecordVO;
import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.mapper.PointsRecordMapper;
import com.tianji.learning.mapper.SignRecordMapper;
import com.tianji.learning.mq.SignInMessage;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SignRecordServiceImpl extends ServiceImpl<SignRecordMapper, SignRecordVO> implements ISignRecordService {
    private final RedisTemplate redisTemplate;
    private final RabbitMqHelper mqHelper;
    //private final PointsRecordMapper getBaseMapper;
    @Override
    public SignResultVO addSignRecords(){
        //0.1获取用户登录
        Long userId = UserContext.getUser();
        //0.2获取日期
        LocalDate now = LocalDate.now();
        //0.3拼接key
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        //0.4计算offset
         int offset =  now.getDayOfMonth()-1;
        //1.签到
        Boolean exist  =  redisTemplate.opsForValue().setBit(key,offset,true);
        if (BooleanUtils.isTrue(exist)){
            throw new BizIllegalException("不允许重复签到");
        }
        //2.保存签到信息
        int signedDays = countSignDays(key,now.getDayOfMonth());
        //3.计算签到得分
        int rewardPoints = 0;
        switch (signedDays){
            case 7:
                rewardPoints = 10 ;
                break;
            case 14:
                rewardPoints = 20;
                break;
            case 28:
                rewardPoints = 40;
                break;
        }

        //4.保存积分明细记录
        mqHelper.send(
                MqConstants.Exchange.LEARNING_EXCHANGE,
                MqConstants.Key.SIGN_IN,
                SignInMessage.of(userId,rewardPoints+1)
        );
        //5.封装VO
        SignResultVO vo = new SignResultVO();
        vo.setSignDays(signedDays);
        vo.setRewardPoints(0);
        return vo;
    }

    private int countSignDays(String key ,int len){
        List<Long> result = redisTemplate.opsForValue()
                .bitField(key, BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(len)).valueAt(0));
        if (CollUtils.isEmpty(result)){
            return 0;
        }
        Long num= result.get(0);
        if (num==null){
            return 0;
        }
        int count = 0;
        while((num&1)==1){

            count++;
            num >>>= 1;
        }
        return count;

    }

    @Override
    public Byte[] querySignRecords(){
        Long userId = UserContext.getUser();
        LocalDate now = LocalDate.now();
        String format = now.format(DateUtils.SIGN_DATE_SUFFIX_FORMATTER);
        String key = RedisConstants.SIGN_RECORD_KEY_PREFIX + userId + format;
        int dayOfMonth = now.getDayOfMonth();
        List<Long> bitField = redisTemplate
                .opsForValue().
                bitField(key,
                        BitFieldSubCommands
                                .create()
                                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        if (CollUtils.isEmpty(bitField) || bitField.get(0) == null) {
            return new Byte[0];
        }
        Long num = bitField.get(0);
        int offset = now.getDayOfMonth() - 1;
        Byte[] arr = new Byte[dayOfMonth];
        while (offset >= 0) {
            arr[offset] = (byte) (num & 1);// 计算最后一天是否签到 赋值结果
            offset--;
            num = num >>> 1;
        }
        return arr;
    }
/*
    @Override
    public void addPointsRecord(Long userId, Integer points, PointsRecordType pointsRecordType){
            int maxPoints = pointsRecordType.getMaxPoints();
            if (maxPoints > 0) {
                LocalDateTime  now = LocalDateTime.now();
                LocalDateTime  begin = DateUtils.getDayStartTime(now);
                LocalDateTime  end = DateUtils.getDayEndTime(now);
                int currentPoints = queryUserPointsByTypeAndDate(userId ,pointsRecordType,begin,end);
            }
    }

    private int queryUserPointsByTypeAndDate(
            Long userId, PointsRecordType pointsRecordType, LocalDateTime begin, LocalDateTime end) {
        QueryWrapper<PointsRecord> wrapper  = new QueryWrapper<>();
        wrapper.lambda()
                .eq(PointsRecord::getUserId, userId)
                .eq(PointsRecord::getType,pointsRecordType)
                .between(PointsRecord::getCreateTime,begin,end);
        Integer points =  getBaseMapper().queryUserPointsByTypeAndDate(wrapper);
    }*/




}
