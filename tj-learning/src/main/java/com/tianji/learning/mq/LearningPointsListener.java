package com.tianji.learning.mq;

import com.tianji.common.constants.MqConstants;
import com.tianji.learning.enums.PointsRecordType;
import com.tianji.learning.service.IPointsRecordService;
import com.tianji.learning.service.ISignRecordService;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class LearningPointsListener {
    private final IPointsRecordService recordService;

    @RabbitListener(bindings = @QueueBinding(
        value = @Queue(name ="qa.points.queues",durable ="true" ),
        exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.WRITE_REPLY
    ))
    public void listenWriteReplyMessage(Long userId){
        recordService.addPointsRecord(userId,5, PointsRecordType.QA);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name ="sign.points.queues",durable ="true" ),
            exchange = @Exchange(name = MqConstants.Exchange.LEARNING_EXCHANGE,type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.SIGN_IN
    ))
    public void listenSignInMessage(SignInMessage msg){
        log.debug("签到增加的积分 消费到消息 {}",msg);
        recordService.addPointsRecord(msg,PointsRecordType.SIGN);
    }
}
