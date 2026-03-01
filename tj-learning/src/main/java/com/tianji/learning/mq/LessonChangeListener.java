package com.tianji.learning.mq;

import com.tianji.api.dto.trade.OrderBasicDTO;
import com.tianji.common.constants.MqConstants;
import com.tianji.common.utils.CollUtils;
import com.tianji.learning.service.ILearningLessonService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LessonChangeListener {
    private final ILearningLessonService lessonService;

    //监听课程购买
    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "learning.lesson.pay.queue", durable = "true"),
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
            key = MqConstants.Key.ORDER_DELAY_KEY

    ))
    public void listenLessonPay(OrderBasicDTO order) {
        if(order==null||order.getUserId()==null|| CollUtils.isEmpty(order.getCourseIds())){
            log.error("接受mq消息有误 订单数据为空");
            return;
        }
        lessonService.addUserLessons(order.getUserId(),order.getCourseIds());
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(value = "trade.refund.result.queue",durable = "true") ,
            exchange = @Exchange(name = MqConstants.Exchange.ORDER_EXCHANGE, type = ExchangeTypes.TOPIC),
         key = MqConstants.Key.ORDER_REFUND_KEY
    ))
    public void listenLessonRefund(OrderBasicDTO dto){
        if(dto == null || dto.getUserId() == null || CollUtils.isEmpty(dto.getCourseIds())){
            log.error("接收到MQ消息有误，订单数据为空");
            return;//不能抛出异常 否则开启了重试
        }
        // 删除课程表中删除该课程
        log.debug("监听到用户{}的订单{}，需要在课表删除课程{}中"
                , dto.getUserId(), dto.getOrderId(), dto.getCourseIds());
        List<Long> courseIds = dto.getCourseIds();
        courseIds.forEach(courseId -> lessonService.deleteCourseFromLesson(dto.getUserId(), courseId));
    }

}
