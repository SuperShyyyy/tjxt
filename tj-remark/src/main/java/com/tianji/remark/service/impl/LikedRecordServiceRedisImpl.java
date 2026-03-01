package com.tianji.remark.service.impl;

import com.tianji.api.dto.remark.LikeTimesDTO;
import com.tianji.common.autoconfigure.mq.RabbitMqHelper;
import com.tianji.common.utils.CollUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.remark.constants.RedisConstants;
import com.tianji.remark.domain.dto.LikeRecordFormDTO;
import com.tianji.remark.domain.dto.LikedTimesDTO;
import com.tianji.remark.domain.po.LikedRecord;
import com.tianji.remark.mapper.LikedRecordMapper;
import com.tianji.remark.service.ILikedRecordService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.swagger.models.auth.In;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.rabbit.connection.PublisherCallbackChannel;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.validation.Valid;

import java.sql.SQLTransactionRollbackException;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
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
@Service
@RequiredArgsConstructor
public class LikedRecordServiceRedisImpl extends ServiceImpl<LikedRecordMapper, LikedRecord> implements ILikedRecordService {
    private final AmqpTemplate amqpTemplate;
    private final RabbitMqHelper mqHelper;
    private final RedisTemplate redisTemplate;

    @Override
    public void addLikeRecord(@Valid LikeRecordFormDTO dto) {
        //1.获取登录用户id
        Long id = UserContext.getUser();
        boolean success = dto.getLiked() ? like(dto) : unlike(dto);
        if (!success) {
            return;
        }
        Long likesTimes = redisTemplate.opsForSet().size(RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId());
        if (likesTimes == null) return;
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX + dto.getBizType();
        redisTemplate.opsForZSet()
                .add(key,
                        dto.getBizId().toString(), likesTimes
                );
    }

    private boolean like(LikeRecordFormDTO dto) {
        Long userId = UserContext.getUser();
        //2.获取key
        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId();
        //3.执行 sadd 命令
        Long result = redisTemplate.opsForSet().add(key, userId.toString());
        return result != null && result > 0;
    }

    private boolean unlike(LikeRecordFormDTO dto) {
        Long userId = UserContext.getUser();


        String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + dto.getBizId();
        Long result = redisTemplate.opsForSet().remove(key, userId.toString());
        // 删除记录

        return result != null && result > 0;
    }


    @Override
    public Set<Long> isBizLiked(List<Long> bizIds) {
        Long userId = UserContext.getUser();
        // 2. 查询点赞状态
        List<Object> objects = redisTemplate.executePipelined(new RedisCallback<Object>() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                StringRedisConnection src = (StringRedisConnection) connection;
                for (Long bizId : bizIds) {
                    String key = RedisConstants.LIKES_BIZ_KEY_PREFIX + bizId;
                    src.sIsMember(key, userId.toString());
                }
                return null;
            }
        });
        // 3. 返回结果
        Set<Long> set = new HashSet<>();
        for (int i = 0; i < objects.size(); i++) {
            Boolean o = (Boolean) objects.get(i);
            if (o) {
                set.add(bizIds.get(i));
            }
        }
        return set;
    }
    @Override
    public void readLikedTimesAndSendMessage(String bizType, int maxBizSize){
        //1.读取并移除点赞总数
        String key = RedisConstants.LIKES_TIMES_KEY_PREFIX+bizType;
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().popMin(key, maxBizSize);
        if(CollUtils.isEmpty(tuples)){
            return;
        }
        //2.数据转换
        List<LikedTimesDTO> list = new ArrayList<>(tuples.size());
        for(ZSetOperations.TypedTuple<String> tuple: tuples){
            String bizId = tuple.getValue();
            Double likedTimes = tuple.getScore();
            if(bizId==null||likedTimes==null){
                return;
            }
            list.add(LikedTimesDTO.of(Long.valueOf(bizId),likedTimes.intValue()));
        }
        //3.发送MQ消息
        mqHelper.send(
                LIKE_RECORD_EXCHANGE,
                StringUtils.format(LIKED_TIMES_KEY_TEMPLATE,bizType),
                list
        );
    }
}
