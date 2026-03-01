package com.tianji.learning.service;

import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.po.LearningLesson;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.util.List;

/**
 * <p>
 * 学生课程表 服务类
 * </p>
 *
 * @author author
 * @since 2026-02-20
 */
public interface ILearningLessonService extends IService<LearningLesson> {

    LearningLesson queryByUserIdAndCourseId(Long userId, Long courseId) ;

    void addUserLessons(Long userId, List<Long> courseIds);

    PageDTO<LearningLessonVO> queryMyLessons(PageQuery query);

    Long isLessonValid(Long courseId);

    LearningPlanPageVO queryMyPlans(PageQuery query);

    void createLearningPlan(@NotNull @Min(1) Long courseId, @NotNull @Range(min = 1, max = 50) Integer freq);

    LearningLessonVO queryLessonByCourseId(Long courseId);

    LearningLessonVO queryMyCurrentCourse();

    void deleteCourseFromLesson(Long userId, Long courseId);

    Integer queryCountByCourseId(Long courseId);
}
