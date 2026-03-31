package com.tianji.learning.task;

import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.po.LearningLesson;
import com.tianji.learning.enums.LessonStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class LessonStatusCheckTask {

    private final IService<LearningLesson> lessonService;

    /**
     * 每分钟执行一次
     */
    @Scheduled(cron = "0 * * * * ?")
    public void lessonStatusCheck() {

        // 直接通过数据库条件更新
        lessonService.lambdaUpdate()
                .ne(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .le(LearningLesson::getExpireTime, LocalDateTime.now())
                .set(LearningLesson::getStatus, LessonStatus.EXPIRED)
                .update();
    }
}