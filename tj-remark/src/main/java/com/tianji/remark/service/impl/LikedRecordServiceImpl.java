package com.tianji.remark.service.impl;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.PublisherCallbackChannel;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.validation.Valid;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.tianji.common.constants.MqConstants.Exchange.LIKE_RECORD_EXCHANGE;
import static com.tianji.common.constants.MqConstants.Key.LIKED_TIMES_KEY_TEMPLATE;

/**
 * <p>
 * 点赞记录表 服务实现类
 * </p>
 *
 * @author author
 * @since 2026-02-24
 */
//@Service
@RequiredArgsConstructor
public class LikedRecordServiceImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    private final AmqpTemplate amqpTemplate;
    private final RabbitMqHelper mqHelper;
    @Override
    public  void addLikeRecord(@Valid LikeRecordFormDTO dto){
        //1.获取登录用户id
        Long id = UserContext.getUser();
        boolean success = dto.getLiked()?like(dto):unlike(dto);
        if(!success){
            return;
        }
        Integer likeTimes = lambdaQuery()
                .eq(LikedRecord::getBizId,dto.getBizId())
                .count();
        mqHelper.send(LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,dto.getBizId()),
                LikeTimesDTO.of(dto.getBizId(),likeTimes)
        );

    }
    private boolean like(LikeRecordFormDTO dto){
        Long userId = UserContext.getUser();
        Integer count =  lambdaQuery()
                .eq(LikedRecord::getUserId,userId)
                .eq(LikedRecord::getBizId,dto.getBizId())
                .count();
        //存在 结束
        if (count> 0){
            return false;
        }
        //不存在 新增
        LikedRecord r = new LikedRecord();
        r.setUserId(userId);
        r.setBizId(dto.getBizId());
        r.setBizType(dto.getBizType());
        save(r);
        return true;
    }
    private boolean unlike(LikeRecordFormDTO dto){
        Long userId = UserContext.getUser();
        Integer count = lambdaQuery()
                .eq(LikedRecord::getBizId,dto.getBizId())
                .eq(LikedRecord::getUserId,userId)
                .count();
        if(count==0){
            return false;
        }
        // 删除记录
        remove(lambdaQuery()
                .eq(LikedRecord::getBizId,dto.getBizId())
                .eq(LikedRecord::getUserId,userId));

        return true;
    }
    @Override
    public Set<Long> isBizLiked(List<Long> bizIds){
        Long userId = UserContext.getUser();
        List<LikedRecord> list = lambdaQuery()
                .in(LikedRecord::getBizId,bizIds)
                .eq(LikedRecord::getUserId,userId)
                .list();
        return list.stream().map(LikedRecord::getBizId).collect(Collectors.toSet());
    }
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize){

    }

}
