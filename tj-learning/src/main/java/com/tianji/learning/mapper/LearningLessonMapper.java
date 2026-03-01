package com.tianji.learning.mapper;

import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

/**
 * <p>
 * 学生课程表 Mapper 接口
 * </p>
 *
 * @author author
 * @since 2026-02-20
 */
public interface LearningLessonMapper extends BaseMapper<LearningLesson> {
    @Select("SELECT SUM(week_freq)  FROM learning_lesson WHERE user_id = #{userId} AND plan_status = 1 AND status IN (0, 1)")
    Integer queryTotalPlan(Long userId);
}
