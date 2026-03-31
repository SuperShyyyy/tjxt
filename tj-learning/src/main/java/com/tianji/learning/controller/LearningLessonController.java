package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.service.ILearningLessonService;
import com.tianji.learning.domain.dto.LearningPlanDTO;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.domain.vo.LearningPlanPageVO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * <p>
 * 学生课程表 前端控制器
 * </p>
 *
 * @author author
 * @since 2026-02-20
 */
@RestController
@RequestMapping("/lessons")
@Api(tags = "我的课表接口")
@RequiredArgsConstructor
public class LearningLessonController {
    private final ILearningLessonService lessonService;
    @ApiOperation("分页查询我的课表")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(PageQuery query){
        return lessonService.queryMyLessons(query);
    }

    @ApiOperation("检验当前课程是否报名")
    @GetMapping("/{couseId}/valid")
    public Long isLessonValid(
            @ApiParam(value = "课程 id",example = "1") @PathVariable("courseId") Long courseId
    ) {
       return lessonService.isLessonValid(courseId);
    }

    /**
     * 创建学习计划
     * @param dto
     */
    @ApiOperation("创建学习计划")
    @PostMapping("plans")
    public void createLearningPlans(@Validated @RequestBody LearningPlanDTO dto){
        lessonService.createLearningPlan(dto);
    }

    @ApiOperation("分页查询我的学习计划")
    @GetMapping("/plans")
    public LearningPlanPageVO queryMyPlans(PageQuery query){
        return lessonService.queryMyPlans(query);
    }


    @ApiOperation("查询用户课表中指定课程状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryLessonByCourseId(@PathVariable("courseId") Long courseId){
        return lessonService.queryLessonByCourseId(courseId);
    }

    @ApiOperation("查询正在学习的课程")
    @GetMapping("now")
    public LearningLessonVO queryMyCurrentCourse(){
        return lessonService.queryMyCurrentCourse();
    }


    @ApiOperation("手动删除当前课程")
    @DeleteMapping
    public void deleteMyLesson(@PathVariable("courseId") Long courseId){
        Long userId = UserContext.getUser();
        lessonService.deleteCourseFromLesson(userId,courseId);
    }


    @ApiOperation("统计课程报名人数")
    @GetMapping("/{courseId}/count")
    public Integer queryCountByCourseId(@PathVariable("courseId")Long courseId){
        return lessonService.queryCountByCourseId(courseId);
    }

}
